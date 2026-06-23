package com.petrichor.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 공개 읽기/수신 API의 예외를 ProblemDetail(RFC 7807)로 일관 변환.
 *
 * 검증 오류(MethodArgumentNotValidException, HandlerMethodValidationException)는
 * spring.mvc.problemdetails.enabled=true 로 Spring이 직접 RFC7807 problem+json 바디를 내므로
 * 별도 핸들러 불필요. ConstraintViolationException 핸들러는 Spring 6.1+ 메서드검증 경로에서
 * 트리거되지 않는 dead code이므로 제거함.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CourseNotFoundException::class)
    fun handleCourseNotFound(ex: CourseNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "course not found").apply {
            title = "Course Not Found"
        }
}
