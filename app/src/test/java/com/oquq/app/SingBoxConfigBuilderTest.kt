package com.oquq.app

import com.oquq.app.core.config.SingBoxConfigBuilder
import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.ProfileParams
import com.oquq.app.core.model.Protocol
import com.oquq.app.core.parser.LinkParser
import com.oquq.app.data.AppSettings
import com.oquq.app.data.PerAppMode
import com.oquq.app.data.ProxyMode
import com.oquq.app.data.TunStack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigBuilderTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun profileOf(
        protocol: Protocol,
        params: ProfileParams = ProfileParams(),
        server: String = "srv.example.net",
        port: Int = 443,
    ) = Profile(
        id = 1,
        name = "test",
        protocol = protocol,
        server = server,
        port = port,
        rawLink = "",
        params = LinkParser.paramsToJson(params),
    )

    private fun build(
        p: Profile,
        s: AppSettings = AppSettings(),
    ) = json.parseToJsonElement(SingBoxConfigBuilder.build(p, s)).jsonObject

    private fun outbounds(cfg: kotlinx.serialization.json.JsonObject) = cfg["outbounds"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun `vless reality builds vless outbound with utls and reality`() {
        val p =
            profileOf(
                Protocol.VLESS,
                ProfileParams(
                    uuid = "uuid-1",
                    flow = "xtls-rprx-vision",
                    transport = "tcp",
                    sni = "s.example",
                    fingerprint = "chrome",
                    realityPbk = "PBK",
                    realitySid = "SID",
                ),
            )
        val cfg = build(p)
        val proxy = outbounds(cfg).first { it["tag"]!!.jsonPrimitive.content == "proxy" }
        assertEquals("vless", proxy["type"]!!.jsonPrimitive.content)
        assertEquals(443, proxy["server_port"]!!.jsonPrimitive.content.toInt())
        val tls = proxy["tls"]!!.jsonObject
        assertTrue(tls["enabled"]!!.jsonPrimitive.content.toBoolean())
        val reality = tls["reality"]!!.jsonObject
        assertEquals("PBK", reality["public_key"]!!.jsonPrimitive.content)
        assertEquals("chrome", tls["utls"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content)
        assertEquals("xudp", proxy["packet_encoding"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tun inbound reflects stack mtu strict route`() {
        val cfg = build(profileOf(Protocol.TROJAN), AppSettings(mtu = 8888, tunStack = TunStack.SYSTEM))
        val tun = cfg["inbounds"]!!.jsonArray.first().jsonObject
        assertEquals("tun", tun["type"]!!.jsonPrimitive.content)
        assertEquals("system", tun["stack"]!!.jsonPrimitive.content)
        assertEquals(8888, tun["mtu"]!!.jsonPrimitive.content.toInt())
        assertTrue(tun["strict_route"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(tun["auto_route"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `route rules include sniff and dns hijack`() {
        val rules = build(profileOf(Protocol.TROJAN))["route"]!!.jsonObject["rules"]!!.jsonArray
        val actions = rules.map { it.jsonObject["action"]?.jsonPrimitive?.content }
        assertTrue("sniff" in actions)
        assertTrue("hijack-dns" in actions)
    }

    @Test
    fun `rule mode bypasses private ips, global does not`() {
        val rule = build(profileOf(Protocol.TROJAN), AppSettings(proxyMode = ProxyMode.RULE))
        val global = build(profileOf(Protocol.TROJAN), AppSettings(proxyMode = ProxyMode.GLOBAL))
        val ruleJson = rule["route"]!!.jsonObject["rules"]!!.jsonArray.joinToString()
        val globalJson = global["route"]!!.jsonObject["rules"]!!.jsonArray.joinToString()
        assertTrue("ip_is_private" in ruleJson)
        assertFalse("ip_is_private" in globalJson)
    }

    @Test
    fun `block ads adds remote rule set`() {
        val cfg = build(profileOf(Protocol.TROJAN), AppSettings(blockAds = true))
        val route = cfg["route"]!!.jsonObject
        assertTrue(route.toString().contains("geosite-category-ads-all"))
        val ruleSets = route["rule_set"]!!.jsonArray
        assertEquals("remote", ruleSets[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `socks share adds lan inbound when enabled`() {
        val cfg = build(profileOf(Protocol.TROJAN), AppSettings(socksShareEnabled = true, socksSharePort = 21080))
        val inbounds = cfg["inbounds"]!!.jsonArray.map { it.jsonObject }
        val socks = inbounds.first { it["type"]!!.jsonPrimitive.content == "socks" }
        assertEquals(21080, socks["listen_port"]!!.jsonPrimitive.content.toInt())
        assertEquals("127.0.0.1", socks["listen"]!!.jsonPrimitive.content)
    }

    @Test
    fun `wireguard goes through endpoint tagged proxy without proxy outbound`() {
        val p =
            profileOf(
                Protocol.WIREGUARD,
                ProfileParams(
                    wgPrivateKey = "KEY",
                    wgPeerPublicKey = "PUB",
                    wgAddresses = listOf("10.7.0.2/32"),
                    wgDns = listOf("1.1.1.1"),
                ),
                server = "wg.example.net",
                port = 51820,
            )
        val cfg = build(p)
        val endpoints = cfg["endpoints"]!!.jsonArray
        assertEquals(1, endpoints.size)
        val ep = endpoints[0].jsonObject
        assertEquals("wireguard", ep["type"]!!.jsonPrimitive.content)
        assertEquals("proxy", ep["tag"]!!.jsonPrimitive.content)
        val peer = ep["peers"]!!.jsonArray[0].jsonObject
        assertEquals("wg.example.net", peer["address"]!!.jsonPrimitive.content)
        assertEquals(51820, peer["port"]!!.jsonPrimitive.content.toInt())
        assertTrue(outbounds(cfg).none { it["tag"]?.jsonPrimitive?.content == "proxy" })
        // DNS must route through the tunnel
        val dnsRemote =
            cfg["dns"]!!
                .jsonObject["servers"]!!
                .jsonArray
                .map { it.jsonObject }
                .first { it["tag"]!!.jsonPrimitive.content == "dns-remote" }
        assertEquals("udp", dnsRemote["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `xhttp profile chains to local xray socks hop`() {
        val p = profileOf(Protocol.VLESS, ProfileParams(uuid = "u", transport = "xhttp"))
        val cfg = build(p)
        val proxy = outbounds(cfg).first { it["tag"]!!.jsonPrimitive.content == "proxy" }
        assertEquals("socks", proxy["type"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", proxy["server"]!!.jsonPrimitive.content)
        assertEquals(
            SingBoxConfigBuilder.XRAY_SOCKS_PORT,
            proxy["server_port"]!!.jsonPrimitive.content.toInt(),
        )
    }

    @Test
    fun `hysteria2 builds obfs and bandwidth`() {
        val p =
            profileOf(
                Protocol.HYSTERIA2,
                ProfileParams(
                    password = "pw",
                    hy2Obfs = "salamander",
                    hy2ObfsPassword = "ob",
                    upMbps = 100,
                    downMbps = 500,
                    sni = "hy.io",
                    alpn = listOf("h3"),
                ),
            )
        val proxy = outbounds(build(p)).first { it["tag"]!!.jsonPrimitive.content == "proxy" }
        assertEquals("hysteria2", proxy["type"]!!.jsonPrimitive.content)
        assertEquals(100, proxy["up_mbps"]!!.jsonPrimitive.content.toInt())
        assertEquals("salamander", proxy["obfs"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val tls = proxy["tls"]!!.jsonObject
        assertEquals("h3", tls["alpn"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `tuic congestion and udp relay`() {
        val p =
            profileOf(
                Protocol.TUIC,
                ProfileParams(uuid = "u", password = "p", congestionControl = "bbr", udpRelayMode = "native"),
            )
        val proxy = outbounds(build(p)).first { it["tag"]!!.jsonPrimitive.content == "proxy" }
        assertEquals("tuic", proxy["type"]!!.jsonPrimitive.content)
        assertEquals("bbr", proxy["congestion_control"]!!.jsonPrimitive.content)
        assertEquals("native", proxy["udp_relay_mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `dns section always has local fallback`() {
        val cfg = build(profileOf(Protocol.TROJAN))
        val servers = cfg["dns"]!!.jsonObject["servers"]!!.jsonArray.map { it.jsonObject }
        assertTrue(servers.any { it["tag"]!!.jsonPrimitive.content == "dns-local" })
        assertTrue(servers.any { it["tag"]!!.jsonPrimitive.content == "dns-remote" })
        assertEquals("dns-remote", cfg["dns"]!!.jsonObject["final"]!!.jsonPrimitive.content)
    }

    @Test
    fun `per app settings are not embedded in config`() {
        // per-app routing is applied on the VpnService.Builder, config must stay app-agnostic
        val cfg =
            build(
                profileOf(Protocol.TROJAN),
                AppSettings(perAppEnabled = true, perAppMode = PerAppMode.INCLUDE, perAppPackages = setOf("a.b")),
            )
        assertFalse(cfg.toString().contains("a.b"))
    }
}
