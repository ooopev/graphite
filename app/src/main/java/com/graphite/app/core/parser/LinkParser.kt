package com.graphite.app.core.parser

import com.graphite.app.core.model.Profile
import com.graphite.app.core.model.ProfileParams
import com.graphite.app.core.model.Protocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses every connection format Graphite accepts:
 * vless:// vmess:// trojan:// ss:// hysteria2:// hy2:// tuic:// socks:// http://
 * vpn:// (Amnezia backup), raw WireGuard/AmneziaWG INI configs.
 *
 * Output is always a [Profile] with [ProfileParams] serialized to JSON.
 */
object LinkParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun paramsToJson(p: ProfileParams): String = json.encodeToString(ProfileParams.serializer(), p)

    fun paramsFromJson(s: String): ProfileParams =
        runCatching { json.decodeFromString(ProfileParams.serializer(), s) }
            .getOrDefault(ProfileParams())

    fun parse(raw: String): Profile? {
        val s = raw.trim()
        return runCatching {
            when {
                s.startsWith("vless://", true) -> {
                    parseVless(s)
                }

                s.startsWith("vmess://", true) -> {
                    parseVmess(s)
                }

                s.startsWith("trojan://", true) -> {
                    parseTrojan(s)
                }

                s.startsWith("ss://", true) -> {
                    parseShadowsocks(s)
                }

                s.startsWith("hysteria2://", true) || s.startsWith("hy2://", true) -> {
                    parseHysteria2(s)
                }

                s.startsWith("tuic://", true) -> {
                    parseTuic(s)
                }

                s.startsWith("vpn://", true) -> {
                    parseAmneziaBackup(s)
                }

                s.startsWith("socks://", true) || s.startsWith("socks5://", true) -> {
                    parseSocksHttp(s, Protocol.SOCKS)
                }

                // http:// proxy links always carry userinfo: http://user:pass@host:port
                s.startsWith("http://", true) && s.substringAfter("://").contains('@') -> {
                    parseSocksHttp(s, Protocol.HTTP)
                }

                s.contains("[Interface]") -> {
                    parseWgIni(s, forcedName = null)
                }

                else -> {
                    null
                }
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- vless

    private fun parseVless(link: String): Profile? {
        val uri = URI(link) ?: return null
        val q = uri.queryMap()
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val transport = q["type"]?.lowercase() ?: "tcp"
        val params =
            ProfileParams(
                uuid = uri.userInfo,
                transport = transport,
                transportPath = q["path"]?.decode(),
                transportHost = q["host"]?.decode(),
                transportServiceName = q["serviceName"]?.decode(),
                sni = (q["sni"] ?: q["peer"] ?: q["host"])?.decode(),
                alpn =
                    q["alpn"]
                        ?.decode()
                        ?.split(',')
                        ?.map { it.trim() }
                        .orEmpty(),
                fingerprint = q["fp"],
                allowInsecure = q.bool("allowInsecure") || q.bool("insecure"),
                flow = q["flow"]?.takeIf { it.isNotBlank() },
                realityPbk = q["pbk"],
                realitySid = q["sid"],
                realitySpx = q["spx"]?.decode(),
                xhttpMode = q["mode"]?.takeIf { transport.equals("xhttp", true) },
            )
        return Profile(
            name = uri.fragment?.decode()?.ifBlank { null } ?: "$host:$port",
            protocol = Protocol.VLESS,
            server = host,
            port = port,
            rawLink = link,
            params = paramsToJson(params),
        )
    }

    // ---------------------------------------------------------------- vmess

    private fun parseVmess(link: String): Profile? {
        val decoded = link.substringAfter("vmess://").decodeBase64OrNull()?.toStringUtf8() ?: return null
        val o = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
        val host = o.str("add") ?: return null
        val port = o.str("port")?.toIntOrNull() ?: return null
        val params =
            ProfileParams(
                uuid = o.str("id"),
                encryption = o.str("scy") ?: "auto",
                transport = o.str("net") ?: "tcp",
                transportPath = o.str("path")?.substringBefore('?')?.ifBlank { null },
                transportHost = o.str("host"),
                headerType = o.str("type"),
                sni = o.str("sni") ?: o.str("host"),
                alpn =
                    o
                        .str("alpn")
                        ?.split(',')
                        ?.map { it.trim() }
                        .orEmpty(),
                fingerprint = o.str("fp"),
            )
        return Profile(
            name = o.str("ps")?.ifBlank { null } ?: "$host:$port",
            protocol = Protocol.VMESS,
            server = host,
            port = port,
            rawLink = link,
            params = paramsToJson(params),
        )
    }

    // ---------------------------------------------------------------- trojan

    private fun parseTrojan(link: String): Profile? {
        val uri = URI(link) ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val q = uri.queryMap()
        val params =
            ProfileParams(
                password = uri.userInfo?.decode(),
                transport = q["type"]?.lowercase() ?: "tcp",
                transportPath = q["path"]?.decode(),
                transportHost = q["host"]?.decode(),
                transportServiceName = q["serviceName"]?.decode(),
                sni = (q["sni"] ?: q["peer"])?.decode(),
                alpn =
                    q["alpn"]
                        ?.decode()
                        ?.split(',')
                        ?.map { it.trim() }
                        .orEmpty(),
                fingerprint = q["fp"],
                allowInsecure = q.bool("allowInsecure") || q.bool("insecure"),
            )
        return Profile(
            name = uri.fragment?.decode()?.ifBlank { null } ?: "$host:$port",
            protocol = Protocol.TROJAN,
            server = host,
            port = port,
            rawLink = link,
            params = paramsToJson(params),
        )
    }

    // ---------------------------------------------------------------- shadowsocks

    private fun parseShadowsocks(link: String): Profile? {
        val main = link.substringAfter("ss://").substringBefore('#')
        val name = link.substringAfter('#', "").decode().ifBlank { null }
        // Form A: userinfo@host:port — SIP002 (base64 userinfo) or SS2022 (plaintext creds)
        val at = main.lastIndexOf('@')
        if (at > 0) {
            val userinfo = main.substring(0, at)
            val cred = userinfo.decodeBase64OrNull()?.toStringUtf8() ?: userinfo.decode()
            val (host, port) = main.substring(at + 1).splitHostPort() ?: return null
            return ssProfile(link, name, host, port, cred.substringBefore(':'), cred.substringAfter(':', ""))
        }
        // Form B: whole payload is base64("method:pass@host:port")
        val decoded = main.decodeBase64OrNull()?.toStringUtf8() ?: return null
        val cred = decoded.substringBefore('@')
        val (host, port) = decoded.substringAfter('@').splitHostPort() ?: return null
        return ssProfile(link, name, host, port, cred.substringBefore(':'), cred.substringAfter(':', ""))
    }

    private fun ssProfile(
        link: String,
        name: String?,
        host: String,
        port: Int,
        method: String,
        password: String,
    ) = Profile(
        name = name ?: "$host:$port",
        protocol = Protocol.SHADOWSOCKS,
        server = host,
        port = port,
        rawLink = link,
        params = paramsToJson(ProfileParams(method = method, password = password)),
    )

    // ---------------------------------------------------------------- hysteria2 / tuic

    private fun parseHysteria2(link: String): Profile? {
        val uri = URI(link) ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val q = uri.queryMap()
        val params =
            ProfileParams(
                password = uri.userInfo?.decode(),
                sni = q["sni"]?.decode(),
                alpn =
                    q["alpn"]
                        ?.decode()
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.takeIf { it.isNotEmpty() } ?: listOf("h3"),
                allowInsecure = q.bool("insecure") || q.bool("allowInsecure"),
                hy2Obfs = q["obfs"],
                hy2ObfsPassword = q["obfs-password"]?.decode(),
                upMbps = q["upmbps"]?.toIntOrNull() ?: q["up"]?.trimEnd('m', 'M', 'b', 'p', 's')?.toIntOrNull(),
                downMbps = q["downmbps"]?.toIntOrNull() ?: q["down"]?.trimEnd('m', 'M', 'b', 'p', 's')?.toIntOrNull(),
            )
        return Profile(
            name = uri.fragment?.decode()?.ifBlank { null } ?: "$host:$port",
            protocol = Protocol.HYSTERIA2,
            server = host,
            port = port,
            rawLink = link,
            params = paramsToJson(params),
        )
    }

    private fun parseTuic(link: String): Profile? {
        val uri = URI(link) ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val q = uri.queryMap()
        val userInfo = uri.userInfo ?: return null
        val params =
            ProfileParams(
                uuid = userInfo.substringBefore(':').decode(),
                password = userInfo.substringAfter(':', "").decode().ifBlank { null },
                sni = q["sni"]?.decode(),
                alpn =
                    q["alpn"]
                        ?.decode()
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.takeIf { it.isNotEmpty() } ?: listOf("h3"),
                allowInsecure = q.bool("allow_insecure") || q.bool("insecure"),
                congestionControl = q["congestion_control"],
                udpRelayMode = q["udp_relay_mode"],
            )
        return Profile(
            name = uri.fragment?.decode()?.ifBlank { null } ?: "$host:$port",
            protocol = Protocol.TUIC,
            server = host,
            port = port,
            rawLink = link,
            params = paramsToJson(params),
        )
    }

    // ---------------------------------------------------------------- socks / http

    private fun parseSocksHttp(
        link: String,
        protocol: Protocol,
    ): Profile? {
        val uri = URI(link) ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: if (protocol == Protocol.SOCKS) 1080 else 8080
        val params =
            ProfileParams(
                username =
                    uri.userInfo
                        ?.substringBefore(':')
                        ?.decode()
                        ?.ifBlank { null },
                password =
                    uri.userInfo
                        ?.substringAfter(':', "")
                        ?.decode()
                        ?.ifBlank { null },
            )
        return Profile(
            name = uri.fragment?.decode()?.ifBlank { null } ?: "$host:$port",
            protocol = protocol,
            server = host,
            port = port,
            rawLink = link,
            params = paramsToJson(params),
        )
    }

    // ---------------------------------------------------------------- wireguard / amneziawg INI

    /** Parses wg-quick style INI, including AmneziaWG junk-packet fields (both key styles). */
    fun parseWgIni(
        text: String,
        forcedName: String?,
    ): Profile? {
        var privateKey: String? = null
        var publicKey: String? = null
        var psk: String? = null
        var endpointHost: String? = null
        var endpointPort = 51820
        val addresses = mutableListOf<String>()
        val dns = mutableListOf<String>()
        var mtu: Int? = null
        var name: String? = forcedName

        // AmneziaWG accepts both short (Jc) and long (JunkPacketCount) keys.
        val awgKeyAliases =
            mapOf(
                "Ic" to "Ic",
                "InitCountJunkPacket" to "Ic",
                "InitCountJunkPackets" to "Ic",
                "Jc" to "Jc",
                "JunkPacketCount" to "Jc",
                "ResponseCountJunkPacket" to "Jc",
                "Jmin" to "Jmin",
                "JunkPacketMinSize" to "Jmin",
                "Jmax" to "Jmax",
                "JunkPacketMaxSize" to "Jmax",
                "S1" to "S1",
                "InitPacketJunkSize" to "S1",
                "S2" to "S2",
                "ResponsePacketJunkSize" to "S2",
                "H1" to "H1",
                "InitPacketMagicHeader" to "H1",
                "H2" to "H2",
                "ResponsePacketMagicHeader" to "H2",
                "H3" to "H3",
                "UnderloadPacketMagicHeader" to "H3",
                "H4" to "H4",
                "TransportPacketMagicHeader" to "H4",
            )
        val awg = mutableMapOf<String, String>()

        var section = ""
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#") && name == null -> {
                    name = line.removePrefix("#").trim().ifBlank { null }
                }

                line.startsWith("[") -> {
                    section = line.trim('[', ']').lowercase()
                }

                else -> {
                    val key = line.substringBefore('=').trim()
                    val value = line.substringAfter('=', "").trim()
                    if (key.isEmpty() || value.isEmpty()) return@forEach
                    awgKeyAliases[key]?.let {
                        awg[it] = value
                        return@forEach
                    }
                    when ("$section.$key".lowercase()) {
                        "interface.privatekey" -> {
                            privateKey = value
                        }

                        "interface.address" -> {
                            addresses += value.split(',').map { it.trim() }
                        }

                        "interface.dns" -> {
                            dns += value.split(',').map { it.trim() }
                        }

                        "interface.mtu" -> {
                            mtu = value.toIntOrNull()
                        }

                        "peer.publickey" -> {
                            publicKey = value
                        }

                        "peer.presharedkey" -> {
                            psk = value
                        }

                        "peer.endpoint" -> {
                            endpointHost = value.substringBeforeLast(':')
                            endpointPort = value.substringAfterLast(':').toIntOrNull() ?: 51820
                        }
                    }
                }
            }
        }
        val host = endpointHost ?: return null
        val params =
            ProfileParams(
                wgPrivateKey = privateKey ?: return null,
                wgPeerPublicKey = publicKey ?: return null,
                wgPsk = psk,
                wgAddresses = addresses,
                wgDns = dns,
                wgMtu = mtu,
                awgIc = awg["Ic"]?.toIntOrNull(),
                awgJc = awg["Jc"]?.toIntOrNull(),
                awgJmin = awg["Jmin"]?.toIntOrNull(),
                awgJmax = awg["Jmax"]?.toIntOrNull(),
                awgS1 = awg["S1"]?.toIntOrNull(),
                awgS2 = awg["S2"]?.toIntOrNull(),
                awgH1 = awg["H1"]?.toLongOrNull(),
                awgH2 = awg["H2"]?.toLongOrNull(),
                awgH3 = awg["H3"]?.toLongOrNull(),
                awgH4 = awg["H4"]?.toLongOrNull(),
            )
        return Profile(
            name = name ?: "$host:$endpointPort",
            protocol = if (awg.isNotEmpty()) Protocol.AMNEZIA_WG else Protocol.WIREGUARD,
            server = host,
            port = endpointPort,
            rawLink = text,
            params = paramsToJson(params),
        )
    }

    // ---------------------------------------------------------------- amnezia vpn:// backup

    /**
     * vpn://<base64 JSON> — the Amnezia share format. The WG/AWG INI lives at
     * containers[].wireguard.last_config; we locate the first [Interface] blob
     * anywhere in the tree to be resilient to format drift.
     */
    private fun parseAmneziaBackup(link: String): Profile? {
        val decoded = link.substringAfter("vpn://").decodeBase64OrNull()?.toStringUtf8() ?: return null
        val tree = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
        val ini = findInterfaceConfig(tree) ?: return null
        val fallbackName = tree.str("name") ?: tree.str("description")
        return parseWgIni(ini, fallbackName)?.copy(rawLink = link)
    }

    private fun findInterfaceConfig(element: JsonElement): String? {
        when (element) {
            is JsonObject -> for ((key, value) in element) {
                if ((key == "last_config" || key == "config") && value is JsonPrimitive &&
                    value.content.contains("[Interface]")
                ) {
                    return value.content
                }
                findInterfaceConfig(value)?.let { return it }
            }

            is JsonArray -> for (item in element) findInterfaceConfig(item)?.let { return it }

            else -> Unit
        }
        return null
    }

    // ---------------------------------------------------------------- helpers

    private fun URI.queryMap(): Map<String, String> {
        val q = rawQuery ?: return emptyMap()
        return q
            .split('&')
            .mapNotNull { kv ->
                val k = kv.substringBefore('=')
                if (k.isBlank()) null else k to kv.substringAfter('=', "")
            }.toMap()
    }

    private fun Map<String, String>.bool(key: String): Boolean = this[key]?.let { it == "1" || it.equals("true", true) } ?: false

    private fun String.decode(): String = runCatching { URLDecoder.decode(replace("+", "%20"), "UTF-8") }.getOrDefault(this)

    private fun String.decodeBase64OrNull(): ByteArray? {
        val normalized =
            trim()
                .replace('-', '+')
                .replace('_', '/')
                .let { if (it.length % 4 == 0) it else it + "=".repeat(4 - it.length % 4) }
        return runCatching {
            java.util.Base64
                .getMimeDecoder()
                .decode(normalized)
        }.getOrNull()
    }

    private fun ByteArray.toStringUtf8(): String = String(this, StandardCharsets.UTF_8)

    private fun String.splitHostPort(): Pair<String, Int>? {
        val host = substringBeforeLast(':').removeSurrounding("[", "]")
        val port = substringAfterLast(':').toIntOrNull() ?: return null
        return host.ifBlank { null }?.to(port)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
