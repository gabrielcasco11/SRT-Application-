package com.gabriel.srtmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gabriel.srtmonitor.model.ConfigStore
import com.gabriel.srtmonitor.ui.components.AppBackground
import com.gabriel.srtmonitor.ui.screens.HomeScreen
import com.gabriel.srtmonitor.ui.screens.PlayerScreen
import com.gabriel.srtmonitor.ui.screens.SetupScreen
import com.gabriel.srtmonitor.ui.screens.SplashScreen
import com.gabriel.srtmonitor.ui.theme.SrtTheme

enum class Screen { SPLASH, HOME, SETUP, PLAYER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as SrtApp
        setContent {
            SrtTheme { AppRoot(app) }
        }
    }
}

@Composable
private fun AppRoot(app: SrtApp) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.SPLASH) }
    var config by remember { mutableStateOf(ConfigStore.load(context)) }
    val wifi by app.wifi.state.collectAsState()

    BackHandler(enabled = screen == Screen.SETUP || screen == Screen.PLAYER) { screen = Screen.HOME }

    AppBackground {
        Crossfade(targetState = screen, animationSpec = tween(250), label = "screens") { s ->
            when (s) {
                Screen.SPLASH -> SplashScreen(onDone = { screen = Screen.HOME })
                Screen.HOME -> HomeScreen(
                    config = config,
                    wifi = wifi,
                    onPlay = { screen = Screen.PLAYER },
                    onEdit = { screen = Screen.SETUP },
                )
                Screen.SETUP -> SetupScreen(
                    initial = config,
                    wifi = wifi,
                    onBack = { screen = Screen.HOME },
                    onSave = { c ->
                        config = c
                        ConfigStore.save(context, c)
                    },
                    onPlay = { c ->
                        config = c
                        ConfigStore.save(context, c)
                        screen = Screen.PLAYER
                    },
                )
                Screen.PLAYER -> PlayerScreen(config = config, app = app, onBack = { screen = Screen.HOME })
            }
        }
    }
}
