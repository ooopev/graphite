package com.oquq.app.vpn.engine

import android.content.Context
import io.github.xtls.libxray.LibXray
import java.io.File

/**
 * Xray-core engine (libxray.aar, XTLS/libxray — Xray-core 26.x).
 *
 * Serves Xray-exclusive stream transports (XHTTP today) as a local SOCKS5
 * hop. The sing-box TUN routes into it; loopback cost is negligible next to
 * network latency.
 *
 * Only file touching LibXray bindings — pin/verify against XTLS/libxray
 * README when updating the AAR.
 */
object XrayEngine {

    @Volatile private var pid: Int = 0

    val running: Boolean get() = pid > 0

    /** Starts the Xray instance; returns false if the core rejected the config. */
    fun start(context: Context, config: String): Boolean {
        check(pid <= 0) { "Xray already running" }
        val datDir = File(context.filesDir, "dat").apply { mkdirs() }
        return runCatching {
            pid = LibXray.runXray("oquq", datDir.absolutePath, config)
            pid > 0
        }.getOrDefault(false)
    }

    fun stop() {
        if (pid > 0) {
            runCatching { LibXray.stopXray(pid) }
            pid = 0
        }
    }

    fun version(): String = runCatching { LibXray.version(-1) }.getOrDefault("xray")
}
