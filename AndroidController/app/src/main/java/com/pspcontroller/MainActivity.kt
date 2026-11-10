package com.pspcontroller

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pspcontroller.ui.ConnectScreen
import com.pspcontroller.ui.ControllerScreen
import com.pspcontroller.ui.ControllerViewModel
import com.pspcontroller.ui.PspTheme
import com.pspcontroller.ui.Screen
import com.pspcontroller.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: ControllerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { PspTheme { App(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground() // releases all buttons, then disconnects
        super.onStop()
    }
}

@Composable
private fun App(vm: ControllerViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val computers by vm.computers.collectAsStateWithLifecycle()
    val state by vm.manager.state.collectAsStateWithLifecycle()
    val rtt by vm.manager.rttMs.collectAsStateWithLifecycle()
    val log by vm.manager.log.collectAsStateWithLifecycle()

    BackHandler(enabled = vm.screen != Screen.Connect) { vm.back() }

    when (vm.screen) {
        Screen.Connect -> ConnectScreen(
            state = state,
            computers = computers,
            initialHost = vm.initialHost,
            onConnect = vm::connect,
            onForget = vm::forgetComputer,
            onOpenSettings = vm::openSettings,
        )
        Screen.Controller -> ControllerScreen(
            settings = settings,
            state = state,
            rttMs = rtt,
            onPressed = vm::setPressed,
            onStick = vm::setStick,
            onPulse = vm::pulse,
            onOpenSettings = vm::openSettings,
        )
        Screen.Settings -> SettingsScreen(
            settings = settings,
            onChange = vm::updateSettings,
            state = state,
            rttMs = rtt,
            log = log,
            onDisconnect = vm::disconnect,
            onBack = vm::back,
        )
    }
}
