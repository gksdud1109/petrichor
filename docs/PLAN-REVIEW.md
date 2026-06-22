# Petrichor — 계획 다관점 리뷰 (PLAN-REVIEW)

> 생성: WORKPLAN 다관점 리뷰 워크플로우(critic·architect·learning-value·security-ops). 발견 42건: **blocker 8 · major 19 · minor 12 · nit 3**.
> 대상: docs/WORKPLAN.md. 이 문서는 WORKPLAN 정정·보강의 근거다.

## 핵심 요약 (액션 우선순위)

### A. 즉시 결정 필요 (build 차단 blocker)
1. **Spring Boot 4.1.0 vs 3.4 다운그레이드** — 3개 렌즈 동시 지적. 실제 스캐폴드=SB 4.1.0+Kotlin 2.3.21(매우 앞선 버전), 계획=3.4. 권고: 학습 레퍼런스 풍부함·라이브러리(bucket4j/springdoc/대부분 가이드가 3.x 기준) 호환성 → **3.4.x 권장**. 4.x 유지 시 webmvc 분리 starter·tools.jackson·Security7 좌표로 전면 갱신하고 리스크를 DECISIONS에 명시. → **사용자 결정 필요.**
2. **JDK 21 정렬** — toolchain=21, 설치=17. → foojay-resolver로 Gradle 자동 다운로드 활성화(이번 적용) 또는 Temurin 21 설치.
3. **정본 구조** — `backend/`+`frontend/`(실제) 채택 확정, WORKPLAN §1의 apps/api 표기 정정.

