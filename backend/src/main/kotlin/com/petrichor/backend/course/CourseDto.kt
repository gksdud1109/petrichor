package com.petrichor.backend.course

/**
 * 코스 목록용 요약 DTO.
 */
data class CourseSummary(
    val id: Long,
    val region: String,
    val title: String,
    val moodTags: List<String>,
) {
    companion object {
        fun from(course: Course): CourseSummary =
            CourseSummary(
                id = requireNotNull(course.id) { "persisted course must have id" },
                region = course.region,
                title = course.title,
                moodTags = course.moodTags,
            )
    }
}

/**
 * 코스 상세 DTO. gradeConfig(셰이더 파라미터)까지 포함.
 */
data class CourseDetail(
    val id: Long,
    val region: String,
    val title: String,
    val moodTags: List<String>,
    val gradeConfig: Map<String, Any>,
) {
    companion object {
        fun from(course: Course): CourseDetail =
            CourseDetail(
                id = requireNotNull(course.id) { "persisted course must have id" },
                region = course.region,
                title = course.title,
                moodTags = course.moodTags,
                gradeConfig = course.gradeConfig,
            )
    }
}
