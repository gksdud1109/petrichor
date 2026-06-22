# Petrichor — 전체 작업 계획 (WORKPLAN)

| 항목 | 내용 |
|---|---|
| **문서 목적** | Phase 0(기반) → 서비스 완성까지의 **구체적 작업 단위 + 완료 게이트**를 정의한 실행 계획 |
| **기준 문서** | [`docs/PRD.md`](./PRD.md) · [`docs/TECH-DESIGN-AND-ROADMAP.md`](./TECH-DESIGN-AND-ROADMAP.md) · [`docs/DECISIONS.md`](./DECISIONS.md) |
| **작성일** | 2026-06-21 |
| **상태** | 초안 v0.1 — *실행 승인 대기(pending approval)* |
| **추적 방식** | **기간 산정 없음.** 캘린더가 아니라 **작업 단위(W*) 완료 + 증거 기반 게이트(G*)** 로 관리 |

> ⚠️ **이 프로젝트의 실패 정의:** "예쁜 사이트 + 얇은 CRUD 백엔드". 이 계획의 모든 Phase는 **백엔드 마일스톤을 필수 산출물로** 못 박고, 프론트는 '충분히 괜찮은' 선에서 **타임박스**한다. 프론트 작업이 예산을 초과하면 즉시 백엔드 깊이 항목으로 전환한다.

---

## 0. 전제 — 확정 사항

이 계획은 아래 결정 위에서 작성됐다. (상세 근거: `docs/DECISIONS.md`)

| 결정 | 값 |
|---|---|
| 첫 무드 | **교대역 일대** (비 갠 뒤 새벽 2시) |
| 영상 소싱 | **직접 촬영** (MVP는 소수 루프만 필요) |
| AI 오디오 | Suno/Udio **유료 티어**(상업 사용 라이선스 확보; 소유권/저작권 등록 아님). **툴 최종 선택은 Phase 1로 유보**, 모든 음원은 `assets.license`/`source_note`에 출처 기록 |
| 네이밍 | `petrichor`는 **내부 코드네임**(상표 혼잡 — 보드게임/게임/밴드 다수, 프리미엄 도메인 선점). 공개 브랜드명은 상업화 직전 재결정 |
| 백엔드 | **Kotlin + Spring Boot 3.4** (JDK 21, Gradle Kotlin DSL) |
| 프론트 | **Vanilla Vite + TypeScript** (UI 프레임워크 없음 — canvas 중심) |
| 로컬 인프라 | **Docker Compose 단일 구성, one-command** (Postgres + Redis + MinIO) |

### 확정 스택 상세

| 레이어 | 선택 | 비고 |
|---|---|---|
| 웹/DI | Spring Boot 3.4 (Web MVC) | 계층 구조 강제 → '얇은 CRUD' 회피 |
| ORM | Spring Data JPA (Hibernate) | `ddl-auto=validate` (스키마는 Flyway 소유) |
| 마이그레이션 | **Flyway** | `db/migration/V*.sql` |
| 관측성 | **Actuator + Micrometer** → Prometheus/Grafana | `/health`·`/metrics` 기본 제공 |
| 인증(P2) | **Spring Security + JWT** | access/refresh |
| 큐/워커(P2) | **Redis Streams** (1순위) 또는 RabbitMQ | BullMQ 대체 |
| 미디어 변환(P2) | **ffmpeg** (ProcessBuilder 서브프로세스) | H.264/VP9·멀티해상도·포스터 |
| 스토리지 | **S3 SDK (AWS Java)** — 로컬 MinIO / 운영 Cloudflare R2 | R2 = S3 호환 |
| 레이트리밋(P1) | **bucket4j + Redis** | |
| API 문서 | **springdoc-openapi** → `openapi-typescript` | Kotlin↔TS 타입 경계 보완 |
| 프론트 WebGL | **ogl** (또는 raw WebGL2) | 2D 풀스크린 쿼드 + 프래그먼트 셰이더 |
| 프론트 오디오 | **Web Audio API** (raw) | GainNode 레이어 + ConvolverNode 리버브 |
| 컨테이너 | **`./gradlew bootBuildImage`** (Buildpacks) | Dockerfile 불필요 |

