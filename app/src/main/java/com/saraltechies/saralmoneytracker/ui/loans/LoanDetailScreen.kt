package com.saraltechies.saralmoneytracker.ui.loans

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEvent
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanMath
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.TxnType
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
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the entry dialog is doing: a payment back, more money given, or changing one history entry. */
private sealed interface EntryMode {
    data object Payment : EntryMode
    data object More : EntryMode
    data class Edit(val event: LoanEvent) : EntryMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    repository: MoneyRepository,
    settings: SettingsRepository,
    loanId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMyDetails: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val item by remember { repository.watch { repository.loanItem(loanId) } }.collectAsStateWithLifecycle(null)
    val accounts by remember { repository.watch { repository.accounts(includeArchived = true, includeLoans = false, book = Books.ALL) } }.collectAsStateWithLifecycle(emptyList())
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<EntryMode?>(null) }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    var statement by rememberSaveable { mutableStateOf(false) }
    // Your details for the loan's book: a business loan's statement shows the business.
    val me by remember { repository.watch { repository.loanItem(loanId)?.let { repository.myDetails(it.loan.bookId) } } }.collectAsStateWithLifecycle(null)
    val currency = LocalCurrency.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { statement = true }) { Icon(Icons.Filled.PictureAsPdf, stringResource(R.string.loan_statement)) }
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val closed = item?.loan?.closed == true
                        DropdownMenuItem(
                            text = { Text(stringResource(if (closed) R.string.loan_reopen else R.string.loan_close)) },
                            onClick = { menu = false; scope.launch { repository.setLoanClosed(loanId, !closed) } },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        val current = item ?: return@Scaffold
        val loan = current.loan
        val s = current.summary
        val lent = loan.direction == LoanDirection.LENT
        val color = loanColor(loan.direction)
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(if (lent) R.string.loan_still_to_receive else R.string.loan_still_to_pay), style = MaterialTheme.typography.labelLarge)
                        Text(money(s.outstanding), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                        Text(loanStatus(current), style = MaterialTheme.typography.bodySmall, color = if (s.overdue > 0) AppTheme.money.expense else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { s.progress }, color = color, trackColor = color.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(12.dp))
                        Figure(stringResource(if (lent) R.string.loan_given else R.string.loan_taken), money(s.given))
                        Figure(stringResource(R.string.loan_paid_back), money(s.repaid))
                        Figure(stringResource(R.string.loan_principal_left), money(s.principalLeft.coerceAtLeast(0)), bold = true)
                        if (loan.rate > 0 || s.interestPaid > 0) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Figure(stringResource(R.string.loan_interest_so_far), money(s.interestAccrued))
                            Figure(stringResource(if (lent) R.string.loan_interest_received else R.string.loan_interest_paid), money(s.interestPaid))
                            Figure(stringResource(R.string.loan_interest_due), money(s.interestDue), bold = true)
                            if (s.monthlyInterest > 0) Figure(stringResource(R.string.loan_interest_per_month), money(s.monthlyInterest))
                        }
                        if (loan.plan == LoanPlan.MONTHLY) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Figure(stringResource(R.string.loan_installment), money(loan.installment))
                            Figure(stringResource(R.string.loan_installments_due), pluralStringResource(R.plurals.loan_installments_of, loan.installments, s.installmentsDue, loan.installments))
                            if (s.overdue > 0) Figure(stringResource(R.string.loan_overdue_label), money(s.overdue), bold = true, color = AppTheme.money.expense)
                        }
                        s.nextDueDate?.takeIf { !loan.closed }?.let {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Figure(stringResource(R.string.loan_next_payment), money(s.nextDueAmount) + " · " + Dates.relative(it), bold = true)
                        }
                    }
                }
            }
            if (s.settled && !loan.closed && s.given > 0) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = AppTheme.money.income)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.loan_settled_text), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { scope.launch { repository.setLoanClosed(loanId, true) } }) { Text(stringResource(R.string.loan_close)) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { entry = EntryMode.Payment }, modifier = Modifier.weight(1f)) {
                        Icon(if (lent) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (lent) R.string.loan_record_received else R.string.loan_record_paid), maxLines = 1)
                    }
                    OutlinedButton(onClick = { entry = EntryMode.More }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (lent) R.string.loan_gave_more else R.string.loan_took_more), maxLines = 1)
                    }
                }
            }
            current.contact?.takeIf { it.hasDetails }?.let { c ->
                item { ContactCard(c) }
            }
            if (loan.note.isNotBlank()) {
                item {
                    Text(loan.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            if (loan.plan == LoanPlan.MONTHLY && loan.installments > 0) {
                item {
                    SectionTitle(stringResource(R.string.loan_plan_title)) {
                        TextButton(onClick = { showPlan = !showPlan }) {
                            Text(stringResource(if (showPlan) R.string.hide else R.string.show))
                            Icon(if (showPlan) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                    }
                }
                if (showPlan) {
                    val plan = LoanMath.schedule(s.given, LoanMath.monthlyRate(loan), loan.installments, loan.installment, loan.startDate)
                    // Payments cover the instalments in order.
                    val covered = plan.runningFold(0L) { sum, row -> sum + row.amount }.drop(1).map { it <= s.totalPaid }
                    items(plan, key = { "plan:${it.number}" }) { row ->
                        val done = covered[row.number - 1]
                        val late = !done && !row.date.isAfter(LocalDate.now())
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${row.number}.", Modifier.width(32.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(Dates.medium(row.date), style = MaterialTheme.typography.bodyMedium)
                                if (row.interest > 0) {
                                    Text(
                                        stringResource(R.string.loan_plan_split, money(row.principal), money(row.interest)),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                money(row.amount), fontWeight = FontWeight.SemiBold,
                                color = when {
                                    done -> AppTheme.money.income
                                    late -> AppTheme.money.expense
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (done) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.CheckCircle, stringResource(R.string.loan_paid), Modifier.size(16.dp), tint = AppTheme.money.income)
                            }
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.loan_history)) }
            val accountNames = accounts.associate { it.id to it.name }
            items(current.events.reversed(), key = { "e:${it.txnId ?: "opening"}:${it.kind}" }) { e ->
                val txn = e.txnId?.let { id -> current.txns.firstOrNull { it.id == id } }
                val accountId = txn?.let { otherSide(it, loan.accountId) }
                EventRow(e, lent, accountId?.let { accountNames[it] }, onClick = { entry = EntryMode.Edit(e) })
            }
        }
    }

    val current = item
    if (current != null) {
        if (statement) {
            val names = accounts.associate { it.id to it.name }
            StatementDialog(
                item = current,
                me = me,
                accountNames = current.txns.associate { t -> t.id to names[otherSide(t, current.loan.accountId)].orEmpty() }.filterValues { it.isNotEmpty() },
                onMyDetails = { statement = false; onMyDetails() },
                onDismiss = { statement = false },
            )
        }
        entry?.let { mode ->
            EntryDialog(
                item = current,
                mode = mode,
                accounts = accounts.filter { !it.archived },
                onDismiss = { entry = null },
                onSave = { kindAmounts, date, accountId, note ->
                    scope.launch {
                        when (mode) {
                            is EntryMode.Edit -> {
                                val (kind, amount) = kindAmounts.first()
                                val txn = mode.event.txnId?.let { id -> current.txns.firstOrNull { it.id == id } }
                                if (txn == null) repository.setLoanOpening(current.loan, amount)
                                else repository.updateLoanEntry(current.loan, txn, kind, amount, date, accountId, note)
                            }
                            else -> {
                                repository.addLoanEntries(current.loan, kindAmounts, date, accountId, note)
                                settings.recordGoodMoment()
                            }
                        }
                    }
                },
                onDelete = { event ->
                    scope.launch {
                        if (event.txnId == null) repository.setLoanOpening(current.loan, 0) else repository.deleteTxn(event.txnId)
                    }
                },
            )
        }
        if (confirmDelete) {
            ConfirmDialog(
                title = stringResource(R.string.loan_delete_title),
                text = stringResource(R.string.loan_delete_text, current.name),
                confirm = stringResource(R.string.delete),
                onConfirm = {
                    scope.launch {
                        repository.deleteLoan(current.loan)
                        onBack()
                    }
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

/** Of a loan entry, the account that's yours (not the loan's own account). */
private fun otherSide(t: com.saraltechies.saralmoneytracker.model.Txn, loanAccount: Long): Long =
    if (t.type == TxnType.TRANSFER && t.accountId == loanAccount) t.toAccountId ?: t.accountId else t.accountId

@Composable
private fun Figure(label: String, value: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun eventLabel(kind: LoanEventKind, lent: Boolean): String = stringResource(
    when (kind) {
        LoanEventKind.GIVEN -> if (lent) R.string.loan_event_given else R.string.loan_event_taken
        LoanEventKind.REPAID -> if (lent) R.string.loan_event_received else R.string.loan_event_repaid
        LoanEventKind.INTEREST -> if (lent) R.string.loan_event_interest_received else R.string.loan_event_interest_paid
    },
)

private fun eventIcon(kind: LoanEventKind, lent: Boolean): ImageVector = when (kind) {
    LoanEventKind.INTEREST -> Icons.Filled.Percent
    LoanEventKind.GIVEN -> if (lent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    LoanEventKind.REPAID -> if (lent) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
}

@Composable
private fun EventRow(e: LoanEvent, lent: Boolean, accountName: String?, onClick: () -> Unit) {
    // Money coming to you is green, money going out red, whichever way the loan goes.
    val incoming = (e.kind == LoanEventKind.GIVEN) != lent
    val color = if (incoming) AppTheme.money.income else AppTheme.money.expense
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(eventIcon(e.kind, lent), color, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(eventLabel(e.kind, lent), style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(
                Dates.short(e.date),
                accountName ?: if (e.txnId == null) stringResource(R.string.loan_before_app) else null,
                e.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(money(if (incoming) e.amount else -e.amount, signed = true), fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Record a payment (split into interest and principal), more money given, or change one history entry. */
@Composable
private fun EntryDialog(
    item: LoanItem,
    mode: EntryMode,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (List<Pair<LoanEventKind, Long>>, LocalDate, Long, String) -> Unit,
    onDelete: (LoanEvent) -> Unit,
) {
    val loan = item.loan
    val lent = loan.direction == LoanDirection.LENT
    val editing = (mode as? EntryMode.Edit)?.event
    val editingTxn = editing?.txnId?.let { id -> item.txns.firstOrNull { it.id == id } }
    val isOpening = editing != null && editing.txnId == null
    val isPayment = mode is EntryMode.Payment

    var amountText by rememberSaveable {
        mutableStateOf(
            when {
                editing != null -> Money.plain(editing.amount)
                isPayment -> item.summary.let { s -> (if (s.nextDueAmount > 0) s.nextDueAmount else s.outstanding).takeIf { it > 0 }?.let(Money::plain).orEmpty() }
                else -> ""
            },
        )
    }
    var interestText by rememberSaveable { mutableStateOf("") }
    var interestEdited by rememberSaveable { mutableStateOf(false) }
    var dateDay by rememberSaveable { mutableStateOf((editing?.date ?: LocalDate.now()).toEpochDay()) }
    var accountId by rememberSaveable {
        mutableStateOf(editingTxn?.let { otherSide(it, loan.accountId) } ?: item.txns.lastOrNull()?.let { otherSide(it, loan.accountId) } ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var note by rememberSaveable { mutableStateOf(editing?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val date = LocalDate.ofEpochDay(dateDay)
    if (accounts.none { it.id == accountId } && accounts.isNotEmpty() && !isOpening) accountId = accounts.first().id

    val amount = Money.parse(amountText)
    // Interest is paid first: the interest due on the payment's date, unless typed in by hand.
    val interestDueThen = remember(dateDay, item) { LoanMath.summary(loan, item.events, date).interestDue }
    val showInterest = isPayment && (loan.rate > 0 || interestDueThen > 0)
    val autoInterest = if (amount != null && amount > 0) LoanMath.split(amount, interestDueThen).first else 0L
    val interest = if (interestEdited) Money.parse(interestText) ?: 0 else autoInterest

    val title = when {
        editing != null -> eventLabel(editing.kind, lent)
        isPayment -> stringResource(if (lent) R.string.loan_record_received else R.string.loan_record_paid)
        else -> stringResource(if (lent) R.string.loan_gave_more else R.string.loan_took_more)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15); error = null },
                    label = { Text(stringResource(R.string.amount)) },
                    prefix = { Text(LocalCurrency.current.symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showInterest) {
                    OutlinedTextField(
                        value = if (interestEdited) interestText else if (autoInterest > 0) Money.plain(autoInterest) else "",
                        onValueChange = {
                            interestText = it.filter { c -> c.isDigit() || c == '.' }.take(15)
                            interestEdited = interestText.isNotEmpty()
                            error = null
                        },
                        label = { Text(stringResource(R.string.loan_interest_part)) },
                        prefix = { Text(LocalCurrency.current.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.loan_split_hint, money(interestDueThen), money(((amount ?: 0) - interest).coerceAtLeast(0))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (!isOpening) {
                    PickerRow(Icons.Filled.CalendarToday, stringResource(R.string.date), Dates.medium(date), onClick = { pickDate = true }, modifier = Modifier.padding(top = 4.dp))
                    val receiving = (editing?.kind ?: if (isPayment) LoanEventKind.REPAID else LoanEventKind.GIVEN).let { k ->
                        if (k == LoanEventKind.GIVEN) !lent else lent
                    }
                    PickerRow(
                        CategoryIcons.account(accounts.firstOrNull { it.id == accountId }?.type ?: com.saraltechies.saralmoneytracker.model.AccountType.CASH),
                        stringResource(if (receiving) R.string.received_in else R.string.paid_from),
                        accounts.firstOrNull { it.id == accountId }?.name.orEmpty(),
                        onClick = { pickAccount = true },
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(200) },
                        placeholder = { Text(stringResource(R.string.note_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(stringResource(R.string.loan_before_app_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    amount == null || amount <= 0 -> R.string.error_amount
                    showInterest && interest > amount -> R.string.error_interest_part
                    !isOpening && accounts.none { it.id == accountId } -> R.string.error_account
                    else -> null
                }
                if (error != null || amount == null) return@TextButton
                val entries = when {
                    editing != null -> listOf(editing.kind to amount)
                    isPayment -> listOf(LoanEventKind.INTEREST to interest, LoanEventKind.REPAID to (amount - interest))
                    else -> listOf(LoanEventKind.GIVEN to amount)
                }
                onSave(entries, date, accountId, note)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (editing != null) TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickDate) DatePickDialog(date, onDismiss = { pickDate = false }, onPick = { dateDay = it.toEpochDay() })
    if (pickAccount) {
        ChoiceDialog(
            title = stringResource(R.string.account),
            options = accounts,
            selected = accounts.firstOrNull { it.id == accountId },
            label = { it.name },
            leading = { IconBadge(CategoryIcons.account(it.type), MaterialTheme.colorScheme.primary, size = 32.dp) },
            onPick = { accountId = it.id },
            onDismiss = { pickAccount = false },
        )
    }
    if (confirmDelete && editing != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_entry_title),
            text = stringResource(R.string.delete_entry_text),
            confirm = stringResource(R.string.delete),
            onConfirm = { onDelete(editing); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Who the loan is with: tap the phone to call, the email to write. */
@Composable
private fun ContactCard(c: com.saraltechies.saralmoneytracker.model.Contact) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                listOf(c.name, stringResource(com.saraltechies.saralmoneytracker.util.StatementPdf.kindLabel(c.kind))).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
            )
            if (c.phone.isNotBlank()) {
                ContactLine(Icons.Filled.Call, c.phone) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + c.phone))) }
                }
            }
            if (c.email.isNotBlank()) {
                ContactLine(Icons.Filled.Email, c.email) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + c.email))) }
                }
            }
            if (c.address.isNotBlank()) ContactLine(Icons.Filled.Place, c.address, null)
            val bank = listOf(c.bankName, c.bankAccount, c.ifsc).filter { it.isNotBlank() }.joinToString(" · ")
            if (bank.isNotBlank()) ContactLine(Icons.Filled.AccountBalance, bank, null)
            if (c.upi.isNotBlank()) ContactLine(Icons.Filled.QrCode, c.upi, null)
            if (c.taxId.isNotBlank()) ContactLine(Icons.Filled.Badge, c.taxId, null)
            if (c.reference.isNotBlank()) ContactLine(Icons.Filled.Tag, c.reference, null)
        }
    }
}

@Composable
private fun ContactLine(icon: ImageVector, text: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
