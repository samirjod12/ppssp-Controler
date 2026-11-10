package com.pspcontroller.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Minimal storage abstraction so persistence can be unit tested without Android. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("psp_controller", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

data class ControllerSettings(
    val sensitivity: Float = 1.0f,
    val deadZone: Float = 0.12f,
    val vibration: Boolean = true,
    val controlScale: Float = 1.0f,
) {
    fun sanitized() = copy(
        sensitivity = sensitivity.coerceIn(0.5f, 2.0f),
        deadZone = deadZone.coerceIn(0.0f, 0.5f),
        controlScale = controlScale.coerceIn(0.8f, 1.3f),
    )
}

/** A previously used PC. The PIN is deliberately NOT stored: the receiver generates a new one on each start. */
data class SavedComputer(val name: String, val host: String, val port: Int)

class SettingsRepository(private val store: KeyValueStore) {

    fun loadSettings(): ControllerSettings {
        val raw = store.getString(KEY_SETTINGS) ?: return ControllerSettings()
        return try {
            val o = JSONObject(raw)
            val d = ControllerSettings()
            ControllerSettings(
                sensitivity = o.optDouble("sensitivity", d.sensitivity.toDouble()).toFloat(),
                deadZone = o.optDouble("deadZone", d.deadZone.toDouble()).toFloat(),
                vibration = o.optBoolean("vibration", d.vibration),
                controlScale = o.optDouble("controlScale", d.controlScale.toDouble()).toFloat(),
            ).sanitized()
        } catch (e: JSONException) {
            ControllerSettings()
        }
    }

    fun saveSettings(s: ControllerSettings) {
        val c = s.sanitized()
        store.putString(
            KEY_SETTINGS,
            JSONObject().put("sensitivity", c.sensitivity.toDouble()).put("deadZone", c.deadZone.toDouble())
                .put("vibration", c.vibration).put("controlScale", c.controlScale.toDouble()).toString(),
        )
    }

    fun loadComputers(): List<SavedComputer> {
        val raw = store.getString(KEY_COMPUTERS) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val host = o.optString("host")
                val port = o.optInt("port", 0)
                if (host.isEmpty() || port !in 1..65535) null
                else SavedComputer(o.optString("name", host).ifEmpty { host }, host, port)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Most recently used first, one entry per host:port, at most [MAX_COMPUTERS]. */
    fun upsertComputer(c: SavedComputer): List<SavedComputer> {
        val list = (listOf(c) + loadComputers().filterNot { it.host == c.host && it.port == c.port }).take(MAX_COMPUTERS)
        writeComputers(list)
        return list
    }

    fun forgetComputer(c: SavedComputer): List<SavedComputer> {
        val list = loadComputers().filterNot { it.host == c.host && it.port == c.port }
        writeComputers(list)
        return list
    }

    private fun writeComputers(list: List<SavedComputer>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("host", it.host).put("port", it.port)) }
        store.putString(KEY_COMPUTERS, arr.toString())
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_COMPUTERS = "computers"
        const val MAX_COMPUTERS = 8
    }
}
