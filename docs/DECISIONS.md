# Petrichor — 의사결정 로그 (DECISIONS)

> WORKPLAN/PRD가 '단일 진실 소스'로 참조하는 결정 기록. 각 결정은 근거·상태를 함께 남긴다.
> 최종 갱신: 2026-06-21.

## 제품 결정

| # | 결정 | 근거 | 상태 |
|---|---|---|---|
| D1 | 첫 무드 = **교대역 일대** | PRD §5.1 예시, 직접촬영 접근성·쪽타일 레퍼런스 풍부 | 확정 |
| D2 | 영상 = **직접 촬영** | 저작권 100%·정밀 큐레이션, MVP는 소수만 필요. 제너릭 스톡/CORS·재배포 함정 회피 | 확정 |
| D3 | AI 오디오 = **Suno/Udio 유료**(상업 라이선스, 소유권 아님) | 앰비언트 재생 용도엔 비독점·저작권 등록불가 무관. 출처는 `assets.license`/`source_note`에 기록. 툴 최종선택은 Phase 1 | 확정(툴 유보) |
| D4 | 네이밍 `petrichor` = **내부 코드네임** | 상표 혼잡(보드게임/게임/밴드 다수)·프리미엄 도메인 선점(.com/.io/.net/.co/.me/.xyz). 공개 브랜드명은 상업화 직전 재결정 | 잠정 |
| D5 | MVP = **무드 1개 깊게**, 성공지표=리텐션 | PRD. 다도시/계정/결제 제외(계정·미디어·분석은 P2 학습목적) | 확정 |

## 기술 스택 결정

| # | 결정 | 근거 | 상태 |
|---|---|---|---|
| T1 | 백엔드 = **Kotlin + Spring Boot** | 한국 백엔드 취업 표준, enterprise 패턴 학습, 깊이 항목 1급 지원 | 확정 |
| T2 | **Spring Boot 3.4.x** (스캐폴드 4.1.0 → 다운그레이드) | 다관점 리뷰 3개 렌즈 권고: 레퍼런스/라이브러리(bucket4j·springdoc·대부분 가이드가 3.x)·주니어 학습 안정성. 4.x의 Spring7/Jakarta/Security7/Jackson3/webmvc 분리 starter는 문서 공백으로 디버깅 비용↑ | 확정 |
| T3 | **JDK 21**(toolchain) + foojay-resolver 자동 프로비저닝 | 가상 스레드·구조적 동시성 학습. 설치 JDK 17과의 격차는 Gradle 자동 다운로드로 해소 | 확정 |
| T4 | Kotlin = **SB 3.4 호환 버전으로 정렬**(스캐폴드 2.3.21 → 다운) | 2.3.21은 SB 3.4와 stdlib/컴파일러 플래그(`-Xannotation-default-target`) 불일치 | 확정 |
| T5 | 정본 구조 = **`backend/` + `frontend/`**(모노레포 apps/ 아님) | 실제 스캐폴드 채택. apps/api 강제는 단일 백엔드 단계서 과설계. 패키지 `com.petrichor.backend` 유지 | 확정 |
| T6 | 프론트 = **Vanilla Vite + TS**(UI 프레임워크 없음) | canvas 중심 앱, 타임박스. ogl/WebGL, Web Audio raw | 확정 |
| T7 | 로컬 인프라 = **Docker Compose 단일 구성**(PG16+Redis7+MinIO) | one-command, MinIO로 미디어 파이프라인 무계정 개발 | 확정 |
| T8 | DB=JPA+Flyway · 캐시/큐=Redis(+Streams P2) · 스토리지=S3 SDK(R2/MinIO) · 인증=Spring Security+JWT(P2) | WORKPLAN 부록A | 확정 |
| T9 | 큐 구현(Redis Streams vs RabbitMQ) | — | Phase 2A에서 확정 |
| T10 | 클라우드 staging 타깃(Render/Fly) | 개발기간엔 Compose-first | Phase 0 배포 게이트에서 확정 |

## 리뷰 반영 결정 (근거: docs/PLAN-REVIEW.md)

- **마이그레이션 버전 배정**: V1=core(assets/courses/course_assets) · V2=media · V3=auth · V4=analytics (동일 V2 3중 충돌 해소).
- **events 계약**: Phase 1부터 **배치(배열) 스키마**로 확정 → P2 재작업 방지(처리만 동기→큐로 교체).
- **ffmpeg '심리스 처리'**: 서버는 루프 in/out **트림만**, 크로스페이드는 프론트(W1.8) 담당(TECH-DESIGN A-2와 정합).
- **라이선스 게이트**: `license`가 NULL/미승인 티어인 asset은 시퀀싱 매니페스트에서 **제외**.
- **보안 blocker 승격**: ffmpeg 하드닝(인자배열·셸금지·UUID경로·격리·타임아웃·리소스한도)·signed URL 설계(짧은만료·GET전용·서버강제키·버킷분리)는 **Phase 2A 착수 전 설계 게이트**.
- **Phase 0 보안**: spring-boot-starter-security를 유지하되 `SecurityConfig`로 `/actuator/**`·`/api/v1/**` permitAll + stateless + csrf off (인증은 P2 도입).
- 전체 major/minor 반영 목록은 `docs/PLAN-REVIEW.md` C절 참조. WORKPLAN은 이 결정들로 갱신 예정.
