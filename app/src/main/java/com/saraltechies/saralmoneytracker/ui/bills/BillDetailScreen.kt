package com.saraltechies.saralmoneytracker.ui.bills

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.BillView
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.BillMath
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.ChoiceDialog
import com.saraltechies.saralmoneytracker.ui.components.ConfirmDialog
import com.saraltechies.saralmoneytracker.ui.components.DatePickDialog
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.ui.components.PickerRow
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import com.saraltechies.saralmoneytracker.util.BillPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** One bill: what was sold, what's due, payments, and sending it as a PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    repository: MoneyRepository,
    settings: SettingsRepository,
    billId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onMyDetails: () -> Unit,
) {
    val context = LocalContext.current
    val currency = LocalCurrency.current
    val scope = rememberCoroutineScope()
    // Null until read; a bill deleted elsewhere (with its customer) comes back as gone.
    val state by remember { repository.watch { listOf(repository.billView(billId)) } }.collectAsStateWithLifecycle(null)
    val view = state?.firstOrNull()
    val me by remember { repository.watch { repository.billView(billId)?.let { repository.myDetails(it.bill.bookId) } } }.collectAsStateWithLifecycle(null)
    val accounts by remember { repository.watch { repository.accounts(book = Books.ALL) } }.collectAsStateWithLifecycle(emptyList())
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var paying by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state) { if (state != null && view == null) onBack() }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val v = view
        if (uri == null || v == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { BillPdf(context, currency).write(v, me, it) } != null }.getOrDefault(false)
            }
            Toast.makeText(context, if (ok) R.string.bill_saved else R.string.statement_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun share() {
        val v = view ?: return
        scope.launch {
            val intent = withContext(Dispatchers.IO) { runCatching { BillPdf.shareIntent(context, v, me, currency) }.getOrNull() }
            if (intent == null) Toast.makeText(context, R.string.statement_failed, Toast.LENGTH_SHORT).show()
            else runCatching { context.startActivity(intent) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(view?.let { stringResource(R.string.bill_title, it.bill.number) }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.statement_save_pdf)) },
                            onClick = { menu = false; view?.let { save.launch(BillPdf.fileName(context, it)) } },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        val v = view ?: return@Scaffold
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { SummaryCard(v, onOpenCustomer) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (v.payment.due > 0) {
                        Button(onClick = { paying = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.ArrowDownward, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.bill_record_payment), maxLines = 1)
                        }
                    }
                    OutlinedButton(onClick = ::share, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.bill_share), maxLines = 1)
                    }
                }
            }
            if (me?.name.isNullOrBlank()) {
                item {
                    Card(
                        onClick = onMyDetails,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.bill_add_shop_details), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.bill_items)) }
            items(v.bill.items.withIndex().toList(), key = { "i:" + it.index }) { (_, line) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, style = MaterialTheme.typography.bodyLarge)
                        val qty = BillMath.quantityText(line.quantity) + if (line.unit.isBlank()) "" else " " + line.unit
                        Text(
                            qty + " × " + money(line.rate) + if (line.gstRate > 0) " · " + stringResource(R.string.bill_gst) + " " + BillMath.rateText(line.gstRate) + "%" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(money(line.amount), fontWeight = FontWeight.SemiBold)
                }
            }
            if (v.payments.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.bill_payments)) }
                items(v.payments, key = { "p:" + it.id }) { t ->
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(money(t.amount), style = MaterialTheme.typography.bodyLarge, color = AppTheme.money.income, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(Dates.short(t.date), accounts.firstOrNull { it.id == t.toAccountId }?.name).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                repository.deleteTxn(t.id)
                                val result = snackbar.showSnackbar(context.getString(R.string.entry_deleted), actionLabel = context.getString(R.string.undo), duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) repository.restoreTxn(t)
                            }
                        }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
            if (v.payment.paid > v.payments.sumOf { it.amount }) {
                item {
                    Text(
                        stringResource(R.string.bill_paid_in_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            if (v.bill.note.isNotBlank()) {
                item { Text(v.bill.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            }
        }
    }

    val current = view
    if (paying && current != null) {
        PaymentDialog(
            current, accounts.filter { !it.archived },
            onDismiss = { paying = false },
            onSave = { amount, date, account ->
                scope.launch {
                    repository.addBillPayment(current.bill, amount, date, account, "")
                    settings.recordGoodMoment()
                }
            },
        )
    }
    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = stringResource(R.string.bill_delete_title),
            text = stringResource(R.string.bill_delete_text, current.bill.number),
            confirm = stringResource(R.string.delete),
            onConfirm = { scope.launch { repository.deleteBill(current.bill.id) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SummaryCard(v: BillView, onOpenCustomer: (Long) -> Unit) {
    val t = v.totals
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(if (v.payment.isPaid) R.string.bill_status_paid else R.string.bill_due), style = MaterialTheme.typography.labelLarge)
            Text(
                money(if (v.payment.isPaid) t.total else v.payment.due), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (v.payment.isPaid) AppTheme.money.income else AppTheme.money.expense,
            )
            val party = v.bill.partyId
            Text(
                v.customerName.ifBlank { stringResource(R.string.bill_walk_in) } + " · " + Dates.medium(v.bill.date),
                style = MaterialTheme.typography.bodyMedium,
                color = if (party != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (party != null) Modifier.clickable { onOpenCustomer(party) }.padding(vertical = 4.dp) else Modifier.padding(vertical = 4.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Line(stringResource(R.string.bill_subtotal), money(t.subtotal))
            if (t.discount > 0) Line(stringResource(R.string.bill_discount), "-" + money(t.discount))
            for (tax in t.taxes.filter { it.rate > 0 }) {
                val rate = BillMath.rateText(tax.rate)
                if (v.bill.interState) {
                    Line(stringResource(R.string.bill_igst, rate), money(tax.tax))
                } else {
                    val half = BillMath.rateText(tax.rate / 2)
                    Line(stringResource(R.string.bill_cgst, half), money(tax.cgst))
                    Line(stringResource(R.string.bill_sgst, half), money(tax.sgst))
                }
            }
            if (t.hasGst && v.bill.pricesIncludeGst) Line(stringResource(R.string.bill_rates_included_note), "")
            Line(stringResource(R.string.bill_total), money(t.total), bold = true)
            if (!v.payment.isPaid) Line(stringResource(R.string.bill_paid), money(v.payment.paid))
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null)
    }
}

@Composable
private fun PaymentDialog(view: BillView, accounts: List<Account>, onDismiss: () -> Unit, onSave: (Long, LocalDate, Long) -> Unit) {
    var amountText by rememberSaveable { mutableStateOf(Money.plain(view.payment.due)) }
    var dateDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var accountId by rememberSaveable { mutableStateOf((accounts.firstOrNull { it.bookId == view.bill.bookId } ?: accounts.firstOrNull())?.id ?: 0L) }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val date = LocalDate.ofEpochDay(dateDay)
    val amount = Money.parse(amountText)
    val account = accounts.firstOrNull { it.id == accountId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_record_payment)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15); error = null },
                    label = { Text(stringResource(R.string.amount)) },
                    prefix = { Text(LocalCurrency.current.symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerRow(Icons.Filled.CalendarToday, stringResource(R.string.date), Dates.medium(date), onClick = { pickDate = true }, modifier = Modifier.padding(top = 4.dp))
                PickerRow(
                    CategoryIcons.account(account?.type ?: com.saraltechies.saralmoneytracker.model.AccountType.CASH),
                    stringResource(R.string.received_in), account?.name.orEmpty(), onClick = { pickAccount = true },
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    amount == null || amount <= 0 -> R.string.error_amount
                    account == null -> R.string.error_account
                    else -> null
                }
                if (error != null || amount == null) return@TextButton
                onSave(amount, date, accountId)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickDate) DatePickDialog(date, onDismiss = { pickDate = false }, onPick = { dateDay = it.toEpochDay() })
    if (pickAccount) {
        ChoiceDialog(
            title = stringResource(R.string.received_in),
            options = accounts,
            selected = account,
            label = { it.name },
            leading = { IconBadge(CategoryIcons.account(it.type), MaterialTheme.colorScheme.primary, size = 32.dp) },
            onPick = { accountId = it.id },
            onDismiss = { pickAccount = false },
        )
    }
}
