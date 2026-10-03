package com.secretaire.ui

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.secretaire.Journal
import com.secretaire.NotificationText
import com.secretaire.ReadingMode
import com.secretaire.Settings
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: Settings,
    onTestVoice: (String) -> Unit,
    onOpenApps: () -> Unit,
) {
    val context = LocalContext.current
    rememberSettingsVersion(settings) // recompose quand les réglages changent

    val journal = remember { Journal(context) }
    val journalVersion = rememberPrefsVersion(journal.prefs)
    val journalEntries = remember(journalVersion) { journal.entries() }

    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    var batteryRestricted by remember { mutableStateOf(isBatteryRestricted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = hasNotificationAccess(context)
        batteryRestricted = isBatteryRestricted(context)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Secrétaire") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!hasAccess) {
                AccessCard(onClick = { openNotificationAccessSettings(context) })
            } else if (batteryRestricted) {
                WarningCard(
                    title = "Optimisation de batterie active",
                    text = "Android risque d'endormir Secrétaire quand l'écran est éteint, " +
                        "et plus rien ne sera lu. Autorisez-la à fonctionner sans restriction.",
                    button = "Désactiver l'optimisation",
                    onClick = { requestBatteryExemption(context) },
                )
            }

            Section("Lecture") {
                SwitchRow(
                    title = "Lire les notifications à voix haute",
                    checked = settings.enabled,
                    onCheckedChange = { settings.enabled = it },
                )
                HorizontalDivider()
                Text(
                    "Par défaut, lire :",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                listOf(ReadingMode.APP_NAME, ReadingMode.FULL).forEach { mode ->
                    RadioRow(
                        label = mode.label,
                        example = example(mode),
                        selected = settings.defaultMode == mode,
                        onClick = { settings.defaultMode = mode },
                    )
                }
                HorizontalDivider()
                ClickableRow(
                    title = "Réglages par appli",
                    subtitle = appsSummary(settings),
                    onClick = onOpenApps,
                )
            }

            Section("Conditions") {
                SwitchRow(
                    title = "Seulement avec des écouteurs",
                    subtitle = "Filaires, USB ou Bluetooth",
                    checked = settings.headphonesOnly,
                    onCheckedChange = { settings.headphonesOnly = it },
                )
                SwitchRow(
                    title = "Seulement écran éteint",
                    subtitle = "Rien n'est lu pendant que vous utilisez le téléphone",
                    checked = settings.screenOffOnly,
                    onCheckedChange = { settings.screenOffOnly = it },
                )
                SwitchRow(
                    title = "Respecter le mode silencieux et Ne pas déranger",
                    subtitle = "Rien n'est lu en silencieux ; en Ne pas déranger, seules les " +
                        "notifications autorisées sont lues. Le vibreur n'empêche pas la lecture.",
                    checked = settings.respectSilentAndDnd,
                    onCheckedChange = { settings.respectSilentAndDnd = it },
                )
                SwitchRow(
                    title = "Ignorer les notifications discrètes",
                    subtitle = "Celles qu'Android affiche sans son",
                    checked = settings.ignoreSilentNotifications,
                    onCheckedChange = { settings.ignoreSilentNotifications = it },
                )
                HorizontalDivider()
                SwitchRow(
                    title = "Plage horaire silencieuse",
                    subtitle = "Ne rien lire de ${formatTime(settings.quietStart)} " +
                        "à ${formatTime(settings.quietEnd)}",
                    checked = settings.quietHoursEnabled,
                    onCheckedChange = { settings.quietHoursEnabled = it },
                )
                if (settings.quietHoursEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pickTime(context, settings.quietStart) { settings.quietStart = it }
                        }) { Text("Début : ${formatTime(settings.quietStart)}") }
                        OutlinedButton(onClick = {
                            pickTime(context, settings.quietEnd) { settings.quietEnd = it }
                        }) { Text("Fin : ${formatTime(settings.quietEnd)}") }
                    }
                }
            }

            Section("Voix") {
                SliderRow(
                    title = "Vitesse",
                    value = settings.speechRate,
                    onValueChange = { settings.speechRate = it },
                )
                SliderRow(
                    title = "Hauteur",
                    value = settings.speechPitch,
                    onValueChange = { settings.speechPitch = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onTestVoice(example(settings.defaultMode)) }) { Text("Tester") }
                    OutlinedButton(onClick = { openTtsSettings(context) }) { Text("Choisir la voix") }
                }
            }

            Section("Journal") {
                if (journalEntries.isEmpty()) {
                    Text(
                        if (hasAccess) {
                            "Aucune notification reçue pour l'instant. Si vous en avez reçu, " +
                                "Android a sans doute arrêté Secrétaire : désactivez l'optimisation " +
                                "de batterie, puis retirez et redonnez l'accès aux notifications."
                        } else {
                            "Aucune notification reçue : l'accès aux notifications n'est pas autorisé."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Dernières notifications et ce qui en a été fait :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    journalEntries.take(20).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { journal.clear() }) { Text("Effacer") }
                }
            }
        }
    }
}

@Composable
private fun AccessCard(onClick: () -> Unit) {
    WarningCard(
        title = "Accès aux notifications requis",
        text = "Pour lire vos notifications, Secrétaire doit y avoir accès. " +
            "Activez « Secrétaire » dans l'écran qui va s'ouvrir.",
        button = "Autoriser l'accès",
        onClick = onClick,
    )
}

@Composable
private fun WarningCard(title: String, text: String, button: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(label: String, example: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("« $example »", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClickableRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text("$title : ${String.format(Locale.FRANCE, "%.1f", value)}×", style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = 0.5f..2f, steps = 14)
    }
}

private fun example(mode: ReadingMode): String = when (mode) {
    ReadingMode.FULL -> "WhatsApp. Marie : On se retrouve à 19 h ?"
    else -> NotificationText.appNameOnly("WhatsApp")
}

private fun appsSummary(settings: Settings): String {
    val count = settings.overriddenPackages().size
    return when (count) {
        0 -> "Toutes les applis utilisent le réglage par défaut"
        1 -> "1 appli a un réglage spécifique"
        else -> "$count applis ont un réglage spécifique"
    }
}

private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun pickTime(context: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
}

private fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun isBatteryRestricted(context: Context): Boolean =
    !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // Appli installée hors Play Store, la lecture écran éteint en dépend.
private fun requestBatteryExemption(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun openNotificationAccessSettings(context: Context) {
    context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

private fun openTtsSettings(context: Context) {
    try {
        context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}
