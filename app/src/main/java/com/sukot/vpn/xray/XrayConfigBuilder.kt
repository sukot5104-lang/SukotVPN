package com.sukot.vpn.xray

import com.sukot.vpn.data.SukotSettings
import com.sukot.vpn.model.VpnProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Xray-core JSON configuration for the selected profile.
 *
 * Structure mirrors v2rayNG: a single local SOCKS inbound on 127.0.0.1:10808 with
 * sniffing enabled; the libv2ray engine binds our TUN file descriptor to that inbound.
 */
object XrayConfigBuilder {

    fun build(profile: VpnProfile, settings: SukotSettings, hasGeoFiles: Boolean): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        val inbound = JSONObject()
            .put("tag", "socks")
            .put("listen", "127.0.0.1")
            .put("port", 10808)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject().put("auth", "noauth").put("udp", true).put("userLevel", 8)
            )
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                    .put("routeOnly", false)
                    .put("metadataOnly", false)
            )
        root.put("inbounds", JSONArray().put(inbound))

        val proxy = buildProxyOutbound(profile)
        val direct = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))
        val block = JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
            .put("settings", JSONObject().put("response", JSONObject().put("type", "http")))
        root.put("outbounds", JSONArray().put(proxy).put(direct).put(block))

        val rules = JSONArray()
        if (settings.blockTorrent) {
            rules.put(
                JSONObject().put("type", "field").put("outboundTag", "block")
                    .put("protocol", JSONArray(listOf("bittorrent")))
            )
        }
        // Private/LAN ranges as plain CIDRs — identical to geoip:private but
        // never depends on geo files being present (fixes "failed to open
        // geoip.dat" crash on first connect).
        rules.put(
            JSONObject().put("type", "field").put("outboundTag", "direct")
                .put(
                    "ip", JSONArray(
                        listOf(
                            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
                            "127.0.0.0/8", "169.254.0.0/16", "100.64.0.0/10",
                            "224.0.0.0/3", "fe80::/10", "fc00::/7", "::1/128"
                        )
                    )
                )
        )
        if (settings.blockQuic) {
            rules.put(
                JSONObject().put("type", "field").put("outboundTag", "block")
                    .put("network", "udp").put("port", JSONArray(listOf(443)))
            )
        }
        if (settings.blockAds && hasGeoFiles) {
            rules.put(
                JSONObject().put("type", "field").put("outboundTag", "block")
                    .put("domain", JSONArray(listOf("geosite:category-ads-all")))
            )
        }
        if (settings.bypassIran) {
            if (hasGeoFiles) {
                rules.put(
                    JSONObject().put("type", "field").put("outboundTag", "direct")
                        .put("domain", JSONArray(listOf("geosite:category-ir")))
                )
                rules.put(
                    JSONObject().put("type", "field").put("outboundTag", "direct")
                        .put("ip", JSONArray(listOf("geoip:ir")))
                )
            } else {
                // Works without geo files: plain TLD rule
                rules.put(
                    JSONObject().put("type", "field").put("outboundTag", "direct")
                        .put("domain", JSONArray(listOf("domain:ir")))
                )
            }
        }
        root.put(
            "routing",
            JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", rules)
        )
        return root.toString()
    }

    private fun buildProxyOutbound(p: VpnProfile): JSONObject {
        val outbound = JSONObject().put("tag", "proxy")
        when (p.protocol.uppercase()) {
            "VMESS" -> {
                outbound.put("protocol", "vmess")
                val user = JSONObject()
                    .put("id", p.uuid)
                    .put("alterId", p.alterId)
                    .put("security", p.encryption.ifBlank { "auto" })
                    .put("level", 8)
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", p.serverAddress)
                                .put("port", p.serverPort)
                                .put("users", JSONArray().put(user))
                        )
                    )
                )
            }
            "VLESS" -> {
                outbound.put("protocol", "vless")
                val user = JSONObject()
                    .put("id", p.uuid)
                    .put("encryption", p.encryption.ifBlank { "none" })
                    .put("level", 8)
                if (p.flow.isNotBlank()) user.put("flow", p.flow)
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", p.serverAddress)
                                .put("port", p.serverPort)
                                .put("users", JSONArray().put(user))
                        )
                    )
                )
            }
            "TROJAN" -> {
                outbound.put("protocol", "trojan")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("address", p.serverAddress)
                                .put("port", p.serverPort)
                                .put("password", p.password)
                                .put("level", 8)
                        )
                    )
                )
            }
            else -> { // SHADOWSOCKS
                outbound.put("protocol", "shadowsocks")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("address", p.serverAddress)
                                .put("port", p.serverPort)
                                .put("method", p.method)
                                .put("password", p.password)
                                .put("level", 8)
                        )
                    )
                )
            }
        }
        outbound.put("streamSettings", buildStream(p))
        return outbound
    }

    private fun buildStream(p: VpnProfile): JSONObject {
        val s = JSONObject().put("network", p.network.ifBlank { "tcp" })
        when (p.security.lowercase()) {
            "tls" -> {
                s.put("security", "tls")
                val tls = JSONObject()
                    .put("serverName", p.sni.ifBlank { p.wsHost.ifBlank { p.serverAddress } })
                    .put("allowInsecure", p.allowInsecure)
                if (p.fingerprint.isNotBlank()) tls.put("fingerprint", p.fingerprint)
                if (p.alpn.isNotBlank()) {
                    tls.put("alpn", JSONArray(p.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                }
                s.put("tlsSettings", tls)
            }
            "reality" -> {
                s.put("security", "reality")
                s.put(
                    "realitySettings",
                    JSONObject()
                        .put("serverName", p.sni.ifBlank { p.serverAddress })
                        .put("fingerprint", p.fingerprint.ifBlank { "chrome" })
                        .put("publicKey", p.publicKey)
                        .put("shortId", p.shortId)
                        .put("spiderX", "")
                )
            }
            else -> s.put("security", "none")
        }
        when (p.network.lowercase()) {
            "ws" -> {
                val ws = JSONObject().put("path", p.wsPath.ifBlank { "/" })
                if (p.wsHost.isNotBlank()) ws.put("headers", JSONObject().put("Host", p.wsHost))
                s.put("wsSettings", ws)
            }
            "httpupgrade" -> s.put(
                "httpupgradeSettings",
                JSONObject()
                    .put("path", p.wsPath.ifBlank { "/" })
                    .put("host", p.wsHost.ifBlank { p.serverAddress })
            )
            "h2", "http" -> s.put(
                "httpSettings",
                JSONObject()
                    .put("path", p.wsPath.ifBlank { "/" })
                    .put("host", JSONArray(listOf(p.wsHost.ifBlank { p.serverAddress })))
            )
            "grpc" -> s.put(
                "grpcSettings",
                JSONObject().put("serviceName", p.wsPath).put("multiMode", false)
            )
            "kcp" -> {
                val k = JSONObject()
                if (p.wsHost.isNotBlank()) k.put("seed", p.wsHost)
                s.put("kcpSettings", k)
            }
            else -> { // tcp
                if (p.headerType == "http" && p.wsHost.isNotBlank()) {
                    val request = JSONObject()
                        .put("version", "1.1")
                        .put("method", "GET")
                        .put("path", JSONArray(listOf(p.wsPath.ifBlank { "/" })))
                        .put(
                            "headers",
                            JSONObject()
                                .put("Host", JSONArray(listOf(p.wsHost)))
                                .put(
                                    "User-Agent",
                                    JSONArray(
                                        listOf(
                                            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36" +
                                                " (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                                        )
                                    )
                                )
                                .put("Accept-Encoding", JSONArray(listOf("gzip, deflate")))
                                .put("Connection", JSONArray(listOf("keep-alive")))
                                .put("Pragma", "no-cache")
                        )
                    s.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", request)))
                }
            }
        }
        return s
    }
}
