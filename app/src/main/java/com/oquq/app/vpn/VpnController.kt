package com.oquq.app.vpn

import android.content.Context
import android.content.Intent
import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnStatus { IDLE, CONNECTING, CONNECTED, DISCONNECTING }

data class VpnState(
    val status: VpnStatus = VpnStatus.IDLE,
    val profileName: String? = null,
    val profileId: Long = -1L,
    val engine: Protocol.Engine? = null,
    /** Cumulative counters; the UI derives speed from deltas. */
    val upBytes: Long = 0L,
    val downBytes: Long = 0L,
    val error: String? = null,
)

/**
 * Single source of truth for VPN state. The service mutates it; everything
 * else (UI, tile, boot receiver) reads the flow and sends commands.
 */
object VpnController {
    private val _state = MutableStateFlow(VpnState())
    val state: StateFlow<VpnState> = _state.asStateFlow()

    /** Set by OquqVpnService only. */
    fun update(transform: (VpnState) -> VpnState) {
        _state.value = transform(_state.value)
    }

    fun reset(profileId: Long = _state.value.profileId) {
        _state.value = VpnState(profileId = profileId)
    }

    // ---- commands (fire-and-forget intents; the service owns the lifecycle) ----

    fun connect(
        context: Context,
        profile: Profile,
    ) {
        val intent =
            Intent(context, OquqVpnService::class.java)
                .setAction(OquqVpnService.ACTION_CONNECT)
                .putExtra(OquqVpnService.EXTRA_PROFILE_ID, profile.id)
        context.startForegroundService(intent)
    }

    fun disconnect(context: Context) {
        context.startService(
            Intent(context, OquqVpnService::class.java).setAction(OquqVpnService.ACTION_DISCONNECT),
        )
    }

    fun toggle(
        context: Context,
        selected: Profile?,
    ) {
        val s = state.value
        if (s.status == VpnStatus.CONNECTED || s.status == VpnStatus.CONNECTING) {
            disconnect(context)
        } else if (selected != null) {
            connect(context, selected)
        }
    }
}
