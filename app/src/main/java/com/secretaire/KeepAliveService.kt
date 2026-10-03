package com.secretaire

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.secretaire.ui.MainActivity

/**
 * Service de premier plan, avec une notification permanente discrète.
 *
 * Il ne fait rien lui-même : sa seule présence empêche Android de mettre en pause
 * le processus de l'appli quand elle est en arrière-plan. Sans lui, beaucoup de
 * téléphones gèlent l'appli et les notifications ne sont lues qu'à sa réouverture.
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Secrétaire actif", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Notification permanente qui permet à Secrétaire de lire " +
                    "les notifications quand l'appli est fermée."
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Secrétaire lit vos notifications")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var running = false
            private set

        /** Démarre le service si la lecture est activée, l'arrête sinon. */
        fun sync(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (!Settings(context).enabled) {
                context.stopService(intent)
                return
            }
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
