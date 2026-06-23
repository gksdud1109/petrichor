package com.petrichor.backend.ratelimit

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * per-IP 토큰 버킷 레이트리밋 필터.
 * - 적용 범위: "/api/v1/" prefix (actuator 헬스/메트릭은 제외 — shouldNotFilter).
 * - 클라이언트 IP: trustForwardedFor=true 시 X-Forwarded-For 첫 항목 사용(신뢰 프록시 뒤에서만);
 *   기본 false — remoteAddr만 사용(XFF 위조 우회 차단).
 * - 버킷 저장소: Caffeine Cache<IP, Bucket> (expireAfterAccess=10분, maximumSize=100_000).
 *   ConcurrentHashMap 대비 TTL 기반 회수로 메모리 DoS 방지. Redis 분산은 향후.
 * - 초과 시 429 + Retry-After(초) + X-RateLimit-Limit / X-RateLimit-Remaining + RFC7807 ProblemDetail 바디.
 *
 * SecurityConfig는 손대지 않는다(이 필터는 Servlet 필터 체인에 @Component로 자동 등록).
 */
@Component
class RateLimitFilter(
    private val properties: RateLimitProperties,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)

    private val bucketCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(100_000)
        .build<String, Bucket>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/v1/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ip = clientIp(request)
        val bucket = bucketCache.get(ip) { newBucket() }

        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            response.setHeader(HEADER_LIMIT, properties.capacity.toString())
            response.setHeader(HEADER_REMAINING, probe.remainingTokens.toString())
            filterChain.doFilter(request, response)
            return
        }

        val retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.nanosToWaitForRefill)
            .coerceAtLeast(1)
        log.warn(
            "rate_limit_exceeded ip={} method={} path={} retryAfterSeconds={}",
            ip, request.method, request.requestURI, retryAfterSeconds,
        )

        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded. Retry after $retryAfterSeconds seconds.",
        ).apply { title = "Too Many Requests" }

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader(HEADER_RETRY_AFTER, retryAfterSeconds.toString())
        response.setHeader(HEADER_LIMIT, properties.capacity.toString())
        response.setHeader(HEADER_REMAINING, "0")
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.writer, problem)
    }

    private fun newBucket(): Bucket =
        Bucket.builder()
            .addLimit { limit ->
                limit.capacity(properties.capacity)
                    .refillGreedy(
                        properties.refillTokens,
                        Duration.ofSeconds(properties.refillPeriodSeconds),
                    )
            }
            .build()

    private fun clientIp(request: HttpServletRequest): String {
        if (properties.trustForwardedFor) {
            val forwarded = request.getHeader("X-Forwarded-For")
            if (!forwarded.isNullOrBlank()) {
                return forwarded.substringBefore(',').trim()
            }
        }
        return request.remoteAddr ?: "unknown"
    }

    companion object {
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val HEADER_LIMIT = "X-RateLimit-Limit"
        private const val HEADER_REMAINING = "X-RateLimit-Remaining"
    }
}
