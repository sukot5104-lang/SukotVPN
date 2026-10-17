package com.sukot.vpn.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Ensures the Xray geo rule files (geoip.dat / geosite.dat) exist in filesDir.
 *
 * The files are BUNDLED in the APK (assets/geo/) so the VPN works on the very
 * first connect with no internet dependency — critical because downloading
 * them from GitHub inside Iran requires an already-working VPN.
 *
 * Users can still refresh them via Fetcher.fetchGeoFiles() (jsDelivr mirrors).
 */
object GeoAssets {

    private const val TAG = "SukotGeoAssets"
    const val MIN_GEOIP = 20_000L      // bundled mini geoip ~36 KB
    const val MIN_GEOSITE = 500_000L   // bundled mini geosite ~2.3 MB

    /** Idempotent, cheap when files already exist (just two stat calls). */
    fun ensure(context: Context) {
        copyIfMissing(context, "geoip.dat", MIN_GEOIP)
        copyIfMissing(context, "geosite.dat", MIN_GEOSITE)
    }

    fun isComplete(context: Context): Boolean {
        val ip = File(context.filesDir, "geoip.dat")
        val site = File(context.filesDir, "geosite.dat")
        return ip.exists() && ip.length() >= MIN_GEOIP &&
            site.exists() && site.length() >= MIN_GEOSITE
    }

    private fun copyIfMissing(context: Context, name: String, minSize: Long = 0) {
        val target = File(context.filesDir, name)
        if (target.exists() && target.length() >= minSize) return
        try {
            context.assets.open("geo/$name").use { input ->
                val tmp = File(context.filesDir, "$name.tmp")
                tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                if (target.length() < minSize && target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
            Log.i(TAG, "installed bundled $name (${File(context.filesDir, name).length()} bytes)")
        } catch (e: Exception) {
            Log.w(TAG, "install $name failed: ${e.message}")
        }
    }
}
