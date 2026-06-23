package com.petrichor.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

/**
 * 공개 읽기/수신 API의 예외를 ProblemDetail(RFC 7807, application/problem+json)로 일관 변환.
 *
 * 검증 오류는 spring.mvc.problemdetails.enabled만으로는 응답 Content-Type이 항상
 * application/problem+json으로 보장되지 않아, 명시 핸들러로 400 + problem+json을 결정적으로 반환한다.
 * - MethodArgumentNotValidException: @RequestBody/@Valid(배치 원소 @Valid 포함) 검증 실패
 * - HandlerMethodValidationException: @Validated + 파라미터 제약(@NotEmpty/@Size) 위반
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CourseNotFoundException::class)
    fun handleCourseNotFound(ex: CourseNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "course not found").apply {
            title = "Course Not Found"
        }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> =
        problem("요청 본문 검증 실패")

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException): ResponseEntity<ProblemDetail> =
        problem("요청 검증 실패")

    private fun problem(detail: String): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
        pd.title = "Validation Failed"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(pd)
    }
}
