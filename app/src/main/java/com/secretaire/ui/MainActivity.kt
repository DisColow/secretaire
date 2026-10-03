package com.secretaire.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.secretaire.KeepAliveService
import com.secretaire.Settings
import com.secretaire.Speaker

class MainActivity : ComponentActivity() {

    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speaker = Speaker(this)
        val settings = Settings(this)

        setContent {
            SecretaireTheme {
                var showApps by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showApps) { showApps = false }
                if (showApps) {
                    AppsScreen(settings = settings, onBack = { showApps = false })
                } else {
                    HomeScreen(
                        settings = settings,
                        onTestVoice = { speaker.speak(it) },
                        onOpenApps = { showApps = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Depuis l'appli au premier plan, Android autorise toujours le démarrage du service.
        KeepAliveService.sync(this)
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
