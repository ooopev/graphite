package com.graphite.app.core.model

/**
 * Protocol families Graphite understands. Each maps to exactly one engine:
 *
 *  - SING_BOX — sing-box core (libbox): everything except AmneziaWG and Xray-only transports.
 *  - XRAY     — Xray-core (libxray): XHTTP and other Xray-exclusive stream transports.
 *               Runs as a local SOCKS hop behind the sing-box TUN (loopback cost is negligible).
 *  - AWG      — AmneziaWG (libawg): obfuscated WireGuard. Owns the TUN fd directly,
 *               because sing-box has no AmneziaWG support (open upstream issues #3159 / #4045).
 */
enum class Protocol(
    val engine: Engine,
) {
    VLESS(Engine.SING_BOX),
    VMESS(Engine.SING_BOX),
    TROJAN(Engine.SING_BOX),
    SHADOWSOCKS(Engine.SING_BOX),
    HYSTERIA2(Engine.SING_BOX),
    TUIC(Engine.SING_BOX),
    WIREGUARD(Engine.SING_BOX),
    AMNEZIA_WG(Engine.AWG),
    SOCKS(Engine.SING_BOX),
    HTTP(Engine.SING_BOX),
    ;

    enum class Engine { SING_BOX, XRAY, AWG }
}

/** XHTTP and other Xray-only transports must be served by the Xray core. */
fun Profile.effectiveEngine(params: ProfileParams): Protocol.Engine =
    if (protocol == Protocol.VLESS && params.transport.equals("xhttp", true)) {
        Protocol.Engine.XRAY
    } else {
        protocol.engine
    }
