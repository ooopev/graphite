package com.oquq.app.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
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

    /** Pushes the current non-VPN default network to the core right away. */
    fun reportNow() {
        listener?.let { report() }
    }

    private fun report() {
        val l = listener ?: return
        runCatching {
            // Never report the VPN itself as the default interface: once the
            // tun is up, activeNetwork IS the VPN and the core would try to
            // send its outbounds into its own tunnel ("no available network
            // interface" on every connection).
            val caps0 = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val network =
                if (caps0 != null && !caps0.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    cm.activeNetwork
                } else {
                    cm.allNetworks.firstOrNull { n ->
                        cm.getNetworkCapabilities(n)?.let {
                            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        } == true
                    }
                } ?: return
            val caps = cm.getNetworkCapabilities(network) ?: return
            val lp = cm.getLinkProperties(network) ?: return
            val name = lp.interfaceName ?: return
            // libbox expects the kernel interface index here — the same value
            // getInterfaces() reports via java.net.NetworkInterface.getIndex(),
            // which is what the core's InterfaceFinder matches against. The
            // interface *type* is derived by the core itself.
            val index =
                runCatching { java.net.NetworkInterface.getByName(name)?.index }.getOrNull() ?: return
            val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val constrained =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED)
            l.updateDefaultInterface(name, index, metered, constrained)
        }
    }

    init {
        cm.registerNetworkCallback(
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                // VPN networks satisfy INTERNET too; we never want their events.
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            callback,
        )
        report()
    }

    fun close() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}
