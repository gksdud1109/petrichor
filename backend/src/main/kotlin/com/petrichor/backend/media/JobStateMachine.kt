package com.petrichor.backend.media

/**
 * 잡 상태머신(순수 Kotlin, Spring/IO 의존 없음).
 *
 * 이벤트 기반 전이만 책임진다. 영속화/타이밍/부수효과는 호출 측(엔티티·서비스)의 몫이다.
 * 불법 전이(예: DONE에서 START)는 [IllegalStateException].
 *
 * attempts 의미론: **PROCESSING 진입(실제 시도) 횟수**.
 *   - markProcessing()이 PROCESSING으로 전이할 때 attempts를 +1 한다.
 *   - RETRY 판정은 이미 증가된 attempts를 기준으로 한다.
 *     attempts < maxAttempts  → QUEUED (재시도 여유 있음)
 *     attempts >= maxAttempts → DEAD   (dead-letter)
 */
object JobStateMachine {

    /**
     * 현재 상태와 이벤트로 다음 상태를 계산한다.
     *
     * @param current     현재 상태
     * @param event       적용할 이벤트
     * @param attempts    PROCESSING 진입 횟수(RETRY 판정에만 사용; 그 외 무시)
     * @param maxAttempts 허용 최대 시도 횟수(RETRY 판정에만 사용; 그 외 무시)
     * @throws IllegalStateException 현재 상태에서 허용되지 않는 이벤트일 때
     */
    fun next(current: JobStatus, event: JobEvent, attempts: Int, maxAttempts: Int): JobStatus =
        when (event) {
            JobEvent.START ->
                if (current == JobStatus.QUEUED) JobStatus.PROCESSING
                else illegal(current, event)

            JobEvent.SUCCEED ->
                if (current == JobStatus.PROCESSING) JobStatus.DONE
                else illegal(current, event)

            JobEvent.FAIL ->
                if (current == JobStatus.PROCESSING) JobStatus.FAILED
                else illegal(current, event)

            JobEvent.RETRY ->
                if (current == JobStatus.FAILED) {
                    // attempts는 이미 markProcessing()에서 증가된 값
                    if (attempts < maxAttempts) JobStatus.QUEUED else JobStatus.DEAD
                } else {
                    illegal(current, event)
                }
        }

    private fun illegal(current: JobStatus, event: JobEvent): Nothing =
        throw IllegalStateException("불법 전이: $current 상태에서 $event 이벤트는 허용되지 않음")
}
