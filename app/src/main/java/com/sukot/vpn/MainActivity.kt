package com.sukot.vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sukot.vpn.core.VpnState
import com.sukot.vpn.service.SukotVpnService
import com.sukot.vpn.ui.AppRoot
import com.sukot.vpn.ui.AppViewModel
import com.sukot.vpn.ui.theme.SukotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.launchConnect(this)
            } else {
                viewModel.onVpnPermissionDenied()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SukotVpnService.ACTION_STATE) return
            val ordinal = intent.getIntExtra(SukotVpnService.EXTRA_STATE, 0)
            val state = VpnState.entries.getOrElse(ordinal) { VpnState.DISCONNECTED }
            val rx = intent.getLongExtra(SukotVpnService.EXTRA_RX, 0)
            val tx = intent.getLongExtra(SukotVpnService.EXTRA_TX, 0)
            val seconds = intent.getLongExtra(SukotVpnService.EXTRA_SECONDS, 0)
            val message = intent.getStringExtra(SukotVpnService.EXTRA_MESSAGE)
            viewModel.onServiceState(state, rx, tx, seconds, message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            SukotTheme {
                VpnBridge(viewModel, ::requestVpnPermission)
                AppRoot(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SukotVpnService.ACTION_STATE)
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(stateReceiver)
        } catch (e: Exception) {
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            viewModel.launchConnect(this)
        } else {
            vpnPermissionLauncher.launch(intent)
        }
    }
}

@Composable
private fun VpnBridge(viewModel: AppViewModel, requestPermission: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.setPermissionRequester(requestPermission)
    }

    DisposableEffect(context) {
        onDispose { }
    }
}
