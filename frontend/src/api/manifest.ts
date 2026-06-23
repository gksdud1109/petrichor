/**
 * 세션 매니페스트 타입 + fetch.
 *
 * 백엔드 계약(backend SessionManifest.kt)과 1:1 대응:
 *   GET /api/v1/courses/{id}/session → SessionManifest
 *   GET /api/v1/courses              → Spring Page<CourseSummary>
 *
 * Phase 1 핵심 제약: 실촬영 푸티지·AI 음원이 아직 없다.
 *  - video[].url / audio.*.url 은 플레이스홀더(MinIO 상대경로). P1은 절차적/합성으로 렌더하되
 *    url을 보존 → 향후 VideoSource/오디오 파일 교체 지점.
 *  - 백엔드 미가동 시 graceful fallback(내장 기본 매니페스트)로 앱이 단독 동작.
 */

/** 그레이드 설정. 서버가 grade(Map<String,Any>)로 보냄 — 알려진 키만 선택적으로 읽는다. */
export interface GradeConfig {
  grain?: number
  cyan?: number
  chroma?: number
  bloom?: number
  downscale?: number
  vignette?: number
  contrast?: number
}

export interface VideoLayer {
  assetId: number
  /** 플레이스홀더 URL(MinIO 상대경로). P1은 미사용, 향후 VideoSource 교체 지점. */
  url: string
  loopInMs: number | null
  loopOutMs: number | null
}

/** 습도 곡선: 파라미터명 → [low, high] lerp 구간. 서버 humidity_curve JSONB. */
export type HumidityCurve = Record<string, [number, number] | number[] | unknown>

export interface AudioLayer {
  assetId: number
  url: string
  baseGain: number
  /** kind 힌트(rain/subway 등)가 payload에 올 수 있음. P1 합성 라우팅에 사용. */
  kind?: string
  humidityCurve: HumidityCurve
}

export interface AudioLayers {
  music: AudioLayer[]
  ambient: AudioLayer[]
}

export interface SessionManifest {
  courseId: number
  region: string
  schemaVersion: string
  video: VideoLayer[]
  audio: AudioLayers
  grade: GradeConfig
  humidity: { default: number }
}

interface CourseSummary {
  id: number
  region: string
  title: string
  moodTags: string[]
}

/** Spring Data Page 응답(부분). content 첫 원소의 id를 기본 courseId로 사용. */
interface SpringPage<T> {
  content: T[]
}

const API_BASE = '/api/v1'
const FETCH_TIMEOUT_MS = 4000

/**
 * 내장 기본 매니페스트 — 백엔드 미가동/실패 시 fallback.
 * grade 값은 docs A-3 / WORKPLAN 예시에 맞춘 '교대역 눅눅한 새벽' 기본값.
 */
export const FALLBACK_MANIFEST: SessionManifest = {
  courseId: 1,
  region: 'gyodae',
  schemaVersion: '1',
  video: [{ assetId: 0, url: '', loopInMs: 0, loopOutMs: 42000 }],
  audio: {
    music: [{ assetId: 0, url: '', baseGain: 0.8, kind: 'pad', humidityCurve: { reverbWet: [0, 0.6] } }],
    ambient: [
      { assetId: 0, url: '', baseGain: 0.3, kind: 'rain', humidityCurve: { gain: [0.1, 1.0] } },
      { assetId: 0, url: '', baseGain: 0.5, kind: 'subway', humidityCurve: { gain: [0.4, 0.7] } },
    ],
  },
  grade: { grain: 0.4, cyan: 0.3, chroma: 0.2, bloom: 0.2, downscale: 0.5, vignette: 0.5, contrast: 0.85 },
  humidity: { default: 0.5 },
}

async function fetchJson<T>(path: string, signal: AbortSignal): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, { signal, headers: { Accept: 'application/json' } })
  if (!res.ok) throw new Error(`${path} → ${res.status}`)
  return (await res.json()) as T
}

/** 목록 첫 코스 id 해석. 실패 시 1. */
async function resolveDefaultCourseId(signal: AbortSignal): Promise<number> {
  try {
    const page = await fetchJson<SpringPage<CourseSummary>>('/courses?page=0&size=1', signal)
    return page.content[0]?.id ?? 1
  } catch {
    return 1
  }
}

/**
 * 세션 매니페스트 로드. 백엔드 미가동 시 FALLBACK_MANIFEST 반환(throw 안 함).
 * @param courseId 명시하지 않으면 목록 첫 코스(또는 1)로 해석.
 */
export async function loadManifest(courseId?: number): Promise<SessionManifest> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS)
  try {
    const id = courseId ?? (await resolveDefaultCourseId(controller.signal))
    const manifest = await fetchJson<SessionManifest>(`/courses/${id}/session`, controller.signal)
    return normalizeManifest(manifest)
  } catch {
    return FALLBACK_MANIFEST
  } finally {
    clearTimeout(timer)
  }
}

/** 서버 매니페스트의 누락 필드를 fallback 기본값으로 보강(부분 응답 방어). */
function normalizeManifest(m: SessionManifest): SessionManifest {
  return {
    ...m,
    grade: { ...FALLBACK_MANIFEST.grade, ...(m.grade ?? {}) },
    humidity: m.humidity ?? FALLBACK_MANIFEST.humidity,
    audio: {
      music: m.audio?.music ?? [],
      ambient: m.audio?.ambient ?? [],
    },
    video: m.video ?? [],
  }
}
