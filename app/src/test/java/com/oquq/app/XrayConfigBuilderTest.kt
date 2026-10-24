package com.oquq.app

import com.oquq.app.core.config.SingBoxConfigBuilder
import com.oquq.app.core.config.XrayConfigBuilder
import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.ProfileParams
import com.oquq.app.core.model.Protocol
import com.oquq.app.core.parser.LinkParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun buildXray(params: ProfileParams) =
        json
            .parseToJsonElement(
                XrayConfigBuilder.build(
                    Profile(
                        id = 1,
                        name = "x",
                        protocol = Protocol.VLESS,
                        server = "x.example.net",
                        port = 443,
                        rawLink = "",
                        params = LinkParser.paramsToJson(params),
                    ),
                ),
            ).jsonObject

    @Test
    fun `socks inbound on loopback xray port with udp and sniffing`() {
        val cfg = buildXray(ProfileParams(uuid = "u", transport = "xhttp"))
        val inbound = cfg["inbounds"]!!.jsonArray[0].jsonObject
        assertEquals("socks", inbound["protocol"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals(
            SingBoxConfigBuilder.XRAY_SOCKS_PORT,
            inbound["port"]!!.jsonPrimitive.content.toInt(),
        )
        assertTrue(
            inbound["settings"]!!
                .jsonObject["udp"]!!
                .jsonPrimitive.content
                .toBoolean(),
        )
    }

    @Test
    fun `xhttp reality outbound fully wired`() {
        val cfg =
            buildXray(
                ProfileParams(
                    uuid = "uuid-x",
                    transport = "xhttp",
                    transportPath = "/wow",
                    transportHost = "x.example.net",
                    xhttpMode = "auto",
                    sni = "cover.example.net",
                    fingerprint = "chrome",
                    realityPbk = "PBK",
                    realitySid = "ABCD",
                    flow = "",
                ),
            )
        val out = cfg["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("vless", out["protocol"]!!.jsonPrimitive.content)
        val user =
            out["settings"]!!
                .jsonObject["vnext"]!!
                .jsonArray[0]
                .jsonObject["users"]!!
                .jsonArray[0]
                .jsonObject
        assertEquals("uuid-x", user["id"]!!.jsonPrimitive.content)
        assertEquals("none", user["encryption"]!!.jsonPrimitive.content)
        val stream = out["streamSettings"]!!.jsonObject
        assertEquals("xhttp", stream["network"]!!.jsonPrimitive.content)
        assertEquals("reality", stream["security"]!!.jsonPrimitive.content)
        val xhttp = stream["xhttpSettings"]!!.jsonObject
        assertEquals("/wow", xhttp["path"]!!.jsonPrimitive.content)
        assertEquals("auto", xhttp["mode"]!!.jsonPrimitive.content)
        val reality = stream["realitySettings"]!!.jsonObject
        assertEquals("PBK", reality["publicKey"]!!.jsonPrimitive.content)
        assertEquals("ABCD", reality["shortId"]!!.jsonPrimitive.content)
        assertEquals("chrome", reality["fingerprint"]!!.jsonPrimitive.content)
        assertTrue(
            out["mux"]!!
                .jsonObject["enabled"]!!
                .jsonPrimitive.content
                .toBoolean()
                .not(),
        )
    }

    @Test
    fun `ws tls outbound uses tlsSettings`() {
        val cfg =
            buildXray(
                ProfileParams(uuid = "u", transport = "ws", transportPath = "/ws", transportHost = "cdn.io", sni = "s.io"),
            )
        val stream = cfg["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        assertEquals("ws", stream["network"]!!.jsonPrimitive.content)
        assertEquals("tls", stream["security"]!!.jsonPrimitive.content)
        assertEquals("s.io", stream["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content)
        assertEquals("/ws", stream["wsSettings"]!!.jsonObject["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun `grpc serviceName mapped from transport params`() {
        val cfg =
            buildXray(
                ProfileParams(uuid = "u", transport = "grpc", transportServiceName = "svc"),
            )
        val stream = cfg["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        assertEquals("grpc", stream["network"]!!.jsonPrimitive.content)
        assertEquals("svc", stream["grpcSettings"]!!.jsonObject["serviceName"]!!.jsonPrimitive.content)
    }
}
