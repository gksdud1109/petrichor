package com.petrichor.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.petrichor.backend.sequencing.SessionManifest
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

/**
 * Redis 캐싱 설정.
 * - 시퀀싱 매니페스트("course-session")는 Kotlin data class(SessionManifest)다.
 *   GenericJackson2JsonRedisSerializer()의 기본 mapper엔 jackson-module-kotlin이 없어
 *   캐시 HIT 시 무인자 생성자 없는 data class 역직렬화가 깨진다(500). → kotlin 모듈을 등록한
 *   타입 고정 직렬화기(Jackson2JsonRedisSerializer)를 사용한다.
 * - 기본 TTL 10분. 쓰기 경로에서 @CacheEvict로 능동 무효화가 1차 보증, TTL은 안전망.
 */
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val mapper: ObjectMapper = ObjectMapper().registerModule(kotlinModule())

        val sessionConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    Jackson2JsonRedisSerializer(mapper, SessionManifest::class.java),
                ),
            )

        return RedisCacheManager.builder(connectionFactory)
            .withCacheConfiguration("course-session", sessionConfig)
            .build()
    }
}
