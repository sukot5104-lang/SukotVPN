package com.sukot.vpn.data

import android.content.Context
import com.sukot.vpn.model.VpnProfile
import com.sukot.vpn.util.ProfileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads free public configs, subscription bodies and geo rule files.
 */
object Fetcher {

    private val CONFIG_SOURCES = listOf(
        "https://cdn.jsdelivr.net/gh/Epodonios/v2ray-configs@main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
        "https://cdn.jsdelivr.net/gh/mahdibland/V2RayAggregator@master/sub/sub_merge.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
        "https://cdn.jsdelivr.net/gh/ALIILAPRO/v2rayNG-Config@main/config.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/config.txt"
    )

    private const val GEO_SITE_URL =
        "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geosite.dat"
    private const val GEO_IP_URL =
        "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geoip.dat"

    /** Try every known free-config source until enough profiles are collected. */
    suspend fun fetchFreeConfigs(): List<VpnProfile> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<VpnProfile>()
        for (url in CONFIG_SOURCES) {
            if (collected.size >= 300) break
            val body = try {
                httpGet(url, timeoutMs = 12000)
            } catch (e: Exception) {
                null
            } ?: continue
            val parsed = ProfileParser.parseMulti(body)
            collected.addAll(parsed)
        }
        // de-duplicate across sources
        val seen = HashSet<String>()
        val out = mutableListOf<VpnProfile>()
        for (p in collected) {
            val key = "${p.protocol}|${p.serverAddress}|${p.serverPort}|${p.uuid}|${p.password}"
            if (seen.add(key)) out.add(p.copy(source = "free"))
            if (out.size >= 300) break
        }
        out
    }

    /** Fetch a user subscription and parse it. */
    suspend fun fetchSubscription(url: String): List<VpnProfile> = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(url, timeoutMs = 15000)
        } catch (e: Exception) {
            ""
        }
        if (body.isBlank()) emptyList() else ProfileParser.parseMulti(body)
    }

    /** Download geo rule files (Iran-aware) into filesDir. Returns a human message. */
    suspend fun fetchGeoFiles(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val site = File(context.filesDir, "geosite.dat")
            val ip = File(context.filesDir, "geoip.dat")
            downloadTo(GEO_SITE_URL, site)
            downloadTo(GEO_IP_URL, ip)
            if (site.length() > 100_000 && ip.length() > 100_000) "ok"
            else "downloaded files look truncated"
        } catch (e: Exception) {
            "error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun hasGeoFiles(context: Context): Boolean =
        File(context.filesDir, "geosite.dat").let { it.exists() && it.length() > 100_000 } &&
            File(context.filesDir, "geoip.dat").let { it.exists() && it.length() > 100_000 }

    // ------------------------------------------------------------ internals

    private fun httpGet(url: String, timeoutMs: Int): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs + 5000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "v2rayNG/1.9.16")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File) {
        val tmp = File(target.absolutePath + ".tmp")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "SukotVPN/1.0")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
