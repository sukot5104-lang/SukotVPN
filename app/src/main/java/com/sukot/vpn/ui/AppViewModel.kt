package com.sukot.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sukot.vpn.core.VpnState
import com.sukot.vpn.data.Fetcher
import com.sukot.vpn.data.GeoAssets
import com.sukot.vpn.data.SpeedTester
import com.sukot.vpn.data.Store
import com.sukot.vpn.data.SukotSettings
import com.sukot.vpn.model.VpnProfile
import com.sukot.vpn.service.SukotVpnService
import com.sukot.vpn.util.ProfileParser
import com.sukot.vpn.xray.XrayConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Hashtable

data class PingState(
    val running: Boolean = false,
    val results: Map<String, Int?> = emptyMap()
)

data class FetchState(
    val running: Boolean = false,
    val message: String? = null
)

data class QrRequest(val name: String, val bitmap: Bitmap)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = Store(application)

    private val _profiles = MutableStateFlow<List<VpnProfile>>(store.loadProfiles())
    val profiles: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()

    private val _selectedId = MutableStateFlow(store.loadSelectedId())
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<SukotSettings> = _settings.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<String>>(store.loadSubscriptions())
    val subscriptions: StateFlow<List<String>> = _subscriptions.asStateFlow()

    private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _traffic = MutableStateFlow(Triple(0L, 0L, 0L)) // rx, tx, seconds
    val traffic: StateFlow<Triple<Long, Long, Long>> = _traffic.asStateFlow()

    private val _ping = MutableStateFlow(PingState())
    val ping: StateFlow<PingState> = _ping.asStateFlow()

    private val _fetch = MutableStateFlow(FetchState())
    val fetch: StateFlow<FetchState> = _fetch.asStateFlow()

    private val _qr = MutableStateFlow<QrRequest?>(null)
    val qr: StateFlow<QrRequest?> = _qr.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var permissionRequester: (() -> Unit)? = null
    private var connectRequested = false

    fun setPermissionRequester(requester: () -> Unit) {
        permissionRequester = requester
        if (connectRequested) {
            connectRequested = false
            requester()
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    // ------------------------------------------------------------ profiles

    fun select(id: String) {
        _selectedId.value = id
        store.saveSelectedId(id)
    }

    fun delete(id: String) {
        _profiles.update { list -> list.filterNot { it.id == id } }
        store.saveProfiles(_profiles.value)
        if (_selectedId.value == id) select(_profiles.value.firstOrNull()?.id ?: "")
    }

    fun addFromText(text: String): Int {
        val parsed = ProfileParser.parseMulti(text)
        if (parsed.isEmpty()) return 0
        val existing = _profiles.value.toMutableList()
        existing.addAll(parsed)
        _profiles.value = existing
        store.saveProfiles(existing)
        if (_selectedId.value.isBlank()) select(parsed.first().id)
        return parsed.size
    }

    fun selectedProfile(): VpnProfile? =
        _profiles.value.firstOrNull { it.id == _selectedId.value }

    // ------------------------------------------------------------ QR

    fun showQr(profile: VpnProfile) {
        viewModelScope.launch(Dispatchers.Default) {
            val uri = ProfileParser.toUri(profile)
            val bitmap = generateQrBitmap(uri, 620)
            bitmap?.let { _qr.value = QrRequest(profile.name, it) }
        }
    }

    fun dismissQr() {
        _qr.value = null
    }

    private fun generateQrBitmap(text: String, size: Int): Bitmap? {
        return try {
            val hints = Hashtable<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = 1
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (matrix.get(x, y)) 0xFF0B0514.toInt() else 0xFFF3EEFF.toInt()
                }
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, size, 0, 0, size, size)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ connection

    fun requestConnect() {
        val requester = permissionRequester
        if (requester == null) {
            connectRequested = true
        } else {
            requester()
        }
    }

    /** Called by MainActivity after VPN permission is granted. */
    fun launchConnect(context: Context) {
        val profile = selectedProfile() ?: return
        val currentSettings = _settings.value
        GeoAssets.ensure(context) // bundled geo rules — no network needed
        val hasGeo = Fetcher.hasGeoFiles(context)
        val config = XrayConfigBuilder.build(profile, currentSettings, hasGeo)
        val intent = Intent(context, SukotVpnService::class.java)
            .setAction(SukotVpnService.ACTION_CONNECT)
            .putExtra(SukotVpnService.EXTRA_CONFIG, config)
            .putExtra(SukotVpnService.EXTRA_MTU, currentSettings.mtu)
        _vpnState.value = VpnState.CONNECTING
        ContextCompat.startForegroundService(context, intent)
    }

    fun onVpnPermissionDenied() {
        _vpnState.value = VpnState.DISCONNECTED
        _toast.value = getApplication<Application>().getString(
            getApplication<Application>().resources.getIdentifier("vpn_permission_denied", "string", getApplication<Application>().packageName)
        )
    }

    fun disconnect(context: Context) {
        _vpnState.value = VpnState.DISCONNECTING
        val intent = Intent(context, SukotVpnService::class.java)
            .setAction(SukotVpnService.ACTION_STOP)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Receives state broadcasts from the daemon service (cross-process). */
    fun onServiceState(state: VpnState, rx: Long, tx: Long, seconds: Long, message: String?) {
        _vpnState.value = state
        _traffic.value = Triple(rx, tx, seconds)
        if (message != null) _toast.value = message
    }

    // ------------------------------------------------------------ ping

    fun pingOne(id: String) {
        val profile = _profiles.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _ping.update { it.copy(results = it.results + (id to null)) }
            val r = SpeedTester.ping(profile.serverAddress, profile.serverPort)
            _ping.update { it.copy(results = it.results + (id to r)) }
        }
    }

    fun pingAll() {
        if (_ping.value.running) return
        val list = _profiles.value
        if (list.isEmpty()) return
        viewModelScope.launch {
            _ping.value = PingState(running = true, results = emptyMap())
            SpeedTester.pingAll(list) { id, result ->
                _ping.update { it.copy(results = it.results + (id to result)) }
            }
            _ping.value = _ping.value.copy(running = false)
        }
    }

    fun connectFastest() {
        if (_ping.value.running) return
        val list = _profiles.value
        if (list.isEmpty()) return
        viewModelScope.launch {
            _ping.value = PingState(running = true, results = emptyMap())
            SpeedTester.pingAll(list) { id, result ->
                _ping.update { it.copy(results = it.results + (id to result)) }
            }
            _ping.value = _ping.value.copy(running = false)
            val best = _ping.value.results
                .filterValues { it != null }
                .minByOrNull { it.value!! }?.key
            if (best != null) {
                select(best)
                _toast.value = getApplication<Application>().getString(
                    getApplication<Application>().resources.getIdentifier("connected_fastest", "string", getApplication<Application>().packageName),
                    _profiles.value.firstOrNull { it.id == best }?.name ?: ""
                )
                requestConnect()
            } else {
                _toast.value = getApplication<Application>().getString(
                    getApplication<Application>().resources.getIdentifier("fetch_failed", "string", getApplication<Application>().packageName)
                )
            }
        }
    }

    // ------------------------------------------------------------ fetch free / subscriptions

    fun fetchFreeConfigs() {
        if (_fetch.value.running) return
        viewModelScope.launch {
            _fetch.value = FetchState(running = true, message = null)
            val fetched = Fetcher.fetchFreeConfigs()
            if (fetched.isEmpty()) {
                _fetch.value = FetchState(
                    running = false,
                    message = getAppString("fetch_failed")
                )
            } else {
                val existing = _profiles.value.toMutableList()
                val known = existing.map { "${it.protocol}|${it.serverAddress}|${it.serverPort}|${it.uuid}|${it.password}" }.toMutableSet()
                val fresh = fetched.filter { known.add("${it.protocol}|${it.serverAddress}|${it.serverPort}|${it.uuid}|${it.password}") }
                existing.addAll(fresh)
                _profiles.value = existing
                store.saveProfiles(existing)
                if (_selectedId.value.isBlank()) select(fresh.first().id)
                _fetch.value = FetchState(
                    running = false,
                    message = getAppString("fetched_count").let { tpl ->
                        if (tpl.contains("%1\$d")) String.format(tpl, fresh.size) else tpl
                    }
                )
            }
        }
    }

    fun addSubscription(url: String) {
        val clean = url.trim()
        if (clean.isBlank() || _subscriptions.value.contains(clean)) return
        val list = _subscriptions.value.toMutableList()
        list.add(clean)
        _subscriptions.value = list
        store.saveSubscriptions(list)
        updateSubscription(clean)
    }

    fun removeSubscription(url: String) {
        val list = _subscriptions.value.filterNot { it == url }
        _subscriptions.value = list
        store.saveSubscriptions(list)
    }

    fun updateAllSubscriptions() {
        _subscriptions.value.forEach { updateSubscription(it) }
    }

    private fun updateSubscription(url: String) {
        viewModelScope.launch {
            _fetch.value = FetchState(running = true, message = null)
            val fetched = Fetcher.fetchSubscription(url)
            if (fetched.isNotEmpty()) {
                val existing = _profiles.value
                    .filterNot { it.subId == url && it.source == "subscription" }
                    .toMutableList()
                val tagged = fetched.map { it.copy(source = "subscription", subId = url) }
                existing.addAll(tagged)
                _profiles.value = existing
                store.saveProfiles(existing)
                _fetch.value = FetchState(
                    running = false,
                    message = String.format(getAppString("subscription_updated"), tagged.size)
                )
            } else {
                _fetch.value = FetchState(running = false, message = getAppString("fetch_failed"))
            }
        }
    }

    // ------------------------------------------------------------ settings

    fun updateSettings(transform: (SukotSettings) -> SukotSettings) {
        _settings.value = transform(_settings.value)
        store.saveSettings(_settings.value)
    }

    fun downloadGeoFiles() {
        if (_fetch.value.running) return
        viewModelScope.launch {
            _fetch.value = FetchState(running = true, message = null)
            val result = Fetcher.fetchGeoFiles(getApplication())
            _fetch.value = FetchState(
                running = false,
                message = if (result == "ok") getAppString("geo_done") else getAppString("geo_failed") + " ($result)"
            )
        }
    }

    // ------------------------------------------------------------ helpers

    private fun getAppString(key: String): String {
        val app = getApplication<Application>()
        val id = app.resources.getIdentifier(key, "string", app.packageName)
        return if (id != 0) app.getString(id) else key
    }
}
