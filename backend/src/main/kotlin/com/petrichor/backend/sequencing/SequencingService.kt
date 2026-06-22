package com.petrichor.backend.sequencing

import com.petrichor.backend.common.CourseNotFoundException
import com.petrichor.backend.course.CourseAsset
import com.petrichor.backend.course.CourseRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 시퀀싱 서비스: 코스를 에셋 조합으로 '구성'해 세션 매니페스트를 반환(정적 JSON 아님).
 *
 * - course_assets 행을 바꾸면 매니페스트가 달라진다(G1.1 비정적 증명).
 * - license 게이트: license가 null/blank인 에셋은 매니페스트에서 제외.
 * - 캐시: "course-session", 키=courseId. 매니페스트 schemaVersion은 캐시 키 일부로 간주(아래 주석).
 *   추후 course/course_asset 쓰기 경로에서 @CacheEvict("course-session", key=courseId)로 무효화.
 */
@Service
class SequencingService(
    private val courseRepository: CourseRepository,
) {

    /**
     * 캐시 키 설계 주석:
     *  - 논리 캐시 키 = courseId + manifestSchemaVersion(=SCHEMA_VERSION).
     *  - 현재 schemaVersion은 상수이므로 물리 키는 courseId만으로 충분하되,
     *    매니페스트 계약(schemaVersion)이 올라가면 캐시명 자체를 버저닝하거나
     *    key를 "#courseId + '-' + version"으로 확장해 stale 매니페스트 서빙을 막는다.
     *  - 습도 등 클라이언트 파라미터는 캐시 키에서 분리(매니페스트는 default만 담음).
     */
    @Transactional(readOnly = true)
    @Cacheable("course-session", key = "#courseId")
    fun compose(courseId: Long): SessionManifest {
        val course = courseRepository.findWithAssetsById(courseId)
            .orElseThrow { CourseNotFoundException(courseId) }

        // license 게이트 + 결정적 정렬(sortOrder, 동률 시 assetId)
        val licensed = course.courseAssets
            .filter { !it.asset.license.isNullOrBlank() }
            .sortedWith(compareBy({ it.sortOrder }, { it.id.assetId }))

        val video = licensed
            .filter { it.asset.kind == KIND_VIDEO_LOOP }
            .map { it.toVideoLayer() }

        val music = licensed
            .filter { it.asset.kind == KIND_MUSIC }
            .map { it.toAudioLayer() }

        val ambient = licensed
            .filter { it.asset.kind == KIND_AMBIENT }
            .map { it.toAudioLayer() }

        return SessionManifest(
            courseId = requireNotNull(course.id) { "persisted course must have id" },
            region = course.region,
            schemaVersion = SCHEMA_VERSION,
            video = video,
            audio = AudioLayers(music = music, ambient = ambient),
            grade = course.gradeConfig,
        )
    }

    private fun CourseAsset.toVideoLayer(): VideoLayer =
        VideoLayer(
            assetId = id.assetId,
            url = mediaUrl(asset.storageKey),
            loopInMs = asset.loopInMs,
            loopOutMs = asset.loopOutMs,
        )

    private fun CourseAsset.toAudioLayer(): AudioLayer =
        AudioLayer(
            assetId = id.assetId,
            url = mediaUrl(asset.storageKey),
            baseGain = baseGain,
            humidityCurve = humidityCurve,
        )

    /**
     * Phase 1: storageKey 기반 상대 경로. signed URL은 Phase 2(S3/R2 presign).
     */
    private fun mediaUrl(storageKey: String): String = "/media/$storageKey"

    companion object {
        const val SCHEMA_VERSION = "1"
        private const val KIND_VIDEO_LOOP = "video_loop"
        private const val KIND_MUSIC = "music"
        private const val KIND_AMBIENT = "ambient"
    }
}
