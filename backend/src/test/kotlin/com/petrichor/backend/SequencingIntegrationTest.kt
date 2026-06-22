package com.petrichor.backend

import com.petrichor.backend.course.CourseRepository
import com.petrichor.backend.sequencing.SessionManifest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.cache.CacheManager
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Phase 1 시퀀싱 e2e (G1.1): 실제 PG+Redis(Testcontainers)에서 Flyway V1+V2(gyodae 시드) 적용 후
 * GET /api/v1/courses/{id}/session 이 서버 구성 매니페스트를 반환하는지 증거화.
 * - license 게이트: NULL-license 에셋 제외(개수로 증명).
 * - 비정적 증명(G1.1): course_assets 일부를 삭제하면 매니페스트가 달라짐.
 * Docker 데몬 필요 — 로컬/CI(docker 가용) 환경에서 실행.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SequencingIntegrationTest {

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

    @Autowired
    lateinit var courseRepository: CourseRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var cacheManager: CacheManager

    private fun gyodaeCourseId(): Long =
        courseRepository.findAll()
            .first { it.region == "gyodae" }
            .id!!

    @Test
    @Order(1)
    fun `session manifest is server-composed with license gate applied`() {
        val courseId = gyodaeCourseId()

        val response = restTemplate.getForEntity(
            "/api/v1/courses/$courseId/session",
            SessionManifest::class.java,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val manifest = response.body!!

        assertThat(manifest.courseId).isEqualTo(courseId)
        assertThat(manifest.region).isEqualTo("gyodae")
        assertThat(manifest.schemaVersion).isEqualTo("1")

        // video 4 / music 2 / ambient 2 — license NULL ambient(unlicensed.mp3)는 제외
        assertThat(manifest.video).hasSize(4)
        assertThat(manifest.audio.music).hasSize(2)
        assertThat(manifest.audio.ambient).hasSize(2)

        // license 게이트 증명: 제외된 에셋의 url은 매니페스트 어디에도 없어야 함
        val allUrls = manifest.video.map { it.url } +
            manifest.audio.music.map { it.url } +
            manifest.audio.ambient.map { it.url }
        assertThat(allUrls).noneMatch { it.contains("unlicensed") }
        assertThat(allUrls).allMatch { it.startsWith("/media/") }

        // grade_config 가 매니페스트 grade로 흐름
        assertThat(manifest.grade).containsKeys("grain", "cyan", "bloom", "downscale")
        assertThat(manifest.humidity.default).isEqualTo(0.5)
    }

    @Test
    @Order(2)
    fun `cached manifest deserializes correctly on a true cache hit (B1 regression)`() {
        val courseId = gyodaeCourseId()
        // 첫 호출: 캐시 미스 → 서버 구성 + Redis 저장
        val first = restTemplate.getForObject(
            "/api/v1/courses/$courseId/session",
            SessionManifest::class.java,
        )!!
        // 둘째 호출: eviction 없이 캐시 HIT → Redis에서 역직렬화.
        // 직렬화기 mapper에 jackson-module-kotlin이 없으면 여기서 500(B1 회귀).
        val secondEntity = restTemplate.getForEntity(
            "/api/v1/courses/$courseId/session",
            SessionManifest::class.java,
        )
        assertThat(secondEntity.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(secondEntity.body).isEqualTo(first)
    }

    @Test
    @Order(3)
    fun `manifest is non-static — changing course_assets changes the manifest (G1·1)`() {
        val courseId = gyodaeCourseId()

        val before = restTemplate.getForObject(
            "/api/v1/courses/$courseId/session",
            SessionManifest::class.java,
        )!!
        assertThat(before.video).hasSize(4)
        val droppedAssetId = before.video.maxByOrNull { it.assetId }!!.assetId

        // course_assets 한 행(비디오)을 삭제 → 시퀀싱 입력이 바뀐다.
        val deleted = jdbcTemplate.update(
            "DELETE FROM course_assets WHERE course_id = ? AND asset_id = ?",
            courseId,
            droppedAssetId,
        )
        assertThat(deleted).isEqualTo(1)

        // 쓰기 경로의 @CacheEvict를 시뮬레이션(Phase 1 캐시 무효화 메커니즘과 정합).
        cacheManager.getCache("course-session")?.evict(courseId)

        val after = restTemplate.getForObject(
            "/api/v1/courses/$courseId/session",
            SessionManifest::class.java,
        )!!

        // 비정적 증명: 입력 행을 바꾸자 매니페스트가 달라짐(정적 JSON이 아님).
        assertThat(after.video).hasSize(3)
        assertThat(after.video.map { it.assetId }).doesNotContain(droppedAssetId)
        assertThat(after.audio.music).hasSize(2)
        assertThat(after.audio.ambient).hasSize(2)
    }
}
