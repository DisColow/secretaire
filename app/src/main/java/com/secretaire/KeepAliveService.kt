package com.secretaire

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.secretaire.ui.MainActivity

/**
 * Service de premier plan, avec une notification permanente qui affiche l'état
 * (lecture activée / en pause) et un bouton pour basculer de l'un à l'autre.
 *
 * Sa présence empêche aussi Android de mettre en pause le processus de l'appli
 * quand elle est en arrière-plan. Sans lui, beaucoup de téléphones gèlent l'appli
 * et les notifications ne sont lues qu'à sa réouverture.
 */
class KeepAliveService : Service() {

    private lateinit var settings: Settings

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.KEY_ENABLED || key == Settings.KEY_DEFAULT_MODE || key == null) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
            running = true
        } catch (e: Exception) {
            Journal(this).add("Secrétaire", "impossible de rester actif en arrière-plan (${e.javaClass.simpleName})")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Republie la notification (par ex. après l'octroi de la permission des notifications).
        if (running) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        // Ancien canal « minimal » (notification invisible) remplacé par un canal visible mais silencieux.
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "État de Secrétaire", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notification permanente : état de la lecture et bouton Pause / Reprendre."
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, ToggleReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val enabled = settings.enabled
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (enabled) "Secrétaire : lecture activée" else "Secrétaire : en pause")
            .setContentText(
                if (enabled) "Lecture : ${settings.defaultMode.label.lowercase()}" else "Aucune notification n'est lue",
            )
            .setContentIntent(openApp)
            .addAction(0, if (enabled) "Mettre en pause" else "Reprendre", toggle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val OLD_CHANNEL_ID = "keep_alive"
        private const val CHANNEL_ID = "status"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var running = false
            private set

        /** Démarre le service (et sa notification) s'il ne tourne pas déjà, même en pause. */
        fun sync(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (running) return
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android interdit de démarrer un service de premier plan depuis l'arrière-plan,
                // sauf si l'optimisation de batterie est désactivée pour l'appli.
                Journal(context).add(
                    "Secrétaire",
                    "impossible de rester actif en arrière-plan : désactivez l'optimisation de batterie",
                )
            }
        }
    }
}