---

## 1. 레포 최종 형상 (목표 구조)

```
petrichor/
├─ apps/
│  ├─ api/                              # Kotlin/Spring Boot (Gradle KTS)
│  │  ├─ build.gradle.kts  settings.gradle.kts  gradle/  gradlew
│  │  ├─ src/main/kotlin/com/petrichor/api/
│  │  │  ├─ PetrichorApplication.kt
│  │  │  ├─ common/        # config, 예외 처리, 로깅, OpenAPI, 페이지네이션
│  │  │  ├─ health/        # Actuator 커스텀 indicator
│  │  │  ├─ storage/       # S3/R2/MinIO 클라이언트 추상화 + signed URL
│  │  │  ├─ asset/         # entity, repository, dto
│  │  │  ├─ course/        # entity, repository, dto, controller
│  │  │  ├─ sequencing/    # 코스 구성(서버 '판단') 서비스
│  │  │  ├─ media/         # (P2) 업로드·트랜스코드 잡·워커·ffmpeg
│  │  │  ├─ queue/         # (P2) Redis Streams/Rabbit 추상화
│  │  │  ├─ auth/          # (P2) Security, JWT, user
│  │  │  ├─ account/       # (P2) 즐겨찾기·습도 프리셋
│  │  │  ├─ analytics/     # (P2) 수집·집계
│  │  │  └─ observability/ # (P3) tracing, metrics 설정
│  │  └─ src/main/resources/
│  │     ├─ application.yml  application-*.yml
│  │     └─ db/migration/V1__init.sql ...
│  └─ web/                              # Vanilla Vite + TS
│     └─ src/{main.ts, player/, gl/, audio/, humidity/, events/, api/, ui/}
├─ docs/                               # PRD · TECH-DESIGN · HANDOFF · DECISIONS · WORKPLAN
├─ infra/                             # (P2~) prometheus.yml, grafana/, (선택) deploy
├─ docker-compose.yml                  # PG + Redis + MinIO  (+ P2: Rabbit/Prometheus/Grafana)
├─ .env.example  .editorconfig  .gitignore  .nvmrc  README.md
└─ .github/workflows/ci.yml
```

---

## 2. 횡단 관심사 (모든 Phase 공통)

| 영역 | 방침 |
|---|---|
| **테스트** | 단위(JUnit5 + MockK) · 통합(**Testcontainers**: PG/Redis/MinIO/Rabbit) · e2e(MockMvc/WebTestClient) · 프론트 스모크(Playwright, 타임박스) |
| **CI/CD** | Phase마다 잡 추가: lint→build→test→migrate→(통합)→이미지 빌드→(배포). 매 커밋 그린 유지 |
| **보안** | 시크릿은 env/Compose secret, signed URL, 레이트리밋, 입력 검증(Bean Validation), 의존성 스캔 |
| **관측성** | 구조적 JSON 로깅 + correlation/trace id, Actuator/Micrometer 메트릭, 헬스 indicator |
| **문서** | `DECISIONS.md` 갱신, OpenAPI를 살아있는 API 문서로, 큰 결정은 ADR |
| **라이선스 거버넌스** | 모든 에셋에 `assets.license` + `source_note` 채움(특히 AI 오디오 티어 출처 추적) |
| **검증 원칙** | 각 게이트는 **verifier 분리 패스**로 증거 확인 후 완료 처리(같은 컨텍스트 self-approve 금지) |

---

## Phase 0 — 기반 (Foundation)

**목표:** 빈 껍데기지만 **배포 가능한 상태**까지. 프론트 예산 = **0**(hello 셸만).
**백엔드 마일스톤:** Spring Boot + JPA + Postgres 기동, Flyway 마이그레이션, `/health`(Actuator), GitHub Actions 그린.

