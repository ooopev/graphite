package com.graphite.app

import com.graphite.app.core.model.Protocol
import com.graphite.app.core.model.Subscription
import com.graphite.app.core.parser.SubscriptionParser
import com.graphite.app.core.parser.SubscriptionUpdate
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class SubscriptionParserTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val linkList =
        listOf(
            "vless://u@a.io:443?type=tcp&security=reality&pbk=K#first",
            "hysteria2://pw@b.io:8443?sni=b.io#second",
            "trojan://pw@c.io:443?sni=c.io#third",
        )

    @Test
    fun `plain body parses all links`() {
        val profiles = SubscriptionParser.parseBody(linkList.joinToString("\n"))
        assertEquals(3, profiles.size)
        assertEquals(Protocol.VLESS, profiles[0].protocol)
        assertEquals("first", profiles[0].name)
        assertEquals(Protocol.HYSTERIA2, profiles[1].protocol)
    }

    @Test
    fun `base64 body decodes and parses`() {
        val b64 = Base64.getEncoder().encodeToString(linkList.joinToString("\n").toByteArray())
        val profiles = SubscriptionParser.parseBody(b64)
        assertEquals(3, profiles.size)
    }

    @Test
    fun `base64 subscription with bom and url alphabet decodes`() {
        val payload = "\uFEFF" + linkList.joinToString("\n")
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        assertEquals(3, SubscriptionParser.parseBody(b64).size)
    }

    @Test
    fun `duplicates and garbage lines dropped`() {
        val body = (linkList + linkList[0] + "total-garbage-line").joinToString("\n")
        val profiles = SubscriptionParser.parseBody(body)
        assertEquals(3, profiles.size)
    }

    @Test
    fun `fetch parses userinfo header and body`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(linkList.joinToString("\n"))
                    .setHeader(
                        "subscription-userinfo",
                        "upload=1000; download=2000; total=10000; expire=1893456000",
                    ),
            )
            val result = SubscriptionParser.fetch(server.url("/sub").toString()).getOrThrow()
            assertEquals(3, result.profiles.size)
            assertEquals(1000L, result.upload)
            assertEquals(2000L, result.download)
            assertEquals(10000L, result.total)
            assertEquals(1893456000L, result.expire)
        }

    @Test
    fun `fetch failure on http error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            assertTrue(SubscriptionParser.fetch(server.url("/sub").toString()).isFailure)
        }

    @Test
    fun `applyToSubscription updates counters and timestamp`() {
        val sub = Subscription(id = 1, name = "s", url = "u")
        val updated =
            SubscriptionParser.applyToSubscription(
                sub,
                SubscriptionUpdate(emptyList(), 10, 20, 30, 42L),
            )
        assertEquals(10L, updated.upload)
        assertEquals(20L, updated.download)
        assertEquals(30L, updated.total)
        assertEquals(42L, updated.expire)
        assertTrue(updated.lastUpdate != null)
    }
}
