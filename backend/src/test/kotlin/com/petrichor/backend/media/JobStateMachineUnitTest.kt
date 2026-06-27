package com.petrichor.backend.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * 잡 상태머신 순수 단위테스트(JUnit5 only — @SpringBootTest/Testcontainers 없음 → Docker 불요).
 *
 * attempts 의미론: **PROCESSING 진입(실제 시도) 횟수**. markProcessing()이 +1 한다.
 * retryOrDead()는 이미 증가된 attempts로 한도 판정(attempts >= maxAttempts → DEAD).
 *
 * 검증 항목:
 *   - 정상 전이 전부
 *   - 불법 전이 예외
 *   - 재시도 한도 미만 → QUEUED / 한도 도달 → DEAD
 *   - 도메인 메서드(MediaJob) 위임
 *   - 멱등성 키 팩토리
 */
class JobStateMachineUnitTest {

    // ── 정상 전이 ────────────────────────────────────────────────────────────

    @Test
    fun `START는 QUEUED를 PROCESSING으로 전이`() {
        assertEquals(
            JobStatus.PROCESSING,
            JobStateMachine.next(JobStatus.QUEUED, JobEvent.START, attempts = 0, maxAttempts = 3),
        )
    }

    @Test
    fun `SUCCEED는 PROCESSING을 DONE으로 전이`() {
        assertEquals(
            JobStatus.DONE,
            JobStateMachine.next(JobStatus.PROCESSING, JobEvent.SUCCEED, attempts = 0, maxAttempts = 3),
        )
    }

    @Test
    fun `FAIL은 PROCESSING을 FAILED로 전이`() {
        assertEquals(
            JobStatus.FAILED,
            JobStateMachine.next(JobStatus.PROCESSING, JobEvent.FAIL, attempts = 0, maxAttempts = 3),
        )
    }

    // ── 재시도 한도 (새 의미론: attempts = markProcessing 횟수, 이미 증가된 값으로 판정) ──

    @Test
    fun `RETRY — attempts가 maxAttempts 미만이면 QUEUED`() {
        // attempts=1(1회 시도), max=3 → 1 < 3 → QUEUED
        assertEquals(
            JobStatus.QUEUED,
            JobStateMachine.next(JobStatus.FAILED, JobEvent.RETRY, attempts = 1, maxAttempts = 3),
        )
        // attempts=2(2회 시도), max=3 → 2 < 3 → QUEUED
        assertEquals(
            JobStatus.QUEUED,
            JobStateMachine.next(JobStatus.FAILED, JobEvent.RETRY, attempts = 2, maxAttempts = 3),
        )
    }

    @Test
    fun `RETRY — attempts가 maxAttempts 이상이면 DEAD`() {
        // attempts=3(3회 시도), max=3 → 3 >= 3 → DEAD
        assertEquals(
            JobStatus.DEAD,
            JobStateMachine.next(JobStatus.FAILED, JobEvent.RETRY, attempts = 3, maxAttempts = 3),
        )
        // attempts=4(초과), max=3 → 4 >= 3 → DEAD
        assertEquals(
            JobStatus.DEAD,
            JobStateMachine.next(JobStatus.FAILED, JobEvent.RETRY, attempts = 4, maxAttempts = 3),
        )
    }

    @Test
    fun `maxAttempts가 1이면 첫 PROCESSING(attempts=1) 후 RETRY는 DEAD`() {
        // attempts=1(1회 시도), max=1 → 1 >= 1 → DEAD
        assertEquals(
            JobStatus.DEAD,
            JobStateMachine.next(JobStatus.FAILED, JobEvent.RETRY, attempts = 1, maxAttempts = 1),
        )
    }

    // ── 불법 전이 ────────────────────────────────────────────────────────────

    @Test
    fun `DONE에서 START는 IllegalStateException`() {
        assertThrows<IllegalStateException> {
            JobStateMachine.next(JobStatus.DONE, JobEvent.START, attempts = 0, maxAttempts = 3)
        }
    }

    @Test
    fun `QUEUED에서 SUCCEED는 IllegalStateException`() {
        assertThrows<IllegalStateException> {
            JobStateMachine.next(JobStatus.QUEUED, JobEvent.SUCCEED, attempts = 0, maxAttempts = 3)
        }
    }