### 작업 단위

| ID | 작업 | 세부 |
|---|---|---|
| W0.1 | 레포 베이스라인 | `git init`, `.gitignore`(Kotlin/Gradle+Node+env), `.editorconfig`, `README`(원커맨드 dev 안내), `.env.example` |
| W0.2 | `docs/DECISIONS.md` | 오늘 결정 5건 + 리서치(AI 라이선스/도메인) 근거 기록 |
| W0.3 | **Docker Compose** | `docker-compose.yml`: Postgres16 + Redis7 + MinIO(+버킷 init), 각 서비스 `healthcheck` |
| W0.4 | Spring Boot 스켈레톤 | Initializr 기반: web, actuator, data-jpa, validation, postgresql, flyway-core(+postgresql), kotlin-reflect, jackson-kotlin; test: spring-boot-starter-test, testcontainers |
| W0.5 | 설정·로깅 | `application.yml`(datasource/JPA `validate`/Actuator 노출 health,info,metrics,prometheus), 구조적 JSON 로깅, profile(`local`/`ci`) |
| W0.6 | **DB 마이그레이션** | `V1__init.sql` = TECH-DESIGN A-5의 `assets`·`courses`·`course_assets`. (`users`/`events`는 P2). 대응 JPA 엔티티 작성(`validate`로 일치 증명) |
| W0.7 | **`/health`** | Actuator `/actuator/health`(DB·Redis 컴포넌트 UP) + `/actuator/prometheus`. 친화 alias `GET /health` 선택 |
| W0.8 | web 셸 | `pnpm create vite`(vanilla-ts) + 풀스크린 `<canvas>` 자리표시 (실내용 P1) |
| W0.9 | **CI** | `.github/workflows/ci.yml`: ① api `./gradlew build`(Postgres 서비스에 Flyway migrate + health e2e) ② web `pnpm build`+typecheck |
| W0.10 | 컨테이너 | `./gradlew bootBuildImage` → OCI 이미지 |
| W0.11 | staging 배포 스캐폴드 | `render.yaml`(또는 `fly.toml`) + deploy 워크플로 — **실행은 계정 프로비저닝 후**(보류, Compose-first) |

### 완료 게이트 (증거)

- **G0.1** `docker compose up -d` → `docker compose ps`에 PG/Redis/MinIO 모두 `healthy`.
- **G0.2** `./gradlew bootRun` 부팅 로그에 Flyway `V1` 적용 + 앱 기동 성공.
- **G0.3** `curl localhost:8080/actuator/health` → `200 {"status":"UP", components: db UP, redis UP}`.
- **G0.4** **CI 그린**(api build/migrate/health-e2e + web build) — 실행 링크.
- **G0.5** `bootBuildImage` 산출 이미지가 `docker images`에 존재, 컨테이너로 health UP 재현.
- **(보류) G0.6** 클라우드 staging URL 그린 — 계정 준비 후 트리거.

---

## Phase 1 — MVP: 무드 1개 (교대역)

**목표:** 로그인 없이 **교대역 무드 1개가 완성도 있게 재생**. "머물고 싶은" 첫 세션 품질.
**백엔드 마일스톤:** **시퀀싱 엔드포인트**(서버가 코스를 에셋 조합으로 구성 — 정적 JSON 금지), 조회 API(페이지네이션·Redis 캐싱), 레이트리밋, 구조적 로깅/correlation id.
**프론트 예산(타임박스):** 풀스크린 루프 + WebGL 그레이딩 + 오디오 2레이어 + 습도 슬라이더 — '충분히 괜찮은' 선.

### 백엔드 작업 단위

