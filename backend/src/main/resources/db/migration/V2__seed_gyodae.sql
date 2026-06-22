-- V2: 교대역(gyodae) 무드 시드 — 시퀀싱 API 검증용 데이터.
-- 근거: WORKPLAN §Phase 1(W1.6 에셋 시딩) + license 게이트(NULL은 매니페스트 제외).
-- id는 IDENTITY → 하드코딩 금지. storage_key/source_note로 식별해 서브쿼리로 FK 연결.

-- 1) 코스 (셰이더 그레이딩 파라미터를 grade_config JSONB에)
INSERT INTO courses (region, title, mood_tags, grade_config)
VALUES (
    'gyodae',
    '교대역, 비 갠 새벽 2시',
    ARRAY['rain', '2am', 'post-rain'],
    '{"grain":0.4,"cyan":0.3,"bloom":0.2,"downscale":0.5}'::jsonb
);

-- 2) 에셋
--   video_loop 4 + music 2 + ambient 2 (모두 license='original')
--   + license NULL 1개(게이트 테스트용)
INSERT INTO assets (kind, storage_key, loop_in_ms, loop_out_ms, license, source_note) VALUES
    ('video_loop', 'gyodae/video/alley_01.mp4',   0, 42000, 'original', 'gyodae alley, handheld, post-rain'),
    ('video_loop', 'gyodae/video/crossing_02.mp4', 0, 38000, 'original', 'gyodae crossing, tripod'),
    ('video_loop', 'gyodae/video/window_03.mp4',   0, 51000, 'original', 'cafe window reflection'),
    ('video_loop', 'gyodae/video/neon_04.mp4',     0, 33000, 'original', 'neon sign wet asphalt'),
    ('music',      'gyodae/music/lofi_a.mp3',       0, 64000, 'original', 'AI music tier-paid; tool TBD (Suno/Udio)'),
    ('music',      'gyodae/music/lofi_b.mp3',       0, 72000, 'original', 'AI music tier-paid; tool TBD (Suno/Udio)'),
    ('ambient',    'gyodae/ambient/rain.mp3',       0, 60000, 'original', 'rain ambience (field recording)'),
    ('ambient',    'gyodae/ambient/subway.mp3',     0, 60000, 'original', 'subway hum ambience (field recording)'),
    -- license NULL: 라이선스 미승인 → 매니페스트에서 제외되어야 함
    ('ambient',    'gyodae/ambient/unlicensed.mp3', 0, 60000, NULL,       'license-gate fixture: must be excluded');

-- 3) course_assets — gyodae 코스에 위 에셋들을 연결.
--    base_gain / humidity_curve(습도 슬라이더가 이 에셋을 어떻게 움직이는지) / sort_order.
--    license NULL 에셋도 연결(매니페스트에서 제외되는지 검증).
INSERT INTO course_assets (course_id, asset_id, base_gain, humidity_curve, sort_order)
SELECT c.id, a.id, v.base_gain, v.humidity_curve::jsonb, v.sort_order
FROM courses c
CROSS JOIN (
    VALUES
        ('gyodae/video/alley_01.mp4',   1.0, '{}',                        0),
        ('gyodae/video/crossing_02.mp4',1.0, '{}',                        1),
        ('gyodae/video/window_03.mp4',  1.0, '{}',                        2),
        ('gyodae/video/neon_04.mp4',    1.0, '{}',                        3),
        ('gyodae/music/lofi_a.mp3',     0.8, '{"reverbWet":[0.0,0.6]}',   10),
        ('gyodae/music/lofi_b.mp3',     0.7, '{"reverbWet":[0.0,0.6]}',   11),
        ('gyodae/ambient/rain.mp3',     0.3, '{"gain":[0.1,1.0]}',        20),
        ('gyodae/ambient/subway.mp3',   0.25,'{"gain":[0.0,0.4]}',        21),
        ('gyodae/ambient/unlicensed.mp3',0.5,'{"gain":[0.1,1.0]}',        22)
) AS v(storage_key, base_gain, humidity_curve, sort_order)
JOIN assets a ON a.storage_key = v.storage_key
WHERE c.region = 'gyodae';
