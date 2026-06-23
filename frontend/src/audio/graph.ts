/**
 * Web Audio 그래프 조립.
 *
 * 구조:
 *   각 레이어 Source → layerGain ─┬─→ dryGain ──────────────────┐
 *                                 └─→ Convolver(wet) → wetGain ─┤
 *                                                                └→ master → destination
 *
 * - music 레이어(패드)와 ambient 레이어(rain/subway)를 매니페스트로 라우팅.
 * - 리버브는 합성 임펄스(파일 없음). wet/dry는 습도가 dryWetGains(dry floor 보장)으로 구동.
 * - autoplay 정책: AudioContext는 정지 상태로 생성하고, resume()은 main.ts의
 *   최초 사용자 제스처에서 호출(start()).
 *
 * 습도 연동 핸들(HumidityController가 직접 호출):
 *   setLayerGains(gains)  — 레이어별 humidityCurve.gain 기반 배열
 *   setReverb(dry, wet)   — wet/dry 버스 (dry floor 보장값 전달)
 */
import type { AudioLayer, SessionManifest } from '../api/manifest'
import { createSourceForKind } from './procedural'
import { createImpulseResponse } from './reverb'
import type { AudioSource } from './source'

const RAMP = 0.08 // 게인 변경 시 click 방지 램프(초)

interface Layer {
  source: AudioSource
  gain: GainNode
  baseGain: number
}

export class AudioGraph {
  readonly ctx: AudioContext
  private readonly master: GainNode
  private readonly dryGain: GainNode
  private readonly wetGain: GainNode
  private readonly convolver: ConvolverNode
  private readonly layers: Layer[] = []
  private started = false
  private muted = false

  constructor(manifest: SessionManifest) {
    // Safari 호환 (webkitAudioContext)
    const Ctor: typeof AudioContext =
      window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    this.ctx = new Ctor()

    this.master = this.ctx.createGain()
    this.master.gain.value = 0.7
    this.master.connect(this.ctx.destination)

    // wet/dry 버스
    this.dryGain = this.ctx.createGain()
    this.wetGain = this.ctx.createGain()
    this.convolver = this.ctx.createConvolver()
    this.convolver.buffer = createImpulseResponse(this.ctx)

    this.dryGain.connect(this.master)
    this.convolver.connect(this.wetGain)
    this.wetGain.connect(this.master)
    // 초기 wet/dry (습도 default 적용 전 안전값)
    this.dryGain.gain.value = 1
    this.wetGain.gain.value = 0.2

    this.buildLayers(manifest)
  }

  private buildLayers(manifest: SessionManifest): void {
    const all: AudioLayer[] = [...manifest.audio.music, ...manifest.audio.ambient]
    for (const def of all) {
      // kind는 절차적 소스 종류(pad/rain/rumble) 결정에만 사용 — gain 제어와 무관
      const source = createSourceForKind(this.ctx, def.kind)
      const gain = this.ctx.createGain()
      gain.gain.value = def.baseGain
      source.outputNode.connect(gain)
      gain.connect(this.dryGain)
      gain.connect(this.convolver)
      this.layers.push({ source, gain, baseGain: def.baseGain })
    }
  }

  /** 최초 사용자 제스처에서 호출: context resume + 소스 start. */
  async start(): Promise<void> {
    if (this.ctx.state === 'suspended') await this.ctx.resume()
    if (this.started) return
    this.started = true
    const when = this.ctx.currentTime + 0.02
    for (const l of this.layers) l.source.start(when)
  }

  /** 재생/일시정지 토글. resume/suspend로 그래프는 보존. 현재 재생 여부를 반환. */
  async toggle(): Promise<boolean> {
    if (this.ctx.state === 'running') {
      await this.ctx.suspend()
      return false
    }
    await this.ctx.resume()
    return true
  }

  /** 마스터 음소거(그래프 유지). */
  setMuted(muted: boolean): void {
    this.muted = muted
    this.rampGain(this.master.gain, muted ? 0 : 0.7)
  }

  get isMuted(): boolean {
    return this.muted
  }

  get isRunning(): boolean {
    return this.ctx.state === 'running'
  }

  // ---- 습도 연동 핸들 -------------------------------------------------------

  /**
   * 레이어별 gain 배열 — HumidityController가 humidityCurve.gain 결과를 인덱스 순으로 전달.
   * 길이가 레이어 수와 다를 경우 공통 구간만 적용(방어).
   */
  setLayerGains(gains: number[]): void {
    const n = Math.min(gains.length, this.layers.length)
    for (let i = 0; i < n; i++) {
      this.rampGain(this.layers[i].gain.gain, gains[i])
    }
  }

  /** 리버브 wet/dry — dry floor 보장값을 컨트롤러가 산출해 전달(curve.ts dryWetGains). */
  setReverb(dry: number, wet: number): void {
    this.rampGain(this.dryGain.gain, dry)
    this.rampGain(this.wetGain.gain, wet)
  }

  private rampGain(param: AudioParam, target: number): void {
    const now = this.ctx.currentTime
    param.cancelScheduledValues(now)
    param.setValueAtTime(param.value, now)
    param.linearRampToValueAtTime(target, now + RAMP)
  }

  dispose(): void {
    for (const l of this.layers) l.source.stop()
    void this.ctx.close()
  }
}
