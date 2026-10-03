package com.secretaire

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Enveloppe autour de [TextToSpeech] : met les phrases en file d'attente,
 * applique vitesse et hauteur, baisse le volume de la musique pendant la lecture,
 * garde le processeur éveillé (écran éteint) et recrée le moteur s'il est tombé.
 *
 * Toutes les méthodes publiques doivent être appelées depuis le thread principal.
 */
class Speaker(context: Context, private val onError: (String) -> Unit = {}) {

    private val appContext = context.applicationContext
    private val settings = Settings(appContext)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wakeLock = appContext.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "secretaire:speech")
        .apply { setReferenceCounted(false) }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private var tts: TextToSpeech? = null
    /** Moteur demandé à la création de [tts] (null = celui du téléphone). */
    private var ttsEngine: String? = null
    private var ready = false
    private val waitingForInit = mutableListOf<String>()
    private val inFlight = mutableSetOf<String>()
    private var nextId = 0

    init {
        createEngine()
    }

    private fun createEngine() {
        ready = false
        // Si le moteur choisi a été désinstallé, on se rabat sur celui du téléphone.
        val wanted = settings.ttsEngine?.takeIf { pkg -> TtsEngines.list(appContext).any { it.packageName == pkg } }
        ttsEngine = settings.ttsEngine
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appContext, { status ->
            mainHandler.post {
                if (tts !== engine) return@post
                if (status == TextToSpeech.SUCCESS) {
                    onInitialized(engine!!)
                } else {
                    onError("Synthèse vocale indisponible (code $status). Vérifiez qu'un moteur est installé.")
                    releaseEngine()
                    finishAll()
                }
            }
        }, wanted)
        tts = engine
    }

    private fun releaseEngine() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun onInitialized(engine: TextToSpeech) {
        engine.setAudioAttributes(attributes)
        applyVoice(engine)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onError("Erreur de lecture (code $errorCode)") }
                finished(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
        ready = true
        val queued = waitingForInit.toList()
        waitingForInit.clear()
        queued.forEach(::speakNow)
    }

    private var appliedVoice: String? = null

    /** Applique la voix choisie, ou à défaut la langue du téléphone. */
    private fun applyVoice(engine: TextToSpeech) {
        val wanted = settings.ttsVoice
        appliedVoice = wanted
        val voice = wanted?.let { name -> runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == name } }
        if (voice != null) {
            engine.voice = voice
            return
        }
        val locale = Locale.getDefault()
        if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = locale
        }
    }

    fun speak(text: String) {
        // Moteur changé dans les réglages : on recrée le moteur.
        if (tts != null && ttsEngine != settings.ttsEngine && inFlight.isEmpty()) releaseEngine()
        if (tts == null) createEngine()
        if (!ready) {
            waitingForInit += text
            acquire()
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return speak(text)
        if (appliedVoice != settings.ttsVoice) applyVoice(engine)
        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.speechPitch)
        acquire()
        val id = "secretaire-${++nextId}"
        inFlight += id
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id)
        if (result != TextToSpeech.SUCCESS) {
            // Le moteur a sans doute été tué en arrière-plan : on le recrée et on réessaie une fois.
            inFlight -= id
            releaseEngine()
            waitingForInit += text
            createEngine()
        }
    }

    private fun acquire() {
        if (inFlight.isEmpty() && waitingForInit.size <= 1) {
            audioManager.requestAudioFocus(focusRequest)
        }
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun finished(utteranceId: String?) {
        mainHandler.post {
            inFlight -= utteranceId ?: return@post
            if (inFlight.isEmpty() && waitingForInit.isEmpty()) finishAll()
        }
    }

    private fun finishAll() {
        inFlight.clear()
        waitingForInit.clear()
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (wakeLock.isHeld) wakeLock.release()
    }

    fun shutdown() {
        tts?.stop()
        releaseEngine()
        finishAll()
    }

    private companion object {
        const val WAKE_LOCK_TIMEOUT_MS = 60_000L
    }
}
