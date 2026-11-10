package com.pspcontroller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pspcontroller.data.ControllerSettings
import com.pspcontroller.model.PspButton
import com.pspcontroller.model.StickValue
import com.pspcontroller.network.ConnectionState

/** Remembers each control's previous pressed set so a haptic pulse fires only for NEW presses. */
private class PressRouter(
    var onPressed: (String, Set<PspButton>) -> Unit,
    var pulse: () -> Unit,
) {
    private val last = HashMap<String, Set<PspButton>>()
    fun update(owner: String, now: Set<PspButton>) {
        val before = last[owner] ?: emptySet()
        if (now.any { it !in before }) pulse()
        last[owner] = now
        onPressed(owner, now)
    }
}

@Composable
fun ControllerScreen(
    settings: ControllerSettings,
    state: ConnectionState,
    rttMs: Long?,
    onPressed: (String, Set<PspButton>) -> Unit,
    onStick: (StickValue) -> Unit,
    onPulse: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val router = remember { PressRouter(onPressed, onPulse) }.also { it.onPressed = onPressed; it.pulse = onPulse }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(PspColors.Body)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val w = maxWidth
        val h = maxHeight
        val k = settings.controlScale

        // Sizes are driven by height so the layout keeps its proportions on any landscape phone.
        val dpadSize = h * 0.46f * k
        val stickSize = h * 0.34f * k
        val faceSize = h * 0.52f * k
        val shoulderW = w * 0.2f * k
        val shoulderH = h * 0.15f * k
        val pillW = w * 0.1f * k
        val pillH = h * 0.1f * k

        Placed(0.18f, 0.40f, dpadSize, dpadSize, w, h) { DPad(dpadSize, { router.update("dpad", it) }) }
        Placed(0.36f, 0.72f, stickSize, stickSize, w, h) { AnalogStick(stickSize, settings, onStick) }
        Placed(0.82f, 0.48f, faceSize, faceSize, w, h) { FaceButtons(faceSize, { router.update("face", it) }) }

        Placed(0.13f, 0.09f, shoulderW, shoulderH, w, h) {
            PillButton("L", PspButton.L, shoulderW, shoulderH, 14.dp) { router.update("L", it) }
        }
        Placed(0.87f, 0.09f, shoulderW, shoulderH, w, h) {
            PillButton("R", PspButton.R, shoulderW, shoulderH, 14.dp) { router.update("R", it) }
        }
        Placed(0.43f, 0.90f, pillW, pillH, w, h) {
            PillButton("SELECT", PspButton.SELECT, pillW, pillH, 20.dp) { router.update("SELECT", it) }
        }
        Placed(0.57f, 0.90f, pillW, pillH, w, h) {
            PillButton("START", PspButton.START, pillW, pillH, 20.dp) { router.update("START", it) }
        }

        StatusChip(state, rttMs, onOpenSettings, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
    }
}

@Composable
private fun Placed(cx: Float, cy: Float, width: Dp, height: Dp, w: Dp, h: Dp, content: @Composable () -> Unit) {
    Box(Modifier.offset(x = w * cx - width / 2, y = h * cy - height / 2).size(width, height)) { content() }
}

@Composable
private fun PillButton(
    label: String,
    button: PspButton,
    width: Dp,
    height: Dp,
    radius: Dp,
    onChange: (Set<PspButton>) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius)
    val brush = if (pressed) Brush.verticalGradient(listOf(PspColors.KeyPressedTop, PspColors.KeyPressedBottom))
    else Brush.verticalGradient(listOf(PspColors.KeyTop, PspColors.KeyBottom))
    Box(
        Modifier
            .size(width, height)
            .clip(shape)
            .background(brush)
            .border(BorderStroke(1.dp, if (pressed) PspColors.Edge else PspColors.Highlight), shape)
            .touchZones(
                hit = { _, _ -> setOf(button) },
                onChange = { pressed = it.isNotEmpty(); onChange(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (pressed) PspColors.Accent else PspColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusChip(state: ConnectionState, rttMs: Long?, onOpenSettings: () -> Unit, modifier: Modifier) {
    val (dot, text) = when (state) {
        is ConnectionState.Connected -> PspColors.Ok to ("Connected" + (rttMs?.let { " · $it ms" } ?: ""))
        is ConnectionState.Reconnecting -> PspColors.Warn to "Reconnecting…"
        ConnectionState.Connecting -> PspColors.Warn to "Connecting…"
        else -> PspColors.Bad to "Disconnected"
    }
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PspColors.Panel)
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
        Text(text, color = PspColors.Text, fontSize = 12.sp)
        Text("Settings", color = PspColors.Accent, fontSize = 12.sp)
    }
}
