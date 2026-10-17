package com.sukot.vpn.core

import android.content.Context
import android.provider.Settings
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.security.MessageDigest

/**
 * Thin wrapper around the AndroidLibXrayLite (libv2ray) engine.
 *
 * Exact gobind API (v26.10.10):
 *   Libv2ray.initCoreEnv(envPath, key)
 *   Libv2ray.newCoreController(handler) -> CoreController
 *   controller.startLoop(configContent: String, tunFd: Int)
 *   controller.stopLoop()
 *   controller.getIsRunning()
 *   controller.queryAllOutboundTrafficStats(): String  "tag,direction,value;..."
 */
class VpnCore(private val context: Context) : CoreCallbackHandler {

    private var controller: CoreController? = null

    fun init() {
        Seq.setContext(context)
        Libv2ray.initCoreEnv(context.filesDir.absolutePath, deviceId())
    }

    @Throws(Exception::class)
    fun connect(configJson: String, tunFd: Int) {
        stop()
        controller = Libv2ray.newCoreController(this)
        controller?.startLoop(configJson, tunFd)
    }

    fun stop() {
        try {
            controller?.stopLoop()
        } catch (e: Exception) {
            Log.w(TAG, "stopLoop: ${e.message}")
        }
        controller = null
    }

    fun isRunning(): Boolean =
        try {
            controller?.getIsRunning() ?: false
        } catch (e: Exception) {
            false
        }

    /** "tag,direction,value;tag,direction,value;..." — counters are reset after read. */
    fun readTrafficStats(): String =
        try {
            controller?.queryAllOutboundTrafficStats() ?: ""
        } catch (e: Exception) {
            ""
        }

    // ------------------------------------------------------------ callbacks

    override fun startup(): Long = 0

    override fun shutdown(): Long = 0

    override fun onEmitStatus(l: Long, s: String?): Long {
        if (s != null && s.length > 512) {
            Log.d(TAG, "status(${l}): ${s.take(512)}…")
        } else {
            Log.d(TAG, "status(${l}): $s")
        }
        return 0
    }

    // ------------------------------------------------------------ helpers

    private fun deviceId(): String = try {
        val androidId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "sukot"
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        digest.joinToString("") { "%02x".format(it) }.take(32)
    } catch (e: Exception) {
        "sukot-default-device-key-0000000000000000"
    }

    companion object {
        private const val TAG = "SukotVpnCore"
    }
}
