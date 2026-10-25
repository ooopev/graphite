package com.oquq.app.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Process
import android.system.OsConstants
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.SocketException
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

/**
 * libbox 1.14 PlatformInterface implementation for oQuQ — the compact
 * sibling of SFA's PlatformInterfaceWrapper: TUN building, socket
 * protection, interface enumeration and a system-resolver DNS transport;
 * root shell / neighbor-table / WIFI extras are intentionally absent.
 */
class BoxPlatformService(
    private val vpnService: VpnService,
    private val networkMonitor: DefaultNetworkMonitor,
) : PlatformInterface {
    // ---- TUN & interface control ----

    override fun openTun(options: TunOptions?): Int {
        val o = options ?: throw IllegalStateException("null TunOptions")
        val builder =
            vpnService
                .Builder()
                .setSession("oQuQ")
                .setBlocking(true)
                .setMtu(o.getMTU())

        fun prefixes(iterator: io.nekohasekai.libbox.RoutePrefixIterator?): List<Pair<String, Int>> {
            val out = mutableListOf<Pair<String, Int>>()
            while (iterator?.hasNext() == true) {
                val p = iterator.next()
                out += p.address() to p.prefix()
            }
            return out
        }

        prefixes(o.inet4Address).forEach { (addr, prefix) -> builder.addAddress(addr, prefix) }
        prefixes(o.inet6Address).forEach { (addr, prefix) -> builder.addAddress(addr, prefix) }
        // Empty route lists = default route (matches our auto_route config).
        val v4Routes = prefixes(o.inet4RouteAddress)
        val v6Routes = prefixes(o.inet6RouteAddress)
        if (v4Routes.isEmpty()) builder.addRoute("0.0.0.0", 0)
        v4Routes.forEach { (addr, prefix) -> builder.addRoute(addr, prefix) }
        if (v6Routes.isEmpty()) builder.addRoute("::", 0)
        v6Routes.forEach { (addr, prefix) -> builder.addRoute(addr, prefix) }

        val dns = mutableListOf<String>()
        val dnsIter = o.getDNSServerAddress()
        while (dnsIter?.hasNext() == true) dns += dnsIter.next()
        if (dns.isEmpty()) dns += "172.19.0.2" // derived fake DNS (tun addr + 1), hijacked by the core
        dns.forEach { builder.addDnsServer(it) }

        // Per-app routing from OverrideOptions must reach the system too:
        // the core's own UID filtering is not enough for sockets created by
        // other engines sharing this process (the Xray SOCKS hop).
        val include = o.includePackage
        while (include?.hasNext() == true) builder.addAllowedApplication(include.next())
        val exclude = o.excludePackage
        while (exclude?.hasNext() == true) builder.addDisallowedApplication(exclude.next())

        val pfd = builder.establish() ?: throw IllegalStateException("VPN permission revoked or establish failed")
        val fd = pfd.detachFd()
        // libbox dups this fd and only ever closes the dup; the original is
        // ours to close. While it stays open the kernel keeps the tun and
        // Android keeps the VPN agent alive — "disconnected" but still on.
        TunFdRegistry.register(fd)
        return fd
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        // The core asks us to protect its default-interface socket.
        if (!vpnService.protect(fd)) throw IllegalStateException("protect($fd) failed")
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun registerMyInterface(name: String?) {
        // Reserved for the core to tag our tun; nothing to do.
    }

    override fun clearDNSCache() {}

    // ---- default interface monitoring ----

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        networkMonitor.listener = listener
        // The core asks for the monitor only while (re)starting; without this
        // immediate push it stays on "missing default interface" until the
        // next connectivity event — dead air right after connect.
        if (listener != null) networkMonitor.reportNow()
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        networkMonitor.listener = null
    }

    // ---- interface enumeration (compact port of SFA's getInterfaces) ----

    override fun getInterfaces(): NetworkInterfaceIterator {
        val cm = vpnService.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val osInterfaces =
            try {
                java.net.NetworkInterface
                    .getNetworkInterfaces()
                    ?.toList()
                    .orEmpty()
            } catch (_: SocketException) {
                emptyList()
            }
        val interfaces = mutableListOf<LibboxNetworkInterface>()
        for (network in cm.allNetworks) {
            val lp = cm.getLinkProperties(network) ?: continue
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val name = lp.interfaceName ?: continue
            val os = osInterfaces.firstOrNull { it.name == name } ?: continue
            interfaces +=
                LibboxNetworkInterface().apply {
                    this.name = name
                    index = os.index
                    runCatching { setMTU(os.mtu) }
                    setDNSServer(
                        StringIteratorImpl(
                            lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }.orEmpty(),
                        ),
                    )
                    type =
                        when {
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                            else -> Libbox.InterfaceTypeOther
                        }
                    var flags = 0
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                    }
                    if (os.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
                    if (os.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
                    this.flags = flags
                    metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    addresses = StringIteratorImpl(os.interfaceAddresses.map { it.toPrefix() })
                }
        }
        return object : NetworkInterfaceIterator {
            private val iter = interfaces.iterator()

            override fun hasNext() = iter.hasNext()

            override fun next(): LibboxNetworkInterface = iter.next()
        }
    }

    private fun InterfaceAddress.toPrefix(): String {
        val bits = if (address is Inet6Address) 128 else 32
        // prefixLength counts contiguous bits; reconstruct CIDR from address+mask.
        // Strip the IPv6 zone (%wlan0): libbox parses each entry with
        // netip.MustParsePrefix, which panics — and kills the whole process —
        // when the prefix carries a zone.
        val host = address.hostAddress?.substringBefore('%') ?: return ""
        return "$host/${networkPrefixLength.toInt().coerceIn(0, bits)}"
    }

    // ---- connection owner (process info for routing rules) ----

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        val owner =
            Libbox.findConnectionOwner(
                ipProtocol,
                sourceAddress,
                sourcePort,
                destinationAddress,
                destinationPort,
            )
        val packages = vpnService.packageManager.getPackagesForUid(owner.userId)
        owner.userName = packages?.firstOrNull() ?: ""
        owner.setAndroidPackageNames(StringIteratorImpl(packages?.toList().orEmpty()))
        return owner
    }

    // ---- local DNS ----

    override fun localDNSTransport(): LocalDNSTransport = SystemDnsTransport

    override fun readWIFIState(): WIFIState? = null // needs location permission; not used by oQuQ configs

    // ---- notifications from the core ----

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification?) {
        // Core notifications (profile updates etc.) are intentionally muted.
    }

    override fun cancelNotification(
        identifier: String?,
        id: Int,
    ) {}

    // ---- neighbor table (root-only in SFA; unsupported here) ----

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}

    // ---- shell / bridge / extras — unsupported, root not required by oQuQ ----

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell(): Unit = throw UnsupportedOperationException("root shell is not supported")

    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int,
    ): ShellSession = throw UnsupportedOperationException("root shell is not supported")

    override fun lookupUser(username: String?): PlatformUser? = null

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession = throw UnsupportedOperationException("bridge is not supported")

    override fun readSystemSSHHostKey(): String = ""

    override fun lookupSFTPServer(): String = ""

    override fun tailscaleHostname(): String = ""
}

/**
 * Minimal "local" DNS transport: hostname lookups go through the Android
 * system resolver (netd). Enough for resolving core's own outbound domains;
 * raw DNS exchange is refused (our configs never request it).
 */
object SystemDnsTransport : LocalDNSTransport {
    override fun raw(): Boolean = false

    override fun exchange(
        context: ExchangeContext?,
        query: ByteArray?,
    ) {
        context?.errorCode(1) // EPERM-like: raw exchange unsupported
    }

    override fun lookup(
        context: ExchangeContext?,
        domain: String?,
        strategy: String?,
    ) {
        if (context == null || domain.isNullOrBlank()) return
        try {
            val answers =
                InetAddress
                    .getAllByName(domain)
                    .mapNotNull { it.hostAddress }
                    .distinct()
            context.success(answers.firstOrNull() ?: "")
        } catch (e: Exception) {
            context.errorCode(2) // NXDOMAIN-ish
        }
    }
}
