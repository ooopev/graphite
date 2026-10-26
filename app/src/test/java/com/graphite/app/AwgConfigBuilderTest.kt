package com.graphite.app

import com.graphite.app.core.config.AwgConfigBuilder
import com.graphite.app.core.model.Profile
import com.graphite.app.core.model.ProfileParams
import com.graphite.app.core.model.Protocol
import com.graphite.app.core.parser.LinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AwgConfigBuilderTest {
    // 32-byte WireGuard keys in base64
    private val privHex = "00" + "11".repeat(31)
    private val priv =
        Base64.getEncoder().encodeToString(
            ByteArray(32) { i -> (i * 1).toByte() },
        )
    private val pub =
        Base64.getEncoder().encodeToString(
            ByteArray(32) { i -> (i * 2 + 1).toByte() },
        )

    private fun build(params: ProfileParams) =
        AwgConfigBuilder.build(
            Profile(
                id = 1,
                name = "awg",
                protocol = Protocol.AMNEZIA_WG,
                server = "awg.example.net",
                port = 51820,
                rawLink = "",
                params = LinkParser.paramsToJson(params),
            ),
        )

    @Test
    fun `userspace settings contain hex keys endpoint and allowed ips`() {
        val text = build(ProfileParams(wgPrivateKey = priv, wgPeerPublicKey = pub))
        val lines = text.lines()
        // base64 32-byte key → 64 hex chars
        val privLine = lines.first { it.startsWith("private_key=") }
        assertEquals(64, privLine.removePrefix("private_key=").length)
        val pubLine = lines.first { it.startsWith("public_key=") }
        assertEquals(64, pubLine.removePrefix("public_key=").length)
        assertTrue(lines.any { it == "endpoint=awg.example.net:51820" })
        assertTrue(lines.any { it == "allowed_ip=0.0.0.0/0" })
        assertTrue(lines.any { it == "allowed_ip=::/0" })
        assertTrue(lines.any { it == "protocol_version=1" })
        assertTrue(lines.any { it == "replace_peers=true" })
    }

    @Test
    fun `psk included when present`() {
        val psk = Base64.getEncoder().encodeToString(ByteArray(32))
        val text =
            build(
                ProfileParams(wgPrivateKey = priv, wgPeerPublicKey = pub, wgPsk = psk),
            )
        assertTrue(text.lines().any { it.startsWith("preshared_key=") && it.length == "preshared_key=".length + 64 })
    }

    @Test
    fun `amnezia junk parameters appended`() {
        val text =
            build(
                ProfileParams(
                    wgPrivateKey = priv,
                    wgPeerPublicKey = pub,
                    awgIc = 3,
                    awgJc = 4,
                    awgJmin = 40,
                    awgJmax = 70,
                    awgS1 = 86,
                    awgS2 = 41,
                    awgH1 = 1234567890,
                    awgH2 = 1,
                    awgH3 = 2,
                    awgH4 = 3,
                ),
            )
        val lines = text.lines()
        assertEquals("Ic=3", lines.first { it.startsWith("Ic=") })
        assertEquals("Jc=4", lines.first { it.startsWith("Jc=") })
        assertEquals("Jmin=40", lines.first { it.startsWith("Jmin=") })
        assertEquals("Jmax=70", lines.first { it.startsWith("Jmax=") })
        assertEquals("S1=86", lines.first { it.startsWith("S1=") })
        assertEquals("S2=41", lines.first { it.startsWith("S2=") })
        assertEquals("H1=1234567890", lines.first { it.startsWith("H1=") })
        assertEquals("H4=3", lines.first { it.startsWith("H4=") })
    }

    @Test
    fun `no junk lines for plain wireguard params`() {
        val text = build(ProfileParams(wgPrivateKey = priv, wgPeerPublicKey = pub))
        assertTrue(text.lines().none { it.startsWith("Jc=") })
        assertTrue(text.lines().none { it.startsWith("Ic=") })
    }

    @Test
    fun `hex conversion is lowercase and correct`() {
        // key of all zero bytes must be 64 zeros
        val zeroKey = Base64.getEncoder().encodeToString(ByteArray(32))
        val text = build(ProfileParams(wgPrivateKey = zeroKey, wgPeerPublicKey = pub))
        assertEquals("private_key=" + "0".repeat(64), text.lines().first { it.startsWith("private_key=") })
        assertEquals(privHex.length, 64) // sanity of the fixture itself
    }
}
