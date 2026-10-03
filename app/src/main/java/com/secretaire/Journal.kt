package com.secretaire

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historique des dernières notifications reçues et de ce qu'on en a fait
 * (lue, ou ignorée et pourquoi). Sert à comprendre pourquoi rien n'a été lu.
 */
class Journal(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("journal", Context.MODE_PRIVATE)

    fun add(app: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        val line = "$time  $app — $message".replace('\n', ' ')
        synchronized(LOCK) {
            val lines = listOf(line) + entries().take(MAX_ENTRIES - 1)
            prefs.edit().putString(KEY_LINES, lines.joinToString("\n")).apply()
        }
    }

    /** Entrées les plus récentes en premier. */
    fun entries(): List<String> =
        prefs.getString(KEY_LINES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun clear() = prefs.edit().remove(KEY_LINES).apply()

    companion object {
        private const val KEY_LINES = "lines"
        private const val MAX_ENTRIES = 50
        private val LOCK = Any()
    }
}
