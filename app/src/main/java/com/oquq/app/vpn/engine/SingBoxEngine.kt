package com.oquq.app.vpn.engine

import android.content.Context
import android.net.VpnService
import com.oquq.app.data.PerAppMode
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import android.util.Log

/**
 * sing-box engine (libbox.aar, sing-box 1.14 — CommandServer API).
 *
 * Owns the TUN for every protocol except AmneziaWG. Flow mirrors SFA 1.14:
 *
 *   Libbox.setup(SetupOptions)                    — once per process
 *   CommandServer(handler, platform).start()
 *   commandServer.startOrReloadService(config, OverrideOptions)
 *     → core calls platform.openTun(TunOptions)   — we build the VPN there
 *     → core calls platform.autoDetectInterfaceControl(fd) — we protect()
 *
 * Per-app routing is pushed into the core via OverrideOptions (sing-box
 * filters by UID inside the tun; no VpnService.Builder app lists needed).
 *
 * Traffic stats: a CommandClient with CommandStatus pushes writeStatus()
 * once per second; no polling loop on our side.
 *
 * NOTE: this file is the only place touching libbox bindings — pin/verify
 * against experimental/libbox when upgrading the AAR.
 */
object SingBoxEngine {
    /** (uplink, downlink) cumulative bytes, pushed by the core each second. */
    private val _stats = MutableStateFlow(0L to 0L)
    val stats: StateFlow<Pair<Long, Long>> = _stats.asStateFlow()

    private var commandServer: CommandServer? = null
    private var statusClient: CommandClient? = null
    private var platform: BoxPlatformService? = null
    private var defaultNetworkMonitor: DefaultNetworkMonitor? = null
    private var setupDone = false

    val running: Boolean get() = commandServer != null

    private fun ensureSetup(context: Context) {
        if (setupDone) return
        // libbox persists cache/state under workingPath. A schema change in
        // the bundled core must not reuse state written by an older build.
        File(context.filesDir, "core").deleteRecursively()
        Libbox.setup(
            SetupOptions().apply {
                basePath = context.filesDir.path
                workingPath = File(context.filesDir, "core").apply { mkdirs() }.path
                tempPath = File(context.cacheDir, "core").apply { mkdirs() }.path
            },
        )
        setupDone = true
    }

    /**
     * @param perAppMode include/exclude mode when perAppPackages is not empty
     * @throws Exception if the core rejects the config or fails to start
     */
    fun start(
        context: Context,
        vpnService: VpnService,
        config: String,
        perAppMode: PerAppMode?,
        perAppPackages: Set<String>,
    ) {
        check(commandServer == null) { "sing-box already running" }
        ensureSetup(context)
        ProxyDiagnostics.start(context)

        defaultNetworkMonitor = DefaultNetworkMonitor(context)
        platform = BoxPlatformService(vpnService, defaultNetworkMonitor!!)

        val server = CommandServer(ServerHandler(), platform!!)
        server.start()
        server.startOrReloadService(
            config,
            OverrideOptions().apply {
                when {
                    perAppMode == PerAppMode.INCLUDE && perAppPackages.isNotEmpty() ->
                        includePackage = StringIteratorImpl(perAppPackages)

                    perAppMode == PerAppMode.EXCLUDE && perAppPackages.isNotEmpty() ->
                        excludePackage = StringIteratorImpl(perAppPackages + context.packageName)

                    else ->
                        // Default: keep our own process OUT of the tun. The
                        // Xray hop dials its upstream from this very process;
                        // without the exclusion that socket is routed back
                        // into the tunnel and loops into the SOCKS hop itself.
                        excludePackage = StringIteratorImpl(listOf(context.packageName))
                }
            },
        )
        commandServer = server

        statusClient =
            CommandClient(
                StatusHandler(),
                CommandClientOptions().apply {
                    addCommand(Libbox.CommandStatus)
                    statusInterval = 1_000_000_000L // 1s in nanoseconds
                },
            ).also { it.connect() }
    }

    /** Stops the 1 Hz status socket push while the screen is off (battery). */
    fun pauseStatusUpdates() {
        runCatching { statusClient?.disconnect() }
        statusClient = null
    }

    /** Re-subscribes after [pauseStatusUpdates]; no-op when not running. */
    fun resumeStatusUpdates() {
        if (statusClient != null || commandServer == null) return
        runCatching {
            statusClient =
                CommandClient(
                    StatusHandler(),
                    CommandClientOptions().apply {
                        addCommand(Libbox.CommandStatus)
                        statusInterval = 1_000_000_000L // 1s in nanoseconds
                    },
                ).also { it.connect() }
        }
    }

    fun stop() {
        runCatching { statusClient?.disconnect() }
        statusClient = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        // Close the tun fds the core leaked (it only closes its dups); while
        // one stays open Android keeps the VPN session alive after disconnect.
        TunFdRegistry.closeAll()
        runCatching { defaultNetworkMonitor?.close() }
        defaultNetworkMonitor = null
        platform = null
        _stats.value = 0L to 0L
    }

    /** Cumulative (up, down) through the tunnel. */
    fun queryStats(): Pair<Long, Long> = _stats.value

    private class StatusHandler : CommandClientHandler {
        override fun clearLogs() {}

        override fun connected() {}

        override fun disconnected(message: String?) {}

        override fun initializeClashMode(
            modes: StringIterator?,
            current: String?,
        ) {}

        override fun setDefaultLogLevel(level: Int) {}

        override fun updateClashMode(mode: String?) {}

        override fun writeConnectionEvents(events: io.nekohasekai.libbox.ConnectionEvents?) {
            val line = "connection-events: $events"
            Log.e("oQuQ-proxyerror", line)
            ProxyDiagnostics.write(line)
        }

        override fun writeGroups(groups: io.nekohasekai.libbox.OutboundGroupIterator?) {}

        override fun writeLogs(logs: io.nekohasekai.libbox.LogIterator?) {
            while (logs?.hasNext() == true) {
                val entry = logs.next()
                val line = "level=${entry.level}: ${entry.message}"
                Log.e("oQuQ-proxyerror", line)
                ProxyDiagnostics.write(line)
            }
        }

        override fun writeOutbounds(outbounds: io.nekohasekai.libbox.OutboundGroupItemIterator?) {}

        override fun writeStatus(message: StatusMessage?) {
            message ?: return
            _stats.value = message.uplink to message.downlink
        }
    }

    private class ServerHandler : CommandServerHandler {
        override fun connectSSHAgent(): Int = -1

        override fun getSystemProxyStatus(): io.nekohasekai.libbox.SystemProxyStatus? = null

        override fun serviceReload() {}

        override fun serviceStop() {}

        override fun setSystemProxyEnabled(enabled: Boolean) {}

        override fun triggerNativeCrash() {}

        override fun writeDebugMessage(message: String?) {}
    }
}

private object ProxyDiagnostics {
    private var file: File? = null
    fun start(context: Context) { file = File(context.filesDir, "proxyerror.log"); runCatching { file?.writeText("sing-box start\n") } }
    fun write(line: String) { runCatching { file?.appendText(line + "\n") } }
}

/** gomobile StringIterator over a collection. */
class StringIteratorImpl(
    private val items: Iterator<String>,
) : StringIterator {
    constructor(items: Iterable<String>) : this(items.iterator())

    override fun hasNext(): Boolean = items.hasNext()

    override fun len(): Int = 0

    override fun next(): String = items.next()
}
