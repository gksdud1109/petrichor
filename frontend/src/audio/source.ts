/**
 * Source 추상화 — '지금은 절차적, 향후 실미디어'를 위한 교체 지점.
 *
 * Phase 1: 실 AI 음원 파일이 없으므로 ProceduralSource(합성)로 시그니처 경험을 동작시킨다.
 * Phase 2+: 동일 인터페이스로 BufferSource(디코드된 오디오 파일)를 구현해
 *   매니페스트의 audio.*.url 을 fetch→decodeAudioData 하면 합성 → 실음원으로 무수정 교체된다.
 *
 * 계약: connect한 노드 그래프의 '출력 노드'를 outputNode로 노출.
 *       start/stop은 멱등(중복 호출 무해). setGain은 레이어 베이스 게인 제어.
 */

export interface AudioSource {
  /** 이 소스의 최종 출력 노드(레이어 GainNode 등 다운스트림에 연결). */
  readonly outputNode: AudioNode
  /** 재생 시작(이미 시작됐으면 무시). */
  start(when: number): void
  /** 정지 및 노드 정리. */
  stop(): void
}
