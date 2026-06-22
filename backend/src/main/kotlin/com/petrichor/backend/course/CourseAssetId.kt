package com.petrichor.backend.course

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * course_assets 복합 기본키 (course_id, asset_id).
 */
@Embeddable
data class CourseAssetId(
    @Column(name = "course_id")
    val courseId: Long = 0,

    @Column(name = "asset_id")
    val assetId: Long = 0,
) : Serializable
