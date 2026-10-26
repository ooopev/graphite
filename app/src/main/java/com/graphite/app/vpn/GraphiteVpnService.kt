package com.graphite.app.vpn

import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.util.Log
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import com.graphite.app.R
import com.graphite.app.core.config.AwgConfigBuilder
import com.graphite.app.core.config.SingBoxConfigBuilder
import com.graphite.app.core.config.XrayConfigBuilder
import com.graphite.app.core.model.Profile
import com.graphite.app.core.model.ProfileParams
import com.graphite.app.core.model.Protocol
import com.graphite.app.core.model.effectiveEngine
import com.graphite.app.core.parser.LinkParser
import com.graphite.app.core.repo.ProfileRepository
import com.graphite.app.data.AppSettings
import com.graphite.app.data.PerAppMode
import com.graphite.app.data.SettingsRepository
import com.graphite.app.ui.util.formatSpeed
import com.graphite.app.vpn.engine.AwgEngine
import com.graphite.app.vpn.engine.SingBoxEngine
import com.graphite.app.vpn.engine.XrayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The one and only VpnService. Orchestrates the engine trio:
 *
 *   SING_BOX — sing-box owns TUN directly (libbox openTun callback).
 *   XRAY     — Xray daemon serves SOCKS on loopback, sing-box TUN routes into it.
 *   AWG      — AmneziaWG userspace core owns the TUN fd itself.
 */
class GraphiteVpnService : VpnService() {
    companion object {
        const val ACTION_CONNECT = "com.graphite.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.graphite.app.action.DISCONNECT"
        const val EXTRA_PROFILE_ID = "profile_id"

        // Engines live in singletons, but the service instance can be
        // recreated at any moment (quick disconnect→connect: stopSelf from
        // the old instance races the new one's onStartCommand). Only the
        // instance that currently owns the engines may stop them.
        @Volatile
        private var activeInstance: GraphiteVpnService? = null
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            screenReceiver,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
                addAction(android.content.Intent.ACTION_SCREEN_ON)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statsJob: Job? = null
    private var tunnelFd: ParcelFileDescriptor? = null

    // Bumped by every command; a coroutine whose generation is stale must
    // not touch engines or state — its error path would otherwise shut down
    // the engines a newer command just started (CONNECT→DISCONNECT→CONNECT
    // inside one second leaves a zombie: VPN agent up, nobody reads the tun).
    private var commandGeneration = 0

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val appContext = applicationContext
        startAsForeground(appContext)
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
                scope.launch { connectTo(profileId) }
            }

            ACTION_DISCONNECT -> {
                scope.launch { shutdown() }
            }

