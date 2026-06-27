package com.petrichor.backend.media.storage

import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SSRF 가드 순수 단위테스트(JUnit5 only — Spring/Docker 불요 → 로컬 실행).
 *
 * 검증:
 *   - 비-http(s) 스킴 거부
 *   - IMDS/링크로컬/사설/루프백/CGNAT/와일드카드/class-E 대역 차단
 *   - 단축·정수·16진수 IPv4(2130706433·127.1·0x7f000001·0 등) 차단
 *   - localhost 호스트명 차단
 *   - IPv6 루프백/링크로컬/unique-local 차단
 *   - IPv6 임베디드 IPv4: IPv4-mapped·IPv4-compatible·NAT64·6to4·Teredo 차단
 *   - 공인 IP·호스트명 허용
 *   - 해석된 주소 재검증(isAllowedResolvedAddress)
 */
class SsrfGuardUnitTest {

    // ── 비-http(s) 스킴 거부 ──────────────────────────────────────────────────

    @Test
    fun `file 스킴 거부`() {
        assertFalse(SsrfGuard.inspect("file:///etc/passwd").allowed)
    }

    @Test
    fun `gopher 스킴 거부`() {
        assertFalse(SsrfGuard.inspect("gopher://example.com/_payload").allowed)
    }

    @Test
    fun `ftp dict 등 기타 스킴 거부`() {
        assertFalse(SsrfGuard.inspect("ftp://example.com/x").allowed)
        assertFalse(SsrfGuard.inspect("dict://example.com:11211/").allowed)
    }

    @Test
    fun `파싱 불가 URL 거부`() {
        assertFalse(SsrfGuard.inspect("not a url").allowed)
        assertFalse(SsrfGuard.inspect("http://").allowed)
    }

    // ── IMDS / 링크로컬 차단 ──────────────────────────────────────────────────

    @Test
    fun `169_254_169_254 IMDS 차단`() {
        assertFalse(SsrfGuard.inspect("http://169.254.169.254/latest/meta-data/").allowed)
        assertFalse(SsrfGuard.inspectHost("169.254.169.254").allowed)
    }

    @Test
    fun `링크로컬 169_254 대역 전반 차단`() {
        assertFalse(SsrfGuard.inspectHost("169.254.0.1").allowed)
        assertFalse(SsrfGuard.inspectHost("169.254.255.255").allowed)
    }

    // ── 사설 대역 차단 ────────────────────────────────────────────────────────

    @Test
    fun `10_8 사설 차단`() {
        assertFalse(SsrfGuard.inspectHost("10.0.0.1").allowed)
        assertFalse(SsrfGuard.inspect("https://10.255.255.254/x").allowed)
    }

    @Test
    fun `172_16_12 사설 차단(경계 포함)`() {
        assertFalse(SsrfGuard.inspectHost("172.16.0.1").allowed)
        assertFalse(SsrfGuard.inspectHost("172.31.255.254").allowed)
        // 172.15.x / 172.32.x는 사설 아님 → 허용
        assertTrue(SsrfGuard.inspectHost("172.15.0.1").allowed)
        assertTrue(SsrfGuard.inspectHost("172.32.0.1").allowed)
    }

    @Test
    fun `192_168_16 사설 차단`() {
        assertFalse(SsrfGuard.inspectHost("192.168.1.1").allowed)
    }

    @Test
    fun `100_64_10 CGNAT 차단`() {
        assertFalse(SsrfGuard.inspectHost("100.64.0.1").allowed)
        assertFalse(SsrfGuard.inspectHost("100.127.255.254").allowed)
        // 100.63.x / 100.128.x는 CGNAT 아님 → 허용
        assertTrue(SsrfGuard.inspectHost("100.63.0.1").allowed)
        assertTrue(SsrfGuard.inspectHost("100.128.0.1").allowed)
    }

    // ── 루프백 / 와일드카드 / class-E 차단 ───────────────────────────────────

    @Test
    fun `127_8 루프백 차단`() {
        assertFalse(SsrfGuard.inspectHost("127.0.0.1").allowed)
        assertFalse(SsrfGuard.inspectHost("127.1.2.3").allowed)
    }

    @Test
    fun `0_0_0_0 와일드카드 차단`() {
        assertFalse(SsrfGuard.inspectHost("0.0.0.0").allowed)
    }

    @Test
    fun `240_4 class-E 예약 차단`() {
        assertFalse(SsrfGuard.inspectHost("240.0.0.1").allowed)
        assertFalse(SsrfGuard.inspectHost("255.255.255.254").allowed)
    }

    @Test
    fun `255_255_255_255 브로드캐스트 차단`() {
        assertFalse(SsrfGuard.inspectHost("255.255.255.255").allowed)
    }

    @Test
    fun `localhost 호스트명 차단`() {
        assertFalse(SsrfGuard.inspect("http://localhost:8080/x").allowed)
        assertFalse(SsrfGuard.inspectHost("localhost").allowed)
        assertFalse(SsrfGuard.inspectHost("foo.localhost").allowed)
    }

    // ── 단축/정수/16진수 IPv4 우회 차단 (MAJOR #1) ────────────────────────────

    @Test
    fun `정수 형식 IPv4 2130706433은 127_0_0_1 루프백으로 차단`() {
        // 2130706433 = 0x7f000001 = 127.0.0.1
        assertFalse(SsrfGuard.inspectHost("2130706433").allowed)
        assertFalse(SsrfGuard.inspect("http://2130706433/x").allowed)
    }

