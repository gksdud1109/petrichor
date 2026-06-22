-- V1: 코어 도메인 (assets / courses / course_assets)
-- 근거: docs/TECH-DESIGN-AND-ROADMAP.md A-5 + docs/PLAN-REVIEW.md(FK 인덱스·license 게이트).
-- users/events는 Phase 2(V3/V4), asset_variants는 Phase 2A(V2)로 분리.

-- 재사용 에셋 (영상 루프 / 음악 / 환경음) + 라이선스 추적
CREATE TABLE assets (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind        VARCHAR(20)  NOT NULL,                 -- 'video_loop' | 'music' | 'ambient'
    storage_key VARCHAR(512) NOT NULL,                 -- R2/MinIO 객체 키
    loop_in_ms  INTEGER,
    loop_out_ms INTEGER,
    license     VARCHAR(50),                           -- 'original'|'suno-pro'|'udio-paid'... NULL/미승인은 매니페스트 제외(license 게이트)
    source_note TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 코스 = 무드 단위. grade_config는 셰이더 파라미터(JSONB)
CREATE TABLE courses (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region       VARCHAR(50)  NOT NULL,
    title        VARCHAR(100) NOT NULL,
    mood_tags    TEXT[]       NOT NULL DEFAULT '{}',
    grade_config JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 코스 ↔ 에셋 조합 + 기본 게인 + 습도 곡선 (시퀀싱 입력)
CREATE TABLE course_assets (
    course_id      BIGINT  NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    asset_id       BIGINT  NOT NULL REFERENCES assets(id)  ON DELETE RESTRICT,
    base_gain      REAL    NOT NULL DEFAULT 1.0,
    humidity_curve JSONB   NOT NULL DEFAULT '{}'::jsonb,  -- 습도 슬라이더가 이 에셋을 어떻게 움직이는지
    sort_order     INTEGER NOT NULL DEFAULT 0,            -- 결정적 시퀀싱 순서
    PRIMARY KEY (course_id, asset_id)
);

-- FK/조회 인덱스 (PLAN-REVIEW: FK 인덱스 누락 방지)
CREATE INDEX idx_assets_kind         ON assets (kind);
CREATE INDEX idx_courses_region      ON courses (region);
CREATE INDEX idx_course_assets_asset       ON course_assets (asset_id);
CREATE INDEX idx_course_assets_course_sort ON course_assets (course_id, sort_order);  -- P1 결정적 시퀀싱(ORDER BY sort_order)
