package com.graphite.app.core.net

import com.graphite.app.core.model.Profile
import com.graphite.app.core.repo.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Latency = cold TCP handshake time to the server (ms), the industry-standard
 * "ping" column in proxy clients. Zero traffic beyond the SYN/SYN-ACK, so
 * testing dozens of servers is cheap on both battery and quota.
 */
object LatencyTester {
    private const val TIMEOUT_MS = 4000L

    suspend fun test(profile: Profile): Int? =
        withTimeout(TIMEOUT_MS + 500) {
            val start = System.nanoTime()
            runCatching {
                with(Dispatchers.IO) {
                    Socket().use { s ->
                        s.soTimeout = TIMEOUT_MS.toInt()
                        s.connect(InetSocketAddress(profile.server, profile.port), TIMEOUT_MS.toInt())
                    }
                }
            }.getOrElse { return@withTimeout null }
            ((System.nanoTime() - start) / 1_000_000).toInt()
        }

    /** Parallel batch test; persists results into Room. */
    suspend fun testAll(
        repo: ProfileRepository,
        profiles: List<Profile>,
    ) = coroutineScope {
        profiles
            .map { p ->
                async(Dispatchers.IO) {
                    val latency = test(p)
                    repo.update(p.copy(latencyMs = latency, lastTestedAt = System.currentTimeMillis()))
                    p.id to latency
                }
            }.awaitAll()
            .toMap()
    }
}
