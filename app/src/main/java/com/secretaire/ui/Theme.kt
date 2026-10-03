package com.secretaire.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.secretaire.Settings
import android.content.SharedPreferences

@Composable
fun SecretaireTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Renvoie un compteur qui change à chaque modification des réglages,
 * pour que l'interface se recompose quand on les lit.
 */
@Composable
fun rememberSettingsVersion(settings: Settings): Int {
    val version = remember { mutableIntStateOf(0) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version.intValue++ }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val current by version
    return current
}
