/**
 * 절차적(합성) 오디오 소스 — 파일 없이 동작하는 Phase 1 플레이스홀더.
 *
 * 향후 실 AI 음원으로 교체: 동일 AudioSource 인터페이스를 구현하는 BufferSource로
 * 갈아끼우면 그래프 변경 없이 음원만 교체된다(source.ts 참조).
 *
 * 세 종류:
 *  - createPad      : 부드러운 패드(디튠 오실레이터 3 + lowpass) — music 레이어
 *  - createRain     : 화이트 노이즈 → bandpass — ambient rain
 *  - createRumble   : 저주파 노이즈 → lowpass — ambient subway/rumble
 */
import type { AudioSource } from './source'

/** 2초 분량 화이트 노이즈 버퍼(루프 재생). */
function createNoiseBuffer(ctx: AudioContext): AudioBuffer {
  const length = ctx.sampleRate * 2
  const buffer = ctx.createBuffer(1, length, ctx.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < length; i++) data[i] = Math.random() * 2 - 1
  return buffer
}

/** 부드러운 디튠 패드: 3 오실레이터(약간씩 디튠) → lowpass → 출력. */
export function createPad(ctx: AudioContext): AudioSource {
  const out = ctx.createGain()
  const lowpass = ctx.createBiquadFilter()
  lowpass.type = 'lowpass'
  lowpass.frequency.value = 900
  lowpass.Q.value = 0.3
  lowpass.connect(out)

  // 낮은 화음(루트/5도/옥타브) + 미세 디튠으로 '눅눅한' 베드
  const freqs = [110, 110 * 1.5, 220]
  const detunes = [-6, 5, -3]
  const oscs: OscillatorNode[] = []
  freqs.forEach((f, i) => {
    const osc = ctx.createOscillator()
    osc.type = 'sine'
    osc.frequency.value = f
    osc.detune.value = detunes[i]
    const g = ctx.createGain()
    g.gain.value = 0.33
    osc.connect(g)
    g.connect(lowpass)
    oscs.push(osc)
  })

  // 느린 LFO로 lowpass cutoff를 흔들어 정적이지 않게
  const lfo = ctx.createOscillator()
  lfo.type = 'sine'
  lfo.frequency.value = 0.05
  const lfoGain = ctx.createGain()
  lfoGain.gain.value = 200
  lfo.connect(lfoGain)
  lfoGain.connect(lowpass.frequency)

  let started = false
  return {
    outputNode: out,
    start(when) {
      if (started) return
      started = true
      oscs.forEach((o) => o.start(when))
      lfo.start(when)
    },
    stop() {
      if (!started) return
      started = false
      oscs.forEach((o) => o.stop())
      lfo.stop()
    },
  }
}

/** 빗소리: 화이트 노이즈 → bandpass(중역) → 출력. */
export function createRain(ctx: AudioContext): AudioSource {
  const out = ctx.createGain()
  const src = ctx.createBufferSource()
  src.buffer = createNoiseBuffer(ctx)
  src.loop = true

  const bandpass = ctx.createBiquadFilter()
  bandpass.type = 'bandpass'
  bandpass.frequency.value = 1400
  bandpass.Q.value = 0.7

  const highshelf = ctx.createBiquadFilter()
  highshelf.type = 'highshelf'
  highshelf.frequency.value = 4000
  highshelf.gain.value = 4

  src.connect(bandpass)
  bandpass.connect(highshelf)
  highshelf.connect(out)

  let started = false
  return {
    outputNode: out,
    start(when) {
      if (started) return
      started = true
      src.start(when)
    },
    stop() {
      if (!started) return
      started = false
      src.stop()
    },
  }
}

/** 지하철/저주파 럼블: 노이즈 → lowpass(저역) → 출력. */
export function createRumble(ctx: AudioContext): AudioSource {
  const out = ctx.createGain()
  const src = ctx.createBufferSource()
  src.buffer = createNoiseBuffer(ctx)
  src.loop = true

  const lowpass = ctx.createBiquadFilter()
  lowpass.type = 'lowpass'
  lowpass.frequency.value = 120
  lowpass.Q.value = 0.5

  src.connect(lowpass)
  lowpass.connect(out)

  let started = false
  return {
    outputNode: out,
    start(when) {
      if (started) return
      started = true
      src.start(when)
    },
    stop() {
      if (!started) return
      started = false
      src.stop()
    },
  }
}

/** kind 힌트 → 절차적 소스 팩토리 매핑(매니페스트 라우팅). */
export function createSourceForKind(ctx: AudioContext, kind: string | undefined): AudioSource {
  switch (kind) {
    case 'rain':
      return createRain(ctx)
    case 'subway':
    case 'rumble':
      return createRumble(ctx)
    case 'pad':
    case 'music':
    default:
      return createPad(ctx)
  }
}
