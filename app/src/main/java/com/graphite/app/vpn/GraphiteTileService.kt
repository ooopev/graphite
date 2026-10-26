package com.graphite.app.vpn

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.graphite.app.core.repo.ProfileRepository
import com.graphite.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Quick Settings tile: tap to toggle the last/selected server.
 * Reflects [VpnController.state] — no separate state machinery.
 */
class GraphiteTileService : TileService() {
    override fun onStartListening() {
        syncTile()
    }

    override fun onClick() {
        val state = VpnController.state.value
        if (state.status == VpnStatus.CONNECTED || state.status == VpnStatus.CONNECTING) {
            VpnController.disconnect(this)
        } else {
            // Selected profile (or the most recently added) — tile has no UI to pick.
            val selected =
                runBlocking {
                    val settings = SettingsRepository(applicationContext).current()
                    val repo = ProfileRepository.get(applicationContext)
                    repo.profileOnce(settings.selectedProfileId)
                        ?: repo.profiles().first().firstOrNull()
                }
            if (selected != null) VpnController.connect(this, selected)
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        when (VpnController.state.value.status) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
            }

            else -> {
                tile.state = Tile.STATE_INACTIVE
            }
        }
        tile.updateTile()
    }
}
