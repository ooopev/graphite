package com.graphite.app.core.config

import com.graphite.app.core.model.Profile
import com.graphite.app.core.model.ProfileParams
import com.graphite.app.core.model.Protocol
import com.graphite.app.core.parser.LinkParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the Xray-core JSON served by the Xray daemon (libxray.aar).
 * The daemon exposes SOCKS5 on 127.0.0.1:[XRAY_SOCKS_PORT]; the sing-box TUN
 * routes into it. Used for Xray-exclusive transports (XHTTP today).
 */
object XrayConfigBuilder {
    fun build(profile: Profile): String {
        val params = LinkParser.paramsFromJson(profile.params)
        val config =
            buildJsonObject {
                putJsonObject("log") { put("loglevel", "warning") }
                put(
                    "inbounds",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("tag", "socks-in")
                                put("listen", "127.0.0.1")
                                put("port", SingBoxConfigBuilder.XRAY_SOCKS_PORT)
                                put("protocol", "socks")
                                putJsonObject("settings") {
                                    put("udp", true)
                                    put("auth", "noauth")
                                }
                                put(
                                    "sniffing",
                                    buildJsonObject {
                                        put("enabled", true)
                                        put("destOverride", JsonArray(listOf("http", "tls", "quic").map(::jp)))
                                    },
                                )
                            },
                        ),
                    ),
                )
                put("outbounds", JsonArray(listOf(outbound(profile, params))))
            }
        return config.toString()
    }

    private fun outbound(
        profile: Profile,
        params: ProfileParams,
    ) = buildJsonObject {
        put("tag", "proxy")
        when (profile.protocol) {
            Protocol.VLESS -> {
                put("protocol", "vless")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        add(
                            buildJsonObject {
                                put("address", profile.server)
                                put("port", profile.port)
                                putJsonArray("users") {
                                    add(
                                        buildJsonObject {
                                            put("id", params.uuid ?: "")
                                            put("encryption", "none")
                                            params.flow?.let { put("flow", it) }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
                put("streamSettings", streamSettings(params))
                // Mux is counter-productive with REALITY/Vision; keep explicit & off.
                put("mux", buildJsonObject { put("enabled", false) })
            }

            Protocol.VMESS -> {
                put("protocol", "vmess")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(
                            buildJsonObject {
                                put("address", profile.server)
                                put("port", profile.port)
                                putJsonArray("users") {
                                    add(
                                        buildJsonObject {
                                            put("id", params.uuid ?: "")
                                            put("security", params.encryption ?: "auto")
                                            put("alterId", 0)
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
                put("streamSettings", streamSettings(params))
            }

            Protocol.TROJAN -> {
                put("protocol", "trojan")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(
                            buildJsonObject {
                                put("address", profile.server)
                                put("port", profile.port)
                                put("password", params.password ?: "")
                            },
                        )
                    }
                }
                put("streamSettings", streamSettings(params))
            }

            else -> {
                throw IllegalArgumentException("Xray engine serves VLESS/VMess/Trojan only, got ${profile.protocol}")
            }
        }
    }

    private fun streamSettings(params: ProfileParams) =
        buildJsonObject {
            put("network", params.transport ?: "tcp")
            put(
                "security",
                if (params.realityPbk != null) {
                    "reality"
                } else if (needsTls(params)) {
                    "tls"
                } else {
                    "none"
                },
            )

            // ---- transport settings ----
            when (params.transport?.lowercase()) {
                "xhttp" -> {
                    put(
                        "xhttpSettings",
                        buildJsonObject {
                            params.transportPath?.let { put("path", it) }
                            params.transportHost?.let { put("host", it) }
                            params.xhttpMode?.let { put("mode", it) }
                        },
                    )
                }

                "ws" -> {
                    put(
                        "wsSettings",
                        buildJsonObject {
                            params.transportPath?.let { put("path", it) }
                            params.transportHost?.let { put("host", it) }
                        },
                    )
                }

                "grpc" -> {
                    put(
                        "grpcSettings",
                        buildJsonObject {
                            put("serviceName", params.transportServiceName ?: params.transportPath ?: "")
                        },
                    )
                }

                "httpupgrade" -> {
                    put(
                        "httpupgradeSettings",
                        buildJsonObject {
                            params.transportPath?.let { put("path", it) }
                            params.transportHost?.let { put("host", it) }
                        },
                    )
                }

                "tcp", "raw", null -> {
                    if (params.headerType == "http") {
                        put(
                            "tcpSettings",
                            buildJsonObject {
                                put(
                                    "header",
                                    buildJsonObject {
                                        put("type", "http")
                                        put(
                                            "request",
                                            buildJsonObject {
                                                put("path", JsonArray(listOf(params.transportPath ?: "/").map(::jp)))
                                                put(
                                                    "headers",
                                                    buildJsonObject {
                                                        put(
                                                            "Host",
                                                            JsonArray(
                                                                listOf(
                                                                    params.transportHost ?: params.sni ?: "",
                                                                ).filter { it.isNotBlank() }.map(::jp),
                                                            ),
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }

            // ---- security settings ----
            if (params.realityPbk != null) {
                put(
                    "realitySettings",
                    buildJsonObject {
                        put("serverName", params.sni ?: "")
                        put("fingerprint", params.fingerprint ?: "chrome")
                        put("publicKey", params.realityPbk)
                        params.realitySid?.let { put("shortId", it) }
                        params.realitySpx?.let { put("spiderX", it) }
                    },
                )
            } else if (needsTls(params)) {
                put(
                    "tlsSettings",
                    buildJsonObject {
                        put("serverName", params.sni ?: "")
                        put("allowInsecure", params.allowInsecure)
                        if (params.alpn.isNotEmpty()) put("alpn", JsonArray(params.alpn.map(::jp)))
                        if (params.fingerprint != null) put("fingerprint", params.fingerprint)
                    },
                )
            }
        }

    private fun needsTls(params: ProfileParams): Boolean = params.sni != null || params.alpn.isNotEmpty() || params.allowInsecure

    private fun jp(v: String): JsonPrimitive = JsonPrimitive(v)
}
