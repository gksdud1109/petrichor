package com.petrichor.backend.sequencing

import java.io.Serializable

/**
 * 세션 매니페스트 — 서버가 코스를 에셋 조합으로 '구성'한 결과(정적 JSON 아님).
 * Redis 캐시 직렬화 대상이므로 Serializable.
 *
 * schemaVersion은 매니페스트 계약 버전. 캐시 키 무효화 전략과 연동(SequencingService 참조).
 */
data class SessionManifest(
    val courseId: Long,
    val region: String,
    val schemaVersion: String = "1",
    val video: List<VideoLayer>,
    val audio: AudioLayers,
    val grade: Map<String, Any>,
    val humidity: HumidityCfg = HumidityCfg(),
) : Serializable

data class VideoLayer(
    val assetId: Long,
    val url: String,
    val loopInMs: Int?,
    val loopOutMs: Int?,
) : Serializable

data class AudioLayers(
    val music: List<AudioLayer>,
    val ambient: List<AudioLayer>,
) : Serializable

data class AudioLayer(
    val assetId: Long,
    val url: String,
    val baseGain: Float,
    val humidityCurve: Map<String, Any>,
) : Serializable

data class HumidityCfg(
    val default: Double = 0.5,
) : Serializable