| ID | 작업 | 세부 |
|---|---|---|
| W1.1 | 도메인 모델 | `asset`/`course`/`course_asset` 엔티티·리포지토리·DTO |
| W1.2 | 조회 API | `GET /api/v1/courses`(페이지네이션), `GET /api/v1/courses/{id}` — **Redis 캐싱**(@Cacheable) |
| W1.3 | **시퀀싱 서비스** | `GET /api/v1/courses/{id}/session` → 서버가 에셋 조합 + base gain + 습도 곡선 + grade_config를 **구성**해 세션 매니페스트 반환. *정적 파일 아님* (아래 예시 참조) |
| W1.4 | 레이트리밋 | bucket4j + Redis, 초과 시 `429` |
| W1.5 | 이벤트 수신(스텁) | `POST /api/v1/events`(session_start/slider_change/heartbeat) — P1은 검증·로깅까지, 파이프라인은 P2 |
| W1.6 | 에셋 시딩 | 교대역 무드 seed: 영상 루프 3~5 + 음악 루프 2~3 + 환경음 레이어. `storage_url`은 MinIO. **`license`/`source_note` 필수 기입** |
| W1.7 | OpenAPI | springdoc 노출 + web용 TS 클라이언트 코드젠 파이프라인 |

**시퀀싱 매니페스트 예시 (서버 '판단' 산출):**
```json
{
  "courseId": 1, "region": "gyodae",
  "video":  [{ "assetId": 12, "url": "...", "loopInMs": 0, "loopOutMs": 42000, "poster": "..." }],
  "audio": {
    "music":   [{ "assetId": 31, "url": "...", "baseGain": 0.8, "humidityCurve": {"reverbWet":[0,0.6]} }],
    "ambient": [{ "assetId": 41, "kind": "rain", "url": "...", "baseGain": 0.3, "humidityCurve": {"gain":[0.1,1.0]} }]
  },
  "grade": { "grain": 0.4, "cyan": 0.3, "bloom": 0.2, "downscale": 0.5 },
  "humidity": { "default": 0.5 }
}
```

### 프론트 작업 단위 (타임박스)

| ID | 작업 | 세부 |
|---|---|---|
| W1.8 | 루프 플레이어 | `<video crossorigin="anonymous">` 풀스크린 + **2버퍼 크로스페이드**로 심리스 |
| W1.9 | WebGL 그레이딩 | ogl 프래그먼트 셰이더: 그레인 · 시안 틴트 · 색수차 · 다운스케일/블룸. 비디오 텍스처 샘플 |
| W1.10 | Web Audio 그래프 | music GainNode + ambient 레이어 GainNode들 + ConvolverNode 리버브(wet/dry) |
| W1.11 | **습도 슬라이더** | `applyHumidity(h)`: rainGain·reverbWet·uCyanTint·uGrain 곡선 동시 제어(TECH-DESIGN A-3 매핑) |
| W1.12 | 이벤트 에미터 | heartbeat 30s + session_start + slider_change → `POST /events` |
| W1.13 | 쪽타일 아이덴티티 | 로딩/프레임 UI에 흰 세로 타일 그리드 모티프 |
| W1.14 | 매니페스트 연동 | 시퀀싱 응답으로 비디오·오디오·그레이드 구동 |

### 완료 게이트 (증거)

- **G1.1** `GET /courses/{id}/session`이 **서버 구성** 결과를 반환 — `course_assets` 행을 바꾸면 매니페스트가 달라짐(정적 아님 증명).
- **G1.2** 동일 코스 2회 호출 시 **Redis 캐시 히트** 관측(메트릭/로그).
- **G1.3** 임계 초과 요청에 `429`.
- **G1.4** 프론트에서 루프가 **심리스**로 재생되고, 셰이더 그레이딩이 보이며, **습도 슬라이더가 4개 파라미터(빗소리/시안/그레인/리버브)를 동시에** 가청·가시적으로 변화시킴.
- **G1.5** `POST /events` 수신·검증 성공(로그/응답).
- **G1.6** CI에 통합 테스트(Testcontainers: PG+Redis) 추가 후 그린.

