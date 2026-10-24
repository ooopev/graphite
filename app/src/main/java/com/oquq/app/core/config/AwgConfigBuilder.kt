package com.oquq.app.core.config

import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.ProfileParams
import com.oquq.app.core.parser.LinkParser

/**
 * Builds the userspace WireGuard settings string consumed by the AmneziaWG
 * engine (wireguard-go fork, same wire protocol as WireGuard-Android GoBackend).
 *
 * Keys are converted to hex — the userspace IPC format requires it.
 * AmneziaWG junk-packet parameters (Ic/Jc/Jmin/Jmax/S1/S2/H1–H4) are appended
 * after the standard block; the amnezia-wg Go core understands both forms.
 */
object AwgConfigBuilder {
    fun build(profile: Profile): String {
        val p = LinkParser.paramsFromJson(profile.params)
        return buildString {
            appendLine("private_key=${p.wgPrivateKey?.toHexOrNull() ?: return ""}")
            appendLine("replace_peers=true")
            appendLine("public_key=${p.wgPeerPublicKey?.toHexOrNull() ?: return ""}")
            p.wgPsk?.let { appendLine("preshared_key=${it.toHexOrNull() ?: return ""}") }
            appendLine("persistent_keepalive_interval=25")
            appendLine("replace_allowed_ips=true")
            appendLine("allowed_ip=0.0.0.0/0")
            appendLine("allowed_ip=::/0")
            appendLine("endpoint=${profile.server}:${profile.port}")
            appendLine("protocol_version=1")
            // AmneziaWG obfuscation
            p.awgIc?.let { appendLine("Ic=$it") }
            p.awgJc?.let { appendLine("Jc=$it") }
            p.awgJmin?.let { appendLine("Jmin=$it") }
            p.awgJmax?.let { appendLine("Jmax=$it") }
            p.awgS1?.let { appendLine("S1=$it") }
            p.awgS2?.let { appendLine("S2=$it") }
            p.awgH1?.let { appendLine("H1=$it") }
            p.awgH2?.let { appendLine("H2=$it") }
            p.awgH3?.let { appendLine("H3=$it") }
            p.awgH4?.let { appendLine("H4=$it") }
        }.trim()
    }

    /** base64 (standard or url-safe) WireGuard key → lowercase hex. */
    private fun String.toHexOrNull(): String? =
        runCatching {
            val normalized =
                replace('-', '+')
                    .replace('_', '/')
                    .let { if (it.length % 4 == 0) it else it + "=".repeat(4 - it.length % 4) }
            java.util.Base64
                .getMimeDecoder()
                .decode(normalized)
                .joinToString("") { "%02x".format(it) }
        }.getOrNull()
}
