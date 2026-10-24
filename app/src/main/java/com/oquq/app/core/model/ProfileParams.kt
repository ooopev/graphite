package com.oquq.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Protocol-specific payload of a profile. Kept as JSON in Room so a single
 * entity serves every protocol without migrations when new fields appear.
 */
@Serializable
data class ProfileParams(
    // --- common ---
    val uuid: String? = null, // VLESS id / VMess id / TUIC uuid
    val password: String? = null, // Trojan / SS / Hy2 auth / TUIC password
    val sni: String? = null,
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,
    val fingerprint: String? = null, // uTLS fingerprint (chrome, firefox, ios, safari…)
    // --- vless / vmess / trojan ---
    val flow: String? = null, // xtls-rprx-vision
    val encryption: String? = null, // vless "none", vmess "auto"
    val transport: String? = null, // tcp | ws | grpc | http | httpupgrade | xhttp
    val transportPath: String? = null,
    val transportHost: String? = null,
    val transportServiceName: String? = null, // grpc serviceName
    val xhttpMode: String? = null, // auto | packet-up | stream-up
    val headerType: String? = null, // vmess tcp header (http / none)
    // --- REALITY ---
    val realityPbk: String? = null,
    val realitySid: String? = null,
    val realitySpx: String? = null,
    // --- shadowsocks ---
    val method: String? = null,
    // --- hysteria2 ---
    val hy2Obfs: String? = null, // salamander
    val hy2ObfsPassword: String? = null,
    val upMbps: Int? = null,
    val downMbps: Int? = null,
    // --- tuic ---
    val congestionControl: String? = null, // bbr / cubic
    val udpRelayMode: String? = null, // native / quic
    // --- wireguard / amneziawg ---
    val wgPrivateKey: String? = null,
    val wgPeerPublicKey: String? = null,
    val wgPsk: String? = null,
    val wgAddresses: List<String> = emptyList(), // client tunnel addresses
    val wgDns: List<String> = emptyList(),
    val wgMtu: Int? = null,
    // AmneziaWG junk-packet obfuscation (Ic/Jc optional on modern configs)
    val awgIc: Int? = null,
    val awgJc: Int? = null,
    val awgJmin: Int? = null,
    val awgJmax: Int? = null,
    val awgS1: Int? = null,
    val awgS2: Int? = null,
    val awgH1: Long? = null,
    val awgH2: Long? = null,
    val awgH3: Long? = null,
    val awgH4: Long? = null,
    // --- socks / http ---
    val username: String? = null,
    /** Anything exotic the link carried that a builder may want later. */
    val extras: JsonObject? = null,
)
