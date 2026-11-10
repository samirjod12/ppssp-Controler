package com.pspcontroller.ui

import com.pspcontroller.model.PspButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val s = 200f

    @Test fun dpadCentreAndEdges() {
        assertEquals(emptySet<PspButton>(), Geometry.dpadHit(100f, 100f, s, s))
        assertEquals(setOf(PspButton.UP), Geometry.dpadHit(100f, 10f, s, s))
        assertEquals(setOf(PspButton.DOWN), Geometry.dpadHit(100f, 190f, s, s))
        assertEquals(setOf(PspButton.LEFT), Geometry.dpadHit(10f, 100f, s, s))
        assertEquals(setOf(PspButton.RIGHT), Geometry.dpadHit(190f, 100f, s, s))
    }

    @Test fun dpadDiagonals() {
        assertEquals(setOf(PspButton.UP, PspButton.RIGHT), Geometry.dpadHit(170f, 30f, s, s))
        assertEquals(setOf(PspButton.DOWN, PspButton.LEFT), Geometry.dpadHit(30f, 170f, s, s))
    }

    @Test fun dpadFarOutsideIsNothing() {
        assertEquals(emptySet<PspButton>(), Geometry.dpadHit(900f, 100f, s, s))
    }

    @Test fun faceButtonsAtTheirPositions() {
        fun at(ux: Float, uy: Float) = Geometry.faceHit(100f + ux * s * Geometry.FACE_SPREAD, 100f + uy * s * Geometry.FACE_SPREAD, s, s)
        assertEquals(setOf(PspButton.TRIANGLE), at(0f, -1f))
        assertEquals(setOf(PspButton.CIRCLE), at(1f, 0f))
        assertEquals(setOf(PspButton.CROSS), at(0f, 1f))
        assertEquals(setOf(PspButton.SQUARE), at(-1f, 0f))
    }

    @Test fun faceCentreAndFarAwayPressNothingAndOnlyOneButtonAtATime() {
        assertEquals(emptySet<PspButton>(), Geometry.faceHit(100f, 100f, s, s))
        assertEquals(emptySet<PspButton>(), Geometry.faceHit(-500f, -500f, s, s))
        for (x in 0..200 step 5) for (y in 0..200 step 5)
            assertTrue(Geometry.faceHit(x.toFloat(), y.toFloat(), s, s).size <= 1)
    }
}