### B. 이미 처리됨 (이번 세션 백엔드 작업이 리뷰와 합치)
- 의존성 추가: flyway-core/flyway-database-postgresql/postgresql/data-redis/micrometer-prometheus ✓
- application.yaml: datasource/JPA(ddl-auto=validate, open-in-view=false)/flyway/actuator(health,info,metrics,prometheus)/redis ✓
- SecurityConfig: /actuator/**·/api/v1/** permitAll + stateless + csrf off ✓ (architect MAJOR '보안 조기도입 게이트충돌' 해소책)
- .gitignore(.env/.omc 제외) + docker-compose(PG/Redis/MinIO, 기본 minio 자격증명 변경) + foojay-resolver ✓
- 미처리: **DECISIONS.md 생성(W0.2)** → 작성 예정

### C. WORKPLAN에 접어넣을 고가치 개선 (major 핵심)
- **DB 깊이**: 트랜잭션 경계/격리, @Version 낙관적 락(favorites/presets→409), N+1 fetch join+EXPLAIN ANALYZE 증거, events 인덱스+월별 RANGE 파티셔닝, 외부 I/O(S3/ffmpeg)는 트랜잭션 밖.
- **큐/워커 신뢰성**: Transactional Outbox, 멱등성 키 명세(media: asset_id+variant_spec UNIQUE / events: client event_id inbox dedup), Redis Streams XACK/XAUTOCLAIM/PEL→DLQ, 백프레셔(소비 lag), crash 복구·부분객체 정리.
- **캐싱 정확성**: @CacheEvict(courseId) 무효화(G1.1 비정적 증명과 정합), stampede 방지, 키=courseId+manifest schema version.
- **관측성**: API=RED, 워커/큐=USE(소비 lag), DB=HikariCP active/pending. SLO 숫자화(시퀀싱 p95<50ms 캐시히트, 부하 N RPS 에러율<1%).
- **보안(blocker 2건 포함)**: ffmpeg 하드닝(인자배열·셸금지·UUID경로·격리컨테이너·타임아웃·리소스한도)[blocker], signed URL 설계(짧은만료·GET전용·서버강제키·버킷분리)[blocker], SSRF 가드, 업로드 남용(크기/매직바이트/쿼터), CORS allowlist, JWT 하드닝(alg고정·refresh회전·폐기), gitleaks/Trivy CI 게이트, PII 보존정책.
- **계획 정합**: V2 마이그레이션 3중 충돌→V2=media/V3=auth/V4=analytics, users→events FK 순서, events 계약을 P1부터 배치(배열)로 확정(P2 재작업 방지), ffmpeg '심리스 처리' 모순 해소(크로스페이드=프론트, 서버=트림), license NULL/미승인 asset은 매니페스트 제외 게이트.
- **수용기준 정량화**: G1.4 주관표현→습도 0→1 스윕 시 4 uniform이 A-3 lerp 범위대로 변함을 단위테스트로, 크로스페이드 무프레임드롭.

---

# 부록: 4개 렌즈 원자료 전문

## 렌즈: learning-value (백엔드 학습/포트폴리오 가치 — 한국 백엔드 주니어 취업 포트폴리오)

**강점**
- 실패 정의('예쁜 사이트 + 얇은 CRUD')를 문서 최상단에 명시하고 모든 Phase에서 '프론트 타임박스 초과 → 백엔드 깊이 항목 강제 전환' 원칙을 반복 강제한 것은 학습가치 보존 장치로 탁월하다. 주니어가 흔히 빠지는 '예쁜 프론트에 시간 다 쓰기' 함정을 구조적으로 차단한다.
- 시퀀싱 API를 '정적 JSON 금지 / 서버가 판단'으로 못 박고, G1.1에서 'course_assets 행을 바꾸면 매니페스트가 달라짐(정적 아님 증명)'이라는 증거 게이트를 둔 것은 '설정 서버화 = 얇은 CRUD'를 회피하는 정확한 안전장치다.
- Phase 2를 '학습의 본체'로 분리하고 미디어 파이프라인(ffmpeg+큐+워커), 재시도/dead-letter, 멱등성, 이벤트 집계, JWT를 명시 — 흔한 CRUD/To-Do와 차별화되는 시스템 설계 경험이 실제로 포트폴리오 한 줄로 역산되어 있다(이력서 매핑 표 §4).
- 검증을 '같은 컨텍스트 self-approve 금지 / verifier 분리 패스 + 증거 기반 게이트(G*)'로 운영하는 것은 시니어 코드리뷰 문화에 가까운 좋은 습관이며, 게이트마다 '무엇으로 증명하는가'가 구체적이다(예: G1.2 캐시 히트 관측, G2A 수동단계 0 자동완결).
- Testcontainers(PG/Redis/MinIO/Rabbit) 기반 통합 테스트를 횡단 관심사로 둔 것은 mock 남용을 피하고 실DB 동작을 검증하는 올바른 방향이다.
- 라이선스 거버넌스(assets.license/source_note)를 데이터 모델 필드로 강제 — 추적가능성을 스키마 레벨에서 다루는 실무 감각이 좋다.

**발견**
- **[blocker]** (환경/스택 정합성 (계획 vs 실제 레포)) WORKPLAN이 가정하는 환경과 실제 스캐폴드가 어긋난다. (1) 계획은 JDK 21을 가정하나 설치된 JDK는 17이다(`java -version` = 17.0.18). build.gradle.kts toolchain은 21을 요구하므로 현 상태로는 빌드가 toolchain 다운로드/실패로 막힌다. (2) 계획은 Spring Boot 3.4를 명시하나 실제 build는 Spring Boot 4.1.0 + Kotlin 2.3.21로 스캐폴드됨 — 4.x는 Spring 7/Jakarta 변경·문서 부족으로 주니어 학습에 함정이 많고, 다수 레퍼런스(bucket4j, springdoc, micrometer 연동 가이드)가 3.x 기준이다. (3) 디렉터리는 `./backend/`인데 계획 §1은 `apps/api/`, 패키지도 실제 `com.petrichor.backend` vs 계획 `com.petrichor.api`. (4) 계획이 기준문서로 참조하는 `docs/DECISIONS.md`가 존재하지 않는다(W0.2 산출물). (5) actuator는 build에 있으나 redis/flyway/postgresql/testcontainers 의존성이 아직 build.gradle.kts에 없다.
    - 권고: Phase 0 착수 전에 스택 버전을 단일 진실로 합치는 W0.0를 추가하라. 권고: JDK는 21로 통일(SDKMAN으로 Temurin 21 설치)하거나, 17 유지 시 가상 스레드·구조적 동시성 학습을 포기하게 되므로 21 설치를 강권. Spring Boot는 학습 레퍼런스 풍부함과 라이브러리 호환성을 위해 3.4(LTS-ish)로 다운그레이드하거나, 4.x를 유지한다면 그 결정과 리스크를 DECISIONS.md에 명시. WORKPLAN의 경로/패키지 표기를 실제 `./backend/`·`com.petrichor.backend`로 정정. DECISIONS.md를 W0.2에서 실제 생성.
    - 위치: 0. 전제 / §1 레포 형상 / W0.2 / build.gradle.kts
- **[major]** (DB 깊이 — 트랜잭션/격리수준/락/인덱싱/쿼리플랜) JPA가 스택에 있으나 계획 어디에도 트랜잭션 경계·격리수준·낙관적 락(@Version)·인덱스 설계·EXPLAIN ANALYZE 기반 쿼리플랜 검증이 학습 목표로 등장하지 않는다. 이것은 한국 백엔드 주니어 면접에서 가장 자주 묻는 영역(트랜잭션 전파/격리, N+1, 인덱스)인데 계획상 'JPA 엔티티 작성'과 'CRUD'에 머물 위험이 크다. 특히 W2A.5 멱등성·W2C 집계는 동시성/경합이 실재하는데 락 전략이 명시되지 않았다.
    - 권고: 고가치·저비용 추가: (1) Phase 1 W1.x에 'course_assets 조회 N+1을 fetch join/@EntityGraph로 제거하고 EXPLAIN ANALYZE 전후 비교를 G로 증거화'. (2) Phase 2B favorites/presets 갱신에 낙관적 락(@Version) + 충돌 시 409 재시도 흐름을 1건 구현. (3) events/event_aggregates에 의도적 인덱스(부분/복합/시간) 설계 + 쿼리플랜 캡처. (4) @Transactional 전파/격리(REQUIRES_NEW, READ_COMMITTED vs REPEATABLE_READ)를 집계 잡에서 1회 의식적으로 사용. 스코프 부풀림 없이 기존 작업에 '증거 게이트'만 부착하면 된다.
    - 위치: W0.6 / W1.1~1.2 / W2A.5 / W2B.4 / W2C.1~3
- **[major]** (큐/워커 신뢰성 — outbox 패턴 / 멱등성 키 / 백프레셔) W2A.5가 '멱등성(중복 잡 방지), 재시도+dead-letter'를 한 줄로 묶었고, W2C.2는 '이벤트 → 큐'를 다루지만 DB 커밋과 큐 발행 사이의 원자성 문제(이중 쓰기, dual-write)를 outbox/inbox 패턴으로 다루겠다는 언급이 없다. 이는 이벤트 기반 시스템에서 시니어가 주니어에게 꼭 가르치고 싶은 핵심 개념이고, 마침 이 프로젝트에 events·media-job이라는 완벽한 실습 대상이 두 개나 있다. 멱등성 '키'의 구체(어떤 키로 dedup하는지)와 컨슈머 백프레셔(소비 지연 시 적재 폭주 제어)도 비어 있다.
    - 권고: W2A/W2C에 명시적으로: (1) Transactional Outbox 패턴 — DB 트랜잭션 내 outbox 테이블 기록 → 별도 릴레이가 Redis Streams로 발행(at-least-once + 멱등 소비). (2) 멱등성 키를 명세화(media: 원본 checksum/asset_id, events: client-generated event_id를 inbox로 dedup). (3) Redis Streams consumer group의 PEL/XACK/XAUTOCLAIM로 재처리·dead-letter를 구현하고, 소비 lag 메트릭으로 백프레셔를 관측. 이 세 가지가 'ffmpeg CRUD'와 '진짜 파이프라인'을 가르는 분기점이다.
    - 위치: W2A.3~2A.5 / W2C.2
- **[major]** (관측성 메트릭의 학습 모델 — RED/USE, 부하의 목표치) W2D/W3에 Prometheus/Grafana/트레이싱이 있으나 '무엇을 어떤 모델로 측정하는가'가 RED(Rate/Errors/Duration)·USE(Utilization/Saturation/Errors) 같은 표준 프레임으로 정리되어 있지 않다. G3.4 부하 점검도 '목표 지연/throughput 명시 후 측정'이라고만 하고 구체 SLO/에러버짓 수치 예시가 없어, 측정이 장식(대시보드 캡처)에 그칠 위험이 있다. 커넥션 풀(HikariCP) 튜닝·포화도 관측도 USE의 핵심인데 빠져 있다.
    - 권고: (1) W2D.1을 'API는 RED, 워커/큐는 USE(소비 lag=saturation), DB는 HikariCP active/pending(USE)'로 메트릭을 프레임화. (2) HikariCP 풀 사이즈를 의도적으로 작게 잡아 포화→pending 대기를 Grafana에서 관측하는 미니 실험을 W2D 또는 W3.5에 추가(커넥션 풀 튜닝 학습). (3) G3.4 SLO를 숫자로 선언(예: 시퀀싱 p95 < 50ms 캐시히트시, 부하 N RPS에서 에러율 < 1%) 후 위반=에러버짓 소진으로 해석. 비용 거의 0, 학습가치 큼.
    - 위치: W2D.1 / W3.5 / G3.4
- **[minor]** (페이지네이션 전략 — 커서 vs 오프셋) W1.2가 'GET /courses(페이지네이션)'을 오프셋 가정으로 두는데, events/analytics 같은 대량·시간순 데이터(W2C 조회)에서 오프셋 페이지네이션은 깊은 페이지 성능 저하·중복/누락 문제가 있어 커서 기반이 더 적절하다. 두 방식의 트레이드오프는 좋은 학습 포인트인데 계획상 구분이 없다.
    - 권고: courses는 오프셋(Pageable)로 두되, events/aggregates 조회(W2C.4)는 커서(키셋) 페이지네이션으로 구현해 두 패턴을 의도적으로 대조. created_at+id 복합 키셋 + 안정 정렬을 증거화.
    - 위치: W1.2 / W2C.4
- **[minor]** (레이트리밋 알고리즘 의식화 / API 버저닝·컨트랙트 테스트) (1) W1.4가 bucket4j+Redis를 쓰지만 '어떤 알고리즘(token bucket vs sliding window)을 왜 쓰는지, 429에 Retry-After/RateLimit 헤더를 주는지'가 명시되지 않아 라이브러리 호출에 그칠 수 있다. (2) URL에 /api/v1은 있으나 버저닝 정책(언제 v2, 하위호환 깨짐 처리)과, Kotlin↔TS 타입 경계(openapi-typescript)를 CI에서 깨지면 실패시키는 컨트랙트 테스트가 게이트화되지 않았다.
    - 권고: (1) W1.4에 token bucket 선택 근거 + 429 응답에 Retry-After/X-RateLimit-* 헤더를 포함하고 통합테스트로 증거화. (2) W1.7 OpenAPI 코드젠을 CI 게이트로 승격 — 스펙 변경 시 생성된 TS 타입 diff가 깨지면 빌드 실패(소비자 주도 컨트랙트의 경량판). 버저닝 정책 한 문단을 DECISIONS.md에 기록.
    - 위치: W1.4 / W1.7 / G1.3
- **[minor]** (12-factor / graceful shutdown / 가상 스레드) (1) graceful shutdown(진행 중 트랜스코드·인플라이트 요청 드레이닝)이 계획에 없는데, ffmpeg 워커가 있는 이 시스템에서 SIGTERM 처리·잡 안전 종료는 운영 견고화의 핵심 학습거리다(W3.6 '운영 견고화'에 암시되나 명시 안 됨). (2) JDK 21을 쓰기로 하면서도 virtual threads(spring.threads.virtual.enabled) / 구조적 동시성을 어디서 학습할지 계획에 없다. (3) 12-factor 중 config(env)·logs(stdout)는 다뤄지나 'disposability(빠른 기동/우아한 종료)'·'admin processes(마이그레이션)'를 명시적 학습 항목으로 묶으면 좋다.
    - 권고: (1) W3.6 또는 Phase 0 W0.5에 graceful shutdown(server.shutdown=graceful + 워커가 XCLAIM 중인 잡 완료 후 종료) 추가, 증거: SIGTERM 후 진행 중 잡 손실 0. (2) JDK 21 확정 시 시퀀싱/이벤트 수신 핸들러에 virtual threads를 켜고 블로킹 I/O 동시성 변화를 부하리포트로 관측(W3.5 연계). (3) 횡단 관심사 표에 '12-factor 체크리스트(특히 disposability)' 한 줄 추가.
    - 위치: W0.5 / W3.5 / W3.6 / §2 횡단 관심사
- **[nit]** (Phase 0 부팅 게이트의 선결 의존성) G0.3이 'redis UP'을 헬스 컴포넌트로 요구하는데, Phase 0 build에는 아직 redis/flyway/postgresql 의존성이 없고 W0.4 목록에도 redis starter가 빠져 있다. 게이트와 의존성 목록이 불일치한다.
    - 권고: W0.4 의존성 목록에 spring-boot-starter-data-redis(또는 data-redis-reactive 불요시 lettuce 기본)와 flyway-database-postgresql, postgresql 드라이버, testcontainers(junit-jupiter, postgresql, ...) 명시. G0.3의 redis UP을 충족하도록 RedisHealthIndicator 활성 경로 확인.
    - 위치: W0.4 / G0.3 / build.gradle.kts

---

## 렌즈: security-ops

**강점**
- 보안을 '횡단 관심사'(§2)로 명시: 시크릿 env/Compose secret, signed URL, 레이트리밋, Bean Validation 입력 검증, 의존성 스캔을 모든 Phase 공통 방침으로 선언 — 보안을 사후 항목이 아니라 기본값으로 두려는 의도가 보임.
- 레이트리밋을 독립 작업 단위(W1.4 bucket4j+Redis)와 게이트(G1.3 429 반환)로 못 박아, '말로만 보안'이 아니라 증거 기반 완료 기준을 둠.
- 시크릿을 코드가 아닌 env/Compose secret으로 외부화하기로 결정. 실제 스캐폴드(application.yaml, backend 코드)에 하드코딩 시크릿이 전혀 없음 — 깨끗한 출발점.
- JWT를 access/refresh 분리로 설계(W2B.2)하고 비밀번호 해시를 bcrypt/argon2로 명시 — A07(인증 실패)의 기본 모범사례를 인지.
- 미디어 파이프라인에 재시도+dead-letter+멱등성+타임아웃(W2A.5)을 신뢰성 항목으로 둠 — 이는 ffmpeg 잡 리소스 고갈/DoS 완화의 토대가 됨.
- signed URL과 CORS(crossorigin 비디오)를 W2A.6에 항목으로 인지하고 있어, CORS taint/픽셀 접근 제약(A-2)을 설계 단계에서 이해함.
- 관측성(Actuator/Micrometer, 구조적 JSON 로깅, correlation/trace id, 파이프라인 메트릭 W2A.7)을 깊게 계획 — A09(로깅·모니터링 실패) 대응 기반이 탄탄함.
- Testcontainers 기반 통합 테스트와 verifier 분리 패스(self-approve 금지) 원칙 — 보안 회귀를 잡을 검증 체계가 존재.

**발견**
- **[blocker]** (A03 Injection / 미디어 파이프라인 (untrusted ffmpeg)) W2A.4 워커는 '원본 다운로드 → ffmpeg(ProcessBuilder)'만 기술하고, 신뢰불가 업로드 영상에 대한 ffmpeg 처리의 핵심 위협(파일명/메타데이터 통한 명령·인자 주입, 악성 미디어로 인한 ffmpeg/디코더 CVE 트리거, CPU/메모리/디스크 리소스 고갈, 무한 길이/zip-bomb형 미디어)에 대한 완화책이 계획에 전무하다. ProcessBuilder를 셸 경유(`bash -c`)로 쓰거나 사용자 제어 파일명을 인자로 직접 넘기면 RCE로 직결된다. 이 워커는 Phase 2의 이력서 핵심 산출물이라 blast radius가 워커 호스트 전체(RCE 시 R2 키·DB 접근까지)다.
    - 권고: W2A.4에 ffmpeg 하드닝을 명시적 하위 작업·게이트로 추가: (1) ProcessBuilder는 절대 셸 경유 금지 — 인자 배열로만 전달(`ProcessBuilder(listOf("ffmpeg","-i",inputPath,...))`), 사용자 파일명은 절대 인자에 넣지 말고 서버 생성 UUID 경로만 사용. (2) ffmpeg 입력 화이트리스트: `-f`로 컨테이너 강제, `-t`(최대 길이)·해상도·프레임수 상한, `-threads` 제한. (3) 워커를 격리 컨테이너(읽기전용 rootfs, 비특권 유저, `--cap-drop ALL`, seccomp, CPU/메모리 cgroup 한도, 네트워크 차단, 디스크 쿼터·임시디렉토리 격리)에서 실행. (4) 잡 타임아웃(W2A.5 연계)으로 행 프로세스 강제 종료. (5) ffmpeg 버전 고정 + CVE 모니터링을 의존성 스캔 대상에 포함. 게이트 G2A에 '악성/초장시간 샘플 입력 시 워커가 타임아웃·종료되고 호스트가 영향받지 않음'을 증거로 추가.
    - 위치: Phase 2A / W2A.4, W2A.5, G2A
- **[blocker]** (A01 Broken Access Control / Signed URL 설계) signed URL이 W2A.6에 'S3 presign(R2 호환)' 한 줄로만 존재하고, 설계 결정(만료시간, 범위/권한, HTTP 메서드 제한, 경로 우회 방지, presigned PUT 업로드의 키 제어)이 계획에 없다. 특히 W2A.2의 'presigned PUT 업로드'는 클라이언트가 임의 키로 객체를 쓰거나 다른 객체를 덮어쓸 수 있어, 키를 서버가 강제하지 않으면 스토리지 오염·접근 우회로 이어진다. 보호 에셋 URL의 만료가 길거나 범위가 넓으면 무인증 영구 접근이 가능해진다.
    - 권고: signed URL을 독립 설계 항목으로 승격: (1) 다운로드 presign은 짧은 만료(예 5~15분), GET 전용, 객체 키 단위 범위로 한정. (2) 업로드 presign(PUT)은 서버가 키를 완전히 결정(UUID 기반), `Content-Length-Range`·`Content-Type` 조건 부착, 만료 짧게. 클라이언트 지정 키 절대 신뢰 금지. (3) 키 정규화로 경로 traversal(`../`) 차단, 버킷/프리픽스 경계 강제. (4) 원본 버킷과 공개 변환물 버킷 분리. 게이트에 '만료 후 URL 403, 타 객체 키로 서명 시도 거부, 업로드 키를 클라가 못 바꿈'을 증거로.
    - 위치: Phase 2A / W2A.2, W2A.6
- **[major]** (A10 SSRF / 원격 미디어 fetch) W2A.4 워커가 '원본 다운로드'를 수행하고 향후 원격 URL 기반 에셋 등록(W1.6 storage_url, 또는 미래의 URL 임포트)이 가능한 구조인데, 서버가 사용자 제공 URL을 fetch할 때의 SSRF 방어가 계획에 없다. 내부 메타데이터 엔드포인트(클라우드 IMDS 169.254.169.254), 내부 서비스(Redis/Postgres/MinIO), localhost로의 요청이 가능하면 자격증명 탈취·내부망 정찰로 이어진다. R2/MinIO 자격증명이 워커에 있으므로 blast radius가 크다.
    - 권고: 서버/워커가 원격 미디어를 fetch하는 모든 경로에 SSRF 가드 추가: (1) 아웃바운드 대상 allowlist(자체 R2/MinIO 엔드포인트만). (2) 사용자 제공 URL fetch가 필요하면 DNS 해석 결과를 검사해 사설/링크로컬/루프백 IP(10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, ::1, fc00::/7) 차단, 리다이렉트 추적 시 매 홉 재검증. (3) ffmpeg에 원격 프로토콜(http/rtmp 등) 직접 입력 금지 — 항상 서버가 검증·다운로드한 로컬 파일만 입력. 게이트에 'IMDS/내부 IP 대상 fetch가 거부됨' 증거 추가.
    - 위치: Phase 2A / W2A.2, W2A.4
- **[major]** (A07 Auth Failures / JWT 함정) W2B.2~W2B.3이 access/refresh와 bcrypt/argon2까지만 명시하고, JWT의 실제 함정(refresh 토큰 회전·재사용 탐지·폐기(블랙리스트/jti), alg confusion 방지(`alg:none`·HS/RS 혼동 차단, 허용 알고리즘 명시 검증), 토큰 만료·시계 오차, logout 시 서버측 무효화, 시크릿 키 강도·로테이션)이 계획에 없다. W2B.3에 logout이 있으나 stateless JWT에서 logout은 서버측 폐기 메커니즘 없이는 무의미하다.
    - 권고: W2B.2에 JWT 하드닝을 명시: (1) 라이브러리에서 허용 알고리즘을 단일 고정(예 HS256 또는 RS256)하고 토큰 헤더 alg를 신뢰하지 말 것 — alg confusion·none 차단. (2) refresh 토큰 회전 + 재사용 탐지(이전 refresh 재사용 시 패밀리 전체 폐기), refresh를 Redis에 저장해 폐기/로그아웃 지원. (3) access 토큰 짧게(예 15분), jti 기반 폐기 옵션. (4) JWT 시크릿은 충분한 엔트로피(≥256bit)로 env 주입, 로테이션 절차 문서화. 게이트 G2B에 'logout 후 기존 refresh 거부, 변조된 alg 토큰 거부'를 증거로.
    - 위치: Phase 2B / W2B.2, W2B.3, G2B
- **[major]** (A05 보안 설정 / 업로드 남용 (크기·타입·쿼터)) W2A.2 업로드가 '원본 등록'만 기술하고, 업로드 남용 방어(파일 크기 상한, MIME/매직바이트 기반 타입 검증, 사용자별 스토리지 쿼터, 업로드 엔드포인트 레이트리밋)가 계획에 없다. 레이트리밋(W1.4)은 P1 조회 API 기준이라 P2 업로드/미디어 엔드포인트에 적용된다는 보장이 없다. 무제한 업로드는 스토리지 비용 폭증·트랜스코드 큐 포화(DoS)로 직결된다.
    - 권고: W2A.2에 업로드 가드 추가: (1) presigned PUT의 `Content-Length-Range`로 서버측 크기 상한 강제(클라 검증 불신뢰). (2) 트랜스코드 전 매직바이트·ffprobe로 실제 컨테이너/코덱 검증, 화이트리스트 외 거부. (3) 사용자/익명별 업로드 쿼터·동시 잡 수 제한. (4) 업로드·미디어 엔드포인트에 별도 레이트리밋 버킷 적용(W1.4 확장). 게이트에 '상한 초과 업로드 거부, 위장 확장자(매직바이트 불일치) 거부' 증거.
    - 위치: Phase 2A / W2A.2, W2A.3, W1.4
- **[major]** (A05 보안 설정 / CORS 정확성 (crossorigin 비디오 + WebGL)) crossorigin 비디오를 WebGL 텍스처로 샘플하려면 미디어 응답에 정확한 `Access-Control-Allow-Origin`이 필요하고(A-2에서 인지), API 자체도 브라우저 호출용 CORS가 필요한데, 계획에 CORS 정책이 항목으로 없다. 와일드카드 `*` + credentials 조합이나 origin 반사(reflect)는 CSRF·데이터 탈취로 이어지고, 반대로 너무 빡빡하면 셰이더 그레이딩이 깨진다(기능 실패).
    - 권고: CORS를 명시적 작업으로: (1) API CORS는 프론트 origin allowlist만 허용, credentials 사용 시 `*` 금지. (2) 미디어/CDN 응답의 `Access-Control-Allow-Origin`은 정확한 origin으로 설정(R2/CDN 캐시 헤더와 함께), Vary: Origin 주의. (3) origin 반사 + credentials 조합 금지. W2A.6과 W1.x에 CORS 검증 게이트 추가('허용 origin만 통과, 비디오가 WebGL에서 taint 없이 샘플됨').
    - 위치: Phase 1~2 / W2A.6, 횡단 §2
- **[major]** (A04 Insecure Design / 미디어·시퀀싱 엔드포인트 DoS) 시퀀싱(W1.3)·미디어 서빙·트랜스코드 큐(W2A.3)가 DoS 표면인데, 비용이 큰 연산(서버 코스 구성, presign 발급, 큐 적재)에 대한 비용 제한·캐시·동시성 제어가 레이트리밋 한 줄(W1.4) 외에 구체화되지 않았다. 트랜스코드 큐는 무제한 enqueue 시 워커 포화로 가용성 붕괴, presign 엔드포인트는 남용 시 스토리지 우회 접근 발급기가 된다.
    - 권고: (1) 시퀀싱 응답을 Redis 캐시(이미 W1.2 @Cacheable 계획)로 비용 제한, 무거운 경로에 엔드포인트별 레이트리밋. (2) 트랜스코드 큐에 사용자별 동시 잡·전역 큐 길이 상한, 백프레셔/429. (3) presign 발급 엔드포인트에 레이트리밋·인증 필수화. (4) 부하 점검(W3.5 k6/Gatling)에 악용 시나리오(대량 업로드·presign 폭주)를 포함. 게이트에 '큐 포화 상황에서 429/백프레셔로 가용성 유지' 증거.
    - 위치: Phase 1~3 / W1.3, W1.4, W2A.3, W3.5
- **[major]** (A02 Cryptographic Failures / 시크릿 처리 (MinIO·R2·Compose·env)) §2에 '시크릿은 env/Compose secret'이라 적었으나 실제 레포의 루트 .gitignore가 0바이트(빈 파일)다. W0.1이 .gitignore를 만들기로 했지만 backend/.gitignore에는 .env 무시 규칙이 없고 루트도 비어 있어, .env/MinIO·R2 키·docker-compose의 평문 시크릿이 실수로 커밋될 위험이 현재 구조상 열려 있다. Compose의 환경변수 인라인 평문도 시크릿 노출 경로다.
    - 권고: (1) W0.1을 강화: 루트 .gitignore에 `.env`, `.env.*`(단 `.env.example` 제외), `*.pem`, `secrets/` 추가하고, 게이트로 'git check-ignore .env가 매치'를 증거화. (2) MinIO/R2 키는 .env로만 주입, Compose는 평문 대신 `env_file`/Docker secret 사용, 기본 MinIO 자격증명(minioadmin) 변경 강제. (3) CI에 시크릿 스캐너(gitleaks/trufflehog) 잡 추가를 의존성 스캔과 함께 §2/CI에 명시. (4) 로그·에러 응답에 시크릿·서명 URL 전체가 찍히지 않도록 로깅 마스킹 정책 추가.
    - 위치: Phase 0 / W0.1, §2 보안, W0.3 Compose
- **[minor]** (A01 Broken Access Control / MinIO·R2 버킷 접근 범위) 스토리지 추상화(storage/)와 버킷 init(W0.3)은 있으나, 버킷 접근 정책(공개/비공개 분리, 익명 읽기 차단, 변환물 vs 원본 권한 분리, 워커 자격증명의 최소권한)이 계획에 없다. 버킷이 공개 읽기로 열려 있으면 signed URL 자체가 무의미해진다.
    - 권고: 버킷 정책을 W2A.6/W0.3에 명시: (1) 원본 버킷은 완전 비공개(서버/워커만), 공개 변환물도 가능하면 signed URL 전용으로 익명 listing/직접 접근 차단. (2) 워커/앱 자격증명을 버킷·작업별 최소권한으로 분리(읽기전용 vs 쓰기). (3) MinIO 로컬도 운영과 동일 정책으로 init해 환경 불일치 방지. 게이트에 '버킷 직접 anonymous GET이 거부됨' 증거.
    - 위치: Phase 0~2 / W0.3, W2A.6, storage/
- **[minor]** (A06 Vulnerable Components / 의존성·이미지 스캔 구체화) §2에 '의존성 스캔'이 한 줄 있으나 CI 잡(W0.9)·게이트에 구체적 도구·실패 임계가 없다. Spring/Kotlin/JPA·ffmpeg·S3 SDK·bucket4j 등 의존성과 bootBuildImage(Buildpacks) 산출 이미지의 CVE를 게이트로 막는 메커니즘이 명시되지 않았다. (참고: 실제 build.gradle.kts가 Spring Boot 4.1.0·Kotlin 2.3.21 등 비현실적 버전을 가리키는데, 버전 정합성 자체는 quality-reviewer 영역이나 의존성 무결성/공급망 관점에서 핀 고정·검증 필요.)
    - 권고: W0.9 CI에 의존성·이미지 스캔 잡 추가: (1) OWASP Dependency-Check 또는 Gradle용 취약점 플러그인 + `gradle dependencies` 핀 고정. (2) bootBuildImage 산출 OCI 이미지에 Trivy/Grype 스캔, CRITICAL/HIGH 발견 시 빌드 실패. (3) ffmpeg 베이스 이미지/버전 핀 + CVE 모니터링. 게이트 G0.4에 '의존성·이미지 스캔 통과'를 포함.
    - 위치: Phase 0 / W0.9, G0.4, §2
- **[minor]** (A09 Logging Failures / 보안 이벤트 로깅·PII) 관측성은 강하나(메트릭·trace id), 보안 이벤트(인증 실패·레이트리밋 차단·업로드 거부·presign 발급) 로깅과 분석 이벤트(events 테이블, anon_id/user_id/IP)의 PII 처리·보존정책이 계획에 없다. anon_id+IP+세션은 개인정보가 될 수 있고, 무기한 보존은 컴플라이언스·유출 시 blast radius를 키운다.
    - 권고: (1) 보안 이벤트(인증 실패, 429, 업로드/presign 거부)를 구조적 로그로 기록하고 메트릭화. (2) events 파이프라인(W2C)에 PII 최소화(IP 해시/마스킹), 보존기간(예 raw 90일 후 집계만 유지) 정책을 스키마·잡에 명시. (3) 로그에 토큰·서명 URL·이메일 평문 마스킹. 게이트에 '인증 실패가 보안 로그로 관측됨' 추가.
    - 위치: Phase 2 / W2C.1, W2D.2, §2 관측성

---

## 렌즈: critic — WORKPLAN.md를 '실행 계획'으로서 평가 (게이트 테스트가능성·수용기준 구체성·Phase경계·'얇은 CRUD' 회피 여부·내부모순·미명세 W*·빠진 리스크·의존순서). THOROUGH 모드로 시작 후 다수 CRITICAL/MAJOR 발견으로 ADVERSARIAL 모드로 격상.

**강점**
- 실패 정의('예쁜 사이트 + 얇은 CRUD')를 문서 최상단에 못 박고, Phase별 '백엔드 마일스톤'을 필수 산출물로 강제하며 프론트를 명시적으로 타임박스 — 학습 목적과 계획 구조가 일관되게 정렬됨.
- G1.1(course_assets 행을 바꾸면 매니페스트가 달라짐=정적 아님 증명)은 '서버 판단' 주장을 실제로 반증가능하게 만든 우수한 게이트 설계. '얇은 CRUD' 회피를 말이 아니라 테스트로 강제함.
- G2A의 '업로드만 하면 수동 단계 0으로 자동 완결 + 실패 잡은 재시도 후 dead-letter로 관측' 은 미디어 파이프라인의 핵심 가치를 구체적·증거기반으로 게이팅함.
- 기간 산정을 버리고 작업단위(W*)+증거 게이트(G*)로 추적, verifier 분리 패스(self-approve 금지)를 횡단 원칙으로 명시 — 검증 규율이 계획에 내장됨.
- 부록 A 스택 매핑(NestJS→Spring 1:1)과 이력서 한 줄 역산으로 Phase 2 완주의 당위를 포트폴리오 목표와 직접 연결 — 우선순위 판단 근거가 명확함.
- G2C가 PRD §4 지표(D1/D7 리텐션·세션길이·슬라이더 사용률)와 직접 연결되어 분석 파이프라인이 장식이 아니라 실제 제품 가설 검증에 묶임.

**발견**
- **[blocker]** (내부 모순 — 계획 vs 실제 레포 (레이아웃)) WORKPLAN §1 '레포 최종 형상'(L51-80)과 §0 결정표(L26)는 백엔드를 `apps/api/`, 프론트를 `apps/web/`로 둔 모노레포를 전제하고, 모든 게이트 명령(`./gradlew`, CI 잡)이 이 경로를 암묵 가정한다. 그러나 실제 레포는 `backend/`에 이미 스캐폴드됐고 `settings.gradle.kts`의 `rootProject.name = "backend"`, 패키지는 `com.petrichor.backend`다(WORKPLAN L56은 `com.petrichor.api` 지정). 프론트(`apps/web` 또는 `frontend/`)·`docker-compose.yml`·`infra/`·`.github/`는 아직 없다. 계획은 '앞으로 만들 구조'를 그렸지만 이미 만들어진 스캐폴드와 경로·이름이 충돌하며, 어디를 따를지 명시가 없다.
    - 권고: §1을 실제 레포에 맞춰 재작성하거나, '기존 backend/를 apps/api로 이동 + rootProject.name/패키지 리네이밍'을 W0.0으로 명시적 선행 작업화하라. executor가 추측 없이 따르려면 '신규 생성'인지 '기존 스캐폴드 수용/이동'인지 한 문장으로 못 박아야 한다.
    - 위치: §0 L26, §1 L51-80, W0.4 L110
- **[blocker]** (내부 모순 — 스택 버전 (Spring Boot 3.4 vs 실제 4.1.0)) WORKPLAN은 §0 L25·L33·부록A L296에서 '**Spring Boot 3.4**'를 확정 스택으로 3회 명시한다. 실제 `backend/build.gradle.kts`는 `org.springframework.boot version "4.1.0"` + Kotlin `2.3.21`다. 메이저 버전 불일치(3.x→4.x)는 단순 패치가 아니라 Spring Security 6→7, Jakarta 베이스라인, 자동설정 변경 등 API 파급이 큰 변화다. 더 결정적으로 의존성도 어긋난다: 실제 스캐폴드는 `spring-boot-starter-webmvc`(Boot 4 신명칭)를 쓰는데 W0.4(L110)는 `web`를 나열하고, W0.4가 요구하는 `flyway-core`/`flyway-database-postgresql`/`postgresql` 드라이버/`data-redis`/`testcontainers`가 build.gradle.kts에 전부 빠져 있다.
    - 권고: 계획의 'Boot 3.4'를 실제 4.1.0으로 갱신하거나(이 경우 부록A·G* 전제·라이브러리 호환성 재검토 필요), 스캐폴드를 3.4로 다운그레이드하라 — 단 둘 중 무엇이 정답인지 계획이 결정해야 한다. 그리고 W0.4 의존성 목록(flyway/postgresql/redis/testcontainers)과 실제 build.gradle.kts의 갭을 W0.4 산출물 체크리스트로 명시하라.
    - 위치: §0 L25 L33, W0.4 L110, 부록A L296
- **[blocker]** (피드환경 — JDK 21 가정 vs 설치된 JDK 17) §0 L25는 'JDK 21'을 확정하고 `build.gradle.kts`의 toolchain도 `JavaLanguageVersion.of(21)`을 요구한다. 그러나 설치된 JDK는 17.0.18이다. Gradle toolchain auto-provisioning이 비활성/네트워크 차단이면 `./gradlew build`·`bootRun`이 'No compatible toolchains' 또는 toolchain 다운로드 단계에서 실패한다. G0.2(`bootRun` 부팅)·G0.4(CI 그린)·G0.5(`bootBuildImage`)가 모두 이 위에 서 있어, 첫 게이트부터 환경 미충족으로 막힌다. 계획에는 'JDK 설치/버전 정렬' 작업 단위 자체가 없다.
    - 권고: W0.1 또는 신규 W0.0에 'JDK 21 설치 + JAVA_HOME/toolchain 검증(`java -version`==21, `./gradlew -q javaToolchains`)'를 명시 작업으로 넣고, .nvmrc처럼 `.sdkmanrc`/CI `setup-java@v4 with java-version: 21`을 산출물로 못 박아라. 또는 toolchain을 17로 낮추되 그 결정을 DECISIONS에 기록하라.
    - 위치: §0 L25, W0.1 L107, G0.2 L122
- **[major]** (미명세 W* — 기준 문서 DECISIONS.md 부재) WORKPLAN은 머리말 L6에서 `docs/DECISIONS.md`를 '기준 문서'로 링크하고 L17·L108(W0.2)·각주에서 '상세 근거: DECISIONS.md'로 3회 참조하지만, 실제 파일은 존재하지 않는다(`ls` 확인). W0.2가 그것을 '생성'하는 작업이긴 하나, 머리말과 §0이 이미 '존재하는 기준 문서'처럼 인용하는 것은 순환/전방참조다. executor가 '근거는 DECISIONS 참조'를 만나면 빈손이 된다.
    - 권고: W0.2를 다른 W0.* 이전의 명시적 선행(blocking)으로 표기하고, 머리말의 DECISIONS 링크에 '(W0.2에서 생성 예정)'을 달거나, 5개 결정의 근거를 §0 표에 인라인으로 최소 1줄씩 담아 전방참조 의존을 끊어라.
    - 위치: 머리말 L6, §0 L17, W0.2 L108
- **[major]** (수용 기준 모호 — Phase 1 프론트 게이트의 비계량성) P1의 목표·게이트가 '머무르고 싶은 첫 세션 품질'(L132)·'충분히 괜찮은 선'(L134)·G1.4의 '가청·가시적으로 변화'(L179)처럼 주관적 표현에 의존한다. PRD §4는 '첫 세션 10분 체류 ≥30%', 'D7 ≥15%', '세션 ≥25분'이라는 정량 지표를 갖고 있는데 WORKPLAN의 P1 게이트는 이를 측정 게이트로 연결하지 않는다. 두 개발자가 'G1.4 통과'를 다르게 판정할 수 있고, '타임박스 초과'의 트리거(L11·L281)도 시간 예산이 수치로 없어 강제력이 없다.
    - 권고: G1.4를 검증가능 술어로 분해하라: 예) '습도 0→1 스윕 시 rainGain·reverbWet·uCyanTint·uGrain 4개 값이 TECH-DESIGN A-3 lerp 범위대로 변함을 콘솔/단위테스트로 증명', '크로스페이드 이음매 무프레임드롭'. 프론트 타임박스는 '시간/세션 수' 같은 측정가능 상한으로 정의하라(없으면 강제전환 원칙이 사문화).
    - 위치: P1 목표 L132, W1 예산 L134, G1.4 L179, 타임박스 원칙 L11 L281
- **[major]** (빠진 리스크/완화 — ffmpeg '심리스 처리' 미명세 + 라이선스 게이트 부재) W2A.4(L198)는 워커가 'H.264 + VP9, 멀티해상도, 포스터 프레임, **심리스 처리**'를 한다고 적지만 '심리스 처리'가 ffmpeg 단에서 무엇을 의미하는지(루프 in/out 트림? 프레임 정합? 크로스페이드?) 전혀 명세가 없다. TECH-DESIGN A-2(L64)는 심리스를 '편집 단계 또는 2버퍼 크로스페이드(프론트)'로 규정해 오히려 서버 책임이 아니라고 본다 — W2A.4와 모순. 또한 PRD 리스크표·WORKPLAN 횡단('라이선스 거버넌스')이 AI 오디오/영상 IP 불확실성을 핵심 리스크로 보지만, 'license/source_note 필드 채우기'를 넘어 '상업 티어 약관 위반 시 에셋 차단' 같은 게이트가 없다. 포트폴리오 공개 시 라이선스 미충족 에셋이 그대로 노출될 수 있다.
    - 권고: W2A.4의 '심리스 처리'를 삭제하거나 구체화(예: '루프 in/out ms로 트림만, 크로스페이드는 프론트 W1.8 담당')해 TECH-DESIGN과 정합화하라. 라이선스는 'license가 NULL/미승인 티어인 asset은 시퀀싱 매니페스트에서 제외'를 G1.* 또는 시딩(W1.6) 게이트로 추가하라.
    - 위치: W2A.4 L198, TECH-DESIGN A-2 L64, 횡단 L93, W1.6 L145
- **[major]** (Phase 경계 — P1 '이벤트 수신 스텁'과 레이트리밋의 어정쩡한 분할) W1.5(L144)는 `POST /events`를 'P1은 검증·로깅까지, 파이프라인은 P2'로 쪼갠다. 그런데 같은 엔드포인트가 W2C.2(L222)에서 '배치 수신→큐'로 재설계된다. P1에서 단건 동기 검증/로깅 컨트롤러를 만들고 P2에서 배치+큐로 갈아엎으면 계약(요청 스키마: 단건 vs 배열)·테스트가 두 번 작성된다. 또 레이트리밋(W1.4)이 P1에 들어오지만 그 대상·임계값(어떤 엔드포인트에 분당 몇 회)이 G1.3(L178 '임계 초과 요청에 429')에 수치로 없어 모호하다. 의존 순서상 events 계약을 P1에서 확정하지 않으면 프론트 W1.12 에미터가 P2에서 다시 수정된다.
    - 권고: P1에서 events 요청 스키마를 '배치(배열) 형태'로 처음부터 확정해 P2 재작업을 없애라(처리만 P2에서 동기→큐로 교체). G1.3에 구체 임계값(예: 'IP당 /events 60req/min 초과 시 429')을 명시하라.
    - 위치: W1.4 L143, W1.5 L144, G1.3 L178, W2C.2 L222, W1.12 L170
- **[major]** (의존 순서 — V2 마이그레이션 3-way 충돌) `V2`라는 동일 버전 번호가 W2A.1(asset_variants+잡 상태, L195), W2B.1(users/favorites/humidity_presets, L209), W2C.1(events/event_aggregates, L219) 세 곳에서 각자 'V2'로 적힌다. Flyway는 동일 버전 파일 충돌을 허용하지 않으므로 셋 중 둘은 V3/V4여야 한다. 2A·2B·2C의 착수 순서가 명시되지 않아 어느 모듈이 V2를 차지하는지 비결정적이고, 병렬 진행 시 마이그레이션 버전 경합이 발생한다.
    - 권고: V2/V3/V4를 모듈별로 배정(예: V2=media, V3=auth, V4=analytics)하고 P2 하위 모듈의 착수 순서를 §6 트리거처럼 명시하라. 또는 타임스탬프 기반 Flyway 버저닝 규칙을 횡단 관심사에 못 박아라.
    - 위치: W2A.1 L195, W2B.1 L209, W2C.1 L219
- **[major]** (빠진 리스크 — DB 스키마 모순(users 외래키 vs P0 범위)) W0.6(L112)은 V1에서 `assets·courses·course_assets`만 만들고 'users/events는 P2'라고 명시한다. 그러나 TECH-DESIGN A-5 events 테이블은 `user_id INT REFERENCES users(id)`로 users를 참조하고, WORKPLAN W2B.5(anon_id→user 연결)·events.user_id가 users에 의존한다. P2에서 events(W2C.1)와 users(W2B.1)가 서로 다른 마이그레이션/모듈로 갈리면 외래키 생성 순서 의존이 생긴다(users 먼저). 계획에 이 순서 제약이 없다.
    - 권고: P2 마이그레이션에서 'users 생성 → events.user_id FK' 순서를 명시하거나, events를 user_id NULL+나중 FK 추가로 분리하라. anon_id→user 백필 전략(W2B.5)도 '기존 events row의 user_id UPDATE' 절차로 구체화하라.
    - 위치: W0.6 L112, W2B.5 L213, W2C.1 L219, TECH-DESIGN A-5 L137
- **[minor]** (수용 기준 — G0.3 Redis 헬스 vs W0.4 의존성 누락) G0.3(L123)은 `/actuator/health`가 'db UP, **redis UP**'을 반환하길 요구하지만, W0.4(L110) 의존성 목록에 `spring-boot-starter-data-redis`가 없고 실제 build.gradle.kts에도 없다. Redis 헬스 indicator는 redis 스타터가 있어야 자동 등록된다. G0.3을 통과하려면 W0.4에 redis 의존이 필요하나 명시 누락.
    - 권고: W0.4 의존성에 `data-redis`를 추가하고(또는 G0.3에서 redis를 P1로 미루고), Redis 연결 설정을 W0.5 application.yml 산출물에 포함하라.
    - 위치: W0.4 L110, W0.5 L111, G0.3 L123
- **[minor]** (파일명 불일치 — application.yml vs 실제 application.yaml) W0.5(L111)·§1(L71)은 `application.yml`을 산출물로 적지만 실제 스캐폴드는 `application.yaml`이며 내용은 `spring.application.name: backend` 한 줄뿐이다(datasource/Actuator 노출/profile 전무). 사소하나 executor가 'application.yml 생성'을 새로 만들면 yaml/yml 두 파일이 공존해 혼선이 생길 수 있다.
    - 권고: 기존 application.yaml을 기준으로 W0.5를 '확장'으로 명시하고 파일명을 통일하라(.yaml).
    - 위치: W0.5 L111, §1 L71
- **[minor]** (누락 산출물 — bootBuildImage(Buildpacks)는 JDK 호환만으로 부족) G0.5(L125)·W0.10은 `./gradlew bootBuildImage`로 OCI 이미지를 만든다고 하나, Buildpacks 빌드는 로컬 Docker 데몬 가동을 전제한다. 계획에 'Docker Desktop/daemon running' 전제가 명시되지 않았고, Boot 4.1 + Paketo buildpack 호환 JDK 21 베이스 선택도 미명세다. 환경 가정이 빠지면 G0.5가 'daemon not running'으로 비결정적으로 실패할 수 있다.
    - 권고: G0.5 전제에 'Docker daemon 가동'과 빌더 이미지 버전 핀을 추가하라.
    - 위치: W0.10 L116, G0.5 L125
- **[nit]** (문서 일관성 — Phase 기간 표기 모순) WORKPLAN은 머리말 L9에서 '기간 산정 없음'을 추적 원칙으로 천명하지만, 부록/기준문서 TECH-DESIGN은 Phase별 '3~4일/2~3주' 기간을 명시한다. 두 문서를 같이 읽는 사람에게 '기간이 있는가 없는가'가 모순으로 보인다(치명적 아님, 단지 두 진실 소스 정렬 필요).
    - 권고: WORKPLAN이 추적의 단일 소스임을 명시하고 TECH-DESIGN 기간은 '참고용 추정'으로 라벨하라.
    - 위치: WORKPLAN L9, TECH-DESIGN L159 L165 L175 L184

---

## 렌즈: architect

**강점**
- 백엔드 '실패 정의'(예쁜 사이트 + 얇은 CRUD)를 문서 전체에서 일관되게 강제하고, 각 Phase에 백엔드 마일스톤을 필수 산출물로 못박은 점 — 학습/포트폴리오 목표에 정렬된 드문 규율. (docs/WORKPLAN.md:11, :185-273)
- 시퀀싱 서비스를 '서버 판단(정적 JSON 금지)'으로 정의하고, course_assets 행 변경이 매니페스트를 바꾸는 것을 게이트(G1.1)로 증명하게 한 점 — 얇은 CRUD 회피의 핵심 설계 의도가 검증 가능한 형태로 명시됨. (docs/WORKPLAN.md:142, :176)
- 게이트(G*)를 증거 기반으로 정의하고 verifier 분리 패스로 self-approve를 금지한 점 — 검증 규율이 계획 레벨에 내장됨. (docs/WORKPLAN.md:94, :280)
- 라이선스 거버넌스를 데이터 모델(assets.license/source_note)과 횡단 관심사 양쪽에 못박아 AI 오디오 출처 추적을 스키마 수준에서 강제한 점. (docs/TECH-DESIGN-AND-ROADMAP.md:105, docs/WORKPLAN.md:93)
- TECH-DESIGN의 NestJS 추천 스택을 Kotlin/Spring 확정 스택으로 1:1 매핑한 부록 A를 둬서 두 문서 간 표류를 방지하려 한 점. (docs/WORKPLAN.md:293-304)
- Testcontainers(PG/Redis/MinIO)를 통합 테스트 기본으로 삼아 인프라 의존 테스트의 재현성을 확보한 점. (docs/WORKPLAN.md:88)

**발견**
- **[blocker]** (빌드/런타임 정합성 (scaffold vs plan)) 실제 backend/build.gradle.kts가 계획과 전면 불일치한다. 계획은 Spring Boot 3.4 + JDK 21을 전제(WORKPLAN.md:25, :33)하나 실제는 Spring Boot 4.1.0(build.gradle.kts:4), Kotlin 2.3.21(:1), Java toolchain 21(:14)로 선언됨. 그런데 머신에 설치된 JDK는 17(java -version: 17.0.18)이다. Gradle toolchain auto-provisioning이 없으면 `./gradlew build`가 'No compatible toolchains found for JavaLanguageVersion 21'로 즉시 실패한다 — G0.2(bootRun) 도달 불가. 게다가 Spring Boot 4.x는 baseline이 JDK 17이 아니라 상위이며 starter 좌표가 재편(webmvc 분리 starter, tools.jackson 모듈)되어 계획의 Initializr 의존성 목록(WORKPLAN.md:110)과 호환되지 않는다. 문서 작성일(2026-06-21) 기준으로도 SB 4.1.0/Kotlin 2.3.21은 매우 앞선 버전이라 의도된 선택인지 Initializr 기본값 사고인지 불명확.
    - 권고: 정본을 하나로 고정하라. 학습/포트폴리오 안정성을 위해 권장: (a) Spring Boot 3.4.x LTS-성격 라인 + JDK 21로 내려 맞추고 toolchain을 21로 두되 SDKMAN/Temurin 21을 설치(또는 Gradle `toolchain { ... }` + foojay-resolver-convention 플러그인으로 auto-download 허용), 또는 (b) SB 4.x를 의도적으로 유지한다면 WORKPLAN.md:25/:33/:110/:295-304와 부록 A를 4.x 좌표(webmvc 분리 starter, tools.jackson, security 기본 동작)로 전면 갱신하라. 어느 쪽이든 먼저 `./gradlew build`를 실제로 통과시켜 버전 매트릭스를 증명한 뒤 진행. Trade-off: (a)는 검증된 레퍼런스/Stack Overflow 자료가 풍부해 주니어 학습에 유리하나 최신 기능 일부 포기; (b)는 최신이지만 문서/예제 공백으로 디버깅 비용↑.
    - 위치: WORKPLAN.md §0(:25,:33), W0.4(:110), 부록 A(:293-304) vs backend/build.gradle.kts:1-16
- **[blocker]** (모듈 경계 / 정본 디렉터리 구조) 계획의 정본 구조는 모노레포 `apps/api` + `apps/web`(WORKPLAN.md:51-80)이나 실제 스캐폴드는 `backend/`(rootProject.name="backend", settings.gradle.kts:1)이고 패키지는 `com.petrichor.backend`다. 계획의 패키지 루트는 `com.petrichor.api`(WORKPLAN.md:56). docker-compose.yml, frontend/, .github/workflows도 부재. 이 표류를 방치하면 이후 모든 W*/G* 경로 참조(예: db/migration, infra/, CI 잡 경로)가 계획과 어긋나 verifier 게이트가 매번 깨진다.
    - 권고: 지금(코드가 거의 없을 때) 한 번에 정합화하라. 단일 백엔드만 있는 현 단계에선 모노레포 `apps/` 강제가 과설계다 — 두 안 중 택1: (A) 실제를 정본화: 루트=레포, `backend/`(API) + 향후 `frontend/`(web) + 루트 `docker-compose.yml`/`infra/`/`.github/`. WORKPLAN.md §1을 이 형상으로 재작성하고 패키지는 `com.petrichor`(하위 도메인 패키지 common/asset/course/sequencing…)로 통일. (B) 계획대로 `apps/api`로 이동. 권장은 (A) — 이동 비용 0, 단일 Gradle 빌드 유지가 주니어에게 단순. 어느 쪽이든 패키지 네이밍(`backend` 제거)과 도메인 패키지 골격(WORKPLAN.md:57-69)을 Phase 0에서 확정.
    - 위치: WORKPLAN.md §1(:51-80) vs settings.gradle.kts:1, BackendApplication.kt:1, 레포 루트(docker-compose/frontend 부재)
