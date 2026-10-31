package com.saraltechies.saralmoneytracker.ui.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.ImportOptions
import com.saraltechies.saralmoneytracker.data.ImportReport
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.ChoiceDialog
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.PickerRow
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.xlsx.ImportResult
import com.saraltechies.saralmoneytracker.xlsx.NotAnXlsxException
import com.saraltechies.saralmoneytracker.xlsx.SpreadsheetImport
import com.saraltechies.saralmoneytracker.xlsx.XlsxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ImportState {
    data object Idle : ImportState
    data object Working : ImportState
    class Preview(val result: ImportResult) : ImportState
    class Done(val report: ImportReport) : ImportState
    class Failed(val message: Int) : ImportState
}

/**
 * Brings in an Excel workbook: a Saral Money Tracker backup, or a hand-made tracker like a monthly "Money Out /
 * Money In" sheet. Shows what was found and lets people decide how card bill payments and budgets are handled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(repository: MoneyRepository, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    val accounts by remember { repository.watch { repository.accounts() } }.collectAsStateWithLifecycle(emptyList())
    var defaultAccount by remember { mutableStateOf<Long?>(null) }
    var cardAsTransfers by remember { mutableStateOf(true) }
    var useBudgets by remember { mutableStateOf(true) }
    var pickAccount by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        state = ImportState.Working
        scope.launch {
            state = withContext(Dispatchers.IO) {
                try {
                    val sheets = context.contentResolver.openInputStream(uri)!!.use { XlsxReader.read(it.buffered()) }
                    val result = SpreadsheetImport.read(sheets)
                    if (result.isEmpty) ImportState.Failed(R.string.import_nothing_found) else ImportState.Preview(result)
                } catch (_: NotAnXlsxException) {
                    ImportState.Failed(R.string.import_not_xlsx)
                } catch (_: Exception) {
                    ImportState.Failed(R.string.import_read_failed)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_excel)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                ImportState.Idle -> {
                    Intro()
                    Button(onClick = { picker.launch(MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_file)) }
                }
                ImportState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.import_reading))
                }
                is ImportState.Failed -> {
                    Message(Icons.Filled.ErrorOutline, stringResource(s.message), MaterialTheme.colorScheme.error)
                    Button(onClick = { picker.launch(MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_other)) }
                }
                is ImportState.Preview -> {
                    val r = s.result
                    val cardRows = MoneyRepository.cardBillCategories(r).let { names ->
                        r.rows.count { it.type == TxnType.EXPENSE && it.category.trim().lowercase() in names }
                    }
                    val budgets = r.categories.count { it.budget != null }
                    val account = accounts.firstOrNull { it.id == defaultAccount } ?: accounts.firstOrNull()
                    Summary(r)
                    if (!r.isBackup && account != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            PickerRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.import_account), account.name, onClick = { pickAccount = true })
                            Text(
                                stringResource(R.string.import_account_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                            )
                        }
                    }
                    if (cardRows > 0) {
                        Option(
                            title = stringResource(R.string.import_card_title),
                            text = pluralStringResource(R.plurals.import_card_text, cardRows, cardRows),
                            checked = cardAsTransfers,
                            onChange = { cardAsTransfers = it },
                        )
                    }
                    if (budgets > 0 && !r.isBackup) {
                        Option(
                            title = stringResource(R.string.import_budgets_title),
                            text = pluralStringResource(R.plurals.import_budgets_text, budgets, budgets),
                            checked = useBudgets,
                            onChange = { useBudgets = it },
                        )
                    }
                    Button(
                        enabled = account != null,
                        onClick = {
                            val options = ImportOptions(
                                defaultAccountId = account!!.id,
                                cardBillsAsTransfers = cardAsTransfers,
                                useBudgets = useBudgets,
                                cardAccountName = context.getString(R.string.default_account_card),
                                otherExpenseName = context.getString(R.string.default_other_expense),
                                otherIncomeName = context.getString(R.string.default_other_income),
                            )
                            state = ImportState.Working
                            scope.launch {
                                state = runCatching { ImportState.Done(repository.applyImport(r, options)) }
                                    .getOrElse { ImportState.Failed(R.string.import_failed) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(pluralStringResource(R.plurals.import_button, r.rows.size, r.rows.size)) }
                    OutlinedButton(onClick = { state = ImportState.Idle }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                }
                is ImportState.Done -> {
                    val rep = s.report
                    Message(Icons.Filled.CheckCircle, pluralStringResource(R.plurals.import_done, rep.added, rep.added), MaterialTheme.colorScheme.primary)
                    val lines = buildList {
                        if (rep.duplicates > 0) add(pluralStringResource(R.plurals.import_done_duplicates, rep.duplicates, rep.duplicates))
                        if (rep.transfers > 0) add(pluralStringResource(R.plurals.import_done_transfers, rep.transfers, rep.transfers))
                        if (rep.categoriesAdded > 0) add(pluralStringResource(R.plurals.import_done_categories, rep.categoriesAdded, rep.categoriesAdded))
                        if (rep.accountsAdded > 0) add(pluralStringResource(R.plurals.import_done_accounts, rep.accountsAdded, rep.accountsAdded))
                        if (rep.budgetsSet > 0) add(pluralStringResource(R.plurals.import_done_budgets, rep.budgetsSet, rep.budgetsSet))
                        if (rep.recurringAdded > 0) add(pluralStringResource(R.plurals.import_done_recurring, rep.recurringAdded, rep.recurringAdded))
                    }
                    lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_see_entries)) }
                }
            }
        }
    }

    if (pickAccount) {
        ChoiceDialog(
            title = stringResource(R.string.import_account),
            options = accounts,
            selected = accounts.firstOrNull { it.id == defaultAccount } ?: accounts.firstOrNull(),
            label = { it.name },
            leading = { IconBadge(CategoryIcons.account(it.type), MaterialTheme.colorScheme.primary, size = 32.dp) },
            onPick = { defaultAccount = it.id },
            onDismiss = { pickAccount = false },
        )
    }
}

// Some file pickers label .xlsx files as a generic binary or zip, so allow those too.
private val MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream",
    "application/zip",
)

@Composable
private fun Intro() {
    Message(Icons.Filled.TableChart, stringResource(R.string.import_intro_title), MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.import_intro_text), style = MaterialTheme.typography.bodyMedium)
    listOf(R.string.import_intro_point1, R.string.import_intro_point2, R.string.import_intro_point3, R.string.import_intro_point4).forEach {
        Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
    }
    Text(stringResource(R.string.import_intro_safe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Summary(r: ImportResult) {
    val expense = r.rows.filter { it.type == TxnType.EXPENSE }
    val income = r.rows.filter { it.type == TxnType.INCOME }
    val transfers = r.rows.count { it.type == TxnType.TRANSFER }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (r.isBackup) R.string.import_found_backup else R.string.import_found),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(pluralStringResource(R.plurals.import_found_expenses, expense.size, expense.size, money(expense.sumOf { it.amount })))
            Text(pluralStringResource(R.plurals.import_found_income, income.size, income.size, money(income.sumOf { it.amount })))
            if (transfers > 0) Text(pluralStringResource(R.plurals.import_found_transfers, transfers, transfers))
            if (r.rows.isNotEmpty()) {
                Text(stringResource(R.string.import_found_range, Dates.medium(r.rows.minOf { it.date }), Dates.medium(r.rows.maxOf { it.date })))
            }
            if (r.duplicatesInFile > 0) Text(pluralStringResource(R.plurals.import_found_duplicates, r.duplicatesInFile, r.duplicatesInFile))
            if (r.skipped > 0) Text(pluralStringResource(R.plurals.import_found_skipped, r.skipped, r.skipped))
            if (r.sheetsRead.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.import_found_sheets, r.sheetsRead.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Option(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
