package com.saraltechies.saralmoneytracker.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.BackupFrequency
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.util.AutoBackup
import kotlinx.coroutines.launch

private enum class Use { PERSONAL, BUSINESS, BOTH }

/**
 * First start: what will you track (personal, a business, or both), the business's name, and an optional
 * automatic backup folder. Everything can be changed later in Settings.
 */
@Composable
fun SetupScreen(repository: MoneyRepository, settings: SettingsRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var use by rememberSaveable { mutableStateOf(Use.PERSONAL) }
    var business by rememberSaveable { mutableStateOf("") }
    var backupFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val needsName = use != Use.PERSONAL

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        backupFolder = uri.toString()
    }

    fun finish() {
        busy = true
        scope.launch {
            if (needsName) {
                val id = repository.createBook(business.trim(), BookKind.BUSINESS)
                // Business only: open on the business book. Both: show everything together.
                settings.setBook(if (use == Use.BUSINESS) id else Books.ALL)
            }
            backupFolder?.let {
                settings.setBackup(it, BackupFrequency.WEEKLY)
                AutoBackup.schedule(context, true)
            }
            settings.setSetupDone()
            onDone()
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.setup_question), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Choice(Icons.Filled.Person, R.string.setup_personal, R.string.setup_personal_hint, use == Use.PERSONAL) { use = Use.PERSONAL }
                Choice(Icons.Filled.Store, R.string.setup_business, R.string.setup_business_hint, use == Use.BUSINESS) { use = Use.BUSINESS }
                Choice(Icons.Filled.Storefront, R.string.setup_both, R.string.setup_both_hint, use == Use.BOTH) { use = Use.BOTH }
                if (needsName) {
                    OutlinedTextField(
                        value = business, onValueChange = { business = it.take(40) },
                        label = { Text(stringResource(R.string.books_business_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.setup_backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (backupFolder == null) {
                    OutlinedButton(onClick = { runCatching { pick.launch(null) } }) {
                        Icon(Icons.Filled.Backup, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_backup_pick))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_backup_on), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { backupFolder = null }) { Text(stringResource(R.string.setup_backup_skip)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Button(
                onClick = ::finish,
                enabled = !busy && (!needsName || business.isNotBlank()),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).height(52.dp),
            ) { Text(stringResource(R.string.setup_start), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, title: Int, hint: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
