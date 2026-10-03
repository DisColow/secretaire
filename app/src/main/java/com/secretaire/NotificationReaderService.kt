package com.secretaire

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
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
    private lateinit var journal: Journal
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
        journal = Journal(this)
        audioManager = getSystemService(AudioManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
    }

    override fun onListenerConnected() {
        speaker()
        KeepAliveService.sync(this)
    }

    override fun onListenerDisconnected() {
        speaker?.shutdown()
        speaker = null
        // Android peut déconnecter le service (mémoire, mise à jour) : on demande à être relié de nouveau.
        requestRebind(ComponentName(this, NotificationReaderService::class.java))
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (!KeepAliveService.running) KeepAliveService.sync(this)
        val notification = sbn.notification
        val flags = notification.flags
        // Notifications permanentes (lecteur, téléchargement…) et résumés de groupe : jamais lus, pas journalisés.
        if (sbn.isOngoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val app = appName(sbn)
        val text = when (val decision = decide(sbn, app)) {
            is Decision.Skip -> {
                journal.add(app, "ignorée : ${decision.reason}")
                return
            }
            is Decision.Speak -> decision.text
        }

        // Les applis republient souvent la même notification : on évite les répétitions.
        val now = SystemClock.elapsedRealtime()
        if (lastSpokenByKey[sbn.key] == text ||
            (text == lastSpokenText && now - lastSpokenAt < DUPLICATE_WINDOW_MS)
        ) return

        lastSpokenByKey[sbn.key] = text
        if (lastSpokenByKey.size > MAX_REMEMBERED_KEYS) {
            lastSpokenByKey.remove(lastSpokenByKey.keys.first())
        }
        lastSpokenText = text
        lastSpokenAt = now

        journal.add(app, "lue")
        speaker().speak(text)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastSpokenByKey.remove(sbn.key)
    }

    private sealed interface Decision {
        data class Speak(val text: String) : Decision
        data class Skip(val reason: String) : Decision
    }

    private fun decide(sbn: StatusBarNotification, app: String): Decision {
        if (!settings.enabled) return Decision.Skip("lecture désactivée")

        val mode = settings.effectiveMode(sbn.packageName)
        if (mode == ReadingMode.MUTED) return Decision.Skip("appli en muet")

        val ranking = Ranking().takeIf { currentRanking?.getRanking(sbn.key, it) == true }
        if (settings.ignoreSilentNotifications && ranking != null &&
            ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        ) return Decision.Skip("notification discrète")

        if (settings.respectSilentAndDnd) {
            silenceReason(ranking)?.let { return Decision.Skip(it) }
        }
        if (settings.headphonesOnly && !headphonesConnected()) return Decision.Skip("pas d'écouteurs")
        if (settings.screenOffOnly && powerManager.isInteractive) return Decision.Skip("écran allumé")

        val calendar = Calendar.getInstance()
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        if (settings.isInQuietHours(minuteOfDay)) return Decision.Skip("plage horaire silencieuse")

        val text = NotificationText.build(app, sbn.notification, mode) ?: return Decision.Skip("rien à lire")
        return Decision.Speak(text)
    }

    private fun speaker(): Speaker =
        speaker ?: Speaker(this) { journal.add("Secrétaire", it) }.also { speaker = it }

    /** Raison de ne pas lire (silencieux / Ne pas déranger), ou null. Le vibreur n'empêche pas la lecture. */
    private fun silenceReason(ranking: Ranking?): String? {
        val filter = currentInterruptionFilter
        if (filter != INTERRUPTION_FILTER_ALL && filter != INTERRUPTION_FILTER_UNKNOWN) {
            // En Ne pas déranger « prioritaire », on laisse passer ce que le système laisse passer.
            if (ranking?.matchesInterruptionFilter() != true) return "Ne pas déranger"
            return null
        }
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return "mode silencieux"
        return null
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
