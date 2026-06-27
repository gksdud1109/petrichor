-- V3: 미디어 파이프라인 기반 (media_jobs / asset_variants)
-- 근거: docs/PLAN-REVIEW.md(2A: 멱등성·잡 상태머신), docs/WORKPLAN.md §7.3-7.4, W2A.1.
-- US-1(스키마/엔티티) + US-2(잡 상태머신) 범위. ffmpeg/스토리지/큐는 US-3~8(다음).

-- 트랜스코드 잡 + 상태머신(QUEUED→PROCESSING→DONE|FAILED→QUEUED|DEAD) + 멱등성/재시도
CREATE TABLE media_jobs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id        BIGINT       NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL,             -- JobStatus enum 이름(대문자): QUEUED|PROCESSING|DONE|FAILED|DEAD
    attempts        INT          NOT NULL DEFAULT 0,   -- PROCESSING 진입(실제 시도) 횟수; markProcessing()이 +1
    max_attempts    INT          NOT NULL,             -- 단일 소스: 엔티티 DEFAULT_MAX_ATTEMPTS(3). DB default 없음
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,      -- 중복 잡 방지(멱등성 키): asset:assetId:codec:height
    error           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(), -- 앱(@PrePersist)이 값 제공; DB default는 폴백
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(), -- 앱(@PreUpdate)이 값 제공; DB default는 폴백
    version         BIGINT       NOT NULL DEFAULT 0     -- 낙관적 락(@Version); 워커 동시성은 US-3+
);

CREATE INDEX idx_media_jobs_status ON media_jobs (status);    -- 큐 폴링(QUEUED 조회)
CREATE INDEX idx_media_jobs_asset  ON media_jobs (asset_id);  -- FK 조회 인덱스

-- 원본 → 변환 산출물(codec·resolution·url·bytes).
-- UNIQUE(asset_id, codec, height)로 멱등 업서트. height NOT NULL(오디오=0 규약, NULLS DISTINCT 문제 제거).
CREATE TABLE asset_variants (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id    BIGINT       NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    codec       VARCHAR(20)  NOT NULL,                      -- 'h264' | 'vp9' ...
    width       INT,
    height      INT          NOT NULL DEFAULT 0,            -- 영상=실제 px, 오디오=0 규약
    bytes       BIGINT,
    storage_key VARCHAR(512) NOT NULL,                      -- R2/MinIO 변환물 객체 키
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),        -- 앱(@PrePersist)이 값 제공; DB default는 폴백
    UNIQUE (asset_id, codec, height)
);

CREATE INDEX idx_asset_variants_asset ON asset_variants (asset_id);  -- FK 조회 인덱스
