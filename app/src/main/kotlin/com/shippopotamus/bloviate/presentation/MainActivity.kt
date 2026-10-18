package com.shippopotamus.bloviate.presentation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.shippopotamus.whisper.WhisperEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Launcher activity hosting the status screen. The [WhisperEngine] injection
 * proves the composite-build substitution and the whisper-android-hilt
 * bindings resolve in the app's Hilt graph; no transcription happens yet.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var whisperEngine: WhisperEngine

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainRoute(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
