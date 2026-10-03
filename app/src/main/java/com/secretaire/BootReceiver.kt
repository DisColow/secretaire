package com.secretaire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance le service de premier plan au démarrage du téléphone et après une mise à jour de l'appli. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> KeepAliveService.sync(context)
        }
    }
}
