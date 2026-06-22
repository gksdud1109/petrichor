/**
 * Petrichor — 프론트엔드 Phase 0 셸.
 * 풀스크린 <canvas>에 무드 플레이스홀더(눅눅한 새벽 배경 + 쪽타일 그리드)를 그린다.
 *
 * Phase 1(W1.8~1.14)에서 이 2D 셸을 대체:
 *  - player/  : <video crossorigin="anonymous"> 루프 + 2버퍼 크로스페이드(심리스)
 *  - gl/      : ogl 단일 패스 셰이더 그레이딩(시안-틸 LUT·시간축 그레인·Bayer 디더·약한 색수차)
 *  - audio/   : Web Audio 레이어 믹서(빗소리·지하철·타이어) + ConvolverNode 리버브
 *  - humidity/: 마스터 uniform 슬라이더(grain·bloom·chroma·blur·rainGain·reverbWet 동시 제어)
 *  - events/  : heartbeat(30s)·session_start·slider_change 배치 에미터 → 백엔드 /api/v1/events
 *  - a11y     : prefers-reduced-motion:reduce 시 비디오 루프·그레인·크로스페이드 정지/완화
 */
import './style.css'

const canvas = document.querySelector<HTMLCanvasElement>('#stage')!
const ctx = canvas.getContext('2d')!

// 쪽타일: 구도심 외벽의 흰/미색 세로 타일 그리드 (비주얼 아이덴티티 placeholder)
const TILE_W = 26
const TILE_H = 92
const GAP = 2

function render() {
  const w = window.innerWidth
  const h = window.innerHeight

  // 눅눅한 새벽: 위→아래 깊은 시안-네이비 그라데이션
  const bg = ctx.createLinearGradient(0, 0, 0, h)
  bg.addColorStop(0, '#0c1418')
  bg.addColorStop(1, '#0a0f12')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, w, h)

  // 쪽타일 그리드 (faint)
  ctx.fillStyle = 'rgba(210, 224, 226, 0.05)'
  for (let y = -TILE_H; y < h + TILE_H; y += TILE_H + GAP) {
    for (let x = 0; x < w; x += TILE_W + GAP) {
      ctx.fillRect(x, y, TILE_W, TILE_H)
    }
  }

  // 중앙 타이틀
  ctx.textAlign = 'center'
  ctx.fillStyle = 'rgba(200, 218, 220, 0.5)'
  ctx.font = '300 22px ui-serif, Georgia, serif'
  ctx.fillText('petrichor', w / 2, h / 2)
  ctx.fillStyle = 'rgba(200, 218, 220, 0.28)'
  ctx.font = '300 11px ui-sans-serif, system-ui'
  ctx.fillText('Phase 0 — 새벽 2시, 교대역', w / 2, h / 2 + 22)
}

function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = Math.floor(window.innerWidth * dpr)
  canvas.height = Math.floor(window.innerHeight * dpr)
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  render()
}

window.addEventListener('resize', resize)
resize()
