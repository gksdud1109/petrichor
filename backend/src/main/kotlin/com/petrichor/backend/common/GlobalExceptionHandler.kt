package com.petrichor.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 도메인 예외를 ProblemDetail(RFC 7807)로 변환.
 * (events 배치 검증은 EventController가 직접 400 problem+json으로 처리한다.)
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CourseNotFoundException::class)
    fun handleCourseNotFound(ex: CourseNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "course not found").apply {
            title = "Course Not Found"
        }
}
