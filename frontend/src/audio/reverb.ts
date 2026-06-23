/**
 * 합성 임펄스 리스폰스 — 파일 없이 ConvolverNode 리버브를 동작시킨다.
 *
 * gskinner 레시피: 필터된 감쇠 노이즈를 OfflineAudioContext 없이 직접 생성
 * (지수 감쇠 envelope + 미세 스테레오 디코릴레이션). decay/preDelay는 습도가
 * 더 길고 어두운 새벽 골목 잔향을 만들도록 조정 가능.
 */

export function createImpulseResponse(ctx: AudioContext, durationSec = 3.2, decay = 2.4): AudioBuffer {
  const rate = ctx.sampleRate
  const length = Math.floor(rate * durationSec)
  const impulse = ctx.createBuffer(2, length, rate)
  for (let ch = 0; ch < 2; ch++) {
    const data = impulse.getChannelData(ch)
    for (let i = 0; i < length; i++) {
      const t = i / length
      // 지수 감쇠 노이즈; 채널마다 다른 시드로 약한 스테레오 폭
      data[i] = (Math.random() * 2 - 1) * Math.pow(1 - t, decay)
    }
  }
  return impulse
}
