package com.petrichor.backend.media.storage

import java.util.UUID

/**
 * 오브젝트 키 정규화·검증(순수 로직, I/O 없음 → 로컬 단위테스트 가능).
 *
 * signed URL 오남용·스토리지 오염을 막기 위한 키 경계 강제기.
 *   - 업로드 키는 **서버가 생성한 UUID**만 허용(클라이언트 지정 키 절대 신뢰 금지).
 *   - 모든 키는 허용 프리픽스(originals/, variants/) 하위로 강제.
 *   - path traversal(`../`, 절대경로, 백슬래시, 빈 세그먼트)을 거부.
 *
 * 키 형식: `<prefix>/<uuid>[/<suffix>]`  예) `originals/3f2c.../source`
 */
object KeyScoping {

    /** 원본 업로드 프리픽스(비공개 버킷). */
    const val ORIGINALS_PREFIX = "originals"

    /** 변환물 프리픽스(presign 전용 노출). */
    const val VARIANTS_PREFIX = "variants"

    private val ALLOWED_PREFIXES = setOf(ORIGINALS_PREFIX, VARIANTS_PREFIX)

    /** UUID v4 정규식(소문자 16진수, 하이픈 포함). */
    private val UUID_REGEX =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    /**
     * 서버 생성 업로드 키. 클라이언트는 키를 지정할 수 없다 — UUID를 서버가 발급한다.
     *
     * @param prefix    허용 프리픽스(originals/variants). 그 외는 거부.
     * @param suffix    선택적 하위 경로(예: "source"). traversal·구분자 포함 시 거부.
     * @return `<prefix>/<uuid>[/<suffix>]` 형식의 안전한 키
     */
    fun newUploadKey(prefix: String = ORIGINALS_PREFIX, suffix: String? = null): String {
        requirePrefix(prefix)
        val uuid = UUID.randomUUID().toString()
        if (suffix == null) return "$prefix/$uuid"
        requireSafeSegment(suffix)
        return "$prefix/$uuid/$suffix"
    }

    /**
     * 외부에서 들어온 키를 검증·정규화한다(다운로드/삭제/존재확인 등).
     * 통과 시 입력과 동일한 키를 반환, 위반 시 [IllegalArgumentException].
     *
     * 검증:
     *   - 비어있지 않음
     *   - 절대경로(`/`로 시작)·백슬래시 금지
     *   - 허용 프리픽스 하위
     *   - 세그먼트별 traversal(`.`, `..`, 빈 세그먼트) 거부
     */
    fun validateKey(key: String): String {
        require(key.isNotBlank()) { "키가 비어 있음" }
        require(!key.startsWith("/")) { "절대경로 키 거부: $key" }
        require(!key.contains('\\')) { "백슬래시 키 거부: $key" }

        val segments = key.split('/')
        require(segments.size >= 2) { "키는 <prefix>/<...> 형식이어야 함: $key" }
        requirePrefix(segments.first())
        segments.drop(1).forEach { requireSafeSegment(it) }
        return key
    }

    private fun requirePrefix(prefix: String) {
        require(prefix in ALLOWED_PREFIXES) {
            "허용되지 않은 프리픽스: '$prefix' (허용: $ALLOWED_PREFIXES)"
        }
    }

    /** 단일 경로 세그먼트 안전성: traversal·구분자·빈값 거부. */
    private fun requireSafeSegment(segment: String) {
        require(segment.isNotBlank()) { "빈 경로 세그먼트 거부" }
        require(segment != "." && segment != "..") { "traversal 세그먼트 거부: '$segment'" }
        require(!segment.contains('/') && !segment.contains('\\')) {
            "경로 구분자 포함 세그먼트 거부: '$segment'"
        }
    }
}
