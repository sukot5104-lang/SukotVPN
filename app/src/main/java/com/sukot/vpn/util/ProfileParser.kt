package com.sukot.vpn.util

import android.util.Base64
import com.sukot.vpn.model.VpnProfile
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/**
 * Parser for VMess / VLESS / Trojan / Shadowsocks share links (single or base64 batch).
 */
object ProfileParser {

    private val PROTOCOL_PREFIXES = listOf("vmess://", "vless://", "trojan://", "ss://")

    fun looksLikeConfig(text: String): Boolean =
        PROTOCOL_PREFIXES.any { text.trim().startsWith(it, ignoreCase = true) }

    /** Parse a block of links (plain list or base64 subscription body). */
    fun parseMulti(text: String): List<VpnProfile> {
        val trimmed = text.trim()
        val uris = mutableListOf<String>()
        if (trimmed.contains("://")) {
            trimmed.lines().forEach { line ->
                val t = line.trim()
                if (t.isNotEmpty() && !t.startsWith("#")) uris.add(t)
            }
        } else {
            decodeB64(trimmed)?.let { decoded ->
                decoded.lines().forEach { line ->
                    val t = line.trim()
                    if (t.contains("://")) uris.add(t)
                }
            }
        }
        val out = mutableListOf<VpnProfile>()
        val seen = HashSet<String>()
        for (u in uris) {
            val p = parseOne(u) ?: continue
            val key = "${p.protocol}|${p.serverAddress}|${p.serverPort}|${p.uuid}|${p.password}"
            if (seen.add(key)) out.add(p)
        }
        return out
    }

    /** Parse a single share link. Returns null when unsupported/invalid. */
    fun parseOne(uri: String): VpnProfile? {
        return try {
            when {
                uri.startsWith("vmess://", ignoreCase = true) -> parseVmess(uri)
                uri.startsWith("vless://", ignoreCase = true) -> parseVless(uri)
                uri.startsWith("trojan://", ignoreCase = true) -> parseTrojan(uri)
                uri.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(uri)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- vmess

    private fun parseVmess(uri: String): VpnProfile? {
        val b64 = uri.substringAfter("://")
        val json = decodeB64(b64) ?: return null
        val o = JSONObject(json)
        val portRaw = o.opt("port")
        val port = when (portRaw) {
            is Number -> portRaw.toInt()
            is String -> portRaw.toIntOrNull() ?: 443
            else -> 443
        }
        val aidRaw = o.opt("aid")
        val aid = when (aidRaw) {
            is Number -> aidRaw.toInt()
            is String -> aidRaw.toIntOrNull() ?: 0
            else -> 0
        }
        val tls = o.optString("tls", "").lowercase()
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = o.optString("ps", "").ifBlank { o.optString("add", "VMess") },
            protocol = "VMESS",
            serverAddress = o.optString("add", ""),
            serverPort = if (port in 1..65535) port else 443,
            uuid = o.optString("id", ""),
            alterId = aid,
            encryption = o.optString("scy", "auto").ifBlank { "auto" },
            network = o.optString("net", "tcp").ifBlank { "tcp" }.lowercase(),
            wsHost = o.optString("host", ""),
            wsPath = o.optString("path", ""),
            headerType = o.optString("type", "").lowercase(),
            security = if (tls == "tls") "tls" else "none",
            sni = o.optString("sni", ""),
            fingerprint = o.optString("fp", ""),
            alpn = o.optString("alpn", ""),
            source = "manual"
        )
    }

    // ---------------------------------------------------------------- vless / trojan

    private fun parseVless(uri: String): VpnProfile? {
        val (authority, q, name) = splitUri(uri)
        val at = authority.lastIndexOf('@')
        if (at < 0) return null
        val uuid = authority.substring(0, at)
        val hp = splitHostPort(authority.substring(at + 1)) ?: return null
        val security = (q["security"] ?: "").lowercase().ifBlank {
            if (q.containsKey("pbk")) "reality" else "none"
        }
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { hp.first },
            protocol = "VLESS",
            serverAddress = hp.first,
            serverPort = hp.second,
            uuid = uuid,
            security = security,
            sni = q["sni"] ?: "",
            fingerprint = q["fp"] ?: "",
            publicKey = q["pbk"] ?: "",
            shortId = q["sid"] ?: "",
            network = (q["type"] ?: "tcp").lowercase(),
            wsPath = q["path"] ?: "",
            wsHost = q["host"] ?: "",
            headerType = (q["headerType"] ?: "").lowercase(),
            flow = q["flow"] ?: "",
            encryption = (q["encryption"] ?: "none").ifBlank { "none" },
            alpn = q["alpn"] ?: "",
            allowInsecure = q["allowInsecure"] == "1" || (q["allowInsecure"] ?: "").lowercase() == "true",
            source = "manual"
        )
    }

    private fun parseTrojan(uri: String): VpnProfile? {
        val (authority, q, name) = splitUri(uri)
        val at = authority.lastIndexOf('@')
        if (at < 0) return null
        val password = authority.substring(0, at)
        val hp = splitHostPort(authority.substring(at + 1)) ?: return null
        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { hp.first },
            protocol = "TROJAN",
            serverAddress = hp.first,
            serverPort = hp.second,
            password = password,
            security = (q["security"] ?: "tls").ifBlank { "tls" }.lowercase(),
            sni = q["sni"] ?: "",
            fingerprint = q["fp"] ?: "",
            network = (q["type"] ?: "tcp").lowercase(),
            wsPath = q["path"] ?: "",
            wsHost = q["host"] ?: "",
            headerType = (q["headerType"] ?: "").lowercase(),
            alpn = q["alpn"] ?: "",
            allowInsecure = q["allowInsecure"] == "1" || (q["allowInsecure"] ?: "").lowercase() == "true",
            source = "manual"
        )
    }

