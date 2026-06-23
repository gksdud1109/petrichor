package com.petrichor.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Phase 1 레이트리밋 e2e (G1.3): per-IP token bucket을 테스트 property로 낮춰(capacity=3) 결정적 검증.
 * - trust-forwarded-for=true + 고유 X-Forwarded-For IP로 각 테스트 버킷을 격리(@DirtiesContext 없이).
 * - 한도 내 호출은 200, 초과 호출은 429 + Retry-After/X-RateLimit-* 헤더.
 * - actuator 제외 증명: X-Forwarded-For를 변えても /actuator/health 는 레이트리밋 없음.
 * Docker 데몬 필요 — 로컬/CI(docker 가용) 환경에서 실행.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimitIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))

        @Container
        @JvmStatic
        val redis = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            // 결정적 검증: 한도를 낮추고, XFF 신뢰 활성화(테스트에서 IP를 직접 제어하기 위해).
            registry.add("petrichor.ratelimit.capacity") { 3 }
            registry.add("petrichor.ratelimit.refill-tokens") { 3 }
            registry.add("petrichor.ratelimit.refill-period-seconds") { 60 }
            registry.add("petrichor.ratelimit.trust-forwarded-for") { true }
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    /** 고유 X-Forwarded-For IP를 헤더에 실어 GET 요청 — 테스트별 버킷 격리용. */
    private fun getWithIp(url: String, fakeIp: String) =
        restTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { set("X-Forwarded-For", fakeIp) }),
            String::class.java,
        )

    @Test
    fun `requests within capacity return 200 then exceeding returns 429 with retry-after`() {
        // 이 테스트 전용 IP — 다른 테스트와 버킷 공유 없음.
        val ip = "10.0.0.1"

        repeat(3) {
            val ok = getWithIp("/api/v1/courses", ip)
            assertThat(ok.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(ok.headers.getFirst("X-RateLimit-Limit")).isEqualTo("3")
        }

        val limited = getWithIp("/api/v1/courses", ip)
        assertThat(limited.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(limited.headers.getFirst("Retry-After")).isNotNull()
        assertThat(limited.headers.getFirst("Retry-After")!!.toInt()).isGreaterThanOrEqualTo(1)
        assertThat(limited.headers.getFirst("X-RateLimit-Limit")).isEqualTo("3")
        assertThat(limited.headers.getFirst("X-RateLimit-Remaining")).isEqualTo("0")
        assertThat(limited.body).contains("Too Many Requests")
    }

    @Test
    fun `actuator health is excluded from rate limiting`() {
        // actuator는 shouldNotFilter로 제외 — XFF IP를 아무리 써도 레이트리밋 헤더 없음.
        val ip = "10.0.0.2"
        repeat(10) {
            val response = getWithIp("/actuator/health", ip)
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.headers.getFirst("X-RateLimit-Limit")).isNull()
        }
    }

    @Test
    fun `different IPs have independent buckets`() {
        // IP A로 capacity를 소진 → IP B는 여전히 통과.
        val ipA = "10.0.0.3"
        val ipB = "10.0.0.4"

        repeat(3) { getWithIp("/api/v1/courses", ipA) }
        assertThat(getWithIp("/api/v1/courses", ipA).statusCode)
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)

        // IP B는 별도 버킷 — 차단되지 않아야 함.
        assertThat(getWithIp("/api/v1/courses", ipB).statusCode)
            .isEqualTo(HttpStatus.OK)
    }
}
