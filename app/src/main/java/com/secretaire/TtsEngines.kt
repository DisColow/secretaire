package com.secretaire

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** Moteurs de synthèse vocale installés (Google, Samsung, sherpa-onnx…) et leurs voix. */
object TtsEngines {

    data class Engine(val packageName: String, val label: String)

    data class VoiceOption(val name: String, val label: String)

    fun list(context: Context): List<Engine> {
        val pm = context.packageManager
        return pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { Engine(it.serviceInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Voix du moteur dans la langue du téléphone (toutes si aucune ne correspond),
     * de la meilleure qualité à la moins bonne, voix hors ligne d'abord.
     */
    fun voices(engine: TextToSpeech): List<VoiceOption> {
        val all = runCatching { engine.voices }.getOrNull().orEmpty()
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        val language = Locale.getDefault().language
        val sameLanguage = all.filter { it.locale.language == language }
        return (sameLanguage.ifEmpty { all })
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
            .map { VoiceOption(it.name, label(it)) }
    }

    private fun label(voice: Voice): String {
        val quality = when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> "très haute qualité"
            voice.quality >= Voice.QUALITY_HIGH -> "haute qualité"
            voice.quality >= Voice.QUALITY_NORMAL -> "qualité normale"
            else -> "qualité basse"
        }
        val network = if (voice.isNetworkConnectionRequired) ", en ligne" else ""
        return "${voice.name} — ${voice.locale.displayName} ($quality$network)"
    }
}
