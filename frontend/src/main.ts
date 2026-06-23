/**
 * Petrichor — 프론트엔드 Phase 1.
 *
 * 조립:
 *   api/      매니페스트 fetch(graceful fallback)
 *   gl/       ogl 풀스크린 쿼드 + 단일 패스 그레이딩 셰이더(절차적 눅눅한 새벽 쪽타일 씬)
 *   audio/    Web Audio 합성 그래프(패드 + rain + subway) + ConvolverNode 리버브
 *   humidity/ 마스터 컨트롤 — 단일 슬라이더가 셰이더 + 오디오를 A-3 lerp로 동시 구동
 *   events/   session_start · slider_change(debounce) · 30s heartbeat → POST /events
 *   ui/       습도 슬라이더 + 재생/일시정지 토글
 *
 * 제약: 실촬영 푸티지·AI 음원이 없어 절차적 플레이스홀더로 시그니처 경험을 동작.
 *       매니페스트의 video/audio url은 보존(향후 VideoSource/오디오 파일 교체 지점).
 * autoplay: AudioContext는 최초 사용자 제스처(클릭/키/터치)에서 resume/start.
 */
import './style.css'
import { loadManifest } from './api/manifest'
import { GLStage } from './gl/renderer'
import { AudioGraph } from './audio/graph'
import { HumidityController } from './humidity/controller'
import { EventEmitter } from './events/emitter'
import { createControls } from './ui/controls'

const canvas = document.querySelector<HTMLCanvasElement>('#stage')!
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

async function boot(): Promise<void> {
  const manifest = await loadManifest()
  const humidityDefault = manifest.humidity.default

  // --- GL stage ---
  const stage = new GLStage(canvas, manifest.grade)
  stage.setGrainAnimated(!reducedMotion.matches)

  // --- Audio graph (정지 상태로 생성; 제스처에서 start) ---
  const audio = new AudioGraph(manifest)

  // --- Events emitter ---
  const events = new EventEmitter(manifest.courseId)
  events.start(humidityDefault)

  // --- Humidity master controller (manifest 전달 — 레이어별 humidityCurve 데이터 주도) ---
  const humidity = new HumidityController(stage, audio, manifest, humidityDefault, (h) =>
    events.sliderChange(h),
  )

  // --- Autoplay gesture gate ---
  let audioStarted = false
  async function ensureAudioStarted(): Promise<void> {
    if (audioStarted) return
    audioStarted = true
    await audio.start()
    controls.setPlaying(audio.isRunning)
  }

  // --- Controls UI ---
  const controls = createControls({
    initialHumidity: humidityDefault,
    onHumidity: (h) => humidity.apply(h),
    onToggle: () => {
      void (async () => {
        // #2: 첫 클릭은 반드시 재생으로. justStarted이면 toggle(suspend) 스킵.
        const justStarted = !audioStarted
        await ensureAudioStarted()
        if (justStarted) {
          controls.setPlaying(true)
          return
        }
        const playing = await audio.toggle()
        controls.setPlaying(playing)
      })()
    },
  })
  document.body.appendChild(controls.element)

  // pointerdown/keydown 게이트(토글 버튼 외 제스처용 — once로 중복 방지)
  const gestureOnce = () => void ensureAudioStarted()
  window.addEventListener('pointerdown', gestureOnce, { once: true })
  window.addEventListener('keydown', gestureOnce, { once: true })

  // --- Resize (DPR cap는 GLStage 내부) ---
  window.addEventListener('resize', () => stage.resize())

  // --- prefers-reduced-motion 변화 반영 ---
  reducedMotion.addEventListener('change', (e) => stage.setGrainAnimated(!e.matches))

  // --- rAF 렌더 루프(uTime 갱신) — #1: 핸들 저장, visibilitychange 일시정지/재개 ---
  let rafId = 0
  const startTime = performance.now()

  function frame(now: number): void {
    stage.setTime((now - startTime) / 1000)
    stage.render()
    rafId = requestAnimationFrame(frame)
  }

  function pauseLoop(): void {
    if (rafId !== 0) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
  }

  function resumeLoop(): void {
    if (rafId === 0) {
      rafId = requestAnimationFrame(frame)
    }
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      pauseLoop()
    } else {
      resumeLoop()
    }
  })

  // #1: pagehide — 루프 취소 + 리소스 해제
  window.addEventListener('pagehide', () => {
    pauseLoop()
    stage.dispose()
    audio.dispose()
    events.dispose()
  })

  rafId = requestAnimationFrame(frame)
}

void boot()
