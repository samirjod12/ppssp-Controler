package com.pspcontroller.network

import com.pspcontroller.model.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

enum class FailReason { INVALID_INPUT, BAD_PIN, RATE_LIMITED, VERSION, BUSY, UNREACHABLE, GAVE_UP, REJECTED }

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val serverName: String) : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Failed(val reason: FailReason, val message: String) : ConnectionState
}

/**
 * Owns the connection lifecycle: connect + PIN handshake, 1 s heartbeat, dead-link detection,
 * automatic reconnect after an interruption, and clean teardown.
 *
 * All methods must be called from the thread that runs [scope] (the main thread in the app).
 * Transport callbacks may arrive on any thread; they are re-posted onto [scope].
 */
class ConnectionManager(
    private val scope: CoroutineScope,
    private val transportFactory: () -> Transport,
    private val clientName: String,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) {
    private data class Params(val host: String, val port: Int, val pin: String)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _rttMs = MutableStateFlow<Long?>(null)
    val rttMs: StateFlow<Long?> = _rttMs

    /** Invoked whenever a session ends for any reason, so input state can be forgotten. */
    var onSessionEnded: (() -> Unit)? = null

    private var params: Params? = null
    private var wantConnected = false
    private var everConnected = false
    private var resumeAfterPause = false
    private var attempt = 0
    private var sessionId = 0
    private var transport: Transport? = null
    private var helloJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    @Volatile private var authenticated = false
    private var lastRx = 0L
    private var lastHeartbeatSent = 0L

    /** Sends a protocol message; false if there is no authenticated connection. Safe from any thread. */
    fun send(text: String): Boolean {
        if (!authenticated) return false
        return transport?.send(text) ?: false
    }

    fun connect(host: String, port: Int, pin: String) {
        if (!Protocol.isValidHost(host) || !Protocol.isValidPort(port) || !Protocol.isValidPin(pin)) {
            _state.value = ConnectionState.Failed(FailReason.INVALID_INPUT, "Check the IP address, port and 6-digit PIN.")
            return
        }
        reconnectJob?.cancel()
        endSession()
        params = Params(host, port, pin)
        wantConnected = true
        everConnected = false
        resumeAfterPause = false
        attempt = 0
        log("Connecting to $host:$port")
        startSession()
    }

    fun disconnect() {
        wantConnected = false
        resumeAfterPause = false
        reconnectJob?.cancel()
        endSession(graceful = true)
        _state.value = ConnectionState.Disconnected
        log("Disconnected by user")
    }

    /** App went to the background: drop the socket (the receiver releases all inputs) but remember to come back. */
    fun pause() {
        val active = wantConnected && params != null
        reconnectJob?.cancel()
        endSession(graceful = true)
        wantConnected = false
        resumeAfterPause = active && everConnected
        if (active) {
            _state.value = ConnectionState.Disconnected
            log("Paused (app in background)")
        }
    }

    fun resume() {
        if (!resumeAfterPause || params == null) return
        resumeAfterPause = false
        wantConnected = true
        attempt = 1
        log("Resuming connection")
        startSession()
    }

    private fun startSession() {
        val p = params ?: return
        val id = ++sessionId
        _state.value = if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting(attempt)
        authenticated = false
        val t = transportFactory()
        transport = t

        helloJob?.cancel()
        helloJob = scope.launch {
            delay(Protocol.HELLO_TIMEOUT_MS)
            if (id == sessionId && !authenticated) handleLost(id, "no response from receiver")
        }

        t.open("ws://${p.host}:${p.port}/", object : Transport.Listener {
            override fun onOpen() {
                scope.launch { if (id == sessionId) t.send(Protocol.hello(p.pin, clientName)) }
            }
            override fun onMessage(text: String) {
                scope.launch { handleMessage(id, text) }
            }
            override fun onClosed(reason: String) {
                scope.launch { handleLost(id, reason) }
            }
        })
    }

    private fun handleMessage(id: Int, text: String) {
        if (id != sessionId) return
        lastRx = clock()
        when (val m = Protocol.parseServerMessage(text)) {
            is com.pspcontroller.model.ServerMessage.Welcome -> {
                if (authenticated) return
                authenticated = true
                everConnected = true
                attempt = 0
                helloJob?.cancel()
                _state.value = ConnectionState.Connected(m.serverName)
                log("Connected to ${m.serverName}")
                startHeartbeat(id)
            }
            is com.pspcontroller.model.ServerMessage.Heartbeat -> {
                if (lastHeartbeatSent > 0) _rttMs.value = clock() - lastHeartbeatSent
            }
            is com.pspcontroller.model.ServerMessage.Error -> handleError(id, m.code)
            com.pspcontroller.model.ServerMessage.Unknown -> log("Ignored unrecognised message from receiver")
        }
    }

    private fun handleError(id: Int, code: String) {
        log("Receiver error: $code")
        if (Protocol.isFatalError(code) || (code == "busy" && !everConnected)) {
            wantConnected = false
            endSession(graceful = false)
            _state.value = ConnectionState.Failed(reasonFor(code), messageFor(code))
        } else {
            // e.g. "busy" while the receiver is still timing out our previous socket: worth retrying.
            handleLost(id, "receiver error: $code")
        }
    }

    private fun startHeartbeat(id: Int) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(Protocol.HEARTBEAT_INTERVAL_MS)
                if (id != sessionId) return@launch
                if (clock() - lastRx > Protocol.SERVER_SILENCE_TIMEOUT_MS) {
                    handleLost(id, "receiver stopped responding")
                    return@launch
                }
                lastHeartbeatSent = clock()
                transport?.send(Protocol.heartbeat())
            }
        }
    }

    private fun handleLost(id: Int, reason: String) {
        if (id != sessionId) return
        log("Connection lost: $reason")
        endSession(graceful = false)
        if (!wantConnected) {
            _state.value = ConnectionState.Disconnected
        } else if (!everConnected) {
            wantConnected = false
            _state.value = ConnectionState.Failed(
                FailReason.UNREACHABLE,
                "Could not reach the receiver ($reason). Check the IP, port, that PSP Receiver is started, and that both devices are on the same Wi-Fi.",
            )
        } else {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        attempt++
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            wantConnected = false
            _state.value = ConnectionState.Failed(FailReason.GAVE_UP, "Lost connection and could not reconnect.")
            log("Giving up after ${attempt - 1} attempts")
            return
        }
        _state.value = ConnectionState.Reconnecting(attempt)
        val wait = BACKOFF_MS[minOf(attempt - 1, BACKOFF_MS.lastIndex)]
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(wait)
            if (wantConnected) startSession()
        }
    }

    private fun endSession(graceful: Boolean = false) {
        authenticated = false
        helloJob?.cancel()
        heartbeatJob?.cancel()
        transport?.close(graceful)
        transport = null
        sessionId++ // invalidate callbacks from the old socket
        onSessionEnded?.invoke()
    }

    private fun reasonFor(code: String) = when (code) {
        "bad_pin" -> FailReason.BAD_PIN
        "rate_limited" -> FailReason.RATE_LIMITED
        "unsupported_version" -> FailReason.VERSION
        "busy" -> FailReason.BUSY
        else -> FailReason.REJECTED
    }

    private fun messageFor(code: String) = when (code) {
        "bad_pin" -> "Wrong PIN. Use the PIN currently shown in PSP Receiver."
        "rate_limited" -> "Too many wrong PINs. Wait a minute and try again."
        "unsupported_version" -> "This app and PSP Receiver use different protocol versions. Update both."
        "busy" -> "Another controller is already connected to this receiver."
        else -> "The receiver rejected the connection ($code)."
    }

    private fun log(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(wallClock())
        _log.value = (_log.value + "$stamp  $line").takeLast(MAX_LOG_LINES)
    }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 30
        const val MAX_LOG_LINES = 100
        val BACKOFF_MS = longArrayOf(500, 1000, 2000, 3000)
    }
}
