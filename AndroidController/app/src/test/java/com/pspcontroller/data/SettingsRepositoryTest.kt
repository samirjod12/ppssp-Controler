package com.pspcontroller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private val store = MemoryStore()

    @Test fun defaultsWhenEmpty() {
        assertEquals(ControllerSettings(), SettingsRepository(store).loadSettings())
        assertTrue(SettingsRepository(store).loadComputers().isEmpty())
    }

    @Test fun settingsSurviveANewRepositoryInstance() {
        SettingsRepository(store).saveSettings(ControllerSettings(sensitivity = 1.4f, deadZone = 0.2f, vibration = false, controlScale = 1.1f))
        val loaded = SettingsRepository(store).loadSettings()
        assertEquals(1.4f, loaded.sensitivity, 1e-6f); assertEquals(0.2f, loaded.deadZone, 1e-6f)
        assertFalse(loaded.vibration); assertEquals(1.1f, loaded.controlScale, 1e-6f)
    }

    @Test fun outOfRangeValuesAreClamped() {
        SettingsRepository(store).saveSettings(ControllerSettings(sensitivity = 99f, deadZone = -1f, controlScale = 0.1f))
        val l = SettingsRepository(store).loadSettings()
        assertEquals(2.0f, l.sensitivity, 0f); assertEquals(0.0f, l.deadZone, 0f); assertEquals(0.8f, l.controlScale, 0f)
    }

    @Test fun corruptStorageFallsBackToDefaults() {
        store.map["settings"] = "{{{ nope"; store.map["computers"] = "[[["
        val repo = SettingsRepository(store)
        assertEquals(ControllerSettings(), repo.loadSettings()); assertTrue(repo.loadComputers().isEmpty())
    }

    @Test fun computersAreDedupedMostRecentFirstAndForgettable() {
        val repo = SettingsRepository(store)
        repo.upsertComputer(SavedComputer("A", "192.168.1.2", 8765))
        repo.upsertComputer(SavedComputer("B", "192.168.1.3", 8765))
        repo.upsertComputer(SavedComputer("A2", "192.168.1.2", 8765))
        val list = SettingsRepository(store).loadComputers()
        assertEquals(listOf("A2", "B"), list.map { it.name })
        repo.forgetComputer(list[0])
        assertEquals(listOf("B"), SettingsRepository(store).loadComputers().map { it.name })
    }

    @Test fun pinIsNeverStored() {
        val repo = SettingsRepository(store)
        repo.upsertComputer(SavedComputer("A", "10.0.0.5", 8765))
        assertFalse(store.map.values.any { it.contains("pin", ignoreCase = true) })
    }
}
