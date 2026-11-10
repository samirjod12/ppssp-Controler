package com.pspcontroller.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSenderTest {
    private val out = mutableListOf<String>()
    private var online = true
    private val sender = InputSender { out += it; online }

    private fun events() = out.map { JSONObject(it) }.filter { it.getString("type") == "button" }
        .map { it.getString("button") + ":" + it.getString("state") }

    @Test fun pressAndReleaseSendDownThenUp() {
        sender.setPressed("face", setOf(PspButton.CROSS))
        sender.setPressed("face", emptySet())
        assertEquals(listOf("CROSS:down", "CROSS:up"), events())
    }

    @Test fun duplicateStateIsNotResent() {
        sender.setPressed("face", setOf(PspButton.CROSS))
        sender.setPressed("face", setOf(PspButton.CROSS))
        assertEquals(listOf("CROSS:down"), events())
    }

    @Test fun simultaneousButtonsAcrossControls() {
        sender.setPressed("face", setOf(PspButton.CROSS))
        sender.setPressed("dpad", setOf(PspButton.UP, PspButton.RIGHT))
        sender.setPressed("L", setOf(PspButton.L))
        assertEquals(setOf(PspButton.CROSS, PspButton.UP, PspButton.RIGHT, PspButton.L), sender.heldButtons())
        sender.setPressed("dpad", setOf(PspButton.UP))
        assertTrue(events().last() == "RIGHT:up")
        assertEquals(setOf(PspButton.CROSS, PspButton.UP, PspButton.L), sender.heldButtons())
    }

    @Test fun releaseAllReleasesEverythingAndCentresStick() {
        sender.setPressed("face", setOf(PspButton.CROSS, PspButton.CIRCLE))
        sender.setPressed("L", setOf(PspButton.L))
        sender.setAnalog(StickValue(0.5, 0.5)); sender.tick()
        out.clear()
        sender.releaseAll()
        assertEquals(setOf("CROSS:up", "CIRCLE:up", "L:up"), events().toSet())
        val last = JSONObject(out.last())
        assertEquals("analog", last.getString("type")); assertEquals(0.0, last.getDouble("x"), 0.0)
        assertTrue(sender.heldButtons().isEmpty())
    }

    @Test fun failedSendIsRetriedOnNextChange() {
        online = false
        sender.setPressed("face", setOf(PspButton.CROSS))
        assertTrue(sender.heldButtons().isEmpty()) // receiver never saw it
        online = true
        sender.setPressed("face", setOf(PspButton.CROSS, PspButton.CIRCLE))
        assertEquals(setOf(PspButton.CROSS, PspButton.CIRCLE), sender.heldButtons())
    }

    @Test fun resetForgetsWithoutSending() {
        sender.setPressed("face", setOf(PspButton.CROSS))
        out.clear()
        sender.reset()
        assertTrue(out.isEmpty()); assertTrue(sender.heldButtons().isEmpty())
        // After reconnect, a still-held finger does not resurrect the button; a fresh press does.
        sender.setPressed("face", setOf(PspButton.CROSS))
        assertEquals(listOf("CROSS:down"), events())
    }

    @Test fun analogOnlySentOnRealChangeAndCentreIsImmediate() {
        sender.setAnalog(StickValue(0.500, 0.0)); sender.tick()
        sender.setAnalog(StickValue(0.504, 0.0)); sender.tick() // < 0.01 change
        assertEquals(1, out.size)
        sender.setAnalog(StickValue(0.6, 0.0)); sender.tick()
        assertEquals(2, out.size)
        sender.setAnalog(StickValue(0.0, 0.0)) // no tick needed
        assertEquals(3, out.size)
        assertEquals(0.0, JSONObject(out.last()).getDouble("x"), 0.0)
        sender.setAnalog(StickValue(0.0, 0.0))
        assertEquals(3, out.size)
    }
}
