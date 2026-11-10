package com.pspcontroller.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.pspcontroller.model.PspButton

object PspColors {
    val Body = Color(0xFF141518)
    val Panel = Color(0xFF22252A)
    val KeyTop = Color(0xFF3A3E46)
    val KeyBottom = Color(0xFF24272C)
    val KeyPressedTop = Color(0xFF1A1C20)
    val KeyPressedBottom = Color(0xFF2A2D33)
    val Edge = Color(0xFF0A0B0D)
    val Highlight = Color(0x33FFFFFF)
    val Text = Color(0xFFE6E8EB)
    val Muted = Color(0xFF8A9099)
    val Accent = Color(0xFF4C8DFF)
    val Ok = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFB300)
    val Bad = Color(0xFFFF5252)
    val Triangle = Color(0xFF3FD47E)
    val Circle = Color(0xFFFF5A5F)
    val Cross = Color(0xFF5B9BFF)
    val Square = Color(0xFFE58CC8)
}

@Composable
fun PspTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PspColors.Accent,
            background = PspColors.Body,
            surface = PspColors.Panel,
            onSurface = PspColors.Text,
            onBackground = PspColors.Text,
        ),
        content = content,
    )
}

/**
 * Multi-touch hit zones. Every finger inside this element is tracked by its own pointer id, the
 * pressed set is the union of what all fingers are over, and [onChange] fires only when that set changes.
 * When the element leaves composition (or the pointer stream is cancelled) everything is released.
 */
@Composable
fun Modifier.touchZones(
    hit: (Offset, IntSize) -> Set<PspButton>,
    onChange: (Set<PspButton>) -> Unit,
): Modifier {
    val currentHit by rememberUpdatedState(hit)
    val currentChange by rememberUpdatedState(onChange)
    DisposableEffect(Unit) { onDispose { currentChange(emptySet()) } }
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            val active = HashMap<PointerId, Set<PspButton>>()
            var last: Set<PspButton> = emptySet()
            while (true) {
                val event = awaitPointerEvent()
                for (c in event.changes) {
                    if (c.pressed) active[c.id] = currentHit(c.position, size) else active.remove(c.id)
                    c.consume()
                }
                val now = active.values.flatten().toSet()
                if (now != last) {
                    last = now
                    currentChange(now)
                }
            }
        }
    }
}
