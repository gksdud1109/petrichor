package com.petrichor.backend.course

import com.petrichor.backend.sequencing.SequencingService
import com.petrichor.backend.sequencing.SessionManifest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 공개 읽기 API. 미존재 코스는 GlobalExceptionHandler에서 404(ProblemDetail).
 */
@RestController
@RequestMapping("/api/v1/courses")
class CourseController(
    private val courseService: CourseService,
    private val sequencingService: SequencingService,
) {

    @GetMapping
    fun list(pageable: Pageable): Page<CourseSummary> =
        courseService.list(pageable)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): CourseDetail =
        courseService.detail(id)

    @GetMapping("/{id}/session")
    fun session(@PathVariable id: Long): SessionManifest =
        sequencingService.compose(id)
}
