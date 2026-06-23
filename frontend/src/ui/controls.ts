/**
 * 하단 컨트롤 UI — 쪽타일톤 미니멀.
 *  - 습도 슬라이더(0..1)
 *  - 재생/일시정지 토글(오디오)
 *
 * 시그니처 컨트롤(습도)은 화면 하단 중앙, 콘텐츠를 가리지 않게 절제.
 * a11y: 슬라이더 aria-label, 토글 aria-pressed.
 */

export interface ControlsOptions {
  initialHumidity: number
  onHumidity: (h: number) => void
  onToggle: () => void
}

export interface ControlsHandle {
  /** 외부(최초 제스처 등)에서 재생 상태 라벨 갱신. */
  setPlaying(playing: boolean): void
  element: HTMLElement
}

export function createControls(opts: ControlsOptions): ControlsHandle {
  const root = document.createElement('div')
  root.className = 'controls'

  // 재생/일시정지
  const toggle = document.createElement('button')
  toggle.className = 'toggle'
  toggle.type = 'button'
  toggle.setAttribute('aria-pressed', 'false')
  toggle.setAttribute('aria-label', '오디오 재생/일시정지')
  toggle.textContent = '▶'
  toggle.addEventListener('click', () => opts.onToggle())

  // 습도 슬라이더 영역
  const sliderWrap = document.createElement('div')
  sliderWrap.className = 'slider-wrap'

  const label = document.createElement('span')
  label.className = 'slider-label'
  label.textContent = '습도'

  const slider = document.createElement('input')
  slider.type = 'range'
  slider.min = '0'
  slider.max = '100'
  slider.step = '1'
  slider.value = String(Math.round(opts.initialHumidity * 100))
  slider.className = 'humidity'
  slider.setAttribute('aria-label', '습도 (0에서 100까지)')
  slider.setAttribute('aria-valuetext', `${slider.value}%`)
  slider.addEventListener('input', () => {
    slider.setAttribute('aria-valuetext', `${slider.value}%`)
    opts.onHumidity(Number(slider.value) / 100)
  })

  sliderWrap.append(label, slider)
  root.append(toggle, sliderWrap)

  return {
    element: root,
    setPlaying(playing: boolean) {
      toggle.textContent = playing ? '❚❚' : '▶'
      toggle.setAttribute('aria-pressed', String(playing))
    },
  }
}
