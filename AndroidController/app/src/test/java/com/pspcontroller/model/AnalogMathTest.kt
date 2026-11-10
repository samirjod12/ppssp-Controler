package com.pspcontroller.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalogMathTest {
    @Test fun insideDeadZoneIsExactlyZero() {
        assertTrue(AnalogMath.process(0.05, 0.05, 0.12, 1.0).isCentered)
        assertTrue(AnalogMath.process(0.0, 0.0, 0.0, 1.0).isCentered)
    }

    @Test fun fullDeflectionIsOne() {
        val v = AnalogMath.process(1.0, 0.0, 0.12, 1.0)
        assertEquals(1.0, v.x, 1e-9); assertEquals(0.0, v.y, 1e-9)
    }

    @Test fun startsAtZeroAtTheDeadZoneEdge() {
        val v = AnalogMath.process(0.2, 0.0, 0.2, 1.0)
        assertEquals(0.0, v.x, 1e-9)
        val just = AnalogMath.process(0.6, 0.0, 0.2, 1.0)
        assertEquals(0.5, just.x, 1e-9)
    }

    @Test fun directionIsPreservedAndYPointsUp() {
        val v = AnalogMath.process(0.0, 0.8, 0.1, 1.0)
        assertEquals(0.0, v.x, 1e-9); assertTrue(v.y > 0.0)
    }

    @Test fun sensitivityScalesButNeverExceedsOne() {
        val half = AnalogMath.process(0.6, 0.0, 0.0, 0.5)
        assertEquals(0.3, half.x, 1e-9)
        val boosted = AnalogMath.process(0.9, 0.0, 0.0, 2.0)
        assertEquals(1.0, boosted.x, 1e-9)
    }

    @Test fun outputMagnitudeNeverExceedsOneOnDiagonals() {
        val v = AnalogMath.process(1.0, 1.0, 0.1, 2.0)
        assertTrue(Math.hypot(v.x, v.y) <= 1.0 + 1e-9)
    }
}
