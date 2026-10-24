package com.oquq.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.oquq.app.MainActivity
import com.oquq.app.R

object Notifications {
    const val CHANNEL_VPN = "vpn_status"
    const val ID_VPN = 1

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_VPN,
                context.getString(R.string.notif_channel_vpn),
                NotificationManager.IMPORTANCE_LOW, // no sound: battery + UX
            ).apply {
                description = context.getString(R.string.notif_channel_vpn_desc)
                setShowBadge(false)
            },
        )
    }

    fun vpnNotification(
        context: Context,
        title: String,
        speedLine: String?,
    ): Notification {
        ensureChannels(context)
        val content = Intent(context, MainActivity::class.java)
        val contentPi =
            PendingIntent.getActivity(
                context,
                0,
                content,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopPi =
            PendingIntent.getService(
                context,
                1,
                Intent(context, OquqVpnService::class.java).setAction(OquqVpnService.ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(speedLine ?: "")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentPi)
            .addAction(0, context.getString(R.string.notif_stop), stopPi)
            .build()
    }
}
