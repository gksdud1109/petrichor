package com.petrichor.backend.media.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 오브젝트 스토리지 설정(@ConfigurationProperties "petrichor.storage").
 *
 * .env(S3_ENDPOINT/S3_REGION/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET)와 정합한 env 기반 기본값을
 * application.yaml에서 주입한다. 비공개 버킷 + 짧은 presign(서버 강제 키)을 전제로 한다.
 *
 * - endpoint: S3 호환 엔드포인트(MinIO/R2). MinIO는 path-style 강제 필요.
 * - region: SDK 서명에 필요(MinIO는 임의값 가능, 기본 us-east-1).
 * - accessKey / secretKey: 정적 자격증명(워커/서버 전용, 익명 접근 차단 전제).
 * - bucket: 미디어 버킷명.
 * - presignTtlSeconds: 다운로드/업로드 presigned URL 만료(기본 600초=10분). 짧게 유지.
 *
 * ⚠️  data class 자동 toString()은 secretKey를 평문으로 노출한다 → toString()을 오버라이드해 마스킹.
 */
@ConfigurationProperties(prefix = "petrichor.storage")
data class StorageProperties(
    val endpoint: String = "http://localhost:9000",
    val region: String = "us-east-1",
    val accessKey: String = "petrichor",
    val secretKey: String = "petrichor-secret",
    val bucket: String = "petrichor-media",
    val presignTtlSeconds: Long = 600,
) {
    /** secretKey를 마스킹해 로그·오류 메시지에 평문이 노출되지 않도록 한다. */
    override fun toString(): String =
        "StorageProperties(endpoint=$endpoint, region=$region, accessKey=$accessKey, " +
            "secretKey=***, bucket=$bucket, presignTtlSeconds=$presignTtlSeconds)"
}
