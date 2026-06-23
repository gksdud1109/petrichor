package com.petrichor.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Phase 1 events 배치 e2e (G1.5): POST /api/v1/events 배치(배열) 수신.
 * - 유효 배치 → 202 Accepted.
 * - 무효(타입 누락 / 배치 크기 초과) → 400 + Content-Type application/problem+json + 오류 바디 단언.
 *   spring.mvc.problemdetails.enabled=true 로 Spring이 RFC7807 problem+json을 자동 생성함.
 * Docker 데몬 필요 — 로컬/CI(docker 가용) 환경에서 실행.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventsIntegrationTest {

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
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    private fun jsonEntity(body: String): HttpEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(body, headers)
    }

    @Test
    fun `valid batch returns 202`() {
        val body = """
            [
              { "type": "session_start", "anonId": "11111111-1111-1111-1111-111111111111" },
              { "type": "slider_change", "courseId": 1, "payload": { "humidity": 0.7 } },
              { "type": "heartbeat", "occurredAt": "2026-06-23T00:00:00Z" }
            ]
        """.trimIndent()

        val response = restTemplate.postForEntity("/api/v1/events", jsonEntity(body), String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
    }

    @Test
    fun `batch with missing type returns 400 problem+json`() {
        val body = """
            [
              { "type": "session_start" },
              { "anonId": "22222222-2222-2222-2222-222222222222" }
            ]
        """.trimIndent()

        val response = restTemplate.postForEntity("/api/v1/events", jsonEntity(body), String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        // spring.mvc.problemdetails.enabled=true → Content-Type: application/problem+json
        assertThat(response.headers.contentType.toString())
            .contains("application/problem+json")
        // RFC7807 필수 필드 단언
        assertThat(response.body)
            .contains("\"status\":400")
            .contains("\"title\":")
    }

    @Test
    fun `batch over max size returns 400 problem+json`() {
        val elements = (1..101).joinToString(",") { """{ "type": "heartbeat" }""" }
        val body = "[$elements]"

        val response = restTemplate.postForEntity("/api/v1/events", jsonEntity(body), String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.headers.contentType.toString())
            .contains("application/problem+json")
        assertThat(response.body)
            .contains("\"status\":400")
    }
}
