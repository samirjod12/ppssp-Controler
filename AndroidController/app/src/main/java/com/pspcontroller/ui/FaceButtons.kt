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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.pspcontroller.model.PspButton

@Composable
fun FaceButtons(size: Dp, onPressed: (Set<PspButton>) -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(emptySet<PspButton>()) }
    Box(
        modifier
            .size(size)
            .touchZones(
                hit = { p, s -> Geometry.faceHit(p.x, p.y, s.width.toFloat(), s.height.toFloat()) },
                onChange = { pressed = it; onPressed(it) },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val r = s * Geometry.FACE_RADIUS
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            for ((button, ux, uy) in Geometry.FACE_LAYOUT) {
                drawFaceButton(
                    center = Offset(c.x + ux * s * Geometry.FACE_SPREAD, c.y + uy * s * Geometry.FACE_SPREAD),
                    radius = r,
                    button = button,
                    isPressed = button in pressed,
                )
            }
        }
    }
}

private fun symbolColor(b: PspButton): Color = when (b) {
    PspButton.TRIANGLE -> PspColors.Triangle
    PspButton.CIRCLE -> PspColors.Circle
    PspButton.CROSS -> PspColors.Cross
    else -> PspColors.Square
}

private fun DrawScope.drawFaceButton(center: Offset, radius: Float, button: PspButton, isPressed: Boolean) {
    val lift = if (isPressed) 0f else radius * 0.08f
    drawCircle(PspColors.Edge, radius, center + Offset(0f, radius * 0.1f)) // shadow
    val top = if (isPressed) PspColors.KeyPressedTop else PspColors.KeyTop
    val bottom = if (isPressed) PspColors.KeyPressedBottom else PspColors.KeyBottom
    val c = center - Offset(0f, lift)
    drawCircle(Brush.verticalGradient(listOf(top, bottom), startY = c.y - radius, endY = c.y + radius), radius, c)
    drawCircle(PspColors.Highlight, radius, c, style = Stroke(width = radius * 0.04f))

    val color = symbolColor(button)
    val sym = radius * 0.46f
    val stroke = Stroke(width = radius * 0.13f)
    when (button) {
        PspButton.TRIANGLE -> {
            val h = sym * 1.1f
            val path = Path().apply {
                moveTo(c.x, c.y - h)
                lineTo(c.x + sym, c.y + sym * 0.8f)
                lineTo(c.x - sym, c.y + sym * 0.8f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
        PspButton.CIRCLE -> drawCircle(color, sym, c, style = stroke)
        PspButton.CROSS -> {
            drawLine(color, Offset(c.x - sym, c.y - sym), Offset(c.x + sym, c.y + sym), stroke.width)
            drawLine(color, Offset(c.x + sym, c.y - sym), Offset(c.x - sym, c.y + sym), stroke.width)
        }
        else -> drawRect(color, Offset(c.x - sym, c.y - sym), Size(sym * 2, sym * 2), style = stroke)
    }
}
