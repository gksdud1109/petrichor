/**
 * 습도 컨트롤러 — 시그니처 경험: 단일 입력(0..1)이 셰이더 + 오디오를 동시에 구동.
 *
 * A-3 매핑(docs/TECH-DESIGN A-3, WORKPLAN W1.11):
 *   각 오디오 레이어 gain = 레이어의 humidityCurve.gain[0..1] lerp (데이터 주도, kind 무관)
 *   reverbWet               = humidityCurve에서 reverbWet 키를 가진 레이어 또는 전역 curve
 *   uCyanTint = lerp(0.05, 0.4, h)
 *   uGrain    = lerp(0.2,  0.7, h)
 *
 * reverbDry 하한(DRY_FLOOR=0.3) 보장 — h=1에서 직접음 소실 방지(#4 fix).
 * per-target easing으로 '물리적' 느낌 부여(DESIGN-REFERENCES single-control-ux).
 */
import type { GLStage, GradeUniforms } from '../gl/renderer'
import type { AudioGraph } from '../audio/graph'
import type { SessionManifest, HumidityCurve } from '../api/manifest'
import { clamp01, curveLerp, dryWetGains } from './curve'

export interface HumidityTargets {
  /** 인덱스 순(music layers + ambient layers) */
  layerGains: number[]
  reverbDry: number
  reverbWet: number
  grade: GradeUniforms
}

/**
 * humidityCurve에서 [low, high] 쌍을 안전하게 추출.
 * 서버는 Map<String,Any>로 보내므로 방어적 파싱이 필요.
 */
function extractRange(curve: HumidityCurve, key: string): [number, number] | undefined {
  const v = curve[key]
  if (Array.isArray(v) && v.length >= 2 && typeof v[0] === 'number' && typeof v[1] === 'number') {
    return [v[0] as number, v[1] as number]
  }
  return undefined
}

/**
 * h∈[0,1] → 모든 타깃 값 산출(순수 함수, 단위테스트 가능).
 * manifest의 audio layers(music+ambient 순)를 받아 humidityCurve 기반으로 gain을 산출.
 */
export function computeTargets(h: number, manifest: SessionManifest): HumidityTargets {
  const t = clamp01(h)
  const allLayers = [...manifest.audio.music, ...manifest.audio.ambient]

  // 레이어별 gain: humidityCurve.gain[low,high] 가 있으면 lerp, 없으면 baseGain 유지
  const layerGains = allLayers.map((layer) => {
    const range = extractRange(layer.humidityCurve, 'gain')
    if (range) return curveLerp(range[0], range[1], t, 'easeInQuad')
    return layer.baseGain
  })

  // reverbWet: 첫 번째로 humidityCurve.reverbWet 를 가진 레이어 또는 전역 fallback
  let wetTarget: [number, number] | undefined
  for (const layer of allLayers) {
    const r = extractRange(layer.humidityCurve, 'reverbWet')
    if (r) { wetTarget = r; break }
  }
  const wetAmount = wetTarget
    ? curveLerp(wetTarget[0], wetTarget[1], t, 'easeOutCubic')
    : curveLerp(0.0, 0.6, t, 'easeOutCubic')

  // dry floor 보장 — h=1에서 직접음 소실 방지(#4 fix, curve.ts dryWetGains)
  const { dry, wet } = dryWetGains(t)
  const reverbDry = dry                  // ≥ DRY_FLOOR(0.3)
  const reverbWet = wet * wetAmount

  // 셰이더 그레이드 (셰이더 uniforms)
  const uCyan     = curveLerp(0.05, 0.4,  t, 'smoothstep')
  const uGrain    = curveLerp(0.2,  0.7,  t, 'linear')
  const uChroma   = curveLerp(0.1,  0.5,  t, 'smoothstep')
  const uBloom    = curveLerp(0.1,  0.45, t, 'easeInQuad')
  const uVignette = curveLerp(0.35, 0.6,  t, 'linear')
  const uContrast = curveLerp(0.95, 0.7,  t, 'smoothstep') // 역방향: 습하면 다이내믹레인지 ↓

  return {
    layerGains,
    reverbDry,
    reverbWet,
    grade: { uGrain, uCyan, uChroma, uBloom, uVignette, uContrast },
  }
}

/**
 * 습도 컨트롤러: 슬라이더(0..1) → 셰이더 + 오디오 동시 구동.
 * onChange는 events 에미터(slider_change 디바운스)로 연결.
 */
export class HumidityController {
  private current: number
  private readonly stage: GLStage
  private readonly audio: AudioGraph
  private readonly manifest: SessionManifest
  private readonly onChange?: (h: number) => void

  constructor(
    stage: GLStage,
    audio: AudioGraph,
    manifest: SessionManifest,
    initial: number,
    onChange?: (h: number) => void,
  ) {
    this.stage = stage
    this.audio = audio
    this.manifest = manifest
    this.onChange = onChange
    this.current = clamp01(initial)
    this.applyInternal(this.current)
  }

  get value(): number {
    return this.current
  }

  /** 슬라이더에서 호출. 셰이더 + 오디오를 동시에 갱신하고 onChange 통지. */
  apply(h: number): void {
    this.current = clamp01(h)
    this.applyInternal(this.current)
    this.onChange?.(this.current)
  }

  private applyInternal(h: number): void {
    const targets = computeTargets(h, this.manifest)
    this.stage.setGrade(targets.grade)
    this.audio.setLayerGains(targets.layerGains)
    this.audio.setReverb(targets.reverbDry, targets.reverbWet)
  }
}
