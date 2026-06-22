package com.petrichor.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Phase 0/1 보안 베이스라인.
 * - Actuator 헬스/정보/메트릭과 공개 읽기 API는 인증 없이 허용(MVP는 로그인 없음).
 * - 무상태(STATELESS) + CSRF 비활성: 토큰 기반 REST 전제(JWT 인증은 Phase 2에서 도입).
 * Phase 2에서 계정 등 보호 리소스와 JWT 필터를 추가하며 이 설정을 강화한다.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/actuator/metrics/**",
                ).permitAll()
                it.requestMatchers("/api/v1/**").permitAll() // Phase 2에서 보호 경로 분리
                it.anyRequest().authenticated()
            }
        return http.build()
    }
}