    // ---------------------------------------------------------------- shadowsocks

    private fun parseShadowsocks(uri: String): VpnProfile? {
        var rest = uri.substring(5) // strip ss://
        var name = ""
        val hashIdx = rest.indexOf('#')
        if (hashIdx >= 0) {
            name = safeDecode(rest.substring(hashIdx + 1))
            rest = rest.substring(0, hashIdx)
        }
        val qIdx = rest.indexOf('?')
        if (qIdx >= 0) rest = rest.substring(0, qIdx)

        return if (rest.contains('@')) {
            // SIP002: ss://base64(method:password)@host:port
            val at = rest.lastIndexOf('@')
            val userInfo = rest.substring(0, at)
            val hp = splitHostPort(rest.substring(at + 1)) ?: return null
            val mp = decodeB64(userInfo) ?: userInfo
            val ci = mp.indexOf(':')
            if (ci < 0) return null
            VpnProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { hp.first },
                protocol = "SHADOWSOCKS",
                serverAddress = hp.first,
                serverPort = hp.second,
                method = mp.substring(0, ci),
                password = mp.substring(ci + 1),
                source = "manual"
            )
        } else {
            // Legacy: ss://base64(method:password@host:port)
            val decoded = decodeB64(rest) ?: return null
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            val mp = decoded.substring(0, at)
            val ci = mp.indexOf(':')
            if (ci < 0) return null
            val hp = splitHostPort(decoded.substring(at + 1)) ?: return null
            VpnProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { hp.first },
                protocol = "SHADOWSOCKS",
                serverAddress = hp.first,
                serverPort = hp.second,
                method = mp.substring(0, ci),
                password = mp.substring(ci + 1),
                source = "manual"
            )
        }
    }

    // ---------------------------------------------------------------- share URI

    /** Rebuild a shareable URI from a profile (used for QR codes). */
    fun toUri(p: VpnProfile): String {
        return when (p.protocol.uppercase()) {
            "VMESS" -> {
                val o = JSONObject()
                o.put("v", "2")
                o.put("ps", p.name)
                o.put("add", p.serverAddress)
                o.put("port", p.serverPort.toString())
                o.put("id", p.uuid)
                o.put("aid", p.alterId.toString())
                o.put("scy", p.encryption.ifBlank { "auto" })
                o.put("net", p.network.ifBlank { "tcp" })
                o.put("host", p.wsHost)
                o.put("path", p.wsPath)
                o.put("type", p.headerType)
                o.put("tls", if (p.security == "tls") "tls" else "")
                o.put("sni", p.sni)
                o.put("fp", p.fingerprint)
                "vmess://" + encodeB64(o.toString())
            }
            "VLESS" -> buildString {
                append("vless://").append(p.uuid).append('@').append(p.serverAddress)
                append(':').append(p.serverPort)
                val q = mutableListOf<String>()
                q.add("encryption=${p.encryption.ifBlank { "none" }}")
                q.add("security=${p.security.ifBlank { "none" }}")
                if (p.sni.isNotBlank()) q.add("sni=${p.sni}")
                if (p.fingerprint.isNotBlank()) q.add("fp=${p.fingerprint}")
                if (p.publicKey.isNotBlank()) q.add("pbk=${p.publicKey}")
                if (p.shortId.isNotBlank()) q.add("sid=${p.shortId}")
                if (p.flow.isNotBlank()) q.add("flow=${p.flow}")
                q.add("type=${p.network.ifBlank { "tcp" }}")
                if (p.wsHost.isNotBlank()) q.add("host=${p.wsHost}")
                if (p.wsPath.isNotBlank()) q.add("path=${p.wsPath}")
                if (p.headerType.isNotBlank()) q.add("headerType=${p.headerType}")
                if (p.alpn.isNotBlank()) q.add("alpn=${p.alpn}")
                if (p.allowInsecure) q.add("allowInsecure=1")
                append('?').append(q.joinToString("&"))
                append('#').append(urlEncode(p.name))
            }
            "TROJAN" -> buildString {
                append("trojan://").append(p.password).append('@').append(p.serverAddress)
                append(':').append(p.serverPort)
                val q = mutableListOf<String>()
                q.add("security=${p.security.ifBlank { "tls" }}")
                if (p.sni.isNotBlank()) q.add("sni=${p.sni}")
                if (p.fingerprint.isNotBlank()) q.add("fp=${p.fingerprint}")
                q.add("type=${p.network.ifBlank { "tcp" }}")
                if (p.wsHost.isNotBlank()) q.add("host=${p.wsHost}")
                if (p.wsPath.isNotBlank()) q.add("path=${p.wsPath}")
                if (p.allowInsecure) q.add("allowInsecure=1")
                append('?').append(q.joinToString("&"))
                append('#').append(urlEncode(p.name))
            }
            else -> buildString {
                append("ss://")
                append(encodeB64("${p.method}:${p.password}@${p.serverAddress}:${p.serverPort}"))
                if (p.name.isNotBlank()) append('#').append(urlEncode(p.name))
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private data class Parts(val authority: String, val query: Map<String, String>, val name: String)

    private fun splitUri(uri: String): Parts {
        var rest = uri.substringAfter("://")
        var name = ""
        val hashIdx = rest.indexOf('#')
        if (hashIdx >= 0) {
            name = safeDecode(rest.substring(hashIdx + 1))
            rest = rest.substring(0, hashIdx)
        }
        var query: MutableMap<String, String> = mutableMapOf()
        val qIdx = rest.indexOf('?')
        if (qIdx >= 0) {
            rest.substring(qIdx + 1).split('&').forEach { pair ->
                val i = pair.indexOf('=')
                if (i > 0) {
                    query[safeDecode(pair.substring(0, i))] = safeDecode(pair.substring(i + 1))
                }
            }
            rest = rest.substring(0, qIdx)
        }
        return Parts(rest, query, name)
    }

    private fun splitHostPort(hp: String): Pair<String, Int>? {
        return try {
            if (hp.startsWith("[")) {
                val end = hp.indexOf(']')
                if (end < 0) return null
                val host = hp.substring(1, end)
                val after = hp.substring(end + 1)
                val port = if (after.startsWith(":")) after.substring(1).toIntOrNull() ?: 443 else 443
                host to port
            } else {
                val i = hp.lastIndexOf(':')
                if (i < 0) hp to 443
                else hp.substring(0, i) to (hp.substring(i + 1).toIntOrNull() ?: 443)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun safeDecode(s: String): String =
        try {
            URLDecoder.decode(s, "UTF-8")
        } catch (e: Exception) {
            s
        }

    private fun urlEncode(s: String): String =
        try {
            URLEncoder.encode(s, "UTF-8")
        } catch (e: Exception) {
            s
        }

    fun decodeB64(input: String): String? {
        return try {
            var s = input.trim().replace('-', '+').replace('_', '/').trimEnd('=')
            s += "=".repeat((4 - s.length % 4) % 4)
            val bytes = Base64.decode(s, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun encodeB64(input: String): String =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
