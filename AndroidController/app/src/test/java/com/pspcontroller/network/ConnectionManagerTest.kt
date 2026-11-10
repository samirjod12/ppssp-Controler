package com.pspcontroller.network

import com.pspcontroller.model.Protocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    private class FakeTransport : Transport {
        lateinit var listener: Transport.Listener
        var url = ""
        val sent = mutableListOf<String>()
        var closed = false
        override fun open(url: String, listener: Transport.Listener) { this.url = url; this.listener = listener }
        override fun send(text: String): Boolean { sent += text; return true }
        override fun close(graceful: Boolean) { closed = true }
        fun types() = sent.map { JSONObject(it).getString("type") }
    }

    private val welcome = """{"type":"welcome","version":1,"serverName":"DESKTOP-ABC"}"""
    private val heartbeat = """{"type":"heartbeat","version":1}"""

    private class Rig(scope: TestScope) {
        val transports = mutableListOf<FakeTransport>()
        var endedCount = 0
        val manager = ConnectionManager(
            scope = scope.backgroundScope,
            transportFactory = { FakeTransport().also { transports += it } },
            clientName = "Test Phone",
            clock = { scope.currentTime },
            wallClock = { 0L },
        ).also { it.onSessionEnded = { endedCount++ } }
        val last get() = transports.last()
    }

    private fun TestScope.connectAndPair(rig: Rig) {
        rig.manager.connect("192.168.1.20", 8765, "123456")
        runCurrent()
        rig.last.listener.onOpen(); runCurrent()
        rig.last.listener.onMessage(welcome); runCurrent()
    }

    @Test fun sendsHelloThenBecomesConnected() = runTest {
        val rig = Rig(this)
        rig.manager.connect("192.168.1.20", 8765, "123456"); runCurrent()
        assertEquals("ws://192.168.1.20:8765/", rig.last.url)
        assertEquals(ConnectionState.Connecting, rig.manager.state.value)
        rig.last.listener.onOpen(); runCurrent()
        val hello = JSONObject(rig.last.sent.single())
        assertEquals("hello", hello.getString("type")); assertEquals("123456", hello.getString("pin")); assertEquals("Test Phone", hello.getString("clientName"))
        rig.last.listener.onMessage(welcome); runCurrent()
        assertEquals(ConnectionState.Connected("DESKTOP-ABC"), rig.manager.state.value)
        assertTrue(rig.manager.send("{}"))
    }

    @Test fun cannotSendBeforeWelcome() = runTest {
        val rig = Rig(this)
        rig.manager.connect("192.168.1.20", 8765, "123456"); runCurrent()
        rig.last.listener.onOpen(); runCurrent()
        assertFalse(rig.manager.send("""{"type":"button"}"""))
    }

    @Test fun wrongPinFailsAndDoesNotRetry() = runTest {
        val rig = Rig(this)
        rig.manager.connect("192.168.1.20", 8765, "000000"); runCurrent()
        rig.last.listener.onOpen(); runCurrent()
        rig.last.listener.onMessage("""{"type":"error","version":1,"code":"bad_pin","message":"Pairing failed"}"""); runCurrent()
        val s = rig.manager.state.value as ConnectionState.Failed
        assertEquals(FailReason.BAD_PIN, s.reason)
        rig.last.listener.onClosed("closed"); advanceTimeBy(60_000); runCurrent()
        assertEquals(1, rig.transports.size) // no reconnect storm that would trip the receiver's lockout
    }

    @Test fun invalidInputNeverOpensASocket() = runTest {
        val rig = Rig(this)
        rig.manager.connect("bad host", 8765, "123456"); runCurrent()
        assertEquals(FailReason.INVALID_INPUT, (rig.manager.state.value as ConnectionState.Failed).reason)
        rig.manager.connect("10.0.0.1", 8765, "12"); runCurrent()
        assertTrue(rig.transports.isEmpty())
    }

    @Test fun firstConnectFailureIsReportedNotRetried() = runTest {
        val rig = Rig(this)
        rig.manager.connect("192.168.1.99", 8765, "123456"); runCurrent()
        rig.last.listener.onClosed("Failed to connect"); runCurrent()
        assertEquals(FailReason.UNREACHABLE, (rig.manager.state.value as ConnectionState.Failed).reason)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, rig.transports.size)
    }

    @Test fun silentReceiverAtHelloTimesOut() = runTest {
        val rig = Rig(this)
        rig.manager.connect("192.168.1.99", 8765, "123456"); runCurrent()
        rig.last.listener.onOpen(); runCurrent()
        advanceTimeBy(Protocol.HELLO_TIMEOUT_MS + 1); runCurrent()
        assertEquals(FailReason.UNREACHABLE, (rig.manager.state.value as ConnectionState.Failed).reason)
    }

    @Test fun reconnectsAfterTheLinkDrops() = runTest {
        val rig = Rig(this)
        connectAndPair(rig)
        val before = rig.endedCount
        rig.last.listener.onClosed("Software caused connection abort"); runCurrent()
        assertEquals(ConnectionState.Reconnecting(1), rig.manager.state.value)
        assertTrue("input state must be forgotten when the session ends", rig.endedCount > before)
        advanceTimeBy(501); runCurrent()
        assertEquals(2, rig.transports.size)
        rig.last.listener.onOpen(); runCurrent()
        rig.last.listener.onMessage(welcome); runCurrent()
        assertEquals(ConnectionState.Connected("DESKTOP-ABC"), rig.manager.state.value)
    }

    @Test fun heartbeatsAreSentEverySecondAndRttIsMeasured() = runTest {
        val rig = Rig(this)
        connectAndPair(rig)
        advanceTimeBy(1001); runCurrent()
        assertEquals(1, rig.last.types().count { it == "heartbeat" })
        advanceTimeBy(40); runCurrent()
        rig.last.listener.onMessage(heartbeat); runCurrent()
        assertEquals(41L, rig.manager.rttMs.value)
    }

    @Test fun deadReceiverIsDetectedByHeartbeatSilence() = runTest {
        val rig = Rig(this)
        connectAndPair(rig)
        advanceTimeBy(Protocol.SERVER_SILENCE_TIMEOUT_MS + 1500); runCurrent()
        assertTrue(rig.manager.state.value is ConnectionState.Reconnecting)
        assertTrue(rig.transports.first().closed)
    }

    @Test fun busyDuringReconnectIsRetriedBecauseItMayBeOurOwnStaleSocket() = runTest {
        val rig = Rig(this)
        connectAndPair(rig)
        rig.last.listener.onClosed("lost"); runCurrent()
        advanceTimeBy(501); runCurrent()
        rig.last.listener.onOpen(); runCurrent()
        rig.last.listener.onMessage("""{"type":"error","version":1,"code":"busy","message":"x"}"""); runCurrent()
        assertTrue(rig.manager.state.value is ConnectionState.Reconnecting)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(3, rig.transports.size)
    }

    @Test fun userDisconnectEndsSessionAndDoesNotReconnect() = runTest {
        val rig = Rig(this)
        connectAndPair(rig)
        rig.manager.disconnect(); runCurrent()
        assertEquals(ConnectionState.Disconnected, rig.manager.state.value)
        rig.last.listener.onClosed("late callback from the old socket"); advanceTimeBy(60_000); runCurrent()
        assertEquals(1, rig.transports.size)
        assertFalse(rig.manager.send("{}"))
    }

    @Test fun pauseDropsTheSocketAndResumeReconnects() = runTest {
        val rig = Rig(this)
        connectAndPair(rig)
        rig.manager.pause(); runCurrent()
        assertTrue(rig.last.closed)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, rig.transports.size)
        rig.manager.resume(); runCurrent()
        assertEquals(2, rig.transports.size)
    }

    @Test fun givesUpAfterTooManyFailedReconnects() = runTest {
        val rig = Rig(this)
        connectAndPair(rig)
        rig.last.listener.onClosed("lost"); runCurrent()
        repeat(40) {
            advanceTimeBy(3_100); runCurrent()
            if (rig.manager.state.value is ConnectionState.Failed) return@repeat
            rig.last.listener.onClosed("still down"); runCurrent()
        }
        assertEquals(FailReason.GAVE_UP, (rig.manager.state.value as ConnectionState.Failed).reason)
    }
}
