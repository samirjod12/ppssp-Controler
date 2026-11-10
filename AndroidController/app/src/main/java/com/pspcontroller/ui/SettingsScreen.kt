package com.pspcontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pspcontroller.data.ControllerSettings
import com.pspcontroller.network.ConnectionState
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: ControllerSettings,
    onChange: (ControllerSettings) -> Unit,
    state: ConnectionState,
    rttMs: Long?,
    log: List<String>,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .background(PspColors.Body)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Settings", color = PspColors.Text, fontSize = 22.sp)

            LabeledSlider("Analog sensitivity", settings.sensitivity, 0.5f..2.0f, "×%.2f") { onChange(settings.copy(sensitivity = it)) }
            LabeledSlider("Analog dead zone", settings.deadZone, 0.0f..0.5f, "%.2f") { onChange(settings.copy(deadZone = it)) }
            LabeledSlider("Control size", settings.controlScale, 0.8f..1.3f, "×%.2f") { onChange(settings.copy(controlScale = it)) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vibration feedback", color = PspColors.Text, fontSize = 14.sp)
                Switch(checked = settings.vibration, onCheckedChange = { onChange(settings.copy(vibration = it)) })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onBack) { Text("Back to controller") }
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Connection diagnostics", color = PspColors.Text, fontSize = 16.sp)
            Text("State: " + describe(state), color = PspColors.Text, fontSize = 13.sp)
            Text("Latency: " + (rttMs?.let { "$it ms" } ?: "—"), color = PspColors.Text, fontSize = 13.sp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PspColors.Panel)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
            ) {
                if (log.isEmpty()) Text("No events yet.", color = PspColors.Muted, fontSize = 11.sp)
                log.forEach { Text(it, color = PspColors.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = PspColors.Text, fontSize = 14.sp)
            Text(String.format(Locale.US, format, value), color = PspColors.Muted, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun describe(s: ConnectionState): String = when (s) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Connecting -> "Connecting"
    is ConnectionState.Connected -> "Connected to ${s.serverName}"
    is ConnectionState.Reconnecting -> "Reconnecting (attempt ${s.attempt})"
    is ConnectionState.Failed -> "Failed: ${s.message}"
}
