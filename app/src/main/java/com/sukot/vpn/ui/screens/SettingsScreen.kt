package com.sukot.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukot.vpn.BuildConfig
import com.sukot.vpn.R
import com.sukot.vpn.ui.AppViewModel
import com.sukot.vpn.ui.theme.TextSecondary

@Composable
fun SettingsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()
    val fetch by viewModel.fetch.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()

    var subUrl by remember { mutableStateOf("") }
    var mtuText by remember(settings.mtu) { mutableStateOf(settings.mtu.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionTitle(stringResource(R.string.general_settings))
        SettingsCard {
            SettingSwitch(
                title = stringResource(R.string.bypass_iran),
                subtitle = stringResource(R.string.bypass_iran_desc),
                checked = settings.bypassIran,
                onChange = { viewModel.updateSettings { s -> s.copy(bypassIran = it) } }
            )
            SettingSwitch(
                title = stringResource(R.string.block_quic),
                subtitle = stringResource(R.string.block_quic_desc),
                checked = settings.blockQuic,
                onChange = { viewModel.updateSettings { s -> s.copy(blockQuic = it) } }
            )
            SettingSwitch(
                title = stringResource(R.string.block_ads),
                subtitle = stringResource(R.string.block_ads_desc),
                checked = settings.blockAds,
                onChange = { viewModel.updateSettings { s -> s.copy(blockAds = it) } }
            )
            SettingSwitch(
                title = stringResource(R.string.block_torrent),
                subtitle = stringResource(R.string.block_torrent_desc),
                checked = settings.blockTorrent,
                onChange = { viewModel.updateSettings { s -> s.copy(blockTorrent = it) } }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mtu_label), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                OutlinedTextField(
                    value = mtuText,
                    onValueChange = { v ->
                        mtuText = v.filter { it.isDigit() }.take(5)
                        mtuText.toIntOrNull()?.let {
                            if (it in 1200..9000) viewModel.updateSettings { s -> s.copy(mtu = it) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.geo_section))
        SettingsCard {
            Button(
                onClick = { viewModel.downloadGeoFiles() },
                enabled = !fetch.running,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (fetch.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.download_geo), fontSize = 13.sp)
            }
            fetch.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.subscription_section))
        SettingsCard {
            if (subscriptions.isEmpty()) {
                Text(
                    stringResource(R.string.subscriptions_empty),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            } else {
                subscriptions.forEach { url ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            url,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            color = TextSecondary
                        )
                        IconButton(onClick = { viewModel.removeSubscription(url) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.remove_subscription),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.updateAllSubscriptions() },
                    enabled = !fetch.running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.update_subscriptions), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = subUrl,
                onValueChange = { subUrl = it },
                placeholder = { Text(stringResource(R.string.subscription_url), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.addSubscription(subUrl)
                    subUrl = ""
                },
                enabled = subUrl.trim().startsWith("http"),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_subscription), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.about_section))
        SettingsCard {
            Row {
                Text(stringResource(R.string.app_version), fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Text(BuildConfig.VERSION_NAME, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("github.com/sukot5104-lang/SukotVPN", fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_body),
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
