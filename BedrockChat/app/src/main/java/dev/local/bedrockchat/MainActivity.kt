package dev.local.bedrockchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.local.bedrockchat.data.ColorMode
import dev.local.bedrockchat.ui.ChatTheme
import dev.local.bedrockchat.ui.LoginScreen
import dev.local.bedrockchat.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PcWatchService.sync(this)
        if (savedInstanceState == null) handleOpen(intent)
        setContent {
            val credentials by vm.credentials.collectAsStateWithLifecycle()
            val colorMode by vm.colorMode.collectAsStateWithLifecycle()
            val dark = when (colorMode) {
                ColorMode.SYSTEM -> isSystemInDarkTheme()
                ColorMode.LIGHT -> false
                ColorMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ChatTheme(dark) {
                if (credentials == null) LoginScreen(onSignIn = vm::signIn) else MainScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    /** A tap on a PC notification opens that session. */
    private fun handleOpen(intent: Intent?) {
        intent?.getStringExtra(PcWatchService.EXTRA_SESSION)?.let { PcScreenState.openRequest.value = it }
    }
}
