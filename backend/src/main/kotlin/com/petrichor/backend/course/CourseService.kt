package com.petrichor.backend.course

import com.petrichor.backend.common.CourseNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 코스 조회 서비스(목록/상세). 외부 I/O 없는 readOnly 트랜잭션.
 */
@Service
class CourseService(
    private val courseRepository: CourseRepository,
) {

    @Transactional(readOnly = true)
    fun list(pageable: Pageable): Page<CourseSummary> =
        courseRepository.findAll(pageable).map(CourseSummary::from)

    @Transactional(readOnly = true)
    fun detail(courseId: Long): CourseDetail =
        courseRepository.findById(courseId)
            .map(CourseDetail::from)
            .orElseThrow { CourseNotFoundException(courseId) }
}
