package com.sukot.vpn.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukot.vpn.R
import com.sukot.vpn.core.VpnState
import com.sukot.vpn.model.VpnProfile
import com.sukot.vpn.ui.AppViewModel
import com.sukot.vpn.ui.theme.Blue
import com.sukot.vpn.ui.theme.BlueDark
import com.sukot.vpn.ui.theme.Cyan
import com.sukot.vpn.ui.theme.ErrorRed
import com.sukot.vpn.ui.theme.SuccessGreen
import com.sukot.vpn.ui.theme.TextSecondary
import com.sukot.vpn.ui.theme.Violet
import com.sukot.vpn.ui.theme.VioletDark
import com.sukot.vpn.ui.theme.WarnYellow
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val vpnState by viewModel.vpnState.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val ping by viewModel.ping.collectAsState()
    val traffic by viewModel.traffic.collectAsState()

    val selected = profiles.firstOrNull { it.id == selectedId }
    val context = LocalContext.current
    val connected = vpnState == VpnState.CONNECTED
    val busy = vpnState == VpnState.CONNECTING || vpnState == VpnState.DISCONNECTING

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        StatusHeader(vpnState)

        Spacer(Modifier.height(28.dp))

        ConnectButton(
            connected = connected,
            busy = busy,
            onClick = {
                if (connected) viewModel.disconnect(context) else viewModel.requestConnect()
            }
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = when {
                connected -> stringResource(R.string.btn_disconnect)
                busy -> stringResource(R.string.status_connecting)
                else -> stringResource(R.string.btn_connect)
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(28.dp))

        SelectedServerCard(selected, ping.results[selectedId])

        if (connected) {
            Spacer(Modifier.height(16.dp))
            TrafficRow(traffic.first, traffic.second, traffic.third)
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatusHeader(state: VpnState) {
    val (color, label) = when (state) {
        VpnState.CONNECTED -> SuccessGreen to stringResource(R.string.status_connected)
        VpnState.CONNECTING -> WarnYellow to stringResource(R.string.status_connecting)
        VpnState.DISCONNECTING -> WarnYellow to stringResource(R.string.status_disconnecting)
        VpnState.ERROR -> ErrorRed to stringResource(R.string.status_error)
        else -> TextSecondary to stringResource(R.string.status_disconnected)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(10.dp))
        Text(text = label, fontSize = 15.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConnectButton(connected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "glow")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val rotate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotate"
    )

    val wave = (sin(pulse * 2.0 * PI).toFloat() + 1f) / 2f
    val glowAlpha = if (connected || busy) 0.55f + 0.45f * wave else 0.25f + 0.25f * wave

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(220.dp)
    ) {
        // rotating outer glow ring
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            rotate(degrees = rotate) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Violet, Blue, Cyan, Color.Transparent, Violet)
                    ),
                    startAngle = 0f,
                    sweepAngle = 340f,
                    useCenter = false,
                    style = stroke,
                    alpha = glowAlpha
                )
            }
            drawCircle(
                color = Violet.copy(alpha = 0.10f + 0.10f * wave),
                radius = size.minDimension / 2f - 14.dp.toPx(),
                style = Stroke(width = 10.dp.toPx())
            )
        }

        // main button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .shadow(26.dp, CircleShape, ambientColor = VioletDark, spotColor = VioletDark)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(VioletDark, BlueDark),
                        start = Offset(0f, 0f),
                        end = Offset(600f, 600f)
                    )
                )
                .then(
                    Modifier.let { m ->
                        if (!busy) m.clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = onClick
                        ) else m
                    }
                )
        ) {
            val icon: ImageVector = if (connected) Icons.Filled.Stop else Icons.Filled.PowerSettingsNew
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(58.dp)
            )
        }
    }
}

@Composable
private fun SelectedServerCard(profile: VpnProfile?, pingMs: Int?) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.selected_server).uppercase(),
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (profile == null) {
                Text(
                    text = stringResource(R.string.no_server_selected),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.select_server_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = profile.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    ProtocolChip(profile.protocolTag)
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        text = profile.displayAddress(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pingMs?.let {
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "•  $it ms",
                            fontSize = 13.sp,
                            color = pingColor(it)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProtocolChip(tag: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = tag,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TrafficRow(rx: Long, tx: Long, seconds: Long) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(Icons.Filled.Bolt, stringResource(R.string.traffic_download), formatBytes(rx), SuccessGreen)
            StatItem(Icons.Filled.Bolt, stringResource(R.string.traffic_upload), formatBytes(tx), Cyan)
            StatItem(Icons.Filled.Bolt, stringResource(R.string.traffic_duration), formatDuration(seconds), Violet)
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

@Composable
fun pingColor(ms: Int): Color = when {
    ms < 200 -> SuccessGreen
    ms < 500 -> WarnYellow
    else -> ErrorRed
}
