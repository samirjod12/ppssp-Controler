package com.pspcontroller.ui

import com.pspcontroller.model.PspButton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Pure hit-testing, kept free of Compose types so it can be unit tested on the JVM. */
object Geometry {
    const val FACE_SPREAD = 0.30f   // distance of each face button from the centre, as a fraction of the control size
    const val FACE_RADIUS = 0.19f   // face button radius, as a fraction of the control size

    /** Face buttons in PSP layout: triangle top, circle right, cross bottom, square left. */
    val FACE_LAYOUT: List<Triple<PspButton, Float, Float>> = listOf(
        Triple(PspButton.TRIANGLE, 0f, -1f),
        Triple(PspButton.CIRCLE, 1f, 0f),
        Triple(PspButton.CROSS, 0f, 1f),
        Triple(PspButton.SQUARE, -1f, 0f),
    )

    /** Touches near the centre press nothing; near a corner they press two directions (diagonal). */
    fun dpadHit(x: Float, y: Float, width: Float, height: Float): Set<PspButton> {
        val r = min(width, height) / 2f
        if (r <= 0f) return emptySet()
        val dx = (x - width / 2f) / r
        val dy = (y - height / 2f) / r
        if (abs(dx) > 1.3f || abs(dy) > 1.3f) return emptySet()
        val out = LinkedHashSet<PspButton>()
        if (dy < -0.3f) out += PspButton.UP
        if (dy > 0.3f) out += PspButton.DOWN
        if (dx < -0.3f) out += PspButton.LEFT
        if (dx > 0.3f) out += PspButton.RIGHT
        return out
    }

    /** The nearest face button within a slightly generous radius, so a rolling thumb can slide between buttons. */
    fun faceHit(x: Float, y: Float, width: Float, height: Float): Set<PspButton> {
        val s = min(width, height)
        val cx = width / 2f
        val cy = height / 2f
        var best: PspButton? = null
        var bestDist = Float.MAX_VALUE
        for ((button, ux, uy) in FACE_LAYOUT) {
            val d = hypot(x - (cx + ux * s * FACE_SPREAD), y - (cy + uy * s * FACE_SPREAD))
            if (d < bestDist) { bestDist = d; best = button }
        }
        return if (best != null && bestDist <= s * FACE_RADIUS * 1.3f) setOf(best) else emptySet()
    }
}