            else -> {
                // No action = the system (re)started us — always-on VPN path.
                scope.launch {
                    val selected = SettingsRepository(appContext).current().selectedProfileId
                    if (selected > 0) {
                        connectTo(selected)
                    } else {
                        shutdown()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ connect

    private suspend fun connectTo(profileId: Long) {
        val generation = ++commandGeneration
        val repo = ProfileRepository.get(this)
        val settings = SettingsRepository(this).current()

        if (VpnController.state.value.status == VpnStatus.CONNECTED ||
            VpnController.state.value.status == VpnStatus.CONNECTING
        ) {
            shutdownEngines()
        }

        val profile =
            if (profileId > 0) {
                repo.profileOnce(profileId)
            } else {
                repo.profileOnce(settings.selectedProfileId)
            }

        if (profile == null) {
            if (generation == commandGeneration) {
                VpnController.update { it.copy(status = VpnStatus.IDLE, error = null) }
                stopCleanly()
            }
            return
        }

        // profileOnce/settings suspend; a newer command may have landed since.
        if (generation != commandGeneration) return

        val params = LinkParser.paramsFromJson(profile.params)
        val engine = profile.effectiveEngine(params)

        VpnController.update {
            VpnState(
                status = VpnStatus.CONNECTING,
                profileId = profile.id,
                profileName = profile.name,
                engine = engine,
            )
        }
        updateNotification()

        val connected =
            runCatching {
                when (engine) {
                    Protocol.Engine.SING_BOX -> {
                        startSingBox(profile, settings)
                        true
                    }

                    Protocol.Engine.XRAY -> {
                        // Xray serves the protocol; sing-box still owns the TUN and chains to it.
                        if (!XrayEngine.start(XrayConfigBuilder.build(profile))) {
                            error("Xray core rejected the config")
                        }
                        startSingBox(profile, settings)
                        true
                    }

                    Protocol.Engine.AWG -> {
                        connectAwg(profile, params, settings)
                    }
                }
            }.getOrElse { e ->
                if (generation != commandGeneration) return // superseded: engines belong to a newer command
                val details = generateSequence<Throwable>(e) { it.cause }
                    .map { "${it::class.java.simpleName}: ${it.message.orEmpty()}" }
                    .joinToString(" <- ")
                Log.e("Graphite", "VPN start failed: $details", e)
                runCatching { java.io.File(filesDir, "last-error.txt").writeText(details) }
                VpnController.update { it.copy(status = VpnStatus.IDLE, error = details) }
                shutdownEngines()
                stopCleanly()
                return
            }

        if (!connected) {
            VpnController.update { it.copy(status = VpnStatus.IDLE, error = "engine failed to start") }
            shutdownEngines()
            stopCleanly()
            return
        }

        VpnController.update { it.copy(status = VpnStatus.CONNECTED, error = null) }
        startStatsLoop(engine)
    }

    private fun startSingBox(
        profile: Profile,
        settings: AppSettings,
    ) {
        SingBoxEngine.start(
            context = this,
            vpnService = this,
            config = SingBoxConfigBuilder.build(
                profile,
                settings,
                java.io.File(filesDir, "singbox.log").path,
            ),
            perAppMode = if (settings.perAppEnabled) settings.perAppMode else null,
            perAppPackages = settings.perAppPackages,
        )
    }

    private fun connectAwg(
        profile: Profile,
        params: ProfileParams,
        settings: AppSettings,
    ): Boolean {
        val builder =
            Builder()
                .setSession("Graphite")
                .setBlocking(true)
                .setMtu(params.wgMtu ?: 1420)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
        (params.wgAddresses.ifEmpty { listOf("172.16.0.2/32") }).forEach { cidr ->
            val addr = cidr.substringBefore('/')
            val prefix = cidr.substringAfter('/').toIntOrNull() ?: return@forEach
            builder.addAddress(addr, prefix)
        }
        (params.wgDns.ifEmpty { listOf("1.1.1.1") }).forEach { builder.addDnsServer(it) }
        if (settings.perAppEnabled && settings.perAppPackages.isNotEmpty()) {
            runCatching {
                when (settings.perAppMode) {
                    PerAppMode.INCLUDE -> {
                        settings.perAppPackages.forEach { builder.addAllowedApplication(it) }
                    }

                    PerAppMode.EXCLUDE -> {
                        settings.perAppPackages.forEach { builder.addDisallowedApplication(it) }
                    }
                }
            }
        }
        val fd = builder.establish() ?: return false
        tunnelFd = fd
        val ok = AwgEngine.start(fd.detachFd(), AwgConfigBuilder.build(profile))
        if (ok) AwgEngine.protectSockets(this)
        return ok
    }

    // ------------------------------------------------------------------ stats

    private var currentEngine: Protocol.Engine? = null
    private var screenOn = true

    /**
     * 1 Hz stats pump feeding both the UI flow and the notification speed
     * line. Everything periodic pauses while the screen is off (see
     * [screenReceiver]): each tick is a CPU wakeup that would otherwise keep
     * the device out of deep sleep for no visible gain.
     */
    private fun startStatsLoop(engine: Protocol.Engine) {
        currentEngine = engine
        if (!screenOn) return
        resumeStatsLoop()
    }

    private fun resumeStatsLoop() {
        val engine = currentEngine ?: return
        statsJob?.cancel()
        val uid = android.os.Process.myUid()
        val baseRx = TrafficStats.getUidRxBytes(uid)
        val baseTx = TrafficStats.getUidTxBytes(uid)
        var prevUp = 0L
        var prevDown = 0L
        statsJob =
            scope.launch {
                while (isActive) {
                    val (up, down) =
                        when (engine) {
                            Protocol.Engine.AWG -> {
                                (TrafficStats.getUidTxBytes(uid) - baseTx) to
                                    (TrafficStats.getUidRxBytes(uid) - baseRx)
                            }

                            else -> {
                                SingBoxEngine.queryStats()
                            }
                        }
                    // Per-second speed, clamped at zero: core reloads reset
                    // the cumulative counters and must not show as negative.
                    val spUp = maxOf(0L, up - prevUp)
                    val spDown = maxOf(0L, down - prevDown)
                    prevUp = up
                    prevDown = down
                    VpnController.update { it.copy(upBytes = up, downBytes = down) }
                    updateNotification(
                        speedLine = formatSpeed(spDown) + " ▼  " + formatSpeed(spUp) + " ▲",
                    )
                    delay(1000)
                }
            }
    }

    private fun pauseStatsLoop() {
        statsJob?.cancel()
        statsJob = null
    }

    private val screenReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                when (intent.action) {
                    android.content.Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        pauseStatsLoop()
                        SingBoxEngine.pauseStatusUpdates()
                    }

                    android.content.Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        if (VpnController.state.value.status == VpnStatus.CONNECTED) {
                            SingBoxEngine.resumeStatusUpdates()
                            resumeStatsLoop()
                        }
                    }
                }
            }
        }

    // ------------------------------------------------------------------ shutdown

    private suspend fun shutdown() {
        // Supersede any in-flight connect: its continuation must not start
        // engines after the user just asked us to stop.
        ++commandGeneration
        VpnController.update { it.copy(status = VpnStatus.DISCONNECTING) }
        shutdownEngines()
        stopCleanly()
        VpnController.reset()
    }

    private fun shutdownEngines() {
        statsJob?.cancel()
        statsJob = null
        currentEngine = null
        runCatching { XrayEngine.stop() }
        runCatching { SingBoxEngine.stop() }
        runCatching { AwgEngine.stop() }
        runCatching { tunnelFd?.close() }
        tunnelFd = null
    }

    private fun stopCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** System revoked the VPN (always-on off / another VPN took over). */
    override fun onRevoke() {
        scope.launch { shutdown() }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        if (activeInstance === this) {
            activeInstance = null
            shutdownEngines()
        }
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notification

    private fun startAsForeground(context: android.content.Context) {
        Notifications.ensureChannels(context)
        val title =
            when (VpnController.state.value.status) {
                VpnStatus.CONNECTED -> {
                    context.getString(
                        R.string.connected_to,
                        VpnController.state.value.profileName ?: "VPN",
                    )
                }

                else -> {
                    context.getString(R.string.notif_connecting)
                }
            }
        val notification = Notifications.vpnNotification(context, title, null)
        val type =
            if (Build.VERSION.SDK_INT >= 34) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        ServiceCompat.startForeground(this, Notifications.ID_VPN, notification, type)
    }

    private fun updateNotification(speedLine: String? = null) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val s = VpnController.state.value
        val title =
            when (s.status) {
                VpnStatus.CONNECTED -> getString(R.string.connected_to, s.profileName ?: "VPN")
                VpnStatus.CONNECTING -> getString(R.string.notif_connecting)
                else -> getString(R.string.app_name)
            }
        nm.notify(Notifications.ID_VPN, Notifications.vpnNotification(this, title, speedLine))
    }
}
