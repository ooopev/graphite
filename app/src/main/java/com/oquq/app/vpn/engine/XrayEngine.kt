package com.oquq.app.vpn.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.nekohasekai.libXray.LibXray

/**
 * Xray-core engine (libXray.aar, XTLS/libxray — Xray-core 26.x, API v3).
 *
 * Serves Xray-exclusive stream transports (XHTTP today) as a local SOCKS5
 * hop. The sing-box TUN routes into it; loopback cost is negligible next to
 * network latency.
 *
 * Protocol: single JNI entry [LibXray.invoke] with JSON in/out:
 *   request  {"apiVersion":3, "method":"runXray", "payload":{"xrayJson":"…"}}
 *   response {"success":bool, "data":…, "error":"…"}
 */
object XrayEngine {
    private const val API_VERSION = 3L
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile var running: Boolean = false
        private set

    /** Starts the Xray instance; false if the core rejected the config. */
    fun start(config: String): Boolean {
        check(!running) { "Xray already running" }
        val request =
            buildJsonObject {
                put("apiVersion", API_VERSION)
                put("method", "runXray")
                put("payload", buildJsonObject { put("xrayJson", config) })
            }.toString()
        val response = LibXray.invoke(request)
        val ok =
            runCatching {
                json
                    .parseToJsonElement(response)
                    .jsonObject["success"]
                    ?.jsonPrimitive
                    ?.content == "true"
            }.getOrDefault(false)
        running = ok
        return ok
    }

    fun stop() {
        runCatching {
            LibXray.invoke(
                buildJsonObject {
                    put("apiVersion", API_VERSION)
                    put("method", "stopXray")
                }.toString(),
            )
        }
        running = false
    }

    fun version(): String =
        runCatching {
            val response =
                LibXray.invoke(
                    buildJsonObject {
                        put("apiVersion", API_VERSION)
                        put("method", "xrayVersion")
                    }.toString(),
                )
            json
                .parseToJsonElement(response)
                .jsonObject["data"]!!
                .jsonObject["version"]!!
                .jsonPrimitive.content
        }.getOrDefault("xray")
}