    @Test
    fun `정수 0은 0_0_0_0 와일드카드로 차단`() {
        assertFalse(SsrfGuard.inspectHost("0").allowed)
    }

    @Test
    fun `단축 점-십진 127_1은 127_0_0_1 루프백으로 차단`() {
        // 127.1 → InetAddress 환원 시 127.0.0.1
        assertFalse(SsrfGuard.inspectHost("127.1").allowed)
    }

    @Test
    fun `단축 점-십진 127_0_1은 127_0_0_1 루프백으로 차단`() {
        // 127.0.1 → 127.0.0.1
        assertFalse(SsrfGuard.inspectHost("127.0.1").allowed)
    }

    @Test
    fun `16진수 0x7f000001은 127_0_0_1 루프백으로 차단`() {
        assertFalse(SsrfGuard.inspectHost("0x7f000001").allowed)
    }

    @Test
    fun `단축 점-십진 169_254_169_254_IMDS 단축형 차단`() {
        // 169.254.43518 = 169.254.169.254 (43518 = 169*256+254)
        assertFalse(SsrfGuard.inspectHost("169.254.43518").allowed)
    }

    @Test
    fun `단축 점-십진 10_1은 10_0_0_1 사설로 차단`() {
        assertFalse(SsrfGuard.inspectHost("10.1").allowed)
    }

    // ── IPv6 차단 ────────────────────────────────────────────────────────────

    @Test
    fun `IPv6 루프백 콜론콜론1 차단`() {
        assertFalse(SsrfGuard.inspectHost("::1").allowed)
        assertFalse(SsrfGuard.inspect("http://[::1]:9000/x").allowed)
    }

    @Test
    fun `IPv6 미지정 콜론콜론 차단`() {
        assertFalse(SsrfGuard.inspectHost("::").allowed)
    }

    @Test
    fun `IPv6 링크로컬 fe80 차단`() {
        assertFalse(SsrfGuard.inspectHost("fe80::1").allowed)
    }

    @Test
    fun `IPv6 unique-local fc00 fd00 차단`() {
        assertFalse(SsrfGuard.inspectHost("fc00::1").allowed)
        assertFalse(SsrfGuard.inspectHost("fd12:3456:789a::1").allowed)
    }

    // ── IPv6 임베디드 IPv4 우회 차단 (MAJOR #2) ───────────────────────────────

    @Test
    fun `IPv4-mapped ffff 127_0_0_1 루프백 차단`() {
        // ::ffff:127.0.0.1 — Java가 Inet4Address로 환원하므로 isBlockedIpv4로 검사됨
        assertFalse(SsrfGuard.inspectHost("::ffff:127.0.0.1").allowed)
        // ::ffff:169.254.169.254 — IMDS
        assertFalse(SsrfGuard.inspectHost("::ffff:169.254.169.254").allowed)
    }

    @Test
    fun `IPv4-compatible 콜론콜론 127_0_0_1 루프백 차단`() {
        // ::127.0.0.1 (IPv4-compatible, ::/96)
        assertFalse(SsrfGuard.inspectHost("::127.0.0.1").allowed)
    }

    @Test
    fun `NAT64 64ff9b 콜론콜론 127_0_0_1 차단`() {
        // 64:ff9b::127.0.0.1
        assertFalse(SsrfGuard.inspectHost("64:ff9b::127.0.0.1").allowed)
    }

    @Test
    fun `6to4 2002_7f00_1 콜론콜론 차단`() {
        // 2002:7f00:0001:: — 6to4, 임베디드 IPv4=127.0.0.1(루프백)
        assertFalse(SsrfGuard.inspectHost("2002:7f00:1::").allowed)
    }

    @Test
    fun `Teredo 2001 콜론콜론 차단`() {
        // 2001:0000::/32 — Teredo 프리픽스
        assertFalse(SsrfGuard.inspectHost("2001::1").allowed)
    }

    @Test
    fun `ffff 0 콜론콜론 127_0_0_1 차단`() {
        // ::ffff:0:127.0.0.1 — SIIT/NAT46 변형
        assertFalse(SsrfGuard.inspect("http://[::ffff:0:127.0.0.1]/x").allowed)
    }

    // ── 공인 IP / 호스트명 허용 ──────────────────────────────────────────────

    @Test
    fun `공인 IPv4 허용`() {
        assertTrue(SsrfGuard.inspectHost("8.8.8.8").allowed)
        assertTrue(SsrfGuard.inspect("https://1.1.1.1/x").allowed)
    }

    @Test
    fun `공인 호스트명 허용(해석 전 형식 단계)`() {
        assertTrue(SsrfGuard.inspect("https://example.com/media.mp4").allowed)
        assertTrue(SsrfGuard.inspect("https://cdn.petrichor.app/v/abc").allowed)
    }

    @Test
    fun `공인 IPv6 허용`() {
        assertTrue(SsrfGuard.inspectHost("2606:4700:4700::1111").allowed) // Cloudflare DNS
    }

    // ── 해석된 주소 재검증 ────────────────────────────────────────────────────

    @Test
    fun `isAllowedResolvedAddress — 루프백 차단, 공인 허용`() {
        assertFalse(SsrfGuard.isAllowedResolvedAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(SsrfGuard.isAllowedResolvedAddress(InetAddress.getByName("169.254.169.254")))
        assertFalse(SsrfGuard.isAllowedResolvedAddress(InetAddress.getByName("10.0.0.5")))
        assertTrue(SsrfGuard.isAllowedResolvedAddress(InetAddress.getByName("8.8.8.8")))
    }
}
