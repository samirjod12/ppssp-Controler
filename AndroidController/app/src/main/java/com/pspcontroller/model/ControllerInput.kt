package com.pspcontroller.model

import org.json.JSONException
import org.json.JSONObject

/** Button names exactly as they appear on the wire (Protocol/NETWORK_PROTOCOL.md). */
enum class PspButton { UP, DOWN, LEFT, RIGHT, CROSS, CIRCLE, SQUARE, TRIANGLE, L, R, START, SELECT }

sealed interface ServerMessage {
    data class Welcome(val serverName: String) : ServerMessage
    data class Error(val code: String, val message: String) : ServerMessage
    data object Heartbeat : ServerMessage
    data object Unknown : ServerMessage
}

/** Protocol v1 message builders/parsers and input validation. */
object Protocol {
    const val VERSION = 1
    const val DEFAULT_PORT = 8765
    const val HEARTBEAT_INTERVAL_MS = 1000L
    /** The server echoes every heartbeat; silence for this long means the link is dead. */
    const val SERVER_SILENCE_TIMEOUT_MS = 3500L
    const val HELLO_TIMEOUT_MS = 5000L

    private val FATAL_ERRORS = setOf("bad_pin", "rate_limited", "unsupported_version", "not_paired")
    private val HOST_REGEX = Regex("^[A-Za-z0-9.-]{1,253}$")

    /** Errors where retrying cannot help and the user has to act. */
    fun isFatalError(code: String) = code in FATAL_ERRORS

    fun isValidPin(pin: String) = pin.length == 6 && pin.all { it in '0'..'9' }
    fun isValidPort(port: Int) = port in 1..65535
    fun isValidHost(host: String) = HOST_REGEX.matches(host)

    /** Server requires 1..40 printable characters. */
    fun sanitizeClientName(raw: String?): String {
        val cleaned = (raw ?: "").filter { !it.isISOControl() }.trim().take(40)
        return cleaned.ifEmpty { "Android" }
    }

    fun hello(pin: String, clientName: String): String = JSONObject()
        .put("type", "hello").put("version", VERSION)
        .put("pin", pin).put("clientName", clientName)
        .toString()

    fun button(button: PspButton, down: Boolean): String = JSONObject()
        .put("type", "button").put("version", VERSION)
        .put("button", button.name).put("state", if (down) "down" else "up")
        .toString()

    /** x, y in [-1, 1]; +x right, +y up. Out-of-range values are clamped, non-finite values become 0. */
    fun analog(x: Double, y: Double): String = JSONObject()
        .put("type", "analog").put("version", VERSION)
        .put("x", clean(x)).put("y", clean(y))
        .toString()

    fun heartbeat(): String = JSONObject().put("type", "heartbeat").put("version", VERSION).toString()

    fun parseServerMessage(text: String): ServerMessage {
        return try {
            val o = JSONObject(text)
            if (o.optInt("version", -1) != VERSION) return ServerMessage.Unknown
            when (o.optString("type")) {
                "welcome" -> ServerMessage.Welcome(o.optString("serverName", "PC"))
                "error" -> ServerMessage.Error(o.optString("code", "unknown"), o.optString("message", ""))
                "heartbeat" -> ServerMessage.Heartbeat
                else -> ServerMessage.Unknown
            }
        } catch (e: JSONException) {
            ServerMessage.Unknown
        }
    }

    private fun clean(v: Double): Double =
        if (v.isNaN() || v.isInfinite()) 0.0 else Math.round(v.coerceIn(-1.0, 1.0) * 1000.0) / 1000.0
}
