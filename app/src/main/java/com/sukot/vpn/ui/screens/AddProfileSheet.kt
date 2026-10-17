package com.sukot.vpn.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sukot.vpn.R
import com.sukot.vpn.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileSheet(viewModel: AppViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    val fetch by viewModel.fetch.collectAsState()
    val scanPrompt = stringResource(R.string.scan_qr)

    var cameraGranted by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { uri ->
            val count = viewModel.addFromText(uri)
            if (count > 0) onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.add_title),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.paste_hint), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(10.dp))

            Row {
                Button(
                    onClick = {
                        val count = viewModel.addFromText(text)
                        if (count > 0) onDismiss()
                    },
                    enabled = text.isNotBlank()
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.import_clipboard))
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = {
                        if (cameraGranted) {
                            val options = ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(scanPrompt)
                                .setBeepEnabled(false)
                                .setOrientationLocked(true)
                            qrLauncher.launch(options)
                        } else {
                            cameraLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_qr))
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { viewModel.fetchFreeConfigs() },
                enabled = !fetch.running,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (fetch.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fetch_free))
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = subUrl,
                onValueChange = { subUrl = it },
                placeholder = { Text(stringResource(R.string.subscription_url), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    viewModel.addSubscription(subUrl.trim())
                    subUrl = ""
                    onDismiss()
                },
                enabled = subUrl.trim().startsWith("http"),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_subscription))
            }
        }
    }
}
