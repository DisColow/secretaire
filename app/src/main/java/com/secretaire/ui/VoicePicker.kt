package com.secretaire.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.secretaire.Settings
import com.secretaire.TtsEngines

/** Choix du moteur de synthèse vocale et de la voix utilisés par Secrétaire. */
@Composable
fun VoicePicker(settings: Settings) {
    val context = LocalContext.current
    var engines by remember { mutableStateOf(TtsEngines.list(context)) }
    // Un moteur (ex. sherpa-onnx) a pu être installé pendant que l'appli était en arrière-plan.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { engines = TtsEngines.list(context) }

    val selectedEngine = settings.ttsEngine?.takeIf { pkg -> engines.any { it.packageName == pkg } }
    var voices by remember { mutableStateOf<List<TtsEngines.VoiceOption>?>(null) }

    // Charge la liste des voix du moteur choisi.
    DisposableEffect(selectedEngine) {
        voices = null
        var probe: TextToSpeech? = null
        probe = TextToSpeech(context, { status ->
            voices = if (status == TextToSpeech.SUCCESS) probe?.let(TtsEngines::voices).orEmpty() else emptyList()
            probe?.shutdown()
            probe = null
        }, selectedEngine)
        onDispose { probe?.shutdown() }
    }

    Picker(
        title = "Moteur",
        value = engines.firstOrNull { it.packageName == selectedEngine }?.label ?: "Celui du téléphone",
        options = listOf(null to "Celui du téléphone") + engines.map { it.packageName to it.label },
        onSelect = { settings.ttsEngine = it },
    )

    val list = voices
    Picker(
        title = "Voix",
        value = when {
            list == null -> "Chargement…"
            else -> list.firstOrNull { it.name == settings.ttsVoice }?.label ?: "Voix par défaut du moteur"
        },
        options = listOf(null to "Voix par défaut du moteur") + list.orEmpty().map { it.name to it.label },
        onSelect = { settings.ttsVoice = it },
        enabled = !list.isNullOrEmpty(),
    )
}

@Composable
private fun Picker(
    title: String,
    value: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { open = true }
                .padding(vertical = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(key)
                        open = false
                    },
                )
            }
        }
    }
}
