package com.petrichor.backend.asset

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 재사용 에셋(영상 루프 / 음악 / 환경음) + 라이선스 추적.
 * V1__init.sql `assets` 테이블과 1:1 매핑(ddl-auto=validate).
 *
 * kind/license는 String 유지(enum 변환 금지). DB는 소문자 'video_loop'|'music'|'ambient'.
 * license가 null/blank인 에셋은 시퀀싱 매니페스트에서 제외(license 게이트).
 */
@Entity
@Table(name = "assets")
class Asset(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val kind: String,

    @Column(name = "storage_key", nullable = false)
    val storageKey: String,

    @Column(name = "loop_in_ms")
    val loopInMs: Int? = null,

    @Column(name = "loop_out_ms")
    val loopOutMs: Int? = null,

    @Column
    val license: String? = null,

    @Column(name = "source_note")
    val sourceNote: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    val createdAt: Instant? = null,
)
