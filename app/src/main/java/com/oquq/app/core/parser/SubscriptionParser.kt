package com.oquq.app.core.parser

import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class SubscriptionUpdate(
    val profiles: List<Profile>,
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long?,
)

/** Fetches and parses subscription payloads (base64 or plain link lists). */
object SubscriptionParser {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .build()

    suspend fun fetch(url: String): Result<SubscriptionUpdate> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", "oQuQ/1.0 (Android; VPN client)")
                        .build()
                client.newCall(request).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code}" }
                    val body = resp.body?.string().orEmpty()
                    val info =
                        resp
                            .header("subscription-userinfo")
                            .orEmpty()
                            .split(';')
                            .mapNotNull { part ->
                                val k = part.substringBefore('=').trim()
                                val v = part.substringAfter('=', "").trim().toLongOrNull() ?: return@mapNotNull null
                                k to v
                            }.toMap()

                    SubscriptionUpdate(
                        profiles = parseBody(body),
                        upload = info["upload"] ?: 0L,
                        download = info["download"] ?: 0L,
                        total = info["total"] ?: 0L,
                        expire = info["expire"]?.takeIf { it > 0 },
                    )
                }
            }
        }

    /** Accepts base64-encoded or plain lists of links, one per line. */
    fun parseBody(body: String): List<Profile> {
        // Providers commonly prepend an UTF-8 BOM and wrap the payload in
        // whitespace. Keep the original lines after removing those bytes;
        // otherwise the first link is skipped and the imported profile can
        // produce an empty/invalid engine config.
        val text = body.trim().removePrefix("\uFEFF").trim()
        val decoded =
            when {
                text.startsWith("vless://", true) || text.startsWith("ss://", true) ||
                    text.startsWith("vmess://", true) || text.startsWith("trojan://", true) ||
                    text.startsWith("hysteria2://", true) || text.startsWith("hy2://", true) ||
                    text.startsWith("tuic://", true) || text.startsWith("vpn://", true) -> {
                    text
                }

                else -> {
                    runCatching {
                        val norm =
                            text
                                .filterNot { it == '\uFEFF' }
                                .replace('-', '+')
                                .replace('_', '/')
                                .let { if (it.length % 4 == 0) it else it + "=".repeat(4 - it.length % 4) }
                        String(
                            java.util.Base64
                                .getMimeDecoder()
                                .decode(norm),
                            Charsets.UTF_8,
                        )
                    }.getOrElse { text }
                }
            }
        return decoded
            .removePrefix("\uFEFF")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { LinkParser.parse(it) }
            .distinctBy { it.rawLink }
            .toList()
    }

    /** Applies a fresh fetch on top of an existing subscription row. */
    fun applyToSubscription(
        sub: Subscription,
        update: SubscriptionUpdate,
    ): Subscription =
        sub.copy(
            lastUpdate = System.currentTimeMillis(),
            upload = update.upload,
            download = update.download,
            total = update.total,
            expire = update.expire,
        )
}
