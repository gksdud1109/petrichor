package com.petrichor.backend.media.storage

import java.net.IDN
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

/**
 * SSRF(Server-Side Request Forgery) 가드 — 순수 로직, 외부 DNS I/O 없음(로컬 단위테스트 가능).
 *
 * 서버/워커가 사용자 제공 URL로 원격 미디어를 fetch할 미래 경로(US-4·워커)의 방어막이다.
 * 클라우드 메타데이터(IMDS 169.254.169.254)·내부 서비스·루프백 접근을 차단한다.
 *
 * ════════════════════════════════════════════════════════════
 * 호출부(US-4/워커) 필수 2단 방어 계약
 * ════════════════════════════════════════════════════════════
 *  ① [inspect] 로 URL 형식·스킴·호스트를 거부한다.
 *  ② 사용자 제공 URL에서 추출한 호스트를 직접 DNS 해석하고, 해석된 **모든** InetAddress를
 *     [isAllowedResolvedAddress]로 검사한다.
 *  ③ 검증된 IP로 직접 connect(IP를 pin)한다. 클라이언트가 호스트명을 다시 해석하거나
 *     HTTP 리다이렉트를 따라가면 DNS rebinding/TOCTOU 우회가 가능하다 —
 *     리다이렉트 매 홉마다 Location 헤더의 호스트를 ①②로 재검증해야 한다.
 *
 * ⚠️  단순히 getByName 후 allow해도 클라이언트가 호스트명을 다시 해석하면 우회된다.
 *     반드시 "검증된 IP로 직접 connect"해야 DNS rebinding 방어가 완성된다.
 * ════════════════════════════════════════════════════════════
 *
 * 차단 정책:
 *   - 스킴: http/https 외 전부 거부(file/gopher/ftp/dict 등 SSRF·LFI 벡터 차단).
 *   - 호스트:
 *       · IDN/전각 호스트명은 [IDN.toASCII]로 정규화 후 판정(전각 dot·homograph 우회 방어).
 *       · "localhost" 및 하위 도메인 차단.
 *       · 숫자형 호스트(점-십진·단축 정수·16진수·콜론 IPv6)는 InetAddress.getByName으로
 *         환원 → 환원된 IP를 대역 검사 / 환원 실패 시 block(안전 기본값).
 *       · 일반 호스트명: 형식 OK → allow, **단 DNS 해석 주소 재검증은 호출 측 필수**.
 *   - IP 차단 대역:
 *       IPv4: 0/8(와일드카드)·10/8(사설)·100.64/10(CGNAT)·127/8(루프백)·
 *             169.254/16(링크로컬·IMDS)·172.16/12(사설)·192.168/16(사설)·
 *             240/4(class E 예약)·255.255.255.255(브로드캐스트)
 *       IPv6: ::(미지정)·::1(루프백)·fe80::/10(링크로컬)·fc00::/7(unique-local)·
 *             ::ffff:0:0/96(IPv4-mapped)·::/96(IPv4-compatible)·
 *             64:ff9b::/96(NAT64)·2002::/16(6to4)·2001::/32(Teredo)
 */
object SsrfGuard {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** 판정 결과: 허용 여부 + 사유. */
    data class Decision(val allowed: Boolean, val reason: String) {
        companion object {
            fun allow(reason: String = "허용") = Decision(true, reason)
            fun block(reason: String) = Decision(false, reason)
        }
    }

    /**
     * URL 문자열을 검사한다(스킴 + 호스트 리터럴/이름).
     * 파싱 불가·호스트 없음·금지 스킴·차단 대역이면 block.
     */
    fun inspect(url: String): Decision {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return Decision.block("URL 파싱 실패: ${e.message}")
        }

        val scheme = uri.scheme?.lowercase()
            ?: return Decision.block("스킴 없음")
        if (scheme !in ALLOWED_SCHEMES) {
            return Decision.block("허용되지 않은 스킴: $scheme (http/https만 허용)")
        }

