package com.oquq.app.core.config

import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.ProfileParams
import com.oquq.app.core.model.Protocol
import com.oquq.app.core.model.effectiveEngine
import com.oquq.app.core.parser.LinkParser
import com.oquq.app.data.AppSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Builds a complete sing-box JSON config for the selected profile.
 *
 * Targets sing-box 1.12+ config format (new typed DNS servers, route rule
 * actions, endpoints). The tun inbound is created by libbox itself; we still
 * declare it because libbox reads TUN settings from here.
 *
 * When the profile is served by the Xray engine (XHTTP & friends), the proxy
 * outbound becomes a local SOCKS hop to the Xray daemon — one TUN owner, two cores.
 */
object SingBoxConfigBuilder {
    const val XRAY_LOOPBACK_TAG = "xray-hop"
    const val XRAY_SOCKS_PORT = 15490
    const val TUN_TAG = "tun-in"

    fun build(
        profile: Profile,
        settings: AppSettings,
    ): String {
        val params = LinkParser.paramsFromJson(profile.params)
        // WireGuard (plain) is served by a 1.11+ endpoint tagged "proxy" — no outbound needed.
        val endpoints = wireguardEndpoint(profile, params)
        val outbounds =
            buildList {
                if (endpoints.isEmpty()) add(proxyOutbound(profile, params))
                add(
                    buildJsonObject {
                        put("type", "direct")
                        put("tag", "direct")
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "block")
                        put("tag", "block")
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "dns")
                        put("tag", "dns-out")
                    },
                )
            }

        val inbounds =
            buildList {
                add(
                    buildJsonObject {
                        put("type", "tun")
                        put("tag", TUN_TAG)
                        put("interface_name", "oQuQ")
                        put("address", JsonArray(listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126").map(::p)))
                        put("mtu", settings.mtu)
                        put("auto_route", true)
                        // strict_route kills any packet that tries to escape the tunnel — VPN-side kill switch.
                        put("strict_route", true)
                        put(
                            "stack",
                            when (settings.tunStack) {
                                com.oquq.app.data.TunStack.SYSTEM -> "system"
                                com.oquq.app.data.TunStack.GVISOR -> "gvisor"
                                com.oquq.app.data.TunStack.MIXED -> "mixed"
                            },
                        )
                    },
                )
                if (settings.socksShareEnabled) {
                    add(
                        buildJsonObject {
                            put("type", "socks")
                            put("tag", "socks-share")
                            put("listen", if (settings.socksShareAllowLan) "::" else "127.0.0.1")
                            put("listen_port", settings.socksSharePort)
                        },
                    )
                }
            }

        val rules =
            buildList {
                // 1.11+ route actions: sniff then hijack DNS inside the tunnel.
                add(buildJsonObject { put("action", "sniff") })
                add(
                    buildJsonObject {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    },
                )
                if (settings.proxyMode == com.oquq.app.data.ProxyMode.RULE) {
                    add(
                        buildJsonObject {
                            put("ip_is_private", true)
                            put("action", "route")
                            put("outbound", "direct")
                        },
                    )
                }
                if (settings.blockAds) {
                    add(
                        buildJsonObject {
                            put("rule_set", JsonArray(listOf("geosite-category-ads-all").map(::p)))
                            put("action", "route")
                            put("outbound", "block")
                        },
                    )
                }
            }

        val config =
            buildJsonObject {
                putJsonObject("log") {
                    put("level", "warn")
                    put("timestamp", true)
                }
                put("dns", dnsSection(profile, params))
                put("endpoints", endpoints)
                put("inbounds", JsonArray(inbounds))
                put("outbounds", JsonArray(outbounds))
                putJsonObject("route") {
                    put("auto_detect_interface", true)
                    put("final", "proxy")
                    put("rules", JsonArray(rules))
                    if (settings.blockAds) {
                        put(
                            "rule_set",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("type", "remote")
                                        put("tag", "geosite-category-ads-all")
                                        put("format", "binary")
                                        put(
                                            "url",
                                            "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ads-all.srs",
                                        )
                                        put("download_detour", "direct")
                                    },
                                ),
                            ),
                        )
                    }
                }
                putJsonObject("experimental") {
                    // cache_file persists rule-set/state between runs: faster cold start, fewer radio wakeups.
                    putJsonObject("cache_file") {
                        put("enabled", true)
                    }
                }
            }
        return config.toString()
    }

    // ------------------------------------------------------------------ dns

    private fun dnsSection(
        profile: Profile,
        params: ProfileParams,
    ): JsonObject {
        val isWg = profile.protocol == Protocol.WIREGUARD || profile.protocol == Protocol.AMNEZIA_WG
        return buildJsonObject {
            put(
                "servers",
                JsonArray(
                    buildList {
                        if (isWg) {
                            // Inside a WG tunnel the only sane resolver is the tunnel's own DNS.
                            add(
                                buildJsonObject {
                                    put("type", "udp")
                                    put("tag", "dns-remote")
                                    put("server", params.wgDns.firstOrNull() ?: "1.1.1.1")
                                    put("detour", "proxy")
                                },
                            )
                        } else {
                            add(
                                buildJsonObject {
                                    put("type", "https")
                                    put("tag", "dns-remote")
                                    put("server", "1.1.1.1")
                                    put("detour", "proxy")
                                },
                            )
                        }
                        // Always present: resolves core's own outbound domains (rule "outbound: any").
                        add(
                            buildJsonObject {
                                put("type", "local")
                                put("tag", "dns-local")
                                put("detour", "direct")
                            },
                        )
                    },
                ),
            )
            put(
                "rules",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("outbound", "any")
                            put("server", "dns-local")
                        },
                    ),
                ),
            )
            put("final", "dns-remote")
            put("independent_cache", true)
        }
    }

    // ------------------------------------------------------------------ wireguard endpoint (1.11+)

    private fun wireguardEndpoint(
        profile: Profile,
        params: ProfileParams,
    ): JsonArray {
        if (profile.protocol != Protocol.WIREGUARD) return JsonArray(emptyList())
        return JsonArray(
            listOf(
                buildJsonObject {
                    put("type", "wireguard")
                    // Reuse the "proxy" tag so all routing rules stay engine-agnostic.
                    put("tag", "proxy")
                    put(
                        "address",
                        JsonArray(
                            (params.wgAddresses.ifEmpty { listOf("172.16.0.2/32") }).map(::p),
                        ),
                    )
                    put("private_key", params.wgPrivateKey ?: "")
                    put("mtu", params.wgMtu ?: 1420)
                    put(
                        "peers",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("address", profile.server)
                                    put("port", profile.port)
                                    put("public_key", params.wgPeerPublicKey ?: "")
                                    params.wgPsk?.let { put("pre_shared_key", it) }
                                    put("allowed_ips", JsonArray(listOf("0.0.0.0/0", "::/0").map(::p)))
                                },
                            ),
                        ),
                    )
                },
            ),
        )
    }

    // ------------------------------------------------------------------ outbounds

    private fun proxyOutbound(
        profile: Profile,
        params: ProfileParams,
    ): JsonObject =
        buildJsonObject {
            put("tag", "proxy")
            when (profile.effectiveEngine(params)) {
                Protocol.Engine.XRAY -> {
                    // Xray serves this profile locally; sing-box just owns the TUN.
                    put("type", "socks")
                    put("server", "127.0.0.1")
                    put("server_port", XRAY_SOCKS_PORT)
                    put("version", "5")
                }

                Protocol.Engine.AWG -> {
                    throw IllegalStateException("AWG profiles do not go through sing-box")
                }

                Protocol.Engine.SING_BOX -> {
                    when (profile.protocol) {
                        Protocol.VLESS -> {
                            put("type", "vless")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            put("uuid", params.uuid ?: "")
                            params.flow?.let { put("flow", it) }
                            put("packet_encoding", "xudp")
                            tls(params, reality = params.realityPbk != null)
                            transport(params)
                        }

                        Protocol.VMESS -> {
                            put("type", "vmess")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            put("uuid", params.uuid ?: "")
                            put("security", params.encryption ?: "auto")
                            put("alter_id", 0)
                            put("packet_encoding", "xudp")
                            if (params.sni != null || params.alpn.isNotEmpty()) tls(params, reality = false)
                            transport(params)
                        }

                        Protocol.TROJAN -> {
                            put("type", "trojan")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            put("password", params.password ?: "")
                            tls(params, reality = false)
                            transport(params)
                        }

                        Protocol.SHADOWSOCKS -> {
                            put("type", "shadowsocks")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            put("method", params.method ?: "aes-256-gcm")
                            put("password", params.password ?: "")
                        }

                        Protocol.HYSTERIA2 -> {
                            put("type", "hysteria2")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            put("password", params.password ?: "")
                            params.upMbps?.let { put("up_mbps", it) }
                            params.downMbps?.let { put("down_mbps", it) }
                            if (params.hy2Obfs != null) {
                                putJsonObject("obfs") {
                                    put("type", "salamander")
                                    put("password", params.hy2ObfsPassword ?: "")
                                }
                            }
                            tls(params, reality = false)
                        }

                        Protocol.TUIC -> {
                            put("type", "tuic")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            put("uuid", params.uuid ?: "")
                            put("password", params.password ?: "")
                            put("congestion_control", params.congestionControl ?: "bbr")
                            put("udp_relay_mode", params.udpRelayMode ?: "native")
                            tls(params, reality = false)
                        }

                        Protocol.WIREGUARD, Protocol.AMNEZIA_WG -> {
                            throw IllegalStateException("WG handled by endpoint/AWG engine")
                        }

                        Protocol.SOCKS -> {
                            put("type", "socks")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            put("version", "5")
                            params.username?.let { put("username", it) }
                            params.password?.let { put("password", it) }
                        }

                        Protocol.HTTP -> {
                            put("type", "http")
                            put("server", profile.server)
                            put("server_port", profile.port)
                            params.username?.let { put("username", it) }
                            params.password?.let { put("password", it) }
                        }
                    }
                }
            }
        }

    private fun JsonObjectBuilder.tls(
        params: ProfileParams,
        reality: Boolean,
    ) {
        putJsonObject("tls") {
            put("enabled", true)
            put("server_name", params.sni ?: "")
            put("insecure", params.allowInsecure)
            if (params.alpn.isNotEmpty()) put("alpn", JsonArray(params.alpn.map(::p)))
            if (params.fingerprint != null || reality) {
                putJsonObject("utls") {
                    put("enabled", true)
                    put("fingerprint", params.fingerprint ?: "chrome")
                }
            }
            if (reality) {
                putJsonObject("reality") {
                    put("enabled", true)
                    put("public_key", params.realityPbk ?: "")
                    params.realitySid?.let { put("short_id", it) }
                }
            }
        }
    }

    private fun JsonObjectBuilder.transport(params: ProfileParams) {
        when (params.transport?.lowercase()) {
            null, "", "tcp", "raw" -> {
                if (params.headerType == "http") {
                    putJsonObject("transport") {
                        put("type", "http")
                        put("host", JsonArray(listOf(params.transportHost ?: "").filter { it.isNotBlank() }.map(::p)))
                        params.transportPath?.let { put("path", it) }
                    }
                }
            }

            "ws" -> {
                putJsonObject("transport") {
                    put("type", "ws")
                    params.transportPath?.let { put("path", it) }
                    params.transportHost?.let {
                        put("headers", buildJsonObject { put("Host", it) })
                    }
                }
            }

            "grpc" -> {
                putJsonObject("transport") {
                    put("type", "grpc")
                    put("service_name", params.transportServiceName ?: params.transportPath ?: "")
                }
            }

            "httpupgrade" -> {
                putJsonObject("transport") {
                    put("type", "httpupgrade")
                    params.transportPath?.let { put("path", it) }
                    params.transportHost?.let { put("host", it) }
                }
            }

            "http", "h2" -> {
                putJsonObject("transport") {
                    put("type", "http")
                    put("host", JsonArray(listOf(params.transportHost ?: "").filter { it.isNotBlank() }.map(::p)))
                    params.transportPath?.let { put("path", it) }
                }
            }

            // xhttp is Xray-only; if it ever lands here the effective engine check failed upstream.
            else -> {
                Unit
            }
        }
    }

    private fun p(v: String): JsonPrimitive = JsonPrimitive(v)
}
