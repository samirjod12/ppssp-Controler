package com.pspcontroller.model

import kotlin.math.abs

/**
 * Turns UI touch state into protocol messages.
 *
 * - Several controls can each report their own pressed set ("owners"); a button is down if any owner holds it.
 * - Only changes are sent (duplicate protection). [sent] is what the receiver believes is down.
 * - A send that fails (not connected) is not recorded, so it is retried on the next change.
 * - Analog values are throttled by calling [tick] at ~60 Hz; returning to centre is sent immediately.
 */
class InputSender(private val send: (String) -> Boolean) {
    private val owners = HashMap<String, Set<PspButton>>()
    private val sent = HashSet<PspButton>()
    private var desired = StickValue(0.0, 0.0)
    private var sentStick = StickValue(0.0, 0.0)

    @Synchronized
    fun setPressed(owner: String, buttons: Set<PspButton>) {
        if (buttons.isEmpty()) owners.remove(owner) else owners[owner] = buttons.toSet()
        syncButtons()
    }

    @Synchronized
    fun setAnalog(value: StickValue) {
        desired = value
        if (value.isCentered) syncStick()
    }

    /** Call about every 16 ms while connected. */
    @Synchronized
    fun tick() {
        if (abs(desired.x - sentStick.x) >= STICK_EPSILON || abs(desired.y - sentStick.y) >= STICK_EPSILON) syncStick()
    }

    /** Send "up" for everything held and centre the stick (app backgrounded, user disconnect, etc.). */
    @Synchronized
    fun releaseAll() {
        owners.clear()
        desired = StickValue(0.0, 0.0)
        syncButtons()
        syncStick()
    }

    /** The connection ended: the receiver already released everything on its side. Forget without sending. */
    @Synchronized
    fun reset() {
        owners.clear()
        sent.clear()
        desired = StickValue(0.0, 0.0)
        sentStick = StickValue(0.0, 0.0)
    }

    @Synchronized
    fun heldButtons(): Set<PspButton> = sent.toSet()

    private fun syncButtons() {
        val want = owners.values.flatten().toSet()
        for (b in sent - want) if (send(Protocol.button(b, false))) sent.remove(b)
        for (b in want - sent) if (send(Protocol.button(b, true))) sent.add(b)
    }

    private fun syncStick() {
        if (desired == sentStick) return
        if (send(Protocol.analog(desired.x, desired.y))) sentStick = desired
    }

    private companion object {
        const val STICK_EPSILON = 0.01
    }
}
