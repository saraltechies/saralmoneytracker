package com.saraltechies.saralmoneytracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.AppPrefs
import com.saraltechies.saralmoneytracker.data.BackupFrequency
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.ui.components.PickerRow
import com.saraltechies.saralmoneytracker.util.AutoBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Turn automatic backup on or off, pick the folder and how often, or back up now. */
@Composable
fun BackupDialog(settings: SettingsRepository, prefs: AppPrefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folderName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.backupFolder) {
        folderName = withContext(Dispatchers.IO) { AutoBackup.folderName(context, prefs.backupFolder) }
    }

    fun apply(folder: String?, frequency: BackupFrequency) {
        scope.launch {
            settings.setBackup(folder, frequency)
            AutoBackup.schedule(context, frequency != BackupFrequency.OFF && folder != null)
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // Keep access to the folder after a restart; give up access to the old one.
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs.backupFolder?.takeIf { it != uri.toString() }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        apply(uri.toString(), if (prefs.backupFrequency == BackupFrequency.OFF) BackupFrequency.WEEKLY else prefs.backupFrequency)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_intro, AutoBackup.KEEP), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerRow(
                    Icons.Filled.Folder, stringResource(R.string.backup_folder),
                    folderName ?: stringResource(if (prefs.backupFolder == null) R.string.backup_choose_folder else R.string.backup_folder_missing),
                    onClick = { runCatching { pick.launch(null) } },
                )
                Text(stringResource(R.string.backup_how_often), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(BackupFrequency.OFF to R.string.backup_off, BackupFrequency.DAILY to R.string.backup_daily, BackupFrequency.WEEKLY to R.string.backup_weekly).forEach { (f, label) ->
                        FilterChip(
                            selected = prefs.backupFrequency == f,
                            onClick = { if (f != BackupFrequency.OFF && prefs.backupFolder == null) runCatching { pick.launch(null) } else apply(prefs.backupFolder, f) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Text(
                    backupStatus(prefs), style = MaterialTheme.typography.bodySmall,
                    color = if (prefs.lastBackupError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.backup_restore_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = prefs.backupFolder != null && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { AutoBackup.run(context, force = true) }
                        busy = false
                        Toast.makeText(
                            context, if (result is AutoBackup.Result.Done) R.string.backup_done else R.string.backup_failed, Toast.LENGTH_LONG,
                        ).show()
                    }
                },
            ) { Text(stringResource(R.string.backup_now)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        modifier = Modifier.padding(0.dp),
    )
}

/** "Weekly · last backup 1 Nov 2026, 2:31 am", "Off", or why the last one failed. */
@Composable
fun backupStatus(prefs: AppPrefs): String {
    val last = prefs.lastBackupAt.takeIf { it > 0 }?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }
    return when {
        prefs.backupFrequency == BackupFrequency.OFF || prefs.backupFolder == null -> stringResource(R.string.backup_status_off)
        prefs.lastBackupError != null -> stringResource(R.string.backup_status_failed)
        last != null -> stringResource(
            R.string.backup_status_last, stringResource(if (prefs.backupFrequency == BackupFrequency.DAILY) R.string.backup_daily else R.string.backup_weekly), last,
        )
        else -> stringResource(R.string.backup_status_pending)
    }
}
