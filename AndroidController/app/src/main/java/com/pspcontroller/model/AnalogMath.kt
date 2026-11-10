package com.pspcontroller.model

import kotlin.math.hypot

data class StickValue(val x: Double, val y: Double) {
    val isCentered get() = x == 0.0 && y == 0.0
}

object AnalogMath {
    /**
     * Radial dead zone + sensitivity.
     * [rawX]/[rawY] are the finger offset from the stick centre in units of the stick radius (+y up).
     * Inside the dead zone the output is exactly zero; outside it is rescaled so the output
     * starts at 0 at the dead-zone edge, then multiplied by [sensitivity] and clamped to 1.
     */
    fun process(rawX: Double, rawY: Double, deadZone: Double, sensitivity: Double): StickValue {
        val mag = hypot(rawX, rawY)
        if (mag.isNaN() || mag < deadZone || mag == 0.0) return StickValue(0.0, 0.0)
        val dz = deadZone.coerceIn(0.0, 0.95)
        val norm = mag.coerceAtMost(1.0)
        val scaled = (((norm - dz) / (1.0 - dz)) * sensitivity).coerceIn(0.0, 1.0)
        return StickValue(rawX / mag * scaled, rawY / mag * scaled)
    }
}