---

## Phase 2 — 백엔드 깊이 ⭐ (학습의 본체)

**목표:** 백엔드 포트폴리오 가치 확보. '흔한 CRUD'와 차별화되는 전부가 여기 있다.
**백엔드 마일스톤:** ① 미디어 파이프라인 ② 인증·계정 ③ 이벤트/분석 파이프라인 ④ 관측성 강화.
**프론트 예산:** 최소(업로드 관리/로그인/프리셋 저장 UI는 '충분히' 선).

### 2A. 미디어 파이프라인 ⭐

| ID | 작업 | 세부 |
|---|---|---|
| W2A.1 | 스키마 확장 | `V2`: `asset_variants`(원본→변환 산출물: codec·resolution·url·bytes), 잡 상태 테이블 |
| W2A.2 | 업로드 | `POST /api/v1/media/uploads` — presigned PUT(MinIO/R2) 또는 멀티파트, 원본 등록 |
| W2A.3 | 큐 적재 | Redis Streams에 transcode 잡 enqueue(consumer group) |
| W2A.4 | **워커** | 원본 다운로드 → **ffmpeg(ProcessBuilder)**: H.264 + VP9, 멀티해상도(1080/720), 포스터 프레임, 심리스 처리 → 변환물 업로드 → `asset_variants` 기록 |
| W2A.5 | 신뢰성 | **재시도 + dead-letter**, 멱등성(중복 잡 방지), 타임아웃 |
| W2A.6 | signed URL 서빙 | S3 presign(R2 호환), CDN `Cache-Control`/CORS(crossorigin 비디오) |
| W2A.7 | 파이프라인 메트릭 | queued/processing/failed/duration → Micrometer |

- **G2A** *영상 1개를 **업로드만 하면*** 트랜스코딩→변환물 저장→`asset_variants` 기록→signed URL 재생까지 **수동 단계 0으로 자동 완결**(TECH-DESIGN Phase 2 완료 기준). 실패 잡은 재시도 후 dead-letter로 관측됨.

### 2B. 인증·계정

| ID | 작업 | 세부 |
|---|---|---|
| W2B.1 | 스키마 | `V2`: `users`, `favorites`, `humidity_presets` |
| W2B.2 | Security + JWT | Spring Security, access/refresh 토큰, 비번 해시(bcrypt/argon2) |
| W2B.3 | 인증 API | `register`/`login`/`refresh`/`logout` |
| W2B.4 | 계정 기능 | `GET/PUT /me/favorites`, `GET/PUT /me/presets`(습도 프리셋 저장/복원) |
| W2B.5 | anon 연결 | 비로그인 `anon_id` 히스토리를 로그인 시 user에 연결 |

- **G2B** 로그인 → 즐겨찾기 코스 + 습도 프리셋 저장 → 로그아웃 → 재로그인 시 **복원**됨(e2e).

### 2C. 이벤트·분석 파이프라인

| ID | 작업 | 세부 |
|---|---|---|
| W2C.1 | 스키마 | `V2`: `events`(raw, 시간 파티셔닝 고려), `event_aggregates` |
| W2C.2 | 수집 | `POST /api/v1/events` 배치 수신 → 큐(Redis Streams) |
| W2C.3 | 집계 잡 | 워커/`@Scheduled`: **D1·D7 리텐션**, 평균 세션 길이, **슬라이더 사용률**, 코스별 재생수 |
| W2C.4 | 조회 | 지표 쿼리 엔드포인트(+선택: 간단 대시보드) |

- **G2C** 이벤트 수집 → 집계 잡 → 리텐션·세션길이·슬라이더 사용률 **수치가 쿼리로 조회**됨(PRD §4 지표와 직접 연결).

### 2D. 관측성 강화 (부분 — 완성은 P3)

