package com.sukot.vpn.data

import android.os.SystemClock
import com.sukot.vpn.model.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP-handshake based latency testing. Measures how fast the proxy server
 * answers a plain TCP connection — a good reachability/latency proxy that
 * works without establishing a full tunnel.
 */
object SpeedTester {

    suspend fun ping(host: String, port: Int, timeoutMs: Int = 3000): Int? =
        withContext(Dispatchers.IO) {
            try {
                val start = SystemClock.elapsedRealtime()
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                (SystemClock.elapsedRealtime() - start).toInt()
            } catch (e: Exception) {
                null
            }
        }

    /** Ping many profiles with bounded concurrency; emits results as they arrive. */
    suspend fun pingAll(
        profiles: List<VpnProfile>,
        concurrency: Int = 6,
        attempts: Int = 2,
        onResult: (String, Int?) -> Unit
    ) = coroutineScope {
        val semaphore = Semaphore(concurrency)
        profiles.forEach { profile ->
            launch {
                semaphore.withPermit {
                    var best: Int? = null
                    repeat(attempts) {
                        val r = ping(profile.serverAddress, profile.serverPort)
                        if (r != null && (best == null || r < best!!)) best = r
                    }
                    onResult(profile.id, best)
                }
            }
        }
    }
}
