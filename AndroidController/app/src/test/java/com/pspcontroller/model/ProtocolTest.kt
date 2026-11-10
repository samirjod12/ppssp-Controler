package com.pspcontroller.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test fun helloMatchesReceiverSpec() {
        val o = JSONObject(Protocol.hello("483920", "Pixel 8"))
        assertEquals("hello", o.getString("type"))
        assertEquals(1, o.getInt("version"))
        assertEquals("483920", o.getString("pin"))
        assertEquals("Pixel 8", o.getString("clientName"))
    }

    @Test fun buttonDownAndUp() {
        val down = JSONObject(Protocol.button(PspButton.CROSS, true))
        assertEquals("CROSS", down.getString("button")); assertEquals("down", down.getString("state"))
        val up = JSONObject(Protocol.button(PspButton.CROSS, false))
        assertEquals("up", up.getString("state"))
    }

    @Test fun everyButtonNameIsOneOfTheSpecifiedNames() {
        val spec = setOf("UP", "DOWN", "LEFT", "RIGHT", "CROSS", "CIRCLE", "SQUARE", "TRIANGLE", "L", "R", "START", "SELECT")
        assertEquals(spec, PspButton.values().map { it.name }.toSet())
    }

    @Test fun analogIsClampedAndFinite() {
        val o = JSONObject(Protocol.analog(5.0, -5.0))
        assertEquals(1.0, o.getDouble("x"), 0.0); assertEquals(-1.0, o.getDouble("y"), 0.0)
        val n = JSONObject(Protocol.analog(Double.NaN, Double.POSITIVE_INFINITY))
        assertEquals(0.0, n.getDouble("x"), 0.0); assertEquals(0.0, n.getDouble("y"), 0.0)
        assertEquals(0.65, JSONObject(Protocol.analog(0.65, 0.0)).getDouble("x"), 1e-9)
    }

    @Test fun parsesServerMessages() {
        assertEquals(ServerMessage.Welcome("DESKTOP-ABC"), Protocol.parseServerMessage("""{"type":"welcome","version":1,"serverName":"DESKTOP-ABC"}"""))
        assertEquals(ServerMessage.Error("bad_pin", "Pairing failed"), Protocol.parseServerMessage("""{"type":"error","version":1,"code":"bad_pin","message":"Pairing failed"}"""))
        assertEquals(ServerMessage.Heartbeat, Protocol.parseServerMessage("""{"type":"heartbeat","version":1}"""))
    }

    @Test fun rejectsGarbageAndWrongVersion() {
        assertEquals(ServerMessage.Unknown, Protocol.parseServerMessage("not json"))
        assertEquals(ServerMessage.Unknown, Protocol.parseServerMessage("""{"type":"welcome","version":2}"""))
        assertEquals(ServerMessage.Unknown, Protocol.parseServerMessage("""{"type":"mystery","version":1}"""))
    }

    @Test fun validation() {
        assertTrue(Protocol.isValidPin("012345")); assertFalse(Protocol.isValidPin("12345")); assertFalse(Protocol.isValidPin("12345a"))
        assertTrue(Protocol.isValidHost("192.168.1.20")); assertTrue(Protocol.isValidHost("my-pc.local"))
        assertFalse(Protocol.isValidHost("")); assertFalse(Protocol.isValidHost("a b")); assertFalse(Protocol.isValidHost("http://x/"))
        assertTrue(Protocol.isValidPort(8765)); assertFalse(Protocol.isValidPort(0)); assertFalse(Protocol.isValidPort(70000))
    }

    @Test fun clientNameIsSanitisedToReceiverRules() {
        assertEquals("Android", Protocol.sanitizeClientName(null))
        assertEquals("Android", Protocol.sanitizeClientName("\n\t"))
        assertEquals("Pixel 8", Protocol.sanitizeClientName("Pixel\n 8"))
        assertEquals(40, Protocol.sanitizeClientName("x".repeat(100)).length)
    }

    @Test fun fatalErrors() {
        assertTrue(Protocol.isFatalError("bad_pin")); assertTrue(Protocol.isFatalError("rate_limited"))
        assertFalse(Protocol.isFatalError("busy")); assertFalse(Protocol.isFatalError("bad_message"))
    }
}
