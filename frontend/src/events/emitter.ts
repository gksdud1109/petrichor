/**
 * 분석 이벤트 에미터.
 *
 * 백엔드 계약(backend EventController.kt): POST /api/v1/events 는 **배치 배열**.
 *   [{ type, anonId?, courseId?, occurredAt?, clientEventId?, payload? }, ...]
 *   type ∈ session_start | course_play | slider_change | heartbeat → 202 Accepted.
 *
 * 동작:
 *  - 로드 시 session_start
 *  - 습도 변경 시 slider_change (디바운스 ~500ms — 마지막 값만)
 *  - 30s heartbeat
 *  - 큐에 모았다가 flush 시 배열로 POST. 실패는 조용히 무시(백엔드 없어도 앱 동작).
 */

type EventType = 'session_start' | 'course_play' | 'slider_change' | 'heartbeat'

interface AnalyticsEvent {
  type: EventType
  anonId?: string
  courseId?: number
  occurredAt: string
  clientEventId: string
  payload?: Record<string, unknown>
}

const ENDPOINT = '/api/v1/events'
const SLIDER_DEBOUNCE_MS = 500
const HEARTBEAT_MS = 30_000
const ANON_KEY = 'petrichor.anonId'

function getAnonId(): string {
  try {
    let id = localStorage.getItem(ANON_KEY)
    if (!id) {
      id = crypto.randomUUID()
      localStorage.setItem(ANON_KEY, id)
    }
    return id
  } catch {
    return crypto.randomUUID()
  }
}

export class EventEmitter {
  private readonly anonId = getAnonId()
  private queue: AnalyticsEvent[] = []
  private sliderTimer: ReturnType<typeof setTimeout> | undefined
  private heartbeatTimer: ReturnType<typeof setInterval> | undefined
  private pendingHumidity = 0
  private readonly courseId: number

  constructor(courseId: number) {
    this.courseId = courseId
  }

  /** 로드 직후 호출: session_start enqueue + heartbeat 시작 + 페이지 이탈 flush 등록. */
  start(humidityDefault: number): void {
    this.pendingHumidity = humidityDefault
    this.enqueue('session_start', { humidity: humidityDefault })
    void this.flush()

    this.heartbeatTimer = setInterval(() => {
      // #5: 탭이 숨겨진 동안 heartbeat skip (백그라운드 불필요 요청 방지)
      if (document.hidden) return
      this.enqueue('heartbeat', { humidity: this.pendingHumidity })
      void this.flush()
    }, HEARTBEAT_MS)

    // 탭 숨김/종료 시 남은 큐 전송(best-effort)
    window.addEventListener('pagehide', () => this.flushBeacon())
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') this.flushBeacon()
    })
  }

  /** 슬라이더 변경 — pendingHumidity에 최신값 저장, 디바운스 후 마지막 값으로 enqueue. */
  sliderChange(humidity: number): void {
    this.pendingHumidity = humidity
    if (this.sliderTimer) clearTimeout(this.sliderTimer)
    // #6: 클로저 캡처 humidity 대신 pendingHumidity(마지막 값)로 전송
    this.sliderTimer = setTimeout(() => {
      this.enqueue('slider_change', { humidity: this.pendingHumidity })
      void this.flush()
    }, SLIDER_DEBOUNCE_MS)
  }

  private enqueue(type: EventType, payload?: Record<string, unknown>): void {
    this.queue.push({
      type,
      anonId: this.anonId,
      courseId: this.courseId,
      occurredAt: new Date().toISOString(),
      clientEventId: crypto.randomUUID(),
      payload,
    })
  }

  /** 큐를 배열로 POST. 실패는 조용히 무시(큐는 비운다 — 무한 누적 방지). */
  private async flush(): Promise<void> {
    if (this.queue.length === 0) return
    const batch = this.queue
    this.queue = []
    try {
      await fetch(ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(batch),
        keepalive: true,
      })
    } catch {
      // 백엔드 미가동: 무시. 앱은 계속 동작.
    }
  }

  /** 페이지 이탈 시 sendBeacon으로 best-effort 전송. */
  private flushBeacon(): void {
    if (this.queue.length === 0) return
    const batch = this.queue
    this.queue = []
    try {
      const blob = new Blob([JSON.stringify(batch)], { type: 'application/json' })
      navigator.sendBeacon?.(ENDPOINT, blob)
    } catch {
      // 무시
    }
  }

  dispose(): void {
    if (this.sliderTimer) clearTimeout(this.sliderTimer)
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
  }
}
