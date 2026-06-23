/**
 * 습도 매핑 곡선 — 마스터 컨트롤의 수학.
 *
 * DESIGN-REFERENCES 'single-control-ux': 단일 t∈[0,1]이 N개의 타깃을 각자의
 * {range:[min,max], curve}로 동시 구동. 퍼셉추얼 품질은 per-target 곡선에 산다
 * (linear-everything = '싸구려' 실패 모드). 따라서 곡선을 데이터로 둔다.
 */

export type Easing = 'linear' | 'easeInQuad' | 'easeOutCubic' | 'smoothstep'

export const EASINGS: Record<Easing, (t: number) => number> = {
  linear: (t) => t,
  easeInQuad: (t) => t * t,
  easeOutCubic: (t) => 1 - Math.pow(1 - t, 3),
  smoothstep: (t) => t * t * (3 - 2 * t),
}

export function clamp01(t: number): number {
  return t < 0 ? 0 : t > 1 ? 1 : t
}

export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t
}

/** t를 명명된 easing으로 변형한 뒤 [a,b]로 lerp. */
export function curveLerp(a: number, b: number, t: number, easing: Easing = 'linear'): number {
  return lerp(a, b, EASINGS[easing](clamp01(t)))
}

/**
 * 리버브 wet/dry 게인 — 중앙 dip 방지(cos 곡선) + dry 하한 보장.
 *
 * 순수 equal-power(cos/sin)는 h=1에서 dry=0이 되어 직접음이 완전히 소실된다.
 * 따라서 dry에 하한(DRY_FLOOR)을 둔다 — wet은 별도 범위로 제어하므로
 * sin^2+cos^2=1 등전력 불변식은 더 이상 성립하지 않는다(asymmetric wet-bias + dry floor).
 * 이로써 h=1에서도 dry ≥ DRY_FLOOR 만큼 직접음이 남아 앰비언트 공간감을 유지한다.
 */
const DRY_FLOOR = 0.3

export function dryWetGains(t: number): { dry: number; wet: number } {
  const x = clamp01(t) * 0.5 * Math.PI
  const dry = Math.max(DRY_FLOOR, Math.cos(x))
  const wet = Math.sin(x)
  return { dry, wet }
}
