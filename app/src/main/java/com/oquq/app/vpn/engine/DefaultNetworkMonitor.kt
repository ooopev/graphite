package com.oquq.app.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.nekohasekai.libbox.InterfaceUpdateListener

/**
 * Feeds libbox's auto_detect_interface with the current default network:
 * wifi ↔ cellular switches rebind the core's sockets immediately.
 */
class DefaultNetworkMonitor(
    context: Context,
) {
    @Volatile
    var listener: InterfaceUpdateListener? = null

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = report()

            override fun onLost(network: Network) = report()

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) = report()
        }

    private fun report() {
        val l = listener ?: return
        runCatching {
            val network = cm.activeNetwork ?: return
            val caps = cm.getNetworkCapabilities(network) ?: return
            val name = cm.getLinkProperties(network)?.interfaceName ?: return
            val type =
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        io.nekohasekai.libbox.Libbox.InterfaceTypeWIFI
                    }

                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        io.nekohasekai.libbox.Libbox.InterfaceTypeCellular
                    }

                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        io.nekohasekai.libbox.Libbox.InterfaceTypeEthernet
                    }

                    else -> {
                        io.nekohasekai.libbox.Libbox.InterfaceTypeOther
                    }
                }
            val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            l.updateDefaultInterface(name, type, metered, true)
        }
    }

    init {
        cm.registerNetworkCallback(
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        report()
    }

    fun close() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}
