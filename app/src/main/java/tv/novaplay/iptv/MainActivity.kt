package tv.novaplay.iptv

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tv.novaplay.iptv.data.model.SourceType
import tv.novaplay.iptv.ui.MainViewModel
import tv.novaplay.iptv.ui.screens.MainScreen
import tv.novaplay.iptv.ui.theme.NovaPlayTheme

/**
 * Main Activity supporting Mobile (Portrait & Landscape), Tablet, Android TV Leanback
 * (D-Pad remote control zapping), True Immersive Fullscreen, Picture-in-Picture (PiP),
 * and external M3U playlist deep-links.
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NovaPlayApplication
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(app.repository, app.playerController) as T
                }
            }
        )[MainViewModel::class.java]

        // Handle external M3U / M3U8 deep-link intent if launched from a link
        intent?.data?.toString()?.takeIf { it.startsWith("http") }?.let { externalM3uUrl ->
            viewModel.addPlaylistSource(
                name = "Playlist Importée",
                type = SourceType.M3U_URL,
                serverUrl = externalM3uUrl,
                navigateToLiveOnSuccess = true
            )
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // True Immersive Fullscreen & Auto-Landscape Rotation when entering fullscreen player
            LaunchedEffect(uiState.isPlayerFullscreen) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (uiState.isPlayerFullscreen) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            NovaPlayTheme {
                MainScreen(
                    viewModel = viewModel,
                    isInPipMode = isInPipMode,
                    onRequestPip = { enterPipIfSupported() }
                )
            }
        }
    }

    /**
     * Android TV & Remote Control D-Pad support:
     * - Channel Up / Down buttons zap channels immediately
     * - Up / Down D-Pad in Fullscreen player zaps channels
     * - Media Play/Pause toggles playback
     * - Back button exits Fullscreen or VOD Detail preview before closing the app
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.uiState.value
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                viewModel.zapChannel(-1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                viewModel.zapChannel(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (state.isPlayerFullscreen) {
                    viewModel.zapChannel(-1)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (state.isPlayerFullscreen) {
                    viewModel.zapChannel(1)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                viewModel.playerController.togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (state.isPlayerFullscreen) {
                    viewModel.toggleFullscreen()
                    return true
                }
                if (state.selectedVodDetail != null) {
                    viewModel.openVodDetail(null)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun enterPipIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.playerController.uiState.value.isPlaying) {
            enterPipIfSupported()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onPause() {
        viewModel.saveActiveVodProgressIfNeeded()
        super.onPause()
    }
}
