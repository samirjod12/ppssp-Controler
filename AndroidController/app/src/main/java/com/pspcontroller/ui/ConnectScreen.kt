package com.pspcontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pspcontroller.data.SavedComputer
import com.pspcontroller.model.Protocol
import com.pspcontroller.network.ConnectionState

@Composable
fun ConnectScreen(
    state: ConnectionState,
    computers: List<SavedComputer>,
    initialHost: String,
    onConnect: (host: String, port: Int, pin: String) -> Unit,
    onForget: (SavedComputer) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var port by rememberSaveable { mutableStateOf(Protocol.DEFAULT_PORT.toString()) }
    var pin by rememberSaveable { mutableStateOf("") }
    val busy = state is ConnectionState.Connecting || state is ConnectionState.Reconnecting

    Row(
        Modifier
            .fillMaxSize()
            .background(PspColors.Body)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("PSP Controller", color = PspColors.Text, fontSize = 22.sp)
            Text(
                "Start PSP Receiver on your PC, then enter its IP address and the PIN it shows. Both devices must be on the same Wi-Fi.",
                color = PspColors.Muted, fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it.trim() },
                    label = { Text("PC IP address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                label = { Text("Pairing PIN (6 digits)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy,
                    onClick = { onConnect(host, port.toIntOrNull() ?: 0, pin) },
                ) { Text(if (busy) "Connecting…" else "Connect") }
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
            if (state is ConnectionState.Failed) Text(state.message, color = PspColors.Bad, fontSize = 13.sp)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Previous computers", color = PspColors.Text, fontSize = 16.sp)
            if (computers.isEmpty()) {
                Text("None yet. A PC is remembered after the first successful connection. PINs are never saved.", color = PspColors.Muted, fontSize = 12.sp)
            }
            computers.forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PspColors.Panel)
                        .clickable { host = c.host; port = c.port.toString() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(c.name, color = PspColors.Text, fontSize = 14.sp)
                        Text("${c.host}:${c.port}", color = PspColors.Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onForget(c) }) { Text("Forget") }
                }
            }
        }
    }
}
