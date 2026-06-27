package com.petrichor.backend.media.storage

import java.net.URI

/**
 * 오브젝트 스토리지 포트(헥사고날 — 인터페이스). 구현은 [S3StorageAdapter](MinIO/R2/S3).
 *
 * 설계 원칙(PLAN-REVIEW 2A signed URL):
 *   - 다운로드 presign: GET 전용, 짧은 TTL, 객체 키 단위.
 *   - 업로드 presign: PUT 전용, **서버가 UUID 키 강제**(클라 지정 키 불신뢰),
 *     Content-Type allowlist 검증, Content-Length 상한([PresignedUpload.maxBytes]) 명시.
 *   - 모든 키는 [KeyScoping]으로 정규화·프리픽스 강제(traversal 차단).
 *
 * ⚠️  Content-Length 상한 강제 계약:
 *   SigV4 query presign(PUT)은 스토리지 측에서 Content-Length-Range를 강제할 수 없다.
 *   [PresignedUpload.maxBytes]는 클라이언트에 전달되는 **안내값**이다.
 *   실제 크기 강제는 업로드 완료 후 `headObject().contentLength()`로 검증해야 한다.
 *   → **US-5 필수 게이트**: 업로드 후 headObject 크기 검증을 완료하지 않으면
 *     트랜스코드 잡을 QUEUED 상태로 진행시키지 말 것.
 */
interface StoragePort {

    /** 바이트를 객체로 저장(서버 측 PUT). 키는 [KeyScoping]로 검증된 값이어야 한다. */
    fun putObject(key: String, bytes: ByteArray, contentType: String)

    /** GET presigned 다운로드 URL(짧은 TTL). 키는 검증 후 서명한다. */
    fun getSignedDownloadUrl(key: String): URI

    /**
     * PUT presigned 업로드를 발급한다. **키는 서버가 UUID로 생성**한다(클라 지정 불가).
     *
     * @param contentType 업로드 허용 Content-Type. 구현은 allowlist를 강제하며
     *                    서명에 결속된다(업로드 시 동일 헤더 필수, 불일치 시 스토리지 거부).
     * @param maxBytes    Content-Length 상한 안내값(서명 조건 결속 불가 — 위 클래스 doc 참조).
     *                    **실제 크기 강제는 업로드 후 headObject 검증(US-5 필수 게이트)으로 완성한다.**
     */
    fun createPresignedUpload(contentType: String, maxBytes: Long): PresignedUpload

    /** 객체 삭제. 키는 검증된 값이어야 한다. */
    fun deleteObject(key: String)

    /** 객체 존재 여부. */
    fun exists(key: String): Boolean
}

/**
 * presigned 업로드 발급 결과.
 *
 * @param key             서버가 생성한 객체 키(클라이언트가 바꿀 수 없음).
 * @param url             PUT을 보낼 presigned URL.
 * @param method          HTTP 메서드(항상 "PUT").
 * @param requiredHeaders 업로드 시 클라이언트가 반드시 동일하게 전송해야 하는 헤더
 *                        (서명에 포함된 Content-Type 등). 누락/불일치 시 스토리지가 거부.
 * @param maxBytes        Content-Length 상한 **안내값**.
 *                        SigV4 PUT presign은 스토리지 측 length-range 강제가 불가하므로,
 *                        실제 크기 검증은 업로드 후 headObject().contentLength()로 수행해야 한다.
 *                        **US-5 필수 게이트**: 크기 초과 시 객체 삭제 + 잡 FAILED 처리.
 * @param expiresInSeconds presign 만료(초).
 */
data class PresignedUpload(
    val key: String,
    val url: URI,
    val method: String,
    val requiredHeaders: Map<String, String>,
    val maxBytes: Long,
    val expiresInSeconds: Long,
)
