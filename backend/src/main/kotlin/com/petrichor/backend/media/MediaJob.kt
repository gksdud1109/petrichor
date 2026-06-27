package com.petrichor.backend.media

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * 미디어 트랜스코드 잡. V3__media.sql `media_jobs` 테이블과 1:1 매핑(ddl-auto=validate).
 *
 * - asset_id는 FK 컬럼만 보유(Asset 연관은 US-3+에서 필요 시 추가). 여기선 순수 도메인만.
 * - status: @Enumerated(STRING) + JobStatus(DB 값 = enum 이름 대문자).
 * - attempts: **PROCESSING 진입(실제 시도) 횟수**. markProcessing() 호출 시 +1.
 * - idempotencyKey: [idempotencyKey] 팩토리로 생성 → asset_variants UNIQUE(asset_id,codec,height)와
 *   같은 튜플을 공유, 코드로 결속됨. UNIQUE 제약으로 중복 잡 차단.
 * - createdAt/updatedAt: @PrePersist에서 앱(JVM)이 설정(시각 소스 단일화). DB DEFAULT now()는 폴백.
 * - version: 낙관적 락(@Version). 워커 동시성 충돌 감지는 US-3+에서 활용.
 *
 * 상태 전이는 [JobStateMachine]에 위임하는 도메인 메서드(markProcessing/markDone/markFailed/retryOrDead)로 노출.
 */
@Entity
@Table(name = "media_jobs")
class MediaJob(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "asset_id", nullable = false)
    val assetId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: JobStatus = JobStatus.QUEUED,

    /**
     * PROCESSING 진입(실제 시도) 횟수. markProcessing()이 PROCESSING으로 전이할 때 +1.
     * retryOrDead()는 이미 증가된 attempts로 한도를 판정한다.
     */
    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,

    /**
     * 멱등성 키. [idempotencyKey] 팩토리로 생성하면 asset_variants UNIQUE(asset_id,codec,height)와
     * 같은 튜플을 공유해 코드로 결속된다. 호출 측 강제는 US-3(enqueue 경로)에서 완성.
     */
    @Column(name = "idempotency_key", nullable = false, length = 120)
    val idempotencyKey: String,

    @Column
    var error: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,

    /** 낙관적 락. 워커 동시성 충돌 감지는 US-3+에서 활용. */
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3

        /**
         * 멱등성 키 팩토리. asset_variants의 UNIQUE(asset_id, codec, height) 튜플과 동일한
         * 값을 키로 사용해 잡과 변환물 행이 1:1로 결속된다.
         * 오디오처럼 height가 없는 경우 height=0 규약을 따른다.
         */
        fun idempotencyKey(assetId: Long, codec: String, height: Int): String =
            "asset:$assetId:$codec:$height"
    }

    /** QUEUED → PROCESSING. attempts를 +1(실제 시도 횟수 기록). */
    fun markProcessing() {
        status = JobStateMachine.next(status, JobEvent.START, attempts, maxAttempts)
        attempts += 1
    }

    /** PROCESSING → DONE. */
    fun markDone() {
        status = JobStateMachine.next(status, JobEvent.SUCCEED, attempts, maxAttempts)
        error = null
    }

    /** PROCESSING → FAILED. 실패 사유 기록. */
    fun markFailed(reason: String? = null) {
        status = JobStateMachine.next(status, JobEvent.FAIL, attempts, maxAttempts)
        error = reason
    }

    /**
     * FAILED → QUEUED(재시도) 또는 DEAD(한도 도달).
     * attempts는 markProcessing()에서 이미 증가됐으므로 여기서는 변경하지 않는다.
     *   attempts < maxAttempts  → QUEUED
     *   attempts >= maxAttempts → DEAD
     * @return 전이 후 상태(QUEUED 또는 DEAD)
     */
    fun retryOrDead(): JobStatus {
        val next = JobStateMachine.next(status, JobEvent.RETRY, attempts, maxAttempts)
        status = next
        return next
    }

    @PrePersist
    fun initTimestamps() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}
