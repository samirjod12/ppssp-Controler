package com.pspcontroller.ui

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pspcontroller.data.ControllerSettings
import com.pspcontroller.data.SavedComputer
import com.pspcontroller.data.SettingsRepository
import com.pspcontroller.data.SharedPrefsStore
import com.pspcontroller.model.InputSender
import com.pspcontroller.model.Protocol
import com.pspcontroller.model.PspButton
import com.pspcontroller.model.StickValue
import com.pspcontroller.network.ConnectionManager
import com.pspcontroller.network.ConnectionState
import com.pspcontroller.network.OkHttpTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { Connect, Controller, Settings }

class ControllerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(SharedPrefsStore(app))

    private val _settings = MutableStateFlow(repo.loadSettings())
    val settings: StateFlow<ControllerSettings> = _settings.asStateFlow()

    private val _computers = MutableStateFlow(repo.loadComputers())
    val computers: StateFlow<List<SavedComputer>> = _computers.asStateFlow()

    val manager = ConnectionManager(viewModelScope, { OkHttpTransport() }, Protocol.sanitizeClientName(Build.MODEL))
    private val sender = InputSender { manager.send(it) }

    var screen by mutableStateOf(Screen.Connect)
        private set
    var initialHost by mutableStateOf(repo.loadComputers().firstOrNull()?.host ?: "")
        private set

    private var pendingTarget: Pair<String, Int>? = null
    private var paused = false
    private var tickJob: Job? = null
    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = app.getSystemService(Vibrator::class.java)

    init {
        manager.onSessionEnded = { sender.reset() }
        viewModelScope.launch { manager.state.collect { onState(it) } }
    }

    fun connect(host: String, port: Int, pin: String) {
        pendingTarget = host to port
        manager.connect(host, port, pin)
    }

    fun disconnect() {
        sender.releaseAll()
        manager.disconnect()
    }

    fun setPressed(owner: String, buttons: Set<PspButton>) = sender.setPressed(owner, buttons)
    fun setStick(value: StickValue) = sender.setAnalog(value)

    fun updateSettings(s: ControllerSettings) {
        val clean = s.sanitized()
        _settings.value = clean
        repo.saveSettings(clean)
    }

    fun forgetComputer(c: SavedComputer) { _computers.value = repo.forgetComputer(c) }

    fun pulse() {
        if (!_settings.value.vibration) return
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun openSettings() { screen = Screen.Settings }

    /** Hardware back: Controller -> Settings -> Controller/Connect. */
    fun back() {
        screen = when (screen) {
            Screen.Controller -> Screen.Settings
            Screen.Settings -> if (manager.state.value is ConnectionState.Connected || manager.state.value is ConnectionState.Reconnecting) Screen.Controller else Screen.Connect
            Screen.Connect -> Screen.Connect
        }
    }

    /** App left the foreground: release every held button first, then drop the socket. */
    fun onBackground() {
        paused = true
        sender.releaseAll()
        manager.pause()
    }

    fun onForeground() {
        paused = false
        manager.resume()
    }

    private fun onState(s: ConnectionState) {
        when (s) {
            is ConnectionState.Connected -> {
                pendingTarget?.let { (h, p) ->
                    _computers.value = repo.upsertComputer(SavedComputer(s.serverName, h, p))
                    initialHost = h
                }
                if (screen == Screen.Connect) screen = Screen.Controller
                startTicker()
            }
            is ConnectionState.Failed -> {
                stopTicker()
                screen = Screen.Connect
            }
            ConnectionState.Disconnected -> {
                stopTicker()
                if (!paused) screen = Screen.Connect
            }
            else -> stopTicker()
        }
    }

    /** ~60 Hz analog flush, only while connected. */
    private fun startTicker() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(16)
                sender.tick()
            }
        }
    }

    private fun stopTicker() { tickJob?.cancel(); tickJob = null }

    override fun onCleared() {
        sender.releaseAll()
        manager.disconnect()
    }
}
