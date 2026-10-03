package com.secretaire.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
        requestNotificationPermission()

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

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // La notification permanente existe déjà ; on la republie pour qu'elle apparaisse.
            if (granted) startService(Intent(this, KeepAliveService::class.java))
        }

    /** Android 13+ : sans cette permission, la notification permanente reste invisible. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
