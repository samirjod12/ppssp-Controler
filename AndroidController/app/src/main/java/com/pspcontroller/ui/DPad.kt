package com.pspcontroller.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.pspcontroller.model.PspButton

@Composable
fun DPad(size: Dp, onPressed: (Set<PspButton>) -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(emptySet<PspButton>()) }
    Box(
        modifier
            .size(size)
            .touchZones(
                hit = { p, s -> Geometry.dpadHit(p.x, p.y, s.width.toFloat(), s.height.toFloat()) },
                onChange = { pressed = it; onPressed(it) },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawDPad(pressed) }
    }
}

private fun DrawScope.drawDPad(pressed: Set<PspButton>) {
    val s = this.size.minDimension
    val arm = s * 0.34f
    val c = Offset(this.size.width / 2f, this.size.height / 2f)
    val corner = CornerRadius(s * 0.07f)
    val horizontalTopLeft = Offset(c.x - s / 2f, c.y - arm / 2f)
    val verticalTopLeft = Offset(c.x - arm / 2f, c.y - s / 2f)
    val horizontalSize = Size(s, arm)
    val verticalSize = Size(arm, s)

    // Drop shadow, then the cross body, then a thin top highlight.
    val shadow = Offset(0f, s * 0.02f)
    drawRoundRect(PspColors.Edge, horizontalTopLeft + shadow, horizontalSize, corner)
    drawRoundRect(PspColors.Edge, verticalTopLeft + shadow, verticalSize, corner)
    val body = Brush.verticalGradient(listOf(PspColors.KeyTop, PspColors.KeyBottom), startY = c.y - s / 2f, endY = c.y + s / 2f)
    drawRoundRect(body, horizontalTopLeft, horizontalSize, corner)
    drawRoundRect(body, verticalTopLeft, verticalSize, corner)

    // Pressed arm darkens.
    val dark = PspColors.KeyPressedTop.copy(alpha = 0.85f)
    if (PspButton.UP in pressed) drawRoundRect(dark, verticalTopLeft, Size(arm, s / 2f), corner)
    if (PspButton.DOWN in pressed) drawRoundRect(dark, Offset(verticalTopLeft.x, c.y), Size(arm, s / 2f), corner)
    if (PspButton.LEFT in pressed) drawRoundRect(dark, horizontalTopLeft, Size(s / 2f, arm), corner)
    if (PspButton.RIGHT in pressed) drawRoundRect(dark, Offset(c.x, horizontalTopLeft.y), Size(s / 2f, arm), corner)

    // Direction arrows.
    val a = s * 0.075f
    val off = s * 0.37f
    drawArrow(Offset(c.x, c.y - off), 0f, -1f, a, if (PspButton.UP in pressed) PspColors.Accent else PspColors.Muted)
    drawArrow(Offset(c.x, c.y + off), 0f, 1f, a, if (PspButton.DOWN in pressed) PspColors.Accent else PspColors.Muted)
    drawArrow(Offset(c.x - off, c.y), -1f, 0f, a, if (PspButton.LEFT in pressed) PspColors.Accent else PspColors.Muted)
    drawArrow(Offset(c.x + off, c.y), 1f, 0f, a, if (PspButton.RIGHT in pressed) PspColors.Accent else PspColors.Muted)

    // Centre dimple.
    drawCircle(PspColors.Edge.copy(alpha = 0.6f), s * 0.07f, c)
}

private fun DrawScope.drawArrow(center: Offset, dx: Float, dy: Float, size: Float, color: Color) {
    // Triangle pointing along (dx, dy).
    val tip = Offset(center.x + dx * size, center.y + dy * size)
    val baseCenter = Offset(center.x - dx * size, center.y - dy * size)
    val px = -dy * size
    val py = dx * size
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseCenter.x + px, baseCenter.y + py)
        lineTo(baseCenter.x - px, baseCenter.y - py)
        close()
    }
    drawPath(path, color)
}
