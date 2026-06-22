package com.petrichor.backend.course

import com.petrichor.backend.asset.Asset
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 코스 ↔ 에셋 조합 + 기본 게인 + 습도 곡선(시퀀싱 입력).
 * V1__init.sql `course_assets` 테이블과 1:1 매핑(ddl-auto=validate).
 *
 * - 복합키 @EmbeddedId(course_id, asset_id)
 * - asset은 @MapsId("assetId")로 복합키의 assetId 컬럼을 공유(asset_id FK 1개)
 * - humidityCurve: JSONB → @JdbcTypeCode(JSON)
 */
@Entity
@Table(name = "course_assets")
class CourseAsset(
    @EmbeddedId
    val id: CourseAssetId,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("courseId")
    @JoinColumn(name = "course_id")
    val course: Course,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("assetId")
    @JoinColumn(name = "asset_id")
    val asset: Asset,

    @Column(name = "base_gain", nullable = false)
    val baseGain: Float = 1.0f,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "humidity_curve", nullable = false, columnDefinition = "jsonb")
    val humidityCurve: Map<String, Any> = emptyMap(),

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,
)
