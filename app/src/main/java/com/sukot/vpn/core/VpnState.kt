package com.sukot.vpn.core

/**
 * VPN connection lifecycle states shared between the daemon process and the UI.
 */
enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}
