package com.petrichor.backend.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/**
 * 분석 이벤트 타입. JSON 표현은 소문자(session_start 등) — Jackson @JsonValue/@JsonCreator로 매핑.
 * P1은 수신·검증·로깅까지. 처리(영속/큐)는 Phase 2.
 */
enum class EventType(@get:JsonValue val json: String) {
    SESSION_START("session_start"),
    COURSE_PLAY("course_play"),
    SLIDER_CHANGE("slider_change"),
    HEARTBEAT("heartbeat"),
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromJson(value: String): EventType =
            entries.firstOrNull { it.json == value }
                ?: throw IllegalArgumentException("unknown event type: $value")
    }
}

/**
 * 단일 분석 이벤트(배치의 한 원소).
 * - type: 필수. anonId/userId/courseId/occurredAt/clientEventId/payload는 선택.
 * - clientEventId: P2 멱등(inbox dedup)용. P1은 dedup하지 않음.
 */
data class EventDto(
    @field:NotNull
    val type: EventType?,
    val anonId: UUID? = null,
    val userId: Long? = null,
    val courseId: Long? = null,
    val occurredAt: Instant? = null,
    val clientEventId: String? = null,
    val payload: Map<String, Any>? = null,
)
