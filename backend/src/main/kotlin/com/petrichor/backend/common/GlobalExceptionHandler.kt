package com.petrichor.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 공개 읽기 API의 예외를 ProblemDetail(RFC 7807)로 일관 변환.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CourseNotFoundException::class)
    fun handleCourseNotFound(ex: CourseNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "course not found").apply {
            title = "Course Not Found"
        }
}
