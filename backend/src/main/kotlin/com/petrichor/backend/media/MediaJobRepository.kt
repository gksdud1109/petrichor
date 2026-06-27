package com.petrichor.backend.media

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MediaJobRepository : JpaRepository<MediaJob, Long> {

    /** 멱등성 키로 기존 잡 조회(중복 enqueue 방지). */
    fun findByIdempotencyKey(idempotencyKey: String): Optional<MediaJob>
}
