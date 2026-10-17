package com.sukot.vpn.model

import com.google.gson.annotations.SerializedName

/**
 * Unified VPN server profile supporting VMess / VLESS / Trojan / Shadowsocks.
 */
data class VpnProfile(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("protocol") val protocol: String,
    @SerializedName("serverAddress") val serverAddress: String,
    @SerializedName("serverPort") val serverPort: Int,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("password") val password: String = "",
    @SerializedName("method") val method: String = "",
    @SerializedName("security") val security: String = "none",
    @SerializedName("sni") val sni: String = "",
    @SerializedName("network") val network: String = "tcp",
    @SerializedName("wsPath") val wsPath: String = "",
    @SerializedName("wsHost") val wsHost: String = "",
    @SerializedName("headerType") val headerType: String = "",
    @SerializedName("flow") val flow: String = "",
    @SerializedName("publicKey") val publicKey: String = "",
    @SerializedName("shortId") val shortId: String = "",
    @SerializedName("fingerprint") val fingerprint: String = "",
    @SerializedName("alpn") val alpn: String = "",
    @SerializedName("allowInsecure") val allowInsecure: Boolean = false,
    @SerializedName("alterId") val alterId: Int = 0,
    @SerializedName("encryption") val encryption: String = "auto",
    @SerializedName("source") val source: String = "manual",
    @SerializedName("subId") val subId: String = ""
) {
    val protocolTag: String
        get() = when (protocol.uppercase()) {
            "VMESS" -> "VMess"
            "VLESS" -> "VLESS"
            "TROJAN" -> "Trojan"
            "SHADOWSOCKS" -> "SS"
            else -> protocol.uppercase()
        }

    fun displayAddress(): String = "$serverAddress:$serverPort"
}
