package com.example.intraplay

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.intraplay.ui.TvHomeScreen
import com.example.intraplay.ui.TvLanCinemaScreen
import com.example.intraplay.ui.TvTheme

enum class TvScreenMode {
    CINEMA_HUB,
    LIVE_IPTV
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Keep TV screen on while watching
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Hide system bars for immersive TV experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            TvTheme {
                TvAppRoot()
            }
        }
    }
}

@Composable
fun TvAppRoot() {
    var screenMode by remember { mutableStateOf(TvScreenMode.CINEMA_HUB) }

    when (screenMode) {
        TvScreenMode.CINEMA_HUB -> {
            TvLanCinemaScreen(
                onNavigateLiveTv = { screenMode = TvScreenMode.LIVE_IPTV },
                onPlayMovie = { /* Future ExoPlayer video launcher */ }
            )
        }
        TvScreenMode.LIVE_IPTV -> {
            TvHomeScreen(
                onNavigateCinema = { screenMode = TvScreenMode.CINEMA_HUB }
            )
        }
    }
}
