package com.secretaire

import android.app.Notification
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Calendar

/**
 * Service appelé par Android à chaque nouvelle notification.
 * Il décide si la notification doit être lue, puis la confie au [Speaker].
 */
class NotificationReaderService : NotificationListenerService() {

    private lateinit var settings: Settings
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private var speaker: Speaker? = null

    /** Dernier texte lu par clé de notification, pour ignorer les simples mises à jour. */
    private val lastSpokenByKey = LinkedHashMap<String, String>()
    private var lastSpokenText: String? = null
    private var lastSpokenAt = 0L

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        audioManager = getSystemService(AudioManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
    }

    override fun onListenerConnected() {
        speaker = speaker ?: Speaker(this)
    }

    override fun onListenerDisconnected() {
        speaker?.shutdown()
        speaker = null
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val text = textToSpeak(sbn) ?: return

        // Les applis republient souvent la même notification : on évite les répétitions.
        if (lastSpokenByKey[sbn.key] == text) return
        val now = SystemClock.elapsedRealtime()
        if (text == lastSpokenText && now - lastSpokenAt < DUPLICATE_WINDOW_MS) return

        lastSpokenByKey[sbn.key] = text
        if (lastSpokenByKey.size > MAX_REMEMBERED_KEYS) {
            lastSpokenByKey.remove(lastSpokenByKey.keys.first())
        }
        lastSpokenText = text
        lastSpokenAt = now

        (speaker ?: Speaker(this).also { speaker = it }).speak(text)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastSpokenByKey.remove(sbn.key)
    }

    private fun textToSpeak(sbn: StatusBarNotification): String? {
        if (!settings.enabled) return null
        if (sbn.packageName == packageName) return null

        val notification = sbn.notification
        val flags = notification.flags
        if (sbn.isOngoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return null
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null

        val mode = settings.effectiveMode(sbn.packageName)
        if (mode == ReadingMode.MUTED) return null

        val ranking = Ranking().takeIf { currentRanking?.getRanking(sbn.key, it) == true }
        if (settings.ignoreSilentNotifications && ranking != null &&
            ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        ) return null

        if (settings.respectSilentAndDnd && isSilenced(ranking)) return null
        if (settings.headphonesOnly && !headphonesConnected()) return null
        if (settings.screenOffOnly && powerManager.isInteractive) return null

        val calendar = Calendar.getInstance()
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        if (settings.isInQuietHours(minuteOfDay)) return null

        return NotificationText.build(appName(sbn), notification, mode)
    }

    private fun isSilenced(ranking: Ranking?): Boolean {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        val filter = currentInterruptionFilter
        if (filter == INTERRUPTION_FILTER_ALL || filter == INTERRUPTION_FILTER_UNKNOWN) return false
        // En mode Ne pas déranger « prioritaire », on laisse passer ce que le système laisse passer.
        return ranking?.matchesInterruptionFilter() != true
    }

    private fun headphonesConnected(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }

    private fun appName(sbn: StatusBarNotification): String {
        val pm = packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            // L'appli n'est pas visible : Android joint souvent ses infos à la notification.
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sbn.notification.extras.getParcelable(EXTRA_APP_INFO, ApplicationInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                sbn.notification.extras.getParcelable(EXTRA_APP_INFO)
            }
            info?.loadLabel(pm)?.toString() ?: sbn.packageName
        }
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 5_000L
        private const val MAX_REMEMBERED_KEYS = 200
        private const val EXTRA_APP_INFO = "android.appInfo"

        private val HEADPHONE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        }
    }
}
