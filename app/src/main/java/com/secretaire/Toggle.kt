package com.secretaire

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

/** Active ou met en pause la lecture, depuis l'appli, la notification ou la tuile. */
object Toggle {

    fun set(context: Context, enabled: Boolean) {
        Settings(context).enabled = enabled
        // La notification permanente se met à jour d'elle-même (elle écoute les réglages) ;
        // la tuile des réglages rapides doit être prévenue.
        TileService.requestListeningState(context, ComponentName(context, QuickTileService::class.java))
    }

    fun flip(context: Context) = set(context, !Settings(context).enabled)
}

/** Reçoit l'appui sur le bouton « Pause / Reprendre » de la notification permanente. */
class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toggle.flip(context)
    }
}