    @Test
    fun `QUEUED에서 FAIL은 IllegalStateException`() {
        assertThrows<IllegalStateException> {
            JobStateMachine.next(JobStatus.QUEUED, JobEvent.FAIL, attempts = 0, maxAttempts = 3)
        }
    }

    @Test
    fun `QUEUED에서 RETRY는 IllegalStateException`() {
        assertThrows<IllegalStateException> {
            JobStateMachine.next(JobStatus.QUEUED, JobEvent.RETRY, attempts = 0, maxAttempts = 3)
        }
    }

    @Test
    fun `PROCESSING에서 START는 IllegalStateException`() {
        assertThrows<IllegalStateException> {
            JobStateMachine.next(JobStatus.PROCESSING, JobEvent.START, attempts = 0, maxAttempts = 3)
        }
    }

    @Test
    fun `DEAD에서 RETRY는 IllegalStateException`() {
        assertThrows<IllegalStateException> {
            JobStateMachine.next(JobStatus.DEAD, JobEvent.RETRY, attempts = 0, maxAttempts = 3)
        }
    }

    // ── 도메인 메서드 위임(MediaJob) — 새 attempts 의미론 ────────────────────

    @Test
    fun `markProcessing이 attempts를 증가시키고 retryOrDead는 증가시키지 않는다`() {
        val job = MediaJob(assetId = 1L, idempotencyKey = MediaJob.idempotencyKey(1L, "h264", 720), maxAttempts = 3)
        assertEquals(0, job.attempts)

        job.markProcessing()
        assertEquals(JobStatus.PROCESSING, job.status)
        assertEquals(1, job.attempts)  // 1회 시도

        job.markFailed("ffmpeg exit 1")
        assertEquals(JobStatus.FAILED, job.status)

        // attempts=1 < maxAttempts=3 → QUEUED; retryOrDead는 attempts 변경 없음
        assertEquals(JobStatus.QUEUED, job.retryOrDead())
        assertEquals(1, job.attempts)  // 여전히 1 — retryOrDead는 증가 안 함

        job.markProcessing()
        assertEquals(2, job.attempts)  // 2회 시도

        job.markFailed("timeout")
        assertEquals(JobStatus.QUEUED, job.retryOrDead())  // 2 < 3 → QUEUED
        assertEquals(2, job.attempts)

        job.markProcessing()
        assertEquals(3, job.attempts)  // 3회 시도

        job.markFailed("disk full")
        assertEquals(JobStatus.DEAD, job.retryOrDead())  // 3 >= 3 → DEAD
        assertEquals(3, job.attempts)  // retryOrDead는 변경 없음
    }

    @Test
    fun `maxAttempts=1 시나리오 — 첫 실패 후 바로 DEAD`() {
        val job = MediaJob(assetId = 2L, idempotencyKey = MediaJob.idempotencyKey(2L, "vp9", 1080), maxAttempts = 1)

        job.markProcessing()
        assertEquals(1, job.attempts)

        job.markFailed("codec error")
        assertEquals(JobStatus.DEAD, job.retryOrDead())  // 1 >= 1 → DEAD
    }

    @Test
    fun `markDone은 PROCESSING에서만 허용되고 error를 비운다`() {
        val job = MediaJob(assetId = 1L, idempotencyKey = MediaJob.idempotencyKey(1L, "vp9", 1080))
        job.markProcessing()
        job.markDone()
        assertEquals(JobStatus.DONE, job.status)
        assertEquals(null, job.error)

        // DONE에서 markProcessing은 불법
        assertThrows<IllegalStateException> { job.markProcessing() }
    }

    // ── 멱등성 키 팩토리 ──────────────────────────────────────────────────────

    @Test
    fun `idempotencyKey 팩토리는 asset_variants UNIQUE 튜플과 같은 구조`() {
        assertEquals("asset:42:h264:720",  MediaJob.idempotencyKey(42L,  "h264", 720))
        assertEquals("asset:1:vp9:1080",   MediaJob.idempotencyKey(1L,   "vp9",  1080))
        assertEquals("asset:99:aac:0",     MediaJob.idempotencyKey(99L,  "aac",  0))  // 오디오=height 0
    }
}
