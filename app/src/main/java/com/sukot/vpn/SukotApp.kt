package com.sukot.vpn

import android.app.Application
import com.sukot.vpn.service.NotificationHelper

class SukotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }
}
