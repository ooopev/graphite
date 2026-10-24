package com.oquq.app.vpn

import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import com.oquq.app.R
import com.oquq.app.core.config.AwgConfigBuilder
import com.oquq.app.core.config.SingBoxConfigBuilder
import com.oquq.app.core.config.XrayConfigBuilder
import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.ProfileParams
import com.oquq.app.core.model.Protocol
import com.oquq.app.core.parser.LinkParser
import com.oquq.app.core.repo.ProfileRepository
import com.oquq.app.data.AppSettings
import com.oquq.app.data.PerAppMode
import com.oquq.app.data.SettingsRepository
import com.oquq.app.ui.util.formatBytes
import com.oquq.app.vpn.engine.AwgEngine
import com.oquq.app.vpn.engine.SingBoxEngine
import com.oquq.app.vpn.engine.XrayEngine
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
class OquqVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.oquq.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.oquq.app.action.DISCONNECT"
        const val EXTRA_PROFILE_ID = "profile_id"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statsJob: Job? = null
    private var tunnelFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appContext = applicationContext
        startAsForeground(appContext)
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
                scope.launch { connectTo(profileId) }
            }
            ACTION_DISCONNECT -> scope.launch { shutdown() }
            else -> {
                // No action = the system (re)started us — always-on VPN path.
                scope.launch {
                    val selected = SettingsRepository(appContext).current().selectedProfileId
                    if (selected > 0) connectTo(selected)
                    else shutdown()
                }
            }
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ connect

    private suspend fun connectTo(profileId: Long) {
        val repo = ProfileRepository.get(this)
        val settings = SettingsRepository(this).current()

        if (VpnController.state.value.status == VpnStatus.CONNECTED ||
            VpnController.state.value.status == VpnStatus.CONNECTING
        ) {
            shutdownEngines()
        }

        val profile = if (profileId > 0) repo.profileOnce(profileId)
        else repo.profileOnce(settings.selectedProfileId)

        if (profile == null) {
            VpnController.update { it.copy(status = VpnStatus.IDLE, error = null) }
            stopCleanly()
            return
        }

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

        val connected = runCatching {
            when (engine) {
                Protocol.Engine.SING_BOX -> {
                    SingBoxEngine.start(this, SingBoxConfigBuilder.build(profile, settings), settings)
                    true
                }
                Protocol.Engine.XRAY -> {
                    // Xray serves the protocol; sing-box still owns the TUN and chains to it.
                    if (!XrayEngine.start(this, XrayConfigBuilder.build(profile)))
                        error("Xray core rejected the config")
                    SingBoxEngine.start(this, SingBoxConfigBuilder.build(profile, settings), settings)
                    true
                }
                Protocol.Engine.AWG -> connectAwg(profile, params, settings)
            }
        }.getOrElse { e ->
            VpnController.update { it.copy(status = VpnStatus.IDLE, error = e.message) }
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

    private fun connectAwg(profile: Profile, params: ProfileParams, settings: AppSettings): Boolean {
        val builder = Builder()
            .setSession("oQuQ")
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
                    PerAppMode.INCLUDE ->
                        settings.perAppPackages.forEach { builder.addAllowedApplication(it) }
                    PerAppMode.EXCLUDE ->
                        settings.perAppPackages.forEach { builder.addDisallowedApplication(it) }
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

    /**
     * 1 Hz stats pump. The notification refresh carries negligible cost (text
     * only, IMPORTANCE_LOW); the UI samples the same flow and pauses with the
     * lifecycle, so nothing ticks while the screen is off beyond this light
     * notification update, which users expect from a VPN app.
     */
    private fun startStatsLoop(engine: Protocol.Engine) {
        statsJob?.cancel()
        val uid = android.os.Process.myUid()
        val baseRx = TrafficStats.getUidRxBytes(uid)
        val baseTx = TrafficStats.getUidTxBytes(uid)
        statsJob = scope.launch {
            while (isActive) {
                val (up, down) = when (engine) {
                    Protocol.Engine.AWG ->
                        (TrafficStats.getUidTxBytes(uid) - baseTx) to
                            (TrafficStats.getUidRxBytes(uid) - baseRx)
                    else -> SingBoxEngine.queryStats()
                }
                VpnController.update { it.copy(upBytes = up, downBytes = down) }
                updateNotification(speedLine = formatBytes(down) + " ▼  " + formatBytes(up) + " ▲")
                delay(1000)
            }
        }
    }

    // ------------------------------------------------------------------ shutdown

    private suspend fun shutdown() {
        VpnController.update { it.copy(status = VpnStatus.DISCONNECTING) }
        shutdownEngines()
        stopCleanly()
        VpnController.reset()
    }

    private fun shutdownEngines() {
        statsJob?.cancel(); statsJob = null
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
        shutdownEngines()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notification

    private fun startAsForeground(context: android.content.Context) {
        Notifications.ensureChannels(context)
        val title = when (VpnController.state.value.status) {
            VpnStatus.CONNECTED -> context.getString(
                R.string.connected_to, VpnController.state.value.profileName ?: "VPN",
            )
            else -> context.getString(R.string.notif_connecting)
        }
        val notification = Notifications.vpnNotification(context, title, null)
        val type = if (Build.VERSION.SDK_INT >= 34)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_VPN, notification, type)
    }

    private fun updateNotification(speedLine: String? = null) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val s = VpnController.state.value
        val title = when (s.status) {
            VpnStatus.CONNECTED -> getString(R.string.connected_to, s.profileName ?: "VPN")
            VpnStatus.CONNECTING -> getString(R.string.notif_connecting)
            else -> getString(R.string.app_name)
        }
        nm.notify(Notifications.ID_VPN, Notifications.vpnNotification(this, title, speedLine))
    }
}
