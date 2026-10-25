package com.oquq.app.vpn.engine

import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.nekohasekai.oquqawg.Oquqawg

/**
 * AmneziaWG engine (libawg.aar — gomobile bind of a thin wrapper around
 * github.com/amnezia-vpn/amnezia-wg, see scripts/awgbind/awg.go).
 *
 * sing-box has no AmneziaWG support (open upstream issues), so AWG profiles
 * take the fast path: the VpnService fd goes straight into the userspace
 * WireGuard core — no intermediate proxy layers at all.
 *
 * Socket protection follows the WireGuard-Android GoBackend pattern:
 * fetch the raw socket fd, let the system VPN service protect() it, push
 * it back into the tunnel.
 */
object AwgEngine {
    private var tunnelHandle = -1

    val running: Boolean get() = tunnelHandle >= 0

    /**
     * @param tunFd fd from [ParcelFileDescriptor.detachFd]
     * @param settings userspace config from AwgConfigBuilder
     */
    fun start(
        tunFd: Int,
        settings: String,
    ): Boolean {
        check(tunnelHandle < 0) { "AWG already running" }
        tunnelHandle = Oquqawg.wgTurnOn("oQuQ", tunFd.toLong(), settings)
        return tunnelHandle >= 0
    }

    /**
     * Protect the tunnel's UDP sockets through the active VPN service.
     * VpnService.protect(fd) is sticky on the fd, mirroring upstream GoBackend.
     */
    fun protectSockets(vpnService: VpnService) {
        val handle = tunnelHandle
        if (handle < 0) return
        runCatching {
            val v4 = Oquqawg.wgGetSocketV4(handle)
            if (v4 >= 0) vpnService.protect(v4)
            val v6 = Oquqawg.wgGetSocketV6(handle)
            if (v6 >= 0) vpnService.protect(v6)
        }
    }

    fun stop() {
        if (tunnelHandle >= 0) {
            runCatching { Oquqawg.wgTurnOff(tunnelHandle) }
            tunnelHandle = -1
        }
    }

    fun version(): String = runCatching { Oquqawg.wgVersion() }.getOrDefault("awg")
}
