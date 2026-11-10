package com.pspcontroller.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.pspcontroller.data.ControllerSettings
import com.pspcontroller.model.AnalogMath
import com.pspcontroller.model.StickValue
import kotlin.math.hypot

/** Travel of the nub as a fraction of the control radius. */
private const val TRAVEL = 0.7f

@Composable
fun AnalogStick(
    size: Dp,
    settings: ControllerSettings,
    onMove: (StickValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nub by remember { mutableStateOf(Offset.Zero) } // normalised, +y down (screen space)
    val currentSettings by rememberUpdatedState(settings)
    val currentMove by rememberUpdatedState(onMove)
    DisposableEffect(Unit) { onDispose { currentMove(StickValue(0.0, 0.0)) } }

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var active: PointerId? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val mine = active?.let { id -> event.changes.firstOrNull { it.id == id } }
                        val change = mine ?: event.changes.firstOrNull { it.changedToDown() }
                        if (change == null) {
                            if (active != null) { // our finger vanished without an "up"
                                active = null
                                nub = Offset.Zero
                                currentMove(StickValue(0.0, 0.0))
                            }
                            continue
                        }
                        active = change.id
                        if (!change.pressed) {
                            active = null
                            nub = Offset.Zero
                            currentMove(StickValue(0.0, 0.0))
                        } else {
                            val r = this.size.width / 2f
                            var dx = (change.position.x - r) / (r * TRAVEL)
                            var dy = (change.position.y - r) / (r * TRAVEL)
                            val mag = hypot(dx, dy)
                            if (mag > 1f) { dx /= mag; dy /= mag }
                            nub = Offset(dx, dy)
                            val s = currentSettings
                            currentMove(AnalogMath.process(dx.toDouble(), -dy.toDouble(), s.deadZone.toDouble(), s.sensitivity.toDouble()))
                        }
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(PspColors.Edge, r, c + Offset(0f, r * 0.04f))
            drawCircle(Brush.verticalGradient(listOf(PspColors.KeyBottom, PspColors.Edge), startY = c.y - r, endY = c.y + r), r, c)
            drawCircle(PspColors.Highlight, r, c, style = Stroke(width = r * 0.025f))
            drawCircle(PspColors.Edge.copy(alpha = 0.7f), r * 0.78f, c, style = Stroke(width = r * 0.03f))
            val nc = c + Offset(nub.x * r * TRAVEL, nub.y * r * TRAVEL)
            val nr = r * 0.3f
            drawCircle(PspColors.Edge, nr, nc + Offset(0f, nr * 0.12f))
            drawCircle(Brush.verticalGradient(listOf(PspColors.KeyTop, PspColors.KeyBottom), startY = nc.y - nr, endY = nc.y + nr), nr, nc)
            drawCircle(PspColors.Highlight, nr, nc, style = Stroke(width = nr * 0.06f))
        }
    }
}
