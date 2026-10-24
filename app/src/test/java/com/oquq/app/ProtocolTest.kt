package com.oquq.app

import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.ProfileParams
import com.oquq.app.core.model.Protocol
import com.oquq.app.core.model.effectiveEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {
    private fun profile(
        protocol: Protocol,
        transport: String? = null,
    ) = Profile(
        id = 1,
        name = "t",
        protocol = protocol,
        server = "example.com",
        port = 443,
        rawLink = "",
        params =
            com.oquq.app.core.parser.LinkParser
                .paramsToJson(ProfileParams(transport = transport)),
    )

    @Test
    fun `amnezia always goes to awg engine`() {
        assertEquals(Protocol.Engine.AWG, profile(Protocol.AMNEZIA_WG, "tcp").effectiveEngine(ProfileParams()))
    }

    @Test
    fun `vless xhttp goes to xray engine`() {
        val params = ProfileParams(transport = "xhttp")
        assertEquals(Protocol.Engine.XRAY, profile(Protocol.VLESS, "xhttp").effectiveEngine(params))
    }

    @Test
    fun `vless tcp stays on sing-box`() {
        val params = ProfileParams(transport = "tcp")
        assertEquals(Protocol.Engine.SING_BOX, profile(Protocol.VLESS, "tcp").effectiveEngine(params))
    }

    @Test
    fun `all standard protocols map to sing-box`() {
        listOf(
            Protocol.VMESS,
            Protocol.TROJAN,
            Protocol.SHADOWSOCKS,
            Protocol.HYSTERIA2,
            Protocol.TUIC,
            Protocol.WIREGUARD,
            Protocol.SOCKS,
            Protocol.HTTP,
        ).forEach { p ->
            assertEquals(Protocol.Engine.SING_BOX, profile(p).effectiveEngine(ProfileParams()))
        }
    }
}
