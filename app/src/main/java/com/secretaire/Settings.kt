package com.secretaire

import android.content.Context
import android.content.SharedPreferences

/**
 * Réglages de l'appli, stockés dans des SharedPreferences pour être lus
 * de façon synchrone par le service d'écoute des notifications.
 */
class Settings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var defaultMode: ReadingMode
        get() = prefs.getString(KEY_DEFAULT_MODE, null)
            ?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ReadingMode.MUTED }
            ?: ReadingMode.APP_NAME
        set(value) = prefs.edit().putString(KEY_DEFAULT_MODE, value.name).apply()

    var headphonesOnly: Boolean
        get() = prefs.getBoolean(KEY_HEADPHONES_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_HEADPHONES_ONLY, value).apply()

    var respectSilentAndDnd: Boolean
        get() = prefs.getBoolean(KEY_RESPECT_SILENT, true)
        set(value) = prefs.edit().putBoolean(KEY_RESPECT_SILENT, value).apply()

    /** Ignorer les notifications qu'Android affiche sans son (importance basse). */
    var ignoreSilentNotifications: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_SILENT_NOTIFS, true)
        set(value) = prefs.edit().putBoolean(KEY_IGNORE_SILENT_NOTIFS, value).apply()

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    /** Début de la plage silencieuse, en minutes depuis minuit. */
    var quietStart: Int
        get() = prefs.getInt(KEY_QUIET_START, 22 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_START, value).apply()

    /** Fin de la plage silencieuse, en minutes depuis minuit. */
    var quietEnd: Int
        get() = prefs.getInt(KEY_QUIET_END, 7 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_END, value).apply()

    /** Vitesse de la voix (1.0 = normale). */
    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_RATE, value).apply()

    /** Hauteur de la voix (1.0 = normale). */
    var speechPitch: Float
        get() = prefs.getFloat(KEY_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value).apply()

    fun appSetting(packageName: String): AppSetting =
        prefs.getString(APP_PREFIX + packageName, null)
            ?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
            ?.let { AppSetting.Override(it) }
            ?: AppSetting.UseDefault

    fun setAppSetting(packageName: String, setting: AppSetting) {
        prefs.edit().apply {
            when (setting) {
                AppSetting.UseDefault -> remove(APP_PREFIX + packageName)
                is AppSetting.Override -> putString(APP_PREFIX + packageName, setting.mode.name)
            }
        }.apply()
    }

    /** Paquets qui ont un réglage spécifique. */
    fun overriddenPackages(): Set<String> =
        prefs.all.keys.filter { it.startsWith(APP_PREFIX) }.map { it.removePrefix(APP_PREFIX) }.toSet()

    /** Mode effectif pour une appli, en tenant compte du réglage global. */
    fun effectiveMode(packageName: String): ReadingMode =
        when (val s = appSetting(packageName)) {
            AppSetting.UseDefault -> defaultMode
            is AppSetting.Override -> s.mode
        }

    /** Vrai si [minuteOfDay] tombe dans la plage silencieuse (qui peut passer minuit). */
    fun isInQuietHours(minuteOfDay: Int): Boolean {
        if (!quietHoursEnabled) return false
        val start = quietStart
        val end = quietEnd
        return when {
            start == end -> false
            start < end -> minuteOfDay in start until end
            else -> minuteOfDay >= start || minuteOfDay < end
        }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEFAULT_MODE = "default_mode"
        private const val KEY_HEADPHONES_ONLY = "headphones_only"
        private const val KEY_RESPECT_SILENT = "respect_silent"
        private const val KEY_IGNORE_SILENT_NOTIFS = "ignore_silent_notifications"
        private const val KEY_QUIET_ENABLED = "quiet_enabled"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "speech_pitch"
        private const val APP_PREFIX = "app_mode:"
    }
}
