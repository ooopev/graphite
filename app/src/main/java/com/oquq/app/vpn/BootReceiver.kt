package com.oquq.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oquq.app.core.repo.ProfileRepository
import com.oquq.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Starts the VPN after boot if the user opted in (default off — battery-friendly). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                val settings = SettingsRepository(context).current()
                if (settings.autoStartOnBoot && settings.selectedProfileId > 0) {
                    val profile =
                        ProfileRepository
                            .get(context)
                            .profileOnce(settings.selectedProfileId)
                    if (profile != null) VpnController.connect(context, profile)
                }
            } finally {
                result.finish()
            }
        }
    }
}
