package com.petrichor.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Phase 0 헬스 e2e (G0.3): 실제 Postgres + Redis(Testcontainers)에서 앱이 부팅하고
 * Flyway V1 적용 후 /actuator/health 가 UP(db·redis 컴포넌트 포함)인지 증거화.
 * Docker 데몬 필요 — 로컬/CI(docker 가용) 환경에서 실행.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HealthIntegrationTest {

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
        fun redisProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `actuator health is UP`() {
        val body = restTemplate.getForObject("/actuator/health", String::class.java)
        // 집계 status가 UP이려면 모든 컴포넌트가 UP이어야 하므로, 컴포넌트 존재 + 최상위 UP을 함께 단언
        // (show-details: always 전제) → G0.3 증거를 실질화
        assertThat(body)
            .contains("\"status\":\"UP\"")
            .contains("\"db\"")
            .contains("\"redis\"")
    }
}
