package com.oquq.app.vpn.engine

import android.os.ParcelFileDescriptor
import android.system.Os
import java.util.concurrent.CopyOnWriteArrayList

/**
 * libbox dups the fd we hand over in openTun and closes only its dup when
 * the service stops — the original stays open forever. An open /dev/tun fd
 * keeps the kernel interface up, and with it Android's VPN agent and the
 * system's binding to our VpnService: the app looks disconnected while the
 * VPN still routes traffic. We track the originals and close them on stop.
 */
internal object TunFdRegistry {
    private val fds = CopyOnWriteArrayList<Int>()

    fun register(fd: Int) {
        fds.add(fd)
    }

    fun closeAll() {
        while (fds.isNotEmpty()) {
            val fd = fds.removeAt(fds.size - 1)
            runCatching {
                // Close only what still is a tun fd: if the number was
                // recycled by another allocation, closing it would kill an
                // unrelated socket.
                if (Os.readlink("/proc/self/fd/$fd") == "/dev/tun") {
                    ParcelFileDescriptor.adoptFd(fd).close()
                }
            }
        }
    }
}
