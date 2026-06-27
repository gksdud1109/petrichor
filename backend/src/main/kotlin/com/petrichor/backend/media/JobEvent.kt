package com.petrichor.backend.media

/**
 * 미디어 잡 상태 전이 이벤트.
 *
 * START   : QUEUED → PROCESSING (markProcessing에서 attempts 증가)
 * SUCCEED : PROCESSING → DONE
 * FAIL    : PROCESSING → FAILED
 * RETRY   : FAILED → QUEUED(attempts < maxAttempts) | DEAD(attempts >= maxAttempts)
 */
enum class JobEvent {
    START,
    SUCCEED,
    FAIL,
    RETRY,
}
