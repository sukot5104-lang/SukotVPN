package com.sukot.vpn

import android.app.Application
import com.sukot.vpn.data.GeoAssets
import com.sukot.vpn.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SukotApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        // Install bundled geo rule files early so the first connect always works.
        appScope.launch { GeoAssets.ensure(this@SukotApp) }
    }
}
