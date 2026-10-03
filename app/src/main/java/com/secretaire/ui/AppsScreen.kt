package com.secretaire.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.secretaire.AppSetting
import com.secretaire.ReadingMode
import com.secretaire.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

private data class AppEntry(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = rememberSettingsVersion(settings)
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadApps(context, settings) }
    }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages par appli") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher une appli") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val filtered = remember(list, query) {
                    list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        // version force la relecture du réglage après un changement.
                        val setting = remember(version, app.packageName) { settings.appSetting(app.packageName) }
                        AppRow(
                            app = app,
                            setting = setting,
                            defaultMode = settings.defaultMode,
                            onChange = { settings.setAppSetting(app.packageName, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    setting: AppSetting,
    defaultMode: ReadingMode,
    onChange: (AppSetting) -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) { loadIcon(context, app.packageName) }
    }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp)) {
                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                val (text, overridden) = when (setting) {
                    AppSetting.UseDefault -> "Par défaut (${defaultMode.label.lowercase()})" to false
                    is AppSetting.Override -> setting.mode.label to true
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (overridden) FontWeight.Bold else FontWeight.Normal,
                    color = if (overridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Par défaut (${defaultMode.label.lowercase()})") },
                onClick = { onChange(AppSetting.UseDefault); menuOpen = false },
            )
            ReadingMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = { onChange(AppSetting.Override(mode)); menuOpen = false },
                )
            }
        }
    }
}

/** Applis visibles dans le lanceur, plus celles qui ont déjà un réglage. */
private fun loadApps(context: Context, settings: Settings): List<AppEntry> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val packages = pm.queryIntentActivities(launcherIntent, 0).map { it.activityInfo.packageName }.toMutableSet()
    packages += settings.overriddenPackages()
    packages -= context.packageName

    val collator = Collator.getInstance(Locale.getDefault())
    return packages.map { pkg ->
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
        AppEntry(pkg, label)
    }.sortedWith { a, b -> collator.compare(a.label, b.label) }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
} catch (_: PackageManager.NameNotFoundException) {
    null
}