| ID | 작업 | 세부 |
|---|---|---|
| W2D.1 | 메트릭 | Micrometer → Prometheus, Grafana 대시보드(Compose에 추가) |
| W2D.2 | 로깅 | correlation/trace id 전파, JSON 로그 |
| W2D.3 | 헬스 | storage·queue 헬스 indicator |

- **G2D** Grafana에서 앱+파이프라인 메트릭(요청률/지연/잡 처리량/실패)을 본다.

---

## Phase 3 — 확장 & 마감 (서비스 완성)

**목표:** 무드 2~3개 + 추천/시퀀싱 고도화 + 운영 관측성 완성. **운영 지표로 의사결정 가능한 상태.**
**백엔드 마일스톤:** 시간대/무드 가중 추천, 캐시 전략 정교화, 트레이싱, 부하 점검.

| ID | 작업 | 세부 |
|---|---|---|
| W3.1 | 무드 확장 | 2~3번째 무드(예: 광명/홍콩 또는 교대역 변주) — 에셋/코스 재사용 + 시퀀싱 검증 |
| W3.2 | **추천/시퀀싱 고도화** | 시간대·무드 가중 선택(서버 로직), Redis 캐시 전략 정교화 |
| W3.3 | 트레이싱 | Micrometer Tracing/OpenTelemetry → Tempo/Jaeger, 스팬 전파 |
| W3.4 | 대시보드/알림 | Grafana 대시보드 확장 + 기본 알림 규칙 |
| W3.5 | 부하 점검 | k6/Gatling으로 시퀀싱·미디어 서빙 부하 리포트 |
| W3.6 | 운영 견고화 | 레이트리밋 튜닝, PG 백업/복구 절차, CDN 캐시 전략, 에러버짓 |

### 완료 게이트 (증거)

- **G3.1** 무드 ≥2개가 라이브, 동일 시퀀싱 엔진으로 구동.
- **G3.2** 추천 엔드포인트가 시간대/무드에 따라 **다른 선택**을 반환.
- **G3.3** 분산 트레이스에서 요청→시퀀싱→캐시/DB 스팬이 보임.
- **G3.4** 부하 리포트 산출(목표 지연/throughput 명시 후 측정).
- **G3.5** 운영 지표(리텐션·지연·잡 실패율)를 보고 의사결정할 수 있음.

---

## 4. 완료 게이트 요약 + 이력서 매핑

| Phase | 백엔드 핵심 산출물 | 프론트(타임박스) | 대표 완료 게이트 | 이력서 한 줄 매핑 |
|---|---|---|---|---|
| 0 기반 | CI/CD·배포 파이프라인, Flyway, Actuator health | hello 셸 | G0.4 CI 그린 + G0.5 이미지 | 운영 기초(Docker/CI/마이그레이션) |
| 1 MVP | **시퀀싱 API**·Redis 캐싱·레이트리밋 | 루프+셰이더+오디오+습도 | G1.1 서버 구성(비정적) | REST 설계·캐싱·서버 시퀀싱 |
| 2 깊이 | **미디어 파이프라인**·인증·분석 | 업로드/로그인/프리셋 | G2A 업로드→자동 배포 | **ffmpeg 트랜스코딩 파이프라인 · R2/signed URL · 이벤트 집계 · JWT** |
| 3 확장 | 추천·트레이싱·부하 | 2~3 무드 | G3.5 지표 기반 운영 | 추천 로직·관측성·성능 |

> **목표 이력서 한 줄(역산):** "ffmpeg 기반 미디어 트랜스코딩 파이프라인(큐/워커)과 오브젝트 스토리지·CDN signed URL 서빙, 이벤트 수집·집계 분석 파이프라인, JWT 인증을 갖춘 앰비언트 웹 서비스 백엔드를 설계·구현 (Kotlin/Spring Boot/PostgreSQL/Redis/R2)." → **Phase 2 완주가 필수.**

---

## 5. 작업 추적 방식