- **[major]** (Spring Security 조기 도입 / Phase 0 게이트 충돌) build.gradle.kts:25가 Phase 0부터 spring-boot-starter-security를 포함한다. 계획은 인증을 P2로 유보(WORKPLAN.md:38, :206-214)했는데, security가 classpath에 있으면 Spring Boot 기본 자동설정이 모든 엔드포인트에 HTTP Basic + 생성된 패스워드를 걸어 /actuator/health, /api/v1/courses 등이 401이 된다. 그러면 G0.3(curl health 200)과 G1.* 공개 재생 경로가 설정 없이는 통과 못 한다. 또한 SB 4.x security 기본 동작 변화까지 겹치면 디버깅 함정이 된다.
    - 권고: 택1: (a) P2 전까지 security 의존성을 제거(가장 단순, 권장) — 인증이 실제 필요해질 때 W2B에서 추가. 또는 (b) 유지하되 Phase 0에 명시적 SecurityFilterChain 빈을 두어 /actuator/**·/api/v1/**를 permitAll로 열고 CSRF를 API에 맞게 비활성화하라. 계획서에 'security는 P2까지 비활성/permitAll'을 명문화해 게이트 충돌을 예방. Trade-off: (a)는 의존성 추가/재설정 1회 비용; (b)는 지금 설정 코드가 필요하나 보안 설정 학습을 앞당김.
    - 위치: build.gradle.kts:25 vs WORKPLAN.md G0.3(:123), §2 인증 P2(:38), W2B(:206-214)
- **[major]** (미디어 파이프라인 신뢰성 — 멱등성·DLQ·백프레셔·crash 복구 공백) W2A.5가 '재시도+dead-letter, 멱등성, 타임아웃'을 한 줄로만 명시(WORKPLAN.md:199)하고 Redis Streams consumer group 운영의 핵심 메커니즘이 설계되지 않았다: (1) 멱등성 키(어떤 컬럼/제약으로 중복 트랜스코드를 방지하는지 — job_id unique? asset_id+variant_spec unique?), (2) Redis Streams는 자동 재시도가 없으므로 XAUTOCLAIM + 시도횟수(delivery count) 임계로 DLQ 스트림 라우팅을 직접 구현해야 함, (3) 워커가 ffmpeg 중간에 죽었을 때 PEL(pending entries list)에 남은 메시지 회수와 부분 산출물(반쯤 올라간 S3 객체) 정리, (4) 백프레셔(워커 동시성·큐 길이 상한)와 ffmpeg 프로세스 자원 한도. 이게 빠지면 '업로드만 하면 자동 완결'(G2A)이 첫 장애에서 무너진다.
    - 권고: W2A를 다음으로 분해해 계획에 명문화: (1) jobs 테이블에 (asset_id, variant_spec) UNIQUE + 상태머신(QUEUED→PROCESSING→DONE/FAILED/DEAD) 컬럼, 상태전이를 멱등 키로 사용; (2) consumer group + XACK on success, XAUTOCLAIM(idle>timeout)로 죽은 워커 메시지 회수, delivery_count>=N이면 dead-letter 스트림으로 이동; (3) ffmpeg는 임시 작업디렉터리 + 성공 후에만 S3 commit(원자적 rename/multipart complete), 실패 시 temp/부분객체 cleanup; (4) 워커 concurrency 상한과 Stream MAXLEN/소비 지연 메트릭으로 백프레셔. ProcessBuilder는 timeout(Process.waitFor(timeout))+destroyForcibly, stdout/stderr 별도 스레드로 소비(파이프 버퍼 데드락 방지)를 필수 항목으로. Trade-off: 설계 부담↑이나 이게 정확히 '흔한 CRUD와 차별화되는' 학습 핵심이므로 생략은 프로젝트 목표 자체를 무너뜨림.
    - 위치: WORKPLAN.md W2A.3-A.5(:198-203), G2A(:203)
- **[major]** (데이터 모델 완전성 — 심리스 루프 메타·asset_variants·events 파티셔닝/인덱스/FK) A-5 스키마(TECH-DESIGN:97-143)에 공백이 다수다: (1) 심리스 루프 메타데이터가 assets에 loop_in_ms/loop_out_ms만 있고, 매니페스트(WORKPLAN.md:148-160)가 요구하는 poster URL, 크로스페이드 길이, 정확한 loop point(프레임 단위) 표현이 없음 — 변환물별로 달라지는데 asset 레벨에만 존재; (2) asset_variants(W2A.1)의 컬럼/제약 미정의 — 원본 FK, codec/resolution/bytes/url, 그리고 위 멱등성 UNIQUE; (3) events가 BIGSERIAL 단일 테이블(:134)인데 시간 파티셔닝은 '고려'로만(W2C.1:220) 남아 인덱스(예: (type,created_at), (course_id,created_at)), 파티션 키(created_at 월별 RANGE), user_id FK on delete 정책이 미정 — 집계 쿼리(D1/D7 리텐션)가 풀스캔이 됨; (4) course_assets.humidity_curve/grade_config가 JSONB raw라 스키마 검증·버전 관리 부재(매니페스트 계약이 깨져도 런타임까지 모름).
    - 권고: (1) poster/loop point/crossfade를 asset_variants(변환물 레벨)로 내리고, 매니페스트가 variant를 참조하도록; (2) asset_variants: id, asset_id FK(NOT NULL, on delete cascade), codec, width/height, bytes, storage_key, UNIQUE(asset_id, codec, height); (3) events는 처음부터 created_at RANGE 파티션(월별) + (type,created_at)·(course_id,created_at) 인덱스, user_id FK ON DELETE SET NULL(:137 의도와 일치), anon_id 인덱스; 집계 결과는 event_aggregates에 멱등 upsert; (4) JSONB 페이로드는 애플리케이션단 DTO + Bean Validation으로 계약 강제하고, 매니페스트 스키마에 version 필드 추가. Trade-off: 파티셔닝을 P0/P2 초기에 넣으면 Flyway 마이그레이션 복잡도↑지만 나중 재파티셔닝(데이터 이관)보다 훨씬 저렴.
    - 위치: TECH-DESIGN A-5(:97-143), WORKPLAN.md W2A.1(:196), W2C.1(:220), 매니페스트(:148-160)
- **[major]** (캐싱 정확성 — 무효화·stampede·키 설계) W1.2/W1.3가 @Cacheable Redis 캐싱과 캐시 히트 게이트(G1.2)만 명시(WORKPLAN.md:141-142, :178)하고 정확성 메커니즘이 없다: (1) 시퀀싱 매니페스트는 courses+course_assets+assets 조인 결과인데, course_assets 행을 바꾸면 매니페스트가 달라져야 한다(G1.1의 비정적 증명!)는 요구가 캐시 무효화와 정면 충돌 — 어떤 쓰기 경로에서 어떤 캐시 키를 evict하는지 미정. (2) TTL/캐시 stampede(동시 미스 시 DB 폭주) 대비 부재 — 인기 코스가 만료되는 순간 thundering herd. (3) 캐시 키 설계(코스ID만? 습도 default 포함? variant 버전 포함?)가 없어 잘못된 매니페스트를 서빙할 위험.
    - 권고: (1) course/course_asset 쓰기 서비스에 @CacheEvict(또는 명시적 evict)로 해당 courseId 매니페스트 키 무효화, 통합 테스트로 'course_assets 변경→캐시 무효화→새 매니페스트' 경로를 G1.1·G1.2와 함께 증명; (2) TTL + stampede 방지(per-key 분산 락 또는 캐시 갱신 단일화/early-recompute), 또는 단순화로 변경 빈도가 낮으니 쓰기 시 능동 갱신(write-through)도 가능; (3) 캐시 키에 매니페스트 계약에 영향 주는 입력(courseId + manifest schema version)을 포함하고 습도 등 클라이언트 파라미터는 키에서 분리. Trade-off: 능동 무효화는 쓰기 경로 결합도↑이나 정확성 보장; TTL-only는 단순하나 stale 윈도우 허용. 학습 가치상 무효화 메커니즘 구현을 권장.
    - 위치: WORKPLAN.md W1.2-1.3(:141-142), G1.1-1.2(:176-178)
- **[minor]** (트랜잭션 경계 / 외부 I/O와 DB 트랜잭션 혼합) 미디어 파이프라인에서 S3 업로드(외부 I/O)와 asset_variants 기록(DB)이 한 단위로 묶일 위험이 설계에 명시되지 않았다. @Transactional 메서드 안에서 S3 PUT/ffmpeg 같은 장기 I/O를 하면 DB 커넥션을 수십 초 점유해 커넥션 풀 고갈을 부른다(HikariCP 기본 10). 시퀀싱 조회도 읽기 전용 트랜잭션 경계가 명시되지 않음.
    - 권고: 규칙을 계획에 명문화: 외부 I/O(S3/ffmpeg)는 트랜잭션 밖에서 수행하고, 결과 메타만 짧은 @Transactional로 커밋. 조회 경로는 @Transactional(readOnly=true). 워커는 'I/O→짧은 DB 트랜잭션' 2단 분리. Trade-off: 코드 분리 비용↑이나 커넥션 풀 안정성 확보.
    - 위치: WORKPLAN.md W2A.4(:198), W1.2-1.3 조회(:141-142)
- **[minor]** (커넥션 풀 / Hibernate 배치·통계 설정 미정) application.yaml이 사실상 비어 있고(application.yaml:1-3) HikariCP 풀 크기, connection-timeout, leak-detection-threshold, Hibernate open-in-view(기본 true — 뷰 렌더까지 커넥션 점유로 안티패턴) 설정이 없다. 시퀀싱 매니페스트 직렬화 중 OSIV가 켜져 있으면 커넥션 점유가 길어진다.
    - 권고: application.yml에 spring.jpa.open-in-view=false 명시, HikariCP 풀(maximum-pool-size, connection-timeout, leak-detection-threshold)·ddl-auto=validate(:34 의도)·Flyway 활성화를 Phase 0 설정 작업(W0.5)에 체크리스트로 넣어라. Trade-off: OSIV off는 lazy 로딩을 서비스 경계 안으로 강제(설계 규율↑)하나 커넥션 효율·예측가능성↑.
    - 위치: WORKPLAN.md W0.5(:111) vs backend/.../application.yaml:1-3
- **[minor]** (관측성 / graceful shutdown / JVM 특이사항) (1) graceful shutdown이 계획 어디에도 없음 — 워커가 ffmpeg 트랜스코딩 중 SIGTERM 받으면 잡 유실/부분 산출물. (2) correlation/trace id(WORKPLAN.md:91, W2D.2:233)는 있으나 MDC 전파가 비동기/워커 스레드 경계를 넘는 방법 미정(Redis Streams consumer는 별 스레드). (3) ProcessBuilder 서브프로세스는 JVM heap이 아닌 native 메모리/파일디스크립터를 쓰므로 컨테이너 메모리 한도(JVM -Xmx + ffmpeg RSS)를 합산 산정해야 OOMKill을 피함 — 계획에 자원 산정 부재.
    - 권고: (1) server.shutdown=graceful + spring.lifecycle.timeout-per-shutdown-phase 설정, 워커는 종료 신호 시 현재 메시지 XACK 보류/재처리 가능하도록(멱등성에 의존) 안전 종료 루프 구현; (2) MDC를 메시지 페이로드/헤더로 전파해 워커에서 복원(correlationId를 잡에 실어 보냄); (3) 컨테이너 메모리 = JVM heap + ffmpeg 동시성×프로세스당 RSS + 여유로 산정하고 워커 동시성으로 상한. bootBuildImage(:45) 컨테이너에 ffmpeg 바이너리 포함 여부도 확인(Buildpack 기본 이미지에 ffmpeg 없음 — 별도 설치 레이어 필요). Trade-off: 설정/운영 항목↑이나 P2 미디어 파이프라인의 운영 신뢰성이 곧 학습 산출물.
    - 위치: WORKPLAN.md §2 관측성(:91), W2D(:230-236), W0.10 bootBuildImage(:45,:116)
- **[nit]** (빈 application.yaml / profile 분리 미구현) application.yaml에 datasource·JPA·Actuator 노출·profile(local/ci) 설정이 전혀 없다(:1-3). W0.5가 이를 요구(:111)하나 아직 미구현 상태로, 현 build.gradle.kts에는 PostgreSQL 드라이버·Flyway 의존성 자체가 없어 data-jpa 자동설정이 부팅 실패를 낸다.
    - 권고: W0.4 의존성에 org.postgresql:postgresql(runtimeOnly), org.flywaydb:flyway-core + flyway-database-postgresql, redis(spring-boot-starter-data-redis), springdoc-openapi, testcontainers(BOM)를 실제 추가하고 application.yml에 datasource/JPA validate/Actuator(health,info,metrics,prometheus)/profile을 채워라 — 현재 의존성 목록(build.gradle.kts:23-37)과 계획(:110)의 격차를 먼저 메우는 것이 G0.2 선결조건.
    - 위치: WORKPLAN.md W0.4(:110), W0.5(:111) vs build.gradle.kts:23-37, application.yaml:1-3

---
