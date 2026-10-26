package com.graphite.app

import com.graphite.app.core.model.ProfileParams
import com.graphite.app.core.model.Protocol
import com.graphite.app.core.parser.LinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class LinkParserTest {
    private fun params(raw: String) = LinkParser.paramsFromJson(LinkParser.parse(raw)!!.params)

    // ------------------------------------------------------------ vless

    @Test
    fun `vless reality vision with tcp`() {
        val link =
            "vless://9f4a2f58-d0a4-4fd3-9a6c-9f0a9f5dcba5@example.net:443" +
                "?encryption=none&security=reality&sni=www.microsoft.com&fp=chrome" +
                "&pbk=pbkKEY123&sid=a1b2c3&type=tcp&flow=xtls-rprx-vision#MyNode"
        val p = LinkParser.parse(link)!!
        assertEquals(Protocol.VLESS, p.protocol)
        assertEquals("example.net", p.server)
        assertEquals(443, p.port)
        assertEquals("MyNode", p.name)
        val pr = p.params.let { LinkParser.paramsFromJson(it) }
        assertEquals("9f4a2f58-d0a4-4fd3-9a6c-9f0a9f5dcba5", pr.uuid)
        assertEquals("xtls-rprx-vision", pr.flow)
        assertEquals("pbkKEY123", pr.realityPbk)
        assertEquals("a1b2c3", pr.realitySid)
        assertEquals("chrome", pr.fingerprint)
        assertEquals("tcp", pr.transport)
    }

    @Test
    fun `vless xhttp mode parsed`() {
        val link =
            "vless://uuid@h.io:443?security=reality&pbk=K&sid=01&type=xhttp" +
                "&path=%2Fwow&host=h.io&mode=auto#X"
        val p = LinkParser.parse(link)!!
        val pr = params(link)
        assertEquals("xhttp", pr.transport)
        assertEquals("/wow", pr.transportPath)
        assertEquals("auto", pr.xhttpMode)
        assertEquals("h.io", pr.transportHost)
    }

    @Test
    fun `vless websocket with tls`() {
        val link =
            "vless://uuid@h.io:8443?encryption=none&security=tls&sni=h.io" +
                "&type=ws&host=cdn.io&path=%2Fws%3Fed%3D2048#ws-node"
        val pr = params(link)
        assertEquals("ws", pr.transport)
        assertEquals("/ws?ed=2048", pr.transportPath)
        assertEquals("cdn.io", pr.transportHost)
        assertEquals("h.io", pr.sni)
    }

    // ------------------------------------------------------------ vmess

    @Test
    fun `vmess base64 json`() {
        val json =
            """
            {"v":"2","ps":"vm node","add":"vm.io","port":"443","id":"aaaabbbb-cccc-dddd-eeee-ffff00001111",
            "scy":"auto","net":"ws","type":"none","host":"vmhost.io","path":"/vm","tls":"tls","sni":"vm.io","fp":"chrome"}
            """.trimIndent()
        val link = "vmess://" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val p = LinkParser.parse(link)!!
        assertEquals(Protocol.VMESS, p.protocol)
        assertEquals("vm node", p.name)
        assertEquals("vm.io", p.server)
        assertEquals(443, p.port)
        val pr = params(link)
        assertEquals("aaaabbbb-cccc-dddd-eeee-ffff00001111", pr.uuid)
        assertEquals("ws", pr.transport)
        assertEquals("/vm", pr.transportPath)
        assertEquals("auto", pr.encryption)
    }

    // ------------------------------------------------------------ trojan

    @Test
    fun `trojan with grpc`() {
        val link =
            "trojan://secretpw@t.io:443?security=tls&sni=t.io&type=grpc" +
                "&serviceName=grpcsvc&fp=firefox#trojan"
        val p = LinkParser.parse(link)!!
        assertEquals(Protocol.TROJAN, p.protocol)
        val pr = params(link)
        assertEquals("secretpw", pr.password)
        assertEquals("grpc", pr.transport)
        assertEquals("grpcsvc", pr.transportServiceName)
    }

    // ------------------------------------------------------------ shadowsocks

    @Test
    fun `ss sip002 urlsafe base64 userinfo`() {
        val userinfo =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString("aes-256-gcm:passw0rd".toByteArray())
        val link = "ss://$userinfo@ss.io:8388#ss-node"
        val p = LinkParser.parse(link)!!
        assertEquals(Protocol.SHADOWSOCKS, p.protocol)
        assertEquals("ss-node", p.name)
        val pr = params(link)
        assertEquals("aes-256-gcm", pr.method)
        assertEquals("passw0rd", pr.password)
    }

    @Test
    fun `ss2022 plaintext credentials`() {
        val link = "ss://2022-blake3-aes-256-gcm:c2VjcmV0MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM=@ss.io:8388#ss22"
        val pr = params(link)
        assertEquals("2022-blake3-aes-256-gcm", pr.method)
        assertEquals("c2VjcmV0MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM=", pr.password)
    }

    @Test
    fun `ss legacy full base64 payload`() {
        val payload = "aes-128-gcm:legacy@ss.io:1234"
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        val p = LinkParser.parse("ss://$b64#legacy")
        assertNotNull(p)
        val pr = params("ss://$b64#legacy")
        assertEquals("aes-128-gcm", pr.method)
        assertEquals("legacy", pr.password)
        assertEquals("ss.io", p!!.server)
        assertEquals(1234, p.port)
    }

    // ------------------------------------------------------------ hysteria2 / tuic

    @Test
    fun `hysteria2 with obfs and bandwidth`() {
        val link =
            "hysteria2://authpw@hy.io:8443/?sni=hy.io&insecure=1&obfs=salamander" +
                "&obfs-password=obfspw&upmbps=100&downmbps=500#hy2"
        val p = LinkParser.parse(link)!!
        assertEquals(Protocol.HYSTERIA2, p.protocol)
        val pr = params(link)
        assertEquals("authpw", pr.password)
        assertEquals("salamander", pr.hy2Obfs)
        assertEquals("obfspw", pr.hy2ObfsPassword)
        assertEquals(100, pr.upMbps)
        assertEquals(500, pr.downMbps)
        assertEquals(true, pr.allowInsecure)
        assertEquals(listOf("h3"), pr.alpn)
    }

    @Test
    fun `hy2 scheme alias`() {
        val p = LinkParser.parse("hy2://pw@hy.io:443?sni=hy.io#alias")!!
        assertEquals(Protocol.HYSTERIA2, p.protocol)
    }

    @Test
    fun `tuic v5`() {
        val link =
            "tuic://uuid-1234:tuicpw@tu.io:8443?congestion_control=bbr" +
                "&udp_relay_mode=native&alpn=h3&sni=tu.io#tuic"
        val p = LinkParser.parse(link)!!
        assertEquals(Protocol.TUIC, p.protocol)
        val pr = params(link)
        assertEquals("uuid-1234", pr.uuid)
        assertEquals("tuicpw", pr.password)
        assertEquals("bbr", pr.congestionControl)
        assertEquals("native", pr.udpRelayMode)
    }

    // ------------------------------------------------------------ socks / http

    @Test
    fun `socks with credentials`() {
        val p = LinkParser.parse("socks://user:pass@s.io:1080#sock")!!
        assertEquals(Protocol.SOCKS, p.protocol)
        val pr = params("socks://user:pass@s.io:1080#sock")
        assertEquals("user", pr.username)
        assertEquals("pass", pr.password)
    }

    @Test
    fun `http proxy requires userinfo`() {
        assertNotNull(LinkParser.parse("http://u:p@h.io:8080#hp"))
        assertNull(LinkParser.parse("http://plain-site.example.com/page"))
    }

    // ------------------------------------------------------------ wireguard ini

    private val wgIni =
        """
        [Interface]
        PrivateKey = ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef=
        Address = 10.7.0.2/32, fd42:42::2/128
        DNS = 1.1.1.1, 1.0.0.1
        MTU = 1420

        [Peer]
        PublicKey = PUBKEYPUBKEYPUBKEYpub=
        PresharedKey = PSKPSKPSKPSK=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = wg.example.net:51820
        PersistentKeepalive = 25
        """.trimIndent()

    @Test
    fun `wireguard ini parsed`() {
        val p = LinkParser.parse(wgIni)!!
        assertEquals(Protocol.WIREGUARD, p.protocol)
        assertEquals("wg.example.net", p.server)
        assertEquals(51820, p.port)
        val pr = params(wgIni)
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef=", pr.wgPrivateKey)
        assertEquals(listOf("10.7.0.2/32", "fd42:42::2/128"), pr.wgAddresses)
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), pr.wgDns)
        assertEquals(1420, pr.wgMtu)
        assertEquals("PSKPSKPSKPSK=", pr.wgPsk)
    }

    @Test
    fun `amnezia ini short keys detected`() {
        val awg = wgIni + "\nIc = 3\nJc = 4\nJmin = 40\nJmax = 70\nS1 = 86\nS2 = 41\nH1 = 1234567890\nH2 = 98\nH3 = 99\nH4 = 100\n"
        val p = LinkParser.parse(awg)!!
        assertEquals(Protocol.AMNEZIA_WG, p.protocol)
        val pr = params(awg)
        assertEquals(3, pr.awgIc)
        assertEquals(4, pr.awgJc)
        assertEquals(40, pr.awgJmin)
        assertEquals(70, pr.awgJmax)
        assertEquals(86, pr.awgS1)
        assertEquals(41, pr.awgS2)
        assertEquals(1234567890L, pr.awgH1)
        assertEquals(100L, pr.awgH4)
    }

    @Test
    fun `amnezia ini long aliases detected`() {
        val awg = wgIni + "\nJunkPacketCount = 5\nJunkPacketMinSize = 50\nJunkPacketMaxSize = 1000\n"
        val p = LinkParser.parse(awg)!!
        assertEquals(Protocol.AMNEZIA_WG, p.protocol)
        val pr = params(awg)
        assertEquals(5, pr.awgJc)
        assertEquals(50, pr.awgJmin)
        assertEquals(1000, pr.awgJmax)
    }

    // ------------------------------------------------------------ amnezia vpn://

    @Test
    fun `amnezia vpn backup with last_config`() {
        val ini = wgIni.replace("wg.example.net", "amnezia.example.net") + "\nJc = 6\n"
        val backup =
            """{"name":"My Amnezia","containers":[{"wireguard":{"last_config":""" +
                "\"" + ini.replace("\n", "\\n").replace("\"", "\\\"") + "\"" + """}}]}"""
        val link = "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(backup.toByteArray())
        val p = LinkParser.parse(link)!!
        assertEquals(Protocol.AMNEZIA_WG, p.protocol)
        assertEquals("My Amnezia", p.name)
        assertEquals("amnezia.example.net", p.server)
        assertEquals(link, p.rawLink)
    }

    // ------------------------------------------------------------ negative

    @Test
    fun `garbage returns null`() {
        assertNull(LinkParser.parse("hello world"))
        assertNull(LinkParser.parse("vless://"))
        assertNull(LinkParser.parse("vmess://%%%invalid-base64"))
        assertNull(LinkParser.parse(""))
    }

    @Test
    fun `params roundtrip keeps defaults`() {
        val pr = ProfileParams(uuid = "x", sni = "y")
        val restored = LinkParser.paramsFromJson(LinkParser.paramsToJson(pr))
        assertEquals(pr, restored)
    }
}
