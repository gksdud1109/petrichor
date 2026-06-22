# Petrichor

2000년대 동아시아 구도심의 눅눅한 새벽을 1인칭으로 거니는 가상 산책/드라이브 분위기 웹앱.
비주얼 코어는 **쪽타일**(구도심 외벽의 흰 세로 타일 그리드). 백엔드 학습 습작.

> 문서: [PRD](docs/PRD.md) · [기술설계·로드맵](docs/TECH-DESIGN-AND-ROADMAP.md) · [작업계획](docs/WORKPLAN.md) · [의사결정](docs/DECISIONS.md) · [계획 리뷰](docs/PLAN-REVIEW.md) · [디자인 레퍼런스](docs/DESIGN-REFERENCES.md)

## 스택
- **백엔드**: Kotlin + Spring Boot 3.4 (JDK 21, Gradle KTS), JPA + Flyway, Actuator/Micrometer, Spring Security
- **로컬 인프라**: Docker Compose — Postgres 16 + Redis 7 + MinIO(R2 호환 오브젝트 스토리지)
- **프론트(예정)**: Vanilla Vite + TS, ogl(WebGL 셰이더), Web Audio API

## 빠른 시작 (로컬)
사전: Docker Desktop 실행. JDK는 Gradle toolchain이 자동 프로비저닝(JDK 21, foojay).

```bash
cp .env.example .env
docker compose up -d                 # Postgres + Redis + MinIO (one-command)
cd backend && ./gradlew bootRun      # http://localhost:8080
curl localhost:8080/actuator/health  # {"status":"UP", db/redis 컴포넌트}
```

## 구조
```
backend/             # Spring Boot API (Kotlin/Gradle)
frontend/            # Vanilla Vite + TS (예정)
docs/                # 기획·설계·계획·리뷰 문서
docker-compose.yml   # 로컬 인프라 (PG + Redis + MinIO)
.github/workflows/   # CI
```

## 개발 메모
- **마이그레이션**: `backend/src/main/resources/db/migration/V*.sql` (Flyway). `ddl-auto=validate`로 엔티티-스키마 일치 강제.
- **헬스**: `/actuator/health`(db·redis), `/actuator/prometheus`(메트릭).
- **테스트**: `cd backend && ./gradlew build` — Testcontainers가 PG/Redis를 자체 기동(Docker 필요).
- **추적**: 기간 없이 작업단위(W*)+증거 게이트(G*)로 관리. WORKPLAN §7(v0.2)이 본문에 우선.
