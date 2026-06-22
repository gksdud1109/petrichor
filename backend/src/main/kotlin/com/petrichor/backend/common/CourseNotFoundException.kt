package com.petrichor.backend.common

/**
 * 코스 미존재 도메인 예외. GlobalExceptionHandler에서 404(ProblemDetail)로 매핑.
 */
class CourseNotFoundException(courseId: Long) :
    RuntimeException("course not found: id=$courseId")