- **기간 산정 없음.** 각 Phase는 `TaskCreate`로 작업 단위(W*)를 등록하고, **게이트(G*) 증거 수집** 시 완료 처리.
- 대규모 스캐폴딩/구현은 `executor`에 위임, 각 게이트는 `verifier`/`code-reviewer` **분리 패스**로 승인(자기 컨텍스트 self-approve 금지).
- 프론트 타임박스 초과 → 백엔드 깊이 항목으로 강제 전환(원칙 재확인).

## 6. 유보·미결 항목 (Phase별 트리거)

| 항목 | 결정 시점 |
|---|---|
| 클라우드 staging 타깃(Render/Fly) + 계정·토큰 | Phase 0 마지막 배포 게이트 |
| AI 오디오 툴 최종(Suno vs Udio) | Phase 1 오디오 제작 직전 |
| 큐 구현(Redis Streams vs RabbitMQ) | Phase 2A 착수 |
| 공개 브랜드명(상표 클리어런스 USPTO+KIPRIS) | 상업화 직전 |
| CDN 제공자(R2+Cloudflare CDN vs Bunny) | Phase 2A signed URL 단계 |

## 부록 A. 스택 매핑 (TECH-DESIGN 추천 ↔ 확정)

| TECH-DESIGN(NestJS 기준) | 확정(Kotlin/Spring) |
|---|---|
| NestJS | Spring Boot 3.4 (Kotlin) |
| Prisma | Spring Data JPA + Flyway |
| BullMQ | Redis Streams / RabbitMQ |
| fluent-ffmpeg | ffmpeg via ProcessBuilder |
| pino 로깅 | Logback JSON + Micrometer |
| `@nestjs/terminus` | Spring Boot Actuator |
| R2 SDK | AWS S3 SDK (R2 호환) + 로컬 MinIO |
| passport/JWT | Spring Security + JWT |

---

---

## 7. 리뷰 반영 개정 (v0.2 — 근거: docs/PLAN-REVIEW.md)

다관점 리뷰(42건) 반영. 아래가 §0~§6 본문보다 **우선**한다.

