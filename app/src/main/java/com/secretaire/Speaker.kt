package com.secretaire

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enveloppe autour de [TextToSpeech] : met les phrases en file d'attente,
 * applique vitesse et hauteur, et baisse le volume de la musique pendant la lecture.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private val settings = Settings(appContext)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private val pendingUtterances = AtomicInteger(0)
    private val nextId = AtomicInteger(0)
    private val waitingForInit = mutableListOf<String>()

    @Volatile
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status != TextToSpeech.SUCCESS) return@TextToSpeech
        onInitialized()
    }

    private fun onInitialized() {
        tts.setAudioAttributes(attributes)
        val locale = Locale.getDefault()
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = onUtteranceFinished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onUtteranceFinished()
            override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceFinished()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = onUtteranceFinished()
        })
        val queued = synchronized(waitingForInit) {
            ready = true
            waitingForInit.toList().also { waitingForInit.clear() }
        }
        queued.forEach(::speak)
    }

    fun speak(text: String) {
        synchronized(waitingForInit) {
            if (!ready) {
                waitingForInit += text
                return
            }
        }
        tts.setSpeechRate(settings.speechRate)
        tts.setPitch(settings.speechPitch)
        if (pendingUtterances.getAndIncrement() == 0) {
            audioManager.requestAudioFocus(focusRequest)
        }
        val id = "secretaire-${nextId.incrementAndGet()}"
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id)
        if (result != TextToSpeech.SUCCESS) onUtteranceFinished()
    }

    fun stop() {
        tts.stop()
        pendingUtterances.set(0)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun onUtteranceFinished() {
        if (pendingUtterances.decrementAndGet() <= 0) {
            pendingUtterances.set(0)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
}
