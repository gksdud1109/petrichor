package com.petrichor.backend.media

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant

/**
 * 원본 → 변환 산출물(codec·resolution·url·bytes).
 * V3__media.sql `asset_variants` 테이블과 1:1 매핑(ddl-auto=validate).
 *
 * - asset_id는 FK 컬럼만 보유(Asset 연관은 US-3+에서 필요 시 추가).
 * - (asset_id, codec, height) UNIQUE로 멱등 업서트. height는 NOT NULL(오디오=0 규약).
 * - 멱등성 키는 [MediaJob.idempotencyKey]와 같은 튜플을 공유해 잡-변환물이 코드로 결속됨.
 * - createdAt: @PrePersist에서 앱(JVM)이 설정(시각 소스 단일화). DB DEFAULT now()는 폴백.
 */
@Entity
@Table(name = "asset_variants")
class AssetVariant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "asset_id", nullable = false)
    val assetId: Long,

    @Column(nullable = false, length = 20)
    val codec: String,

    @Column
    val width: Int? = null,

    /**
     * 출력 높이(px). 오디오처럼 영상 해상도가 없는 경우 0 규약.
     * NOT NULL이므로 Postgres NULLS DISTINCT 문제 없이 UNIQUE(asset_id,codec,height)가 중복 차단.
     */
    @Column(nullable = false)
    val height: Int = 0,

    @Column
    val bytes: Long? = null,

    @Column(name = "storage_key", nullable = false, length = 512)
    val storageKey: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {

    @PrePersist
    fun initTimestamps() {
        createdAt = Instant.now()
    }
}
