package com.sukot.vpn.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.sukot.vpn.core.VpnCore
import com.sukot.vpn.core.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * The VPN service. Runs in the ":daemon" process so a native core crash
 * never takes the UI down. Communication with the UI happens through
 * explicit-package broadcasts (cross-process safe).
 */
class SukotVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var core: VpnCore? = null
    private var tunFd: Int = -1
    private var connectTime: Long = 0
    private var totalRx: Long = 0
    private var totalTx: Long = 0

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                startForegroundCompat(connected = false)
                broadcast(VpnState.CONNECTING)
                val config = intent.getStringExtra(EXTRA_CONFIG)
                val mtu = intent.getIntExtra(EXTRA_MTU, 8500)
                if (config.isNullOrBlank()) {
                    stopVpn("empty configuration")
                    return START_NOT_STICKY
                }
                scope.launch {
                    try {
                        val fd = establishTun(mtu)
                        val vpnCore = VpnCore(applicationContext)
                        vpnCore.init()
                        core = vpnCore
                        vpnCore.connect(config, fd)
                        connectTime = SystemClock.elapsedRealtime()
                        totalRx = 0
                        totalTx = 0
                        startForegroundCompat(connected = true)
                        broadcast(VpnState.CONNECTED)
                        startStatsLoop()
                    } catch (e: Exception) {
                        Log.e(TAG, "connect failed", e)
                        stopVpn(e.message ?: e.javaClass.simpleName)
                    }
                }
                return START_STICKY
            }
            else -> {
                // Restart without data — cannot reconnect safely.
                if (intent == null) {
                    stopVpn()
                    return START_NOT_STICKY
                }
                return START_NOT_STICKY
            }
        }
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ tunnel

    private fun establishTun(mtu: Int): Int {
        val builder = this.Builder()
        builder.setSession("Sukot VPN")
        builder.setMtu(mtu)
        builder.addAddress("198.18.0.2", 32)
        builder.addDnsServer("1.1.1.1")
        builder.addDnsServer("8.8.8.8")
        builder.addRoute("0.0.0.0", 0)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            builder.setBlocking(true)
        }
        val pfd = builder.establish()
            ?: throw IllegalStateException("VPN permission denied (establish returned null)")
        val fd = pfd.detachFd()
        tunFd = fd
        return fd
    }

    private fun stopVpn(errorMessage: String? = null) {
        try {
            core?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "core stop: ${e.message}")
        }
        core = null
        if (tunFd >= 0) {
            try {
                ParcelFileDescriptor.adoptFd(tunFd).close()
            } catch (e: Exception) {
                Log.w(TAG, "close tun: ${e.message}")
            }
            tunFd = -1
        }
        broadcast(if (errorMessage != null) VpnState.ERROR else VpnState.DISCONNECTED, errorMessage)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------ stats

    private fun startStatsLoop() {
        scope.launch {
            while (isActive && tunFd >= 0) {
                delay(2000)
                val stats = core?.readTrafficStats() ?: ""
                if (stats.isNotBlank()) {
                    parseStats(stats)?.let { (rx, tx) ->
                        // counters reset after each query -> accumulate
                        totalRx += rx
                        totalTx += tx
                    }
                } else {
                    // fallback: TUN interface counters (absolute, not incremental)
                    readSysfs()?.let { (rx, tx) ->
                        if (rx > totalRx) totalRx = rx
                        if (tx > totalTx) totalTx = tx
                    }
                }
                broadcast(VpnState.CONNECTED)
            }
        }
    }

    /** Returns (down, up) incremental bytes parsed from "tag,direction,value;..." */
    private fun parseStats(stats: String): Pair<Long, Long>? {
        var rx = 0L
        var tx = 0L
        var any = false
        stats.split(';').forEach { entry ->
            val parts = entry.split(',')
            if (parts.size >= 3) {
                val value = parts[2].trim().toLongOrNull() ?: return@forEach
                val dir = parts[1].trim().lowercase()
                any = true
                when {
                    dir.contains("down") -> rx += value
                    dir.contains("up") -> tx += value
                }
            }
        }
        return if (any) rx to tx else null
    }

    private fun readSysfs(): Pair<Long, Long>? {
        return try {
            val base = File("/sys/class/net/tun0/statistics")
            val rx = File(base, "rx_bytes").readText().trim().toLongOrNull() ?: 0L
            val tx = File(base, "tx_bytes").readText().trim().toLongOrNull() ?: 0L
            rx to tx
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ misc

    private fun startForegroundCompat(connected: Boolean) {
        startForeground(NotificationHelper.NOTIF_ID, NotificationHelper.build(this, connected))
    }

    private fun broadcast(state: VpnState, message: String? = null) {
        val seconds = if (connectTime > 0) (SystemClock.elapsedRealtime() - connectTime) / 1000 else 0
        val intent = Intent(ACTION_STATE).setPackage(packageName)
            .putExtra(EXTRA_STATE, state.ordinal)
            .putExtra(EXTRA_RX, totalRx)
            .putExtra(EXTRA_TX, totalTx)
            .putExtra(EXTRA_SECONDS, seconds)
        if (message != null) intent.putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "SukotVpnService"

        const val ACTION_CONNECT = "com.sukot.vpn.action.CONNECT"
        const val ACTION_STOP = "com.sukot.vpn.action.STOP"
        const val ACTION_STATE = "com.sukot.vpn.action.STATE"

        const val EXTRA_CONFIG = "config_json"
        const val EXTRA_MTU = "mtu"
        const val EXTRA_STATE = "state"
        const val EXTRA_RX = "rx"
        const val EXTRA_TX = "tx"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_MESSAGE = "message"
    }
}
