/**
 * 단일 패스 그레이딩 셰이더 GLSL.
 *
 * 베이스 씬 = 절차적 '눅눅한 새벽 쪽타일'(셰이더 내부 생성):
 *   - 위→아래 시안-네이비 그라데이션
 *   - 흰 세로 타일 그리드(구도심 외벽 쪽타일)
 *   - 미세 노이즈(습기/먼지)
 *   - 가로등 글로우 + 약한 블룸 느낌
 *
 * 그레이딩(DESIGN-REFERENCES film-grain-shaders):
 *   - 시안-틸 컬러시프트(shadow→teal, highlight→warm)
 *   - 시간축 애니메이션 필름 그레인(3D-ish noise, uTime z-offset)
 *   - Bayer 4x4 ordered dither
 *   - 약한 색수차(가장자리)
 *   - 비네팅
 *   - contrast 축소(눅눅함): col = mix(0.5, col, contrast)
 *
 * uniforms: uTime, uResolution, uGrain, uCyan, uChroma, uBloom, uVignette, uContrast
 * 향후 실촬영 교체: uUseTexture=1 + tMap에 VideoSource 텍스처를 바인딩하면
 * 절차적 씬 대신 비디오를 그레이딩한다(scene() 분기 참조).
 */

export const VERTEX = /* glsl */ `
  attribute vec2 uv;
  attribute vec2 position;
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position, 0.0, 1.0);
  }
`

export const FRAGMENT = /* glsl */ `
  precision highp float;

  varying vec2 vUv;

  uniform float uTime;
  uniform vec2  uResolution;
  uniform float uGrain;      // 그레인 강도
  uniform float uCyan;       // 시안-틸 틴트 강도
  uniform float uChroma;     // 색수차 강도
  uniform float uBloom;      // 가로등 글로우/블룸
  uniform float uVignette;   // 비네팅 강도
  uniform float uContrast;   // 1=원본, <1=눅눅(다이내믹레인지 축소)
  uniform float uGrainAnim;  // 0=정지(prefers-reduced-motion), 1=시간축 애니메이션

  // ---- hash / noise -------------------------------------------------------
  float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
  }

  // 시간축 그레인: z를 시간으로 흘려 '살아있는' 입자
  float grainNoise(vec2 uv, float t) {
    vec2 g = uv * uResolution;
    return hash21(g + vec2(t * 57.0, t * 113.0));
  }

  float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
  }

  // ---- Bayer 4x4 ordered dither ------------------------------------------
  float bayer4x4(vec2 fragCoord) {
    int x = int(mod(fragCoord.x, 4.0));
    int y = int(mod(fragCoord.y, 4.0));
    int index = x + y * 4;
    // 0..15 정규화된 Bayer 행렬 (펼쳐서 비교)
    float m[16];
    m[0]=0.0;  m[1]=8.0;  m[2]=2.0;  m[3]=10.0;
    m[4]=12.0; m[5]=4.0;  m[6]=14.0; m[7]=6.0;
    m[8]=3.0;  m[9]=11.0; m[10]=1.0; m[11]=9.0;
    m[12]=15.0;m[13]=7.0; m[14]=13.0;m[15]=5.0;
    float v = 0.0;
    for (int i = 0; i < 16; i++) { if (i == index) v = m[i]; }
    return (v + 0.5) / 16.0 - 0.5;
  }

  // ---- 절차적 '눅눅한 새벽 쪽타일' 씬 -------------------------------------
  vec3 scene(vec2 uv) {
    // 위(어두운 하늘)→아래(약간 밝은 노면) 깊은 시안-네이비 그라데이션
    vec3 top = vec3(0.043, 0.071, 0.090);   // #0b1217
    vec3 bot = vec3(0.027, 0.043, 0.055);   // #070b0e
    vec3 col = mix(top, bot, smoothstep(0.0, 1.0, uv.y));

    // 미세 습기 노이즈(저주파 얼룩)
    float damp = valueNoise(uv * vec2(6.0, 9.0) + vec2(0.0, uTime * 0.01));
    col += (damp - 0.5) * 0.015;

    // 쪽타일: 흰 세로 타일 그리드. 타일 폭/간격은 화면비 보정.
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    vec2 grid = uv * vec2(28.0 * aspect, 9.0);
    vec2 cell = fract(grid);
    // 타일 사이 얇은 어두운 줄눈(grout)
    float vline = smoothstep(0.0, 0.04, cell.x) * smoothstep(0.0, 0.04, 1.0 - cell.x);
    float hline = smoothstep(0.0, 0.06, cell.y) * smoothstep(0.0, 0.06, 1.0 - cell.y);
    float tileFace = vline * hline;
    // 타일 표면을 아주 약하게 들어올림(faint, 콘텐츠 안 가림)
    col += vec3(0.10, 0.12, 0.125) * tileFace * 0.10;

    // 가로등 글로우: 화면 좌상/우중에 두 개의 따뜻한 점광 + 블룸
    vec2 p = vec2(uv.x * aspect, uv.y);
    vec2 lampA = vec2(0.22 * aspect, 0.30);
    vec2 lampB = vec2(0.82 * aspect, 0.55);
    float gA = 0.012 / (distance(p, lampA) + 0.02);
    float gB = 0.009 / (distance(p, lampB) + 0.02);
    vec3 warm = vec3(0.85, 0.62, 0.40);
    col += warm * (gA + gB) * (0.35 + uBloom * 0.9);

    return col;
  }

  void main() {
    vec2 uv = vUv;
    vec2 fragCoord = vUv * uResolution;

    // 약한 색수차: 화면 가장자리에서 R/B 채널을 수평으로 분리
    float edge = distance(uv, vec2(0.5));
    float ca = uChroma * 0.004 * edge;
    vec3 col;
    col.r = scene(uv + vec2(ca, 0.0)).r;
    col.g = scene(uv).g;
    col.b = scene(uv - vec2(ca, 0.0)).b;

    // 시안-틸 컬러그레이드: shadow→teal, highlight→warm
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 teal = vec3(0.0, 0.55, 0.60);
    vec3 warm = vec3(0.75, 0.55, 0.35);
    vec3 tint = mix(teal, warm, smoothstep(0.15, 0.75, lum));
    col = mix(col, col * (0.6 + tint), uCyan);

    // contrast 축소(눅눅함): 0.5 기준으로 다이내믹레인지 압축
    col = mix(vec3(0.5), col, uContrast);

    // 비네팅
    float vig = smoothstep(0.85, 0.25, edge);
    col *= mix(1.0, vig, uVignette);

    // Bayer 4x4 디더(밴딩 → 의도된 질감) — 약하게
    col += bayer4x4(fragCoord) * (1.0 / 64.0);

    // 시간축 필름 그레인 — luminance 높은 곳은 덜(soft-light 근사)
    float t = uGrainAnim * uTime;
    float g = grainNoise(uv, t) - 0.5;
    float lumFinal = dot(col, vec3(0.299, 0.587, 0.114));
    float grainMask = mix(1.0, 0.4, smoothstep(0.4, 0.9, lumFinal));
    col += g * uGrain * 0.18 * grainMask;

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
  }
`
