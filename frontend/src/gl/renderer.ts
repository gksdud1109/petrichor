/**
 * ogl 풀스크린 쿼드 + 단일 패스 그레이딩 셰이더 렌더러.
 *
 * - Triangle geometry(풀스크린 단일 삼각형) + Program(shaders.ts) + Mesh.
 * - rAF는 main.ts가 소유한다. 여기서는 setTime/setUniforms/render만 제공.
 * - 리사이즈 시 DPR을 2로 캡(모바일 GPU 부담 완화).
 *
 * 향후 실촬영 교체 지점: addVideoTexture(video) 같은 메서드를 추가하고
 * shaders.ts의 scene()을 tMap 분기로 바꾸면 절차적 씬 → VideoSource 텍스처로 전환된다.
 */
import { Renderer, Program, Mesh, Triangle } from 'ogl'
import type { GradeConfig } from '../api/manifest'
import { FRAGMENT, VERTEX } from './shaders'

const DPR_CAP = 2

/** 셰이더 uniform 값(렌더러 외부에서 습도 컨트롤러가 갱신). */
export interface GradeUniforms {
  uGrain: number
  uCyan: number
  uChroma: number
  uBloom: number
  uVignette: number
  uContrast: number
}

export class GLStage {
  private readonly renderer: Renderer
  private readonly program: Program
  private readonly mesh: Mesh
  private readonly canvas: HTMLCanvasElement

  constructor(canvas: HTMLCanvasElement, grade: GradeConfig) {
    this.canvas = canvas
    this.renderer = new Renderer({
      canvas,
      dpr: Math.min(window.devicePixelRatio || 1, DPR_CAP),
      alpha: false,
      antialias: false,
      premultipliedAlpha: false,
    })
    const gl = this.renderer.gl
    gl.clearColor(0.03, 0.05, 0.06, 1)

    const geometry = new Triangle(gl)
    this.program = new Program(gl, {
      vertex: VERTEX,
      fragment: FRAGMENT,
      uniforms: {
        uTime: { value: 0 },
        uResolution: { value: [1, 1] },
        uGrain: { value: grade.grain ?? 0.4 },
        uCyan: { value: grade.cyan ?? 0.3 },
        uChroma: { value: grade.chroma ?? 0.2 },
        uBloom: { value: grade.bloom ?? 0.2 },
        uVignette: { value: grade.vignette ?? 0.5 },
        uContrast: { value: grade.contrast ?? 0.85 },
        uGrainAnim: { value: 1 },
      },
    })
    this.mesh = new Mesh(gl, { geometry, program: this.program })
    this.resize()
  }

  /** rAF 루프에서 매 프레임 호출. seconds 단위 시간. */
  setTime(seconds: number): void {
    this.program.uniforms.uTime.value = seconds
  }

  /** 습도 컨트롤러가 산출한 그레이드 uniform을 반영. */
  setGrade(u: GradeUniforms): void {
    const p = this.program.uniforms
    p.uGrain.value = u.uGrain
    p.uCyan.value = u.uCyan
    p.uChroma.value = u.uChroma
    p.uBloom.value = u.uBloom
    p.uVignette.value = u.uVignette
    p.uContrast.value = u.uContrast
  }

  /** prefers-reduced-motion: 그레인 시간축 애니메이션 정지/완화. */
  setGrainAnimated(animated: boolean): void {
    this.program.uniforms.uGrainAnim.value = animated ? 1 : 0
  }

  render(): void {
    this.renderer.render({ scene: this.mesh })
  }

  resize(): void {
    const w = window.innerWidth
    const h = window.innerHeight
    this.renderer.dpr = Math.min(window.devicePixelRatio || 1, DPR_CAP)
    this.renderer.setSize(w, h)
    const gl = this.renderer.gl
    this.program.uniforms.uResolution.value = [gl.drawingBufferWidth, gl.drawingBufferHeight]
  }

  dispose(): void {
    const gl = this.renderer.gl
    const ext = gl.getExtension('WEBGL_lose_context')
    ext?.loseContext?.()
  }

  get element(): HTMLCanvasElement {
    return this.canvas
  }
}
