package com.oquq.app.vpn.engine

import android.net.VpnService
import com.oquq.app.data.AppSettings
import com.oquq.app.data.PerAppMode
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.BoxServiceOptions
import io.nekohasekai.libbox.NewBoxServiceOptions
import io.nekohasekai.libbox.NewService
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunOptions

/**
 * sing-box engine (libbox.aar, sing-box 1.12+).
 *
 * Owns the TUN for every protocol except AmneziaWG: libbox parses the tun
 * inbound from our config, asks [BoxPlatformService.openTun] for an fd, and
 * runs the whole box (routing, DNS, crypto) in Go threads.
 *
 * NOTE: this file is the only place that touches libbox bindings. Field and
 * method names follow the gomobile-generated API shipped in libbox.aar
 * (io.nekohasekai.libbox.*). If a libbox upgrade renames something, it is
 * fixed here and nowhere else.
 */
object SingBoxEngine {

    @Volatile private var service: BoxService? = null
    @Volatile private var platform: BoxPlatformService? = null

    val running: Boolean get() = service != null

    fun start(vpnService: VpnService, config: String, settings: AppSettings) {
        check(service == null) { "sing-box already running" }
        val platformService = BoxPlatformService(vpnService, settings)
        platform = platformService
        val options = NewBoxServiceOptions().apply {
            platformInterface = platformService
        }
        service = NewService(config, options).also { it.start() }
    }

    fun stop() {
        service?.let { runCatching { it.close() } }
        service = null
        platform = null
    }

    /**
     * Cumulative traffic through the tunnel, (up, down) bytes.
     * QueryStats(direct=true) = download counter, false = upload counter.
     */
    fun queryStats(): Pair<Long, Long> {
        val svc = service ?: return 0L to 0L
        return runCatching {
            svc.queryStats("", true) to svc.queryStats("", false)
        }.getOrDefault(0L to 0L)
    }
}

/**
 * Callback target libbox uses to create the TUN and cache files on Android.
 * Implements the gomobile-bound [PlatformInterface].
 */
class BoxPlatformService(
    private val vpnService: VpnService,
    private val settings: AppSettings,
) : PlatformInterface {

    /**
     * libbox hands us its computed TUN parameters; we map them onto a
     * VpnService.Builder, apply per-app routing and return the fd.
     */
    override fun openTun(options: TunOptions?): Int {
        val o = options ?: throw IllegalStateException("null TunOptions")
        val builder = vpnService.Builder()
            .setSession("oQuQ")
            .setBlocking(true)
            .setMtu(o.mTU.toInt())
            .addAddress("172.19.0.1", 30) // matches SingBoxConfigBuilder tun block
            .addAddress("fdfe:dcba:9876::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("172.19.0.1") // in-tunnel DNS; sing-box hijacks it

        applyPerApp(builder)

        return builder.establish()?.detachFd()
            ?: throw IllegalStateException("VPN permission revoked or establish failed")
    }

    override fun writeFile(path: String?, content: ByteArray?) {
        if (path == null || content == null) return
        java.io.File(path).apply { parentFile?.mkdirs() }.writeBytes(content)
    }

    override fun readFile(path: String?): ByteArray = java.io.File(path ?: "").readBytes()

    /** Per-app split tunneling — one of oQuQ's headline features, engine-agnostic. */
    private fun applyPerApp(builder: VpnService.Builder) {
        if (!settings.perAppEnabled || settings.perAppPackages.isEmpty()) return
        runCatching {
            when (settings.perAppMode) {
                PerAppMode.INCLUDE ->
                    settings.perAppPackages.forEach { builder.addAllowedApplication(it) }
                PerAppMode.EXCLUDE ->
                    settings.perAppPackages.forEach { builder.addDisallowedApplication(it) }
            }
        }
    }
}