        val host = uri.host
            ?: return Decision.block("호스트 없음 또는 비표준 형식")
        return inspectHost(host)
    }

    /**
     * 호스트(IP 리터럴 또는 호스트명)를 검사한다.
     *
     * 처리 순서:
     *  1. URI.host IPv6 대괄호 제거, IDN/전각 정규화([IDN.toASCII]).
     *  2. localhost/하위 차단.
     *  3. 숫자형 호스트(콜론 포함·점-십진·단축 정수·hex 등)는 InetAddress.getByName으로
     *     환원 → 환원 IP를 대역 검사; 환원 실패 시 block.
     *  4. 일반 호스트명: 형식상 allow — **호출 측이 DNS 해석 후 [isAllowedResolvedAddress] 재검증 필수**.
     */
    fun inspectHost(rawHost: String): Decision {
        // URI.host는 IPv6를 대괄호로 감싼다([::1]) → 제거.
        val stripped = rawHost.removePrefix("[").removeSuffix("]")

        // IDN/전각 정규화(punycode 우회·전각 dot·homograph 방어).
        val host = try {
            IDN.toASCII(stripped, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (e: Exception) {
            stripped.lowercase()
        }

        if (host.isBlank()) return Decision.block("호스트 비어 있음")

        // localhost 및 하위 도메인 차단.
        if (host == "localhost" || host.endsWith(".localhost")) {
            return Decision.block("localhost 호스트 차단")
        }

        // 숫자형 호스트 판정: IPv6 리터럴(콜론 포함)·IPv4 계열(점-십진/단축/hex/정수).
        // isNumericHost()가 true면 DNS를 치지 않고 InetAddress.getByName으로 환원 가능.
        // false면 진짜 호스트명 → DNS 해석은 I/O 금지 원칙상 호출 측으로 위임.
        if (isNumericHost(host)) {
            val addr = try {
                InetAddress.getByName(host)
            } catch (e: UnknownHostException) {
                // 숫자처럼 보이지만 파싱 불가 → 안전 기본값 block.
                return Decision.block("숫자형 호스트 환원 실패: $host")
            }
            return if (isBlockedAddress(addr)) {
                Decision.block("차단 대역 IP: ${addr.hostAddress}")
            } else {
                Decision.allow("공인 IP: ${addr.hostAddress}")
            }
        }

        // 일반 호스트명: 형식상 허용. 실제 해석 주소 재검증은 호출 측 책임.
        return Decision.allow("호스트명(DNS 해석 후 isAllowedResolvedAddress 재검증 필수): $host")
    }

    /**
     * 이미 해석된 [InetAddress]가 허용 대역인지 검사한다.
     * DNS 해석·리다이렉트 매 홉 후 호출해 DNS rebinding 방어를 완성한다.
     *
     * @return true=허용(공인), false=차단(사설/루프백/링크로컬/unique-local 등)
     */
    fun isAllowedResolvedAddress(addr: InetAddress): Boolean = !isBlockedAddress(addr)

    /**
     * 주소가 차단 대역인지 판정한다(IPv4/IPv6).
     *
     * Java의 InetAddress.getByName은 "::ffff:a.b.c.d" 형식의 IPv4-mapped를
     * Inet4Address로 자동 환원한다(bytes.size==4). 그 외 임베디드 IPv4는
     * [isBlockedIpv6]에서 바이트 레벨로 검출한다.
     */
    private fun isBlockedAddress(addr: InetAddress): Boolean {
        val bytes = addr.address
        return when (bytes.size) {
            4  -> isBlockedIpv4(bytes)
            16 -> isBlockedIpv6(bytes)
            else -> true // 알 수 없는 주소 길이 → 안전하게 차단
        }
    }

    /**
     * IPv4 차단 대역.
     *   0/8        — 와일드카드/미지정
     *   10/8       — 사설
     *   100.64/10  — CGNAT
     *   127/8      — 루프백
     *   169.254/16 — 링크로컬(IMDS)
     *   172.16/12  — 사설
     *   192.168/16 — 사설
     *   240/4      — class E 예약 (o0 >= 240, 브로드캐스트 포함)
     *   255.255.255.255 — 한정 브로드캐스트(240/4에 포함되나 명시)
     */
    private fun isBlockedIpv4(b: ByteArray): Boolean {
        val o0 = b[0].toInt() and 0xff
        val o1 = b[1].toInt() and 0xff
        return when {
            o0 == 0                       -> true  // 0.0.0.0/8 와일드카드/미지정
            o0 == 10                      -> true  // 10/8 사설
            o0 == 127                     -> true  // 127/8 루프백
            o0 == 100 && o1 in 64..127    -> true  // 100.64/10 CGNAT
            o0 == 169 && o1 == 254        -> true  // 169.254/16 링크로컬(IMDS)
            o0 == 172 && o1 in 16..31     -> true  // 172.16/12 사설
            o0 == 192 && o1 == 168        -> true  // 192.168/16 사설
            o0 >= 240                     -> true  // 240/4 class E 예약 (255.255.255.255 포함)
            else                          -> false
        }
    }

    /**
     * IPv6 차단 대역.
     *
     * 임베디드 IPv4 검출(Java가 자동 환원하지 않는 형식):
     *   ::ffff:0:0/96  (IPv4-mapped,  bytes[10..11] == ff ff)
     *   ::/96          (IPv4-compatible, bytes[0..11] 모두 0)
     *   64:ff9b::/96   (NAT64 Well-Known Prefix)
     *
     * 터널링 프리픽스 차단:
     *   2002::/16  (6to4  — 처음 2바이트 == 0x20 0x02)
     *   2001::/32  (Teredo — 처음 4바이트 == 0x20 0x01 0x00 0x00)
     *
     * 그 외:
     *   ::          미지정
     *   ::1         루프백
     *   fe80::/10   링크로컬
     *   fc00::/7    unique-local
     */
    private fun isBlockedIpv6(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xff
        val b1 = b[1].toInt() and 0xff

        // :: 미지정 (전부 0)
        if (b.all { it.toInt() == 0 }) return true

        // ::1 루프백 (앞 15바이트 0 + 마지막 1)
        if (b.take(15).all { it.toInt() == 0 } && (b[15].toInt() and 0xff) == 1) return true

        // fe80::/10 링크로컬: 첫 10비트 = 1111111010
        if (b0 == 0xfe && (b1 and 0xc0) == 0x80) return true

        // fc00::/7 unique-local: 첫 7비트 = 1111110 → 0xfc 또는 0xfd
        if ((b0 and 0xfe) == 0xfc) return true

        // 2002::/16 — 6to4 터널링(내부에 임베디드 IPv4, bytes[2..5])
        if (b0 == 0x20 && b1 == 0x02) {
            val embedded = b.copyOfRange(2, 6)
            if (isBlockedIpv4(embedded)) return true
        }

        // 2001:0000::/32 — Teredo (bytes[4..7]은 서버 IP를 XOR·보수로 담음; 상위 프리픽스만 차단)
        if (b0 == 0x20 && b1 == 0x01 && (b[2].toInt() and 0xff) == 0x00 && (b[3].toInt() and 0xff) == 0x00) {
            return true
        }

        // ::ffff:0:0/96 — 표준 IPv4-mapped (bytes[0..9]=0, bytes[10..11]=0xff 0xff)
        // Java InetAddress.getByName("::ffff:a.b.c.d")는 Inet4Address로 자동 환원하므로
        // 여기 도달하는 경우는 드물다. 일부 JVM/OS 차이 대비.
        val isStdIpv4Mapped = b.take(10).all { it.toInt() == 0 } &&
            (b[10].toInt() and 0xff) == 0xff && (b[11].toInt() and 0xff) == 0xff
        if (isStdIpv4Mapped) {
            val embedded = b.copyOfRange(12, 16)
            if (isBlockedIpv4(embedded)) return true
        }

        // ::ffff:0:0/96 — SIIT/NAT46 변형 (bytes[0..7]=0, bytes[8..9]=0xff 0xff, bytes[10..11]=0)
        // 예: ::ffff:0:127.0.0.1 → bytes=[0..7=0, 8=ff, 9=ff, 10=0, 11=0, 12..15=127.0.0.1]
        val isSiitMapped = b.take(8).all { it.toInt() == 0 } &&
            (b[8].toInt() and 0xff) == 0xff && (b[9].toInt() and 0xff) == 0xff &&
            (b[10].toInt() and 0xff) == 0x00 && (b[11].toInt() and 0xff) == 0x00
        if (isSiitMapped) {
            val embedded = b.copyOfRange(12, 16)
            if (isBlockedIpv4(embedded)) return true
        }

        // ::/96 — IPv4-compatible (bytes[0..11] 모두 0, bytes[12..15]가 IPv4)
        // 예: ::127.0.0.1 → bytes=[0..11=0, 12..15=127.0.0.1]
        // ::1(루프백)·::(미지정)은 위에서 이미 차단됨.
        val isIpv4Compatible = b.take(12).all { it.toInt() == 0 } &&
            !b.drop(12).all { it.toInt() == 0 }
        if (isIpv4Compatible) {
            val embedded = b.copyOfRange(12, 16)
            if (isBlockedIpv4(embedded)) return true
        }

        // 64:ff9b::/96 — NAT64 Well-Known Prefix
        // bytes[0..1]=0x00 0x64, [2..3]=0xff 0x9b, [4..11]=0, [12..15]=임베디드 IPv4
        val isNat64 = (b[0].toInt() and 0xff) == 0x00 && (b[1].toInt() and 0xff) == 0x64 &&
            (b[2].toInt() and 0xff) == 0xff && (b[3].toInt() and 0xff) == 0x9b &&
            b.slice(4..11).all { it.toInt() == 0 }
        if (isNat64) {
            val embedded = b.copyOfRange(12, 16)
            if (isBlockedIpv4(embedded)) return true
        }

        return false
    }

    /**
     * 숫자형 호스트 판정 — DNS를 치지 않고 구문으로만 판단한다.
     *
     * 다음 중 하나이면 true:
     *   - IPv6 리터럴: 콜론 포함
     *   - IPv4 점-십진: 세그먼트가 모두 십진수(4개 이하, 단축 형식 포함 — 127.1 등)
     *   - 정수 형식: 숫자만(2130706433 등 32비트 정수 IPv4)
     *   - 16진수 형식: 0x로 시작
     *   - 점-16진수: 세그먼트가 0x로 시작
     *
     * 이 판정이 true이면 InetAddress.getByName이 DNS 없이 환원 가능하다.
     * false(일반 호스트명)이면 getByName은 DNS를 치므로 I/O 금지 원칙상 호출하지 않는다.
     */
    private fun isNumericHost(host: String): Boolean {
        // IPv6: 콜론 포함
        if (host.contains(':')) return true

        // 16진수 단일값(0x7f000001 등)
        if (host.startsWith("0x")) return true

        val parts = host.split('.')

        // 점-16진수(0x7f.0x0.0x0.0x1)
        if (parts.any { it.startsWith("0x") }) return true

        // 정수 단일값(순수 숫자, 점 없음)
        if (parts.size == 1 && parts[0].all(Char::isDigit)) return true

        // 점-십진(1~4 세그먼트, 각 세그먼트 십진 숫자) — 127.1, 127.0.1, 127.0.0.1 모두 포함
        if (parts.size in 1..4 && parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) }) return true

        return false
    }
}
