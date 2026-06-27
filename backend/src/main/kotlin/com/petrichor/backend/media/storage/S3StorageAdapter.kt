package com.petrichor.backend.media.storage

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

/**
 * [StoragePort]의 S3/MinIO/R2 구현(AWS SDK v2).
 *
 * MinIO/R2 호환 구성:
 *   - endpointOverride: StorageProperties.endpoint(예: http://localhost:9000).
 *   - pathStyleAccessEnabled(true): MinIO는 가상호스트 버킷 라우팅 미지원 → path-style 강제.
 *   - region + 정적 자격증명: StorageProperties.
 *
 * signed URL 정책:
 *   - 다운로드: GET 전용, presignTtlSeconds(짧음).
 *   - 업로드: PUT 전용, **서버 생성 UUID 키**([KeyScoping.newUploadKey]),
 *     Content-Type을 [ALLOWED_CONTENT_TYPES] allowlist로 검증 + 서명에 결속.
 *     maxBytes는 안내값([PresignedUpload.maxBytes] doc 참조) — 실제 크기 강제는 US-5에서 완성.
 *
 * S3Client/S3Presigner는 thread-safe → 빈으로 1회 생성해 재사용.
 * Spring 컨테이너 종료 시 [@PreDestroy]로 닫힌다.
 */
@Component
class S3StorageAdapter(
    private val props: StorageProperties,
) : StoragePort, AutoCloseable {

    companion object {
        /**
         * 업로드 허용 Content-Type allowlist.
         * 여기 없는 타입은 [createPresignedUpload]에서 [IllegalArgumentException]으로 거부한다.
         * 실제 컨테이너·코덱 검증(매직바이트/ffprobe)은 트랜스코드 전 US-5에서 수행한다.
         */
        val ALLOWED_CONTENT_TYPES: Set<String> = setOf(
            "video/mp4",
            "video/webm",
            "video/ogg",
            "audio/mpeg",
            "audio/ogg",
            "audio/wav",
            "audio/webm",
            "image/jpeg",
            "image/png",
            "image/webp",
        )
    }

    private val credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(props.accessKey, props.secretKey),
    )

    private val region: Region = Region.of(props.region)

    private val s3: S3Client = S3Client.builder()
        .endpointOverride(URI(props.endpoint))
        .region(region)
        .credentialsProvider(credentials)
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    private val presigner: S3Presigner = S3Presigner.builder()
        .endpointOverride(URI(props.endpoint))
        .region(region)
        .credentialsProvider(credentials)
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    private val presignTtl: Duration = Duration.ofSeconds(props.presignTtlSeconds)

    override fun putObject(key: String, bytes: ByteArray, contentType: String) {
        val safeKey = KeyScoping.validateKey(key)
        val request = PutObjectRequest.builder()
            .bucket(props.bucket)
            .key(safeKey)
            .contentType(contentType)
            .contentLength(bytes.size.toLong())
            .build()
        s3.putObject(request, RequestBody.fromBytes(bytes))
    }

    override fun getSignedDownloadUrl(key: String): URI {
        val safeKey = KeyScoping.validateKey(key)
        val getRequest = GetObjectRequest.builder()
            .bucket(props.bucket)
            .key(safeKey)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(presignTtl)
            .getObjectRequest(getRequest)
            .build()
        return presigner.presignGetObject(presignRequest).url().toURI()
    }

    override fun createPresignedUpload(contentType: String, maxBytes: Long): PresignedUpload {
        require(maxBytes > 0) { "maxBytes는 0보다 커야 함: $maxBytes" }
        require(contentType in ALLOWED_CONTENT_TYPES) {
            "허용되지 않은 Content-Type: '$contentType' (허용: $ALLOWED_CONTENT_TYPES)"
        }

        // 키는 서버가 생성 — 클라이언트는 키를 지정/변경할 수 없다.
        val key = KeyScoping.newUploadKey(prefix = KeyScoping.ORIGINALS_PREFIX, suffix = "source")

        // Content-Type을 서명에 결속(업로드 시 동일 헤더 강제, 불일치 시 스토리지 거부).
        // maxBytes는 SigV4 query presign 한계로 스토리지 측 강제 불가 → PresignedUpload에 안내값으로 반환.
        // 실제 크기 강제는 업로드 후 headObject().contentLength() 검증(US-5 필수 게이트)으로 완성한다.
        val putRequest = PutObjectRequest.builder()
            .bucket(props.bucket)
            .key(key)
            .contentType(contentType)
            .build()
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(presignTtl)
            .putObjectRequest(putRequest)
            .build()
        val presigned = presigner.presignPutObject(presignRequest)

        return PresignedUpload(
            key = key,
            url = presigned.url().toURI(),
            method = "PUT",
            requiredHeaders = mapOf("Content-Type" to contentType),
            maxBytes = maxBytes,
            expiresInSeconds = props.presignTtlSeconds,
        )
    }

    override fun deleteObject(key: String) {
        val safeKey = KeyScoping.validateKey(key)
        s3.deleteObject { it.bucket(props.bucket).key(safeKey) }
    }

    override fun exists(key: String): Boolean {
        val safeKey = KeyScoping.validateKey(key)
        return try {
            s3.headObject(
                HeadObjectRequest.builder().bucket(props.bucket).key(safeKey).build(),
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        }
    }

    /** Spring 컨테이너 종료 시 S3Client·S3Presigner를 닫아 커넥션 풀을 반환한다. */
    @PreDestroy
    override fun close() {
        s3.close()
        presigner.close()
    }
}
