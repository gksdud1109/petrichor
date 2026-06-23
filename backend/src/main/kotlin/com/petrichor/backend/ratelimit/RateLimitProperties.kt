package com.petrichor.backend.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 레이트리밋 설정(per-IP token bucket).
 * - capacity: 버킷 최대 토큰 수(버스트 허용량).
 * - refillTokens / refillPeriodSeconds: greedy refill — refillPeriodSeconds 동안 refillTokens개를 연속 보충.
 * - limit(X-RateLimit-Limit 헤더 값)은 capacity와 동일하게 노출.
 * - trustForwardedFor: 신뢰 프록시 뒤에서만 XFF 신뢰(기본 off — 위조 우회 차단).
 *   true 시 X-Forwarded-For 첫 항목을 클라이언트 IP로 사용(리버스 프록시 배포 시 활성화).
 *   false 시 remoteAddr만 사용(직접 연결 또는 프록시 미신뢰).
 *
 * 테스트에서 property로 낮춰 결정적 검증 가능(@TestPropertySource / DynamicPropertySource).
 */
@ConfigurationProperties(prefix = "petrichor.ratelimit")
data class RateLimitProperties(
    val capacity: Long = 120,
    val refillTokens: Long = 120,
    val refillPeriodSeconds: Long = 60,
    val trustForwardedFor: Boolean = false,
)
