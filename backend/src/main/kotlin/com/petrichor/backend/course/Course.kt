package com.petrichor.backend.course

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 코스 = 무드 단위. V1__init.sql `courses` 테이블과 1:1 매핑(ddl-auto=validate).
 *
 * - moodTags: TEXT[] → @JdbcTypeCode(ARRAY)
 * - gradeConfig: JSONB(셰이더 파라미터) → @JdbcTypeCode(JSON), Map<String, Any>
 */
@Entity
@Table(name = "courses")
class Course(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val region: String,

    @Column(nullable = false)
    val title: String,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "mood_tags", nullable = false, columnDefinition = "text[]")
    val moodTags: List<String> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grade_config", nullable = false, columnDefinition = "jsonb")
    val gradeConfig: Map<String, Any> = emptyMap(),

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val courseAssets: List<CourseAsset> = emptyList(),

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    val createdAt: Instant? = null,
)
