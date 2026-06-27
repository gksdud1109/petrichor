package com.petrichor.backend.media.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KeyScoping 순수 단위테스트(JUnit5 only — Spring/Docker 불요 → 로컬 실행).
 *
 * 검증:
 *   - `../` traversal 거부(세그먼트·결합 형태)
 *   - 절대경로·백슬래시 거부
 *   - 허용 프리픽스 강제(originals/variants 외 거부)
 *   - 서버 생성 UUID 키 형식
 *   - 정상 키 통과(왕복)
 */
class KeyScopingUnitTest {

    private val UUID_KEY_REGEX =
        Regex("^(originals|variants)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(/source)?$")

    // ── 서버 생성 UUID 키 형식 ────────────────────────────────────────────────

    @Test
    fun `newUploadKey는 prefix uuid 형식`() {
        val key = KeyScoping.newUploadKey()
        assertTrue(key.startsWith("originals/"), "기본 프리픽스는 originals: $key")
        assertTrue(UUID_KEY_REGEX.matches(key), "UUID 키 형식 위반: $key")
    }

    @Test
    fun `newUploadKey suffix 포함 형식`() {
        val key = KeyScoping.newUploadKey(prefix = KeyScoping.ORIGINALS_PREFIX, suffix = "source")
        assertTrue(key.startsWith("originals/"))
        assertTrue(key.endsWith("/source"))
        assertTrue(UUID_KEY_REGEX.matches(key), "UUID 키 형식 위반: $key")
    }

    @Test
    fun `newUploadKey는 매번 다른 UUID`() {
        val a = KeyScoping.newUploadKey()
        val b = KeyScoping.newUploadKey()
        assertTrue(a != b, "UUID 키가 충돌하면 안 됨")
    }

    @Test
    fun `newUploadKey는 허용되지 않은 prefix 거부`() {
        assertThrows<IllegalArgumentException> { KeyScoping.newUploadKey(prefix = "evil") }
    }

    @Test
    fun `newUploadKey suffix의 traversal 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.newUploadKey(suffix = "..")
        }
        assertThrows<IllegalArgumentException> {
            KeyScoping.newUploadKey(suffix = "a/b")
        }
    }

    // ── traversal 거부 ────────────────────────────────────────────────────────

    @Test
    fun `점점 traversal 세그먼트 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("originals/../secret")
        }
    }

    @Test
    fun `중첩 traversal 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("variants/a/../../etc/passwd")
        }
    }

    @Test
    fun `단일 점 세그먼트 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("originals/./x")
        }
    }

    @Test
    fun `빈 세그먼트(이중 슬래시) 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("originals//x")
        }
    }

    // ── 절대경로 / 백슬래시 거부 ──────────────────────────────────────────────

    @Test
    fun `절대경로 키 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("/originals/x")
        }
    }

    @Test
    fun `백슬래시 키 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("originals\\..\\secret")
        }
    }

    @Test
    fun `빈 키 거부`() {
        assertThrows<IllegalArgumentException> { KeyScoping.validateKey("") }
        assertThrows<IllegalArgumentException> { KeyScoping.validateKey("   ") }
    }

    // ── 프리픽스 강제 ────────────────────────────────────────────────────────

    @Test
    fun `허용되지 않은 프리픽스 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("secrets/abc")
        }
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("public/abc")
        }
    }

    @Test
    fun `프리픽스 없는 단일 세그먼트 키 거부`() {
        assertThrows<IllegalArgumentException> {
            KeyScoping.validateKey("justakey")
        }
    }

    // ── 정상 키 통과 ──────────────────────────────────────────────────────────

    @Test
    fun `정상 originals 키 통과`() {
        val key = "originals/3f2c4b1a-0000-4000-8000-000000000001/source"
        assertEquals(key, KeyScoping.validateKey(key))
    }

    @Test
    fun `정상 variants 키 통과`() {
        val key = "variants/3f2c4b1a-0000-4000-8000-000000000001/h264-720.mp4"
        assertEquals(key, KeyScoping.validateKey(key))
    }

    @Test
    fun `newUploadKey 결과는 validateKey를 통과`() {
        val key = KeyScoping.newUploadKey(suffix = "source")
        assertEquals(key, KeyScoping.validateKey(key))
    }
}
