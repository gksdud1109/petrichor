package com.petrichor.backend.events

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 이벤트 수신 — 배치(배열) 계약(DECISIONS: P1부터 배치 확정, 처리만 P2에서 동기→큐 교체).
 *
 * P1 동작: Bean Validation(type 필수, 배치 크기 1..MAX) + 타입별 카운트 구조적 로그 + 202 Accepted.
 * DB 영속화/큐는 Phase 2(events 테이블·V4는 만들지 않음).
 */
@RestController
@RequestMapping("/api/v1/events")
@Validated
class EventController {

    private val log = LoggerFactory.getLogger(EventController::class.java)

    @PostMapping
    fun ingest(
        @RequestBody
        @Valid
        @NotEmpty
        @Size(max = MAX_BATCH_SIZE)
        events: List<@Valid EventDto>,
    ): ResponseEntity<Void> {
        val countsByType = events.groupingBy { it.type }.eachCount()
        log.info(
            "events_received batchSize={} counts={}",
            events.size,
            countsByType.entries.joinToString(prefix = "{", postfix = "}") {
                "${it.key?.json ?: "null"}=${it.value}"
            },
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    companion object {
        const val MAX_BATCH_SIZE = 100
    }
}
