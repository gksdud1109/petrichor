package com.petrichor.backend.events

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 이벤트 수신 — 배치(배열) 계약(DECISIONS: P1부터 배치 확정, 처리만 P2에서 동기→큐 교체).
 *
 * P1 동작: 수동 검증(비어있지 않음·배치 상한·type 필수) → 위반 시 400 RFC7807 problem+json,
 * 정상 시 타입별 카운트 구조적 로그 + 202 Accepted. DB 영속화/큐는 Phase 2.
 *
 * 검증을 @Validated/@Valid(선언적)가 아니라 컨트롤러에서 직접 하는 이유: Spring 6.1+에서
 * @Validated+@Valid 조합이 던지는 예외 타입(MethodArgumentNotValid vs HandlerMethodValidation)과
 * 응답 Content-Type(problem+json) 보장이 모호 → 결정적 응답을 위해 수동 검증한다.
 */
@RestController
@RequestMapping("/api/v1/events")
class EventController {

    private val log = LoggerFactory.getLogger(EventController::class.java)

    @PostMapping
    fun ingest(@RequestBody events: List<EventDto>): ResponseEntity<*> {
        if (events.isEmpty()) return badRequest("events 배열이 비어 있음")
        if (events.size > MAX_BATCH_SIZE) {
            return badRequest("배치 크기 상한 $MAX_BATCH_SIZE 초과 (size=${events.size})")
        }
        val missingTypeIdx = events.indexOfFirst { it.type == null }
        if (missingTypeIdx >= 0) return badRequest("events[$missingTypeIdx].type 필수")

        val countsByType = events.groupingBy { it.type }.eachCount()
        log.info(
            "events_received batchSize={} counts={}",
            events.size,
            countsByType.entries.joinToString(prefix = "{", postfix = "}") {
                "${it.key?.json ?: "null"}=${it.value}"
            },
        )
        return ResponseEntity.accepted().build<Void>()
    }

    private fun badRequest(detail: String): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
        pd.title = "Validation Failed"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(pd)
    }

    companion object {
        const val MAX_BATCH_SIZE = 100
    }
}
