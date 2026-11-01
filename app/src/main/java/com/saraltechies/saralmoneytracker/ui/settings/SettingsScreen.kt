package com.saraltechies.saralmoneytracker.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.BuildConfig
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.AppPrefs
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.data.ThemeMode
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.ui.components.ChoiceDialog
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.currencyName
import com.saraltechies.saralmoneytracker.util.AppLock
import com.saraltechies.saralmoneytracker.util.PlayStore
import com.saraltechies.saralmoneytracker.util.Reminders
import com.saraltechies.saralmoneytracker.util.Alerts
import com.saraltechies.saralmoneytracker.xlsx.BackupFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private enum class Picker { CURRENCY, THEME, WEEK, MONTH, TIME, DELETE, BACKUP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: MoneyRepository,
    settings: SettingsRepository,
    prefs: AppPrefs,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onCategories: () -> Unit,
    onMyDetails: () -> Unit,
    onLoans: () -> Unit,
    onBooks: () -> Unit,
    onLedger: () -> Unit,
    openBackup: Boolean = false,
    onWhatsNew: () -> Unit = {},
    onRecurring: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(if (openBackup) Picker.BACKUP else null) }
    val lockAvailable = remember { AppLock.available(context) }

    fun notify(message: Int) {
        scope.launch { snackbar.showSnackbar(context.getString(message)) }
    }

    fun setReminder(on: Boolean, minute: Int = prefs.reminderMinute) {
        scope.launch {
            settings.setReminder(on, minute)
            Reminders.schedule(context, on, minute)
        }
    }

    // For alerts and the monthly summary: they stay on; without permission Android just won't show them.
    val alertsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) notify(R.string.reminder_permission_denied)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setReminder(true) else notify(R.string.reminder_permission_denied)
    }

    val exportXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use {
                        BackupFormat.writeXlsx(repository.exportData(), it, LocalDate.now(), BuildConfig.VERSION_NAME)
                    }
                }.isSuccess
            }
            notify(if (ok) R.string.export_done else R.string.export_failed)
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { BackupFormat.writeCsv(repository.exportData(), it) }
                }.isSuccess
            }
            notify(if (ok) R.string.export_done else R.string.export_failed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { SectionTitle(stringResource(R.string.settings_general)) }
            item {
                Setting(Icons.Filled.CurrencyExchange, stringResource(R.string.currency), "${prefs.currency.symbol.trim()} · ${currencyName(prefs.currency)}") { picker = Picker.CURRENCY }
            }
            item {
                Setting(Icons.Filled.CalendarMonth, stringResource(R.string.month_starts_on), stringResource(R.string.month_starts_on_value, prefs.period.monthStartDay)) { picker = Picker.MONTH }
            }
            item {
                Setting(Icons.Filled.DateRange, stringResource(R.string.week_starts_on), prefs.period.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault())) { picker = Picker.WEEK }
            }
            item { Setting(Icons.Filled.DarkMode, stringResource(R.string.theme), stringResource(themeLabel(prefs.theme))) { picker = Picker.THEME } }

            item { SectionTitle(stringResource(R.string.settings_organise)) }
            item { Setting(Icons.Filled.Category, stringResource(R.string.categories), stringResource(R.string.categories_summary), onClick = onCategories) }
            item { Setting(Icons.Filled.Store, stringResource(R.string.books_title), stringResource(R.string.books_summary), onClick = onBooks) }
            item { Setting(Icons.Filled.Storefront, stringResource(R.string.ledger_title), stringResource(R.string.ledger_row_empty), onClick = onLedger) }
            item { Setting(Icons.Filled.Handshake, stringResource(R.string.loans_title), stringResource(R.string.loans_row_empty), onClick = onLoans) }
            item { Setting(Icons.Filled.Badge, stringResource(R.string.my_details), stringResource(R.string.my_details_summary), onClick = onMyDetails) }
            item { Setting(Icons.Filled.Repeat, stringResource(R.string.repeating_entries), stringResource(R.string.repeating_summary), onClick = onRecurring) }

            item { SectionTitle(stringResource(R.string.settings_reminder)) }
            item {
                SwitchSetting(Icons.Filled.Notifications, stringResource(R.string.daily_reminder), stringResource(R.string.daily_reminder_summary), prefs.reminderOn) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else setReminder(on)
                }
            }
            if (prefs.reminderOn) {
                item {
                    val time = LocalTime.of(prefs.reminderMinute / 60, prefs.reminderMinute % 60)
                    Setting(Icons.Filled.Alarm, stringResource(R.string.reminder_time), time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) { picker = Picker.TIME }
                }
            }
            item {
                SwitchSetting(Icons.Filled.NotificationsActive, stringResource(R.string.alerts_setting), stringResource(R.string.alerts_setting_summary), prefs.alertsOn) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) alertsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    scope.launch {
                        settings.setAlerts(on)
                        Alerts.schedule(context, on || prefs.monthlySummaryOn)
                    }
                }
            }
            item {
                SwitchSetting(Icons.Filled.CalendarMonth, stringResource(R.string.summary_setting), stringResource(R.string.summary_setting_summary), prefs.monthlySummaryOn) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) alertsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    scope.launch {
                        settings.setMonthlySummary(on)
                        Alerts.schedule(context, on || prefs.alertsOn)
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_privacy)) }
            item {
                SwitchSetting(
                    Icons.Filled.Fingerprint, stringResource(R.string.app_lock),
                    stringResource(if (lockAvailable) R.string.app_lock_summary else R.string.app_lock_unavailable),
                    prefs.appLock, enabled = lockAvailable || prefs.appLock,
                ) { on -> scope.launch { settings.setAppLock(on) } }
            }
            item {
                SwitchSetting(Icons.Filled.VisibilityOff, stringResource(R.string.hide_in_recents), stringResource(R.string.hide_in_recents_summary), prefs.hideInRecents) { on ->
                    scope.launch { settings.setHideInRecents(on) }
                }
            }
            item { Setting(Icons.Filled.Security, stringResource(R.string.privacy_promise), stringResource(R.string.privacy_promise_text), onClick = null) }

            item { SectionTitle(stringResource(R.string.settings_data)) }
            item { Setting(Icons.Filled.Backup, stringResource(R.string.backup_title), backupStatus(prefs)) { picker = Picker.BACKUP } }
            item { Setting(Icons.Filled.Upload, stringResource(R.string.import_excel), stringResource(R.string.import_excel_summary), onClick = onImport) }
            item {
                Setting(Icons.Filled.TableChart, stringResource(R.string.export_excel), stringResource(R.string.export_excel_summary)) {
                    exportXlsx.launch("Saral Money Tracker ${LocalDate.now()}.xlsx")
                }
            }
            item {
                Setting(Icons.Filled.Download, stringResource(R.string.export_csv), stringResource(R.string.export_csv_summary)) {
                    exportCsv.launch("Saral Money Tracker ${LocalDate.now()}.csv")
                }
            }
            item { Setting(Icons.Filled.DeleteForever, stringResource(R.string.delete_all), stringResource(R.string.delete_all_summary)) { picker = Picker.DELETE } }

            item { SectionTitle(stringResource(R.string.settings_about)) }
            item { Setting(Icons.Filled.NewReleases, stringResource(R.string.whatsnew_title), stringResource(R.string.whatsnew_settings_summary), onClick = onWhatsNew) }
            item { Setting(Icons.Filled.Star, stringResource(R.string.rate_app), stringResource(R.string.rate_app_summary)) { PlayStore.openListing(context, PlayStore.ownPackage(context)) } }
            item {
                Setting(Icons.Filled.Share, stringResource(R.string.share_app), stringResource(R.string.share_app_summary)) {
                    val text = context.getString(R.string.share_text, "https://play.google.com/store/apps/details?id=" + PlayStore.ownPackage(context))
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }
            }
            item { Setting(Icons.Filled.Policy, stringResource(R.string.privacy_policy), null) { PlayStore.openUrl(context, PlayStore.PRIVACY_POLICY) } }
            item { SectionTitle(stringResource(R.string.more_apps)) }
            item { Setting(Icons.Filled.Folder, stringResource(R.string.app_file_manager), stringResource(R.string.app_file_manager_summary)) { PlayStore.openListing(context, PlayStore.SARAL_FILE_MANAGER) } }
            item { Setting(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.app_reader), stringResource(R.string.app_reader_summary)) { PlayStore.openListing(context, PlayStore.SARALREADER) } }
            item { Setting(Icons.Filled.QrCodeScanner, stringResource(R.string.app_qrscan), stringResource(R.string.app_qrscan_summary)) { PlayStore.openListing(context, PlayStore.SARALQRSCAN) } }
            item { Setting(Icons.Filled.Lock, stringResource(R.string.app_authenticator), stringResource(R.string.app_authenticator_summary)) { PlayStore.openListing(context, PlayStore.SARAL_AUTHENTICATOR) } }
            item { Setting(Icons.Filled.Language, stringResource(R.string.website), "saraltechies.com") { PlayStore.openUrl(context, PlayStore.WEBSITE) } }
            item {
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }

    when (picker) {
        Picker.CURRENCY -> ChoiceDialog(
            title = stringResource(R.string.currency),
            options = Currencies.all,
            selected = prefs.currency,
            label = { "${it.code} · ${currencyName(it)}" },
            onPick = { scope.launch { settings.setCurrency(it.code) } },
            onDismiss = { picker = null },
        )
        Picker.THEME -> ChoiceDialog(
            title = stringResource(R.string.theme),
            options = ThemeMode.entries,
            selected = prefs.theme,
            label = { stringResource(themeLabel(it)) },
            onPick = { scope.launch { settings.setTheme(it) } },
            onDismiss = { picker = null },
        )
        Picker.WEEK -> ChoiceDialog(
            title = stringResource(R.string.week_starts_on),
            options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY),
            selected = prefs.period.weekStart,
            label = { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
            onPick = { scope.launch { settings.setWeekStart(it) } },
            onDismiss = { picker = null },
        )
        Picker.MONTH -> MonthStartDialog(prefs.period.monthStartDay, onPick = { scope.launch { settings.setMonthStartDay(it) } }, onDismiss = { picker = null })
        Picker.TIME -> {
            val state = rememberTimePickerState(prefs.reminderMinute / 60, prefs.reminderMinute % 60)
            AlertDialog(
                onDismissRequest = { picker = null },
                title = { Text(stringResource(R.string.reminder_time)) },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = { setReminder(true, state.hour * 60 + state.minute); picker = null }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        Picker.BACKUP -> BackupDialog(settings, prefs, onDismiss = { picker = null })
        Picker.DELETE -> DeleteAllDialog(
            onConfirm = {
                scope.launch {
                    repository.deleteAll()
                    notify(R.string.delete_all_done)
                }
            },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun Setting(icon: ImageVector, title: String, summary: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchSetting(icon: ImageVector, title: String, summary: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun MonthStartDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.month_starts_on),
        options = (1..28).toList(),
        selected = current,
        label = { day -> if (day == 1) stringResource(R.string.month_starts_calendar) else stringResource(R.string.month_starts_on_value, day) },
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

/** Asks for the word DELETE, since this can't be undone (unless an export was made). */
@Composable
private fun DeleteAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val word = stringResource(R.string.delete_all_word)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_all_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_all_text, word))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = typed.trim().equals(word, ignoreCase = true), onClick = { onConfirm(); onDismiss() }) { Text(stringResource(R.string.delete_all)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
