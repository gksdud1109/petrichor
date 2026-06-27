package com.petrichor.backend.media

/**
 * 미디어 트랜스코드 잡 상태. DB 값 = enum 이름(대문자), media_jobs.status VARCHAR(20)와 1:1.
 *
 * 전이(JobStateMachine):
 *   QUEUED --START--> PROCESSING
 *   PROCESSING --SUCCEED--> DONE
 *   PROCESSING --FAIL--> FAILED
 *   FAILED --RETRY--> QUEUED (재시도 한도 미만) | DEAD (한도 도달, dead-letter)
 */
enum class JobStatus {
    QUEUED,
    PROCESSING,
    DONE,
    FAILED,
    DEAD,
}