### 7.1 정정 (blocker)
- **정본 구조**: §1 트리의 `apps/api`→**`backend/`**, `apps/web`→**`frontend/`**로 읽는다(모노레포 apps/ 폐기). 패키지 `com.petrichor.backend`.
- **스택 확정**: Spring Boot **3.4.13**(스캐폴드 4.1.0에서 다운그레이드), Kotlin **2.1.21**, JDK 21 toolchain + **foojay-resolver 자동 프로비저닝**(설치 17 격차 해소). 근거 DECISIONS T2~T5.
- **Phase 0 처리됨**: build.gradle에 flyway/postgresql/data-redis/micrometer-prometheus 추가, `application.yaml`(datasource·JPA validate·open-in-view=false·flyway·actuator·redis), `SecurityConfig`(/actuator/**·/api/v1/** permitAll+stateless+csrf off), `.gitignore`(.env/.omc), `docker-compose`(기본 minio 자격증명 변경), `DECISIONS.md` 생성.
- **Phase 0 DB 검증 범위 정정**: Flyway V1 적용 + 부팅까지. **JPA 엔티티/매핑은 Phase 1 도메인 작업으로 연기**(ddl-auto=validate는 엔티티 0개에서 무해 통과) — 본문 Phase 0 표 #4의 'JPA 엔티티 작성(validate 일치 증명)' 문구는 이 항목으로 대체한다.

### 7.2 계획 정합 (major)
- **마이그레이션 배정**: V1=core(assets/courses/course_assets) · V2=media · V3=auth · V4=analytics (동일 V2 3중 충돌 해소). users→events FK 순서 보장.
- **events 계약**: Phase 1부터 **배치(배열) 스키마**로 확정(처리만 P2에서 동기→큐 교체) → 프론트 에미터·계약 재작업 방지.
- **ffmpeg '심리스 처리'**: 서버=루프 in/out **트림만**, 크로스페이드=프론트 W1.8(TECH-DESIGN A-2 정합).
- **라이선스 게이트**: `license` NULL/미승인 asset은 시퀀싱 매니페스트에서 **제외**(W1.6 시딩·G1 게이트).

### 7.3 백엔드 깊이 보강 (학습 핵심)
- **DB**: 조회는 `@Transactional(readOnly=true)`; 외부 I/O(S3/ffmpeg)는 **트랜잭션 밖** 후 짧은 tx로 메타 커밋; favorites/presets에 `@Version` 낙관락→409(P2B); N+1은 fetch join/@EntityGraph + **EXPLAIN ANALYZE 전후** 증거(P1); events **월별 RANGE 파티셔닝** + (type,created_at)/(course_id,created_at) 인덱스(P2C).
- **큐/워커 신뢰성(P2A)**: **Transactional Outbox**; 멱등성 키(media: (asset_id,variant_spec) UNIQUE / events: client event_id inbox dedup); Redis Streams consumer group **XACK/XAUTOCLAIM/PEL→DLQ**(delivery_count 임계); 백프레셔(소비 lag 메트릭·동시성 상한); crash 시 PEL 회수·부분객체 cleanup; ProcessBuilder `waitFor(timeout)+destroyForcibly`, stdout/stderr 별도 스레드 소비.
- **캐싱(P1)**: course/course_asset 쓰기 시 `@CacheEvict(courseId)` 무효화(G1.1 비정적 증명과 정합), stampede 방지, 키=courseId+manifest schema version.
- **관측성(P2D/P3)**: API=**RED**, 워커/큐=**USE**(소비 lag=saturation), DB=HikariCP active/pending; **SLO 숫자화**(시퀀싱 p95<50ms 캐시히트, 부하 N RPS 에러율<1%); HikariCP 포화 미니 실험.
- **graceful shutdown**: `server.shutdown=graceful` + 워커 안전 종료(진행 잡 손실 0).

### 7.4 보안 설계 게이트 (blocker 포함, Phase 2A 착수 전 필수)
- **ffmpeg 하드닝**[blocker]: 인자 배열만(셸 경유 금지), 사용자 파일명 인자 금지·서버 UUID 경로, 입력 화이트리스트(-f·-t·해상도·threads 상한), 격리 컨테이너(cap-drop·seccomp·cgroup·네트워크 차단·디스크 쿼터), 잡 타임아웃.
- **signed URL**[blocker]: 다운로드 짧은 만료·GET 전용·객체 키 범위; 업로드 PUT은 서버가 키 강제(UUID)+Content-Length-Range·Content-Type; 경로 traversal 차단; 원본/공개 버킷 분리.
- **그 외**: SSRF 가드(내부 IP/IMDS 차단), 업로드 남용(크기·매직바이트·쿼터), CORS allowlist(credentials 시 `*` 금지, crossorigin 비디오 ACAO 정확), JWT 하드닝(alg 고정·refresh 회전·폐기), CI에 gitleaks+Trivy, PII 보존정책(IP 해시·raw 90일).

### 7.5 수용기준 정량화
- **G1.4**: 습도 0→1 스윕 시 rainGain·reverbWet·uCyanTint·uGrain 4값이 A-3 lerp 범위대로 변함을 **단위테스트**로, 크로스페이드 **무프레임드롭**.
- **G1.3**: 레이트리밋을 token bucket으로, 429에 `Retry-After`/`X-RateLimit-*`; 임계 숫자화(예: IP당 /events 60req/min).
- **CI**: OpenAPI→TS 코드젠 diff 깨지면 빌드 실패(경량 컨트랙트), 의존성/이미지 스캔 게이트.

---

*상태: 실행 승인 대기 → Phase 0 착수(진행 중). 본 개정(v0.2)이 본문에 우선한다.*
