package com.saraltechies.saralmoneytracker.ui.ledger

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.PartyItem
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.LedgerEntry
import com.saraltechies.saralmoneytracker.model.LedgerKind
import com.saraltechies.saralmoneytracker.model.Ledgers
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.PartyRole
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
import com.saraltechies.saralmoneytracker.ui.components.frequencyLabel
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import com.saraltechies.saralmoneytracker.util.PartyStatementPdf
import com.saraltechies.saralmoneytracker.util.StatementFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** One customer's or supplier's account: what they owe, every entry, and quick ways to record and remind. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyScreen(
    repository: MoneyRepository,
    partyId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMyDetails: () -> Unit,
) {
    val context = LocalContext.current
    val currency = LocalCurrency.current
    val scope = rememberCoroutineScope()
    val item by remember { repository.watch { repository.partyItem(partyId) } }.collectAsStateWithLifecycle(null)
    val accounts by remember { repository.watch { repository.accounts(book = Books.ALL) } }.collectAsStateWithLifecycle(emptyList())
    val categories by remember { repository.watch { repository.categories(book = Books.ALL) } }.collectAsStateWithLifecycle(emptyList())
    val me by remember { repository.watch { repository.partyItem(partyId)?.let { repository.myDetails(it.party.bookId) } } }.collectAsStateWithLifecycle(null)
    var entry by remember { mutableStateOf<Pair<Boolean, LedgerEntry?>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val rules by remember { repository.watch { repository.partyItem(partyId)?.let { repository.partyRules(it.party) }.orEmpty() } }.collectAsStateWithLifecycle(emptyList())
    var remind by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(item?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    item?.contact?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } }) {
                            Icon(Icons.Filled.Call, stringResource(R.string.contact_phone))
                        }
                    }
                    IconButton(onClick = {
                        val current = item ?: return@IconButton
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                runCatching {
                                    val dir = File(context.cacheDir, "statements").apply { mkdirs() }
                                    val name = current.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifEmpty { "account" }
                                    File(dir, context.getString(R.string.ledger_statement_file, name, LocalDate.now().toString()) + ".pdf").also { f ->
                                        f.outputStream().use { PartyStatementPdf(context, currency).write(current, me, accountNames(accounts), it) }
                                    }
                                }.getOrNull()
                            }
                            if (file == null) {
                                Toast.makeText(context, R.string.statement_failed, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val uri = StatementFiles.uri(context, file)
                            val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                                .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            current.contact?.email?.takeIf { it.isNotBlank() }?.let { send.putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
                            send.clipData = android.content.ClipData.newRawUri(file.name, uri)
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        }
                    }) { Icon(Icons.Filled.PictureAsPdf, stringResource(R.string.loan_statement)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.ledger_edit)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.my_details)) }, onClick = { menu = false; onMyDetails() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        val current = item ?: return@Scaffold
        val b = current.balance
        val names = accountNames(accounts)
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(
                                when {
                                    b > 0 -> R.string.ledger_you_will_get
                                    b < 0 -> R.string.ledger_you_will_give
                                    else -> R.string.ledger_settled
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            money(kotlin.math.abs(b)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = when {
                                b > 0 -> AppTheme.money.income
                                b < 0 -> AppTheme.money.expense
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            stringResource(if (current.party.role == PartyRole.CUSTOMER) R.string.ledger_customer else R.string.ledger_supplier) +
                                (current.contact?.phone?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val dues = remember(current) { Ledgers.dues(current.party, current.entries, LocalDate.now()) }
                        if (dues.overdue > 0) {
                            Text(
                                stringResource(R.string.ledger_overdue_since, money(dues.overdue), Dates.short(dues.oldestOverdue ?: LocalDate.now())),
                                style = MaterialTheme.typography.bodyMedium, color = AppTheme.money.expense, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        dues.next?.let { (date, amount) ->
                            Text(
                                stringResource(R.string.ledger_next_due, money(amount), Dates.relative(date)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { entry = true to null }, modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.money.expense),
                    ) { Text(stringResource(R.string.ledger_you_gave), fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { entry = false to null }, modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.money.income),
                    ) { Text(stringResource(R.string.ledger_you_got), fontWeight = FontWeight.SemiBold) }
                }
            }
            if (b != 0L) {
                item {
                    OutlinedButton(onClick = { remind = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (b > 0) R.string.ledger_remind else R.string.ledger_share_balance))
                    }
                }
            }
            if (rules.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.repeating_entries)) }
                items(rules, key = { "rule:${it.id}" }) { r ->
                    val gaveRule = (r.type == TxnType.INCOME) || (r.type == TxnType.TRANSFER && r.toAccountId == current.party.accountId)
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (gaveRule) R.string.ledger_you_gave else R.string.ledger_you_got) + " · " + money(r.amount) + " · " +
                                    stringResource(frequencyLabel(r.frequency)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                listOfNotNull(stringResource(R.string.next_on, Dates.relative(r.nextDate)), r.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { repository.deleteRecurring(r.id) } }) { Text(stringResource(R.string.ledger_stop_repeat)) }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.loan_history)) }
            if (current.entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.ledger_entries_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(current.entries.reversed(), key = { "e:${it.txnId ?: "opening"}" }) { e ->
                EntryRow(e, categories.firstOrNull { it.id == e.categoryId }, e.accountId?.let { names[it] }) {
                    entry = (e.change > 0) to e
                }
            }
        }
    }

    val current = item
    if (current != null) {
        entry?.let { (gave, existing) ->
            EntryDialog(
                item = current, gave = gave, existing = existing, accounts = accounts.filter { !it.archived },
                categories = categories.filter { !it.archived && it.bookId == current.party.bookId },
                onDismiss = { entry = null },
                onSave = { amount, date, accountId, categoryId, note, repeat ->
                    scope.launch {
                        if (existing?.kind == LedgerKind.OPENING) {
                            repository.updateParty(current.party, current.contact ?: Contact(name = current.name), current.party.role, current.party.bookId, if (gave) amount else -amount)
                        } else if (repeat != null) {
                            repository.savePartyRule(current.party, gave, amount, date, accountId, categoryId, note, repeat)
                        } else {
                            repository.savePartyEntry(
                                current.party, gave, amount, date, accountId, categoryId, note,
                                existing?.txnId?.let { id -> current.txns.firstOrNull { it.id == id } },
                            )
                        }
                    }
                },
                onDelete = { e ->
                    scope.launch {
                        val party = current.party
                        val contact = current.contact ?: Contact(name = current.name)
                        val undo: suspend () -> Unit = if (e.txnId == null) {
                            val before = current.opening
                            repository.updateParty(party, contact, party.role, party.bookId, 0)
                            suspend { repository.updateParty(party, contact, party.role, party.bookId, before) }
                        } else {
                            val txn = current.txns.firstOrNull { it.id == e.txnId }
                            repository.deleteTxn(e.txnId)
                            suspend { txn?.let { repository.restoreTxn(it) }; Unit }
                        }
                        val result = snackbar.showSnackbar(context.getString(R.string.entry_deleted), actionLabel = context.getString(R.string.undo), duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) undo()
                    }
                },
            )
        }
        if (remind) ReminderDialog(current, me, onMyDetails = { remind = false; onMyDetails() }, onDismiss = { remind = false })
        if (confirmDelete) {
            ConfirmDialog(
                title = stringResource(R.string.ledger_delete_title),
                text = stringResource(R.string.ledger_delete_text, current.name),
                confirm = stringResource(R.string.delete),
                onConfirm = { scope.launch { repository.deleteParty(current.party); onBack() } },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

private fun accountNames(accounts: List<Account>) = accounts.associate { it.id to it.name }

@Composable
fun ledgerLabel(kind: LedgerKind): String = stringResource(
    when (kind) {
        LedgerKind.GAVE_GOODS -> R.string.ledger_kind_gave_goods
        LedgerKind.GAVE_MONEY -> R.string.ledger_kind_gave_money
        LedgerKind.GOT_GOODS -> R.string.ledger_kind_got_goods
        LedgerKind.GOT_MONEY -> R.string.ledger_kind_got_money
        LedgerKind.OPENING -> R.string.ledger_kind_opening
    },
)

@Composable
private fun EntryRow(e: LedgerEntry, category: Category?, accountName: String?, onClick: () -> Unit) {
    val color = if (e.gave) AppTheme.money.expense else AppTheme.money.income
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ledgerLabel(e.kind), style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(
                if (e.kind == LedgerKind.OPENING) null else Dates.short(e.date),
                category?.name, accountName, e.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.ledger_balance_after, money(kotlin.math.abs(e.balanceAfter))) +
                    if (e.balanceAfter > 0) " " + stringResource(R.string.ledger_you_will_get_short) else if (e.balanceAfter < 0) " " + stringResource(R.string.ledger_you_will_give_short) else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(money(e.amount), fontWeight = FontWeight.SemiBold, color = color)
    }
}

/**
 * Record "You gave" or "You got". Goods (a sale or purchase on credit) count in profit through a category; money
 * moves between one of your accounts and theirs.
 */
@Composable
private fun EntryDialog(
    item: PartyItem,
    gave: Boolean,
    existing: LedgerEntry?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (amount: Long, date: LocalDate, accountId: Long?, categoryId: Long?, note: String, repeat: Frequency?) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    val customer = item.party.role == PartyRole.CUSTOMER
    val isOpening = existing?.kind == LedgerKind.OPENING
    // Customers usually get goods on credit and pay money; suppliers the other way round.
    var goods by rememberSaveable {
        mutableStateOf(
            existing?.let { it.kind == LedgerKind.GAVE_GOODS || it.kind == LedgerKind.GOT_GOODS } ?: (if (gave) customer else !customer),
        )
    }
    val categoryKind = if (gave) TxnType.INCOME else TxnType.EXPENSE
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.plain(it.amount) }.orEmpty()) }
    var dateDay by rememberSaveable { mutableStateOf((existing?.date ?: LocalDate.now()).toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var categoryId by rememberSaveable {
        mutableStateOf(existing?.categoryId ?: -1L)
    }
    var accountId by rememberSaveable {
        mutableStateOf(existing?.accountId ?: accounts.firstOrNull { it.bookId == item.party.bookId }?.id ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var repeat by rememberSaveable { mutableStateOf<Frequency?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    val date = LocalDate.ofEpochDay(dateDay)
    val kindCategories = categories.filter { it.kind == categoryKind }
    // Sales (or Purchases) by default, the categories a business book starts with.
    val chosenCategory = kindCategories.firstOrNull { it.id == categoryId }
        ?: kindCategories.firstOrNull { it.icon == "store" } ?: kindCategories.firstOrNull { it.icon == "other" } ?: kindCategories.firstOrNull()
    val amount = Money.parse(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isOpening) ledgerLabel(LedgerKind.OPENING) else stringResource(if (gave) R.string.ledger_you_gave else R.string.ledger_you_got)) },
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
                if (!isOpening) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = goods, onClick = { goods = true },
                            label = { Text(stringResource(if (gave) R.string.ledger_goods_sold else R.string.ledger_goods_bought)) },
                        )
                        FilterChip(selected = !goods, onClick = { goods = false }, label = { Text(stringResource(R.string.ledger_money)) })
                    }
                    Text(
                        stringResource(
                            when {
                                goods && gave -> R.string.ledger_goods_sold_hint
                                goods -> R.string.ledger_goods_bought_hint
                                gave -> R.string.ledger_money_gave_hint
                                else -> R.string.ledger_money_got_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (goods) {
                        PickerRow(
                            CategoryIcons.of(chosenCategory?.icon), stringResource(R.string.category), chosenCategory?.name.orEmpty(),
                            onClick = { pickCategory = true },
                        )
                    } else {
                        PickerRow(
                            CategoryIcons.account(accounts.firstOrNull { it.id == accountId }?.type ?: com.saraltechies.saralmoneytracker.model.AccountType.CASH),
                            stringResource(if (gave) R.string.paid_from else R.string.received_in),
                            accounts.firstOrNull { it.id == accountId }?.name.orEmpty(), onClick = { pickAccount = true },
                        )
                    }
                    PickerRow(Icons.Filled.CalendarToday, stringResource(R.string.date), Dates.medium(date), onClick = { pickDate = true })
                    if (existing == null) {
                        // Rent from a tenant, a monthly milk bill: recorded on its date every time.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.labelLarge)
                            listOf(null, Frequency.WEEKLY, Frequency.MONTHLY).forEach { f ->
                                FilterChip(
                                    selected = repeat == f, onClick = { repeat = f },
                                    label = { Text(f?.let { stringResource(frequencyLabel(it)) } ?: stringResource(R.string.repeat_never)) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(200) },
                        placeholder = { Text(stringResource(R.string.ledger_note_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    amount == null || amount <= 0 -> R.string.error_amount
                    !isOpening && goods && chosenCategory == null -> R.string.ledger_error_category
                    !isOpening && !goods && accounts.none { it.id == accountId } -> R.string.error_account
                    else -> null
                }
                if (error != null || amount == null) return@TextButton
                onSave(amount, date, if (goods) null else accountId, if (goods) chosenCategory?.id else null, note, if (existing == null) repeat else null)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickDate) DatePickDialog(date, onDismiss = { pickDate = false }, onPick = { dateDay = it.toEpochDay() })
    if (pickAccount) {
        ChoiceDialog(
            title = stringResource(R.string.account), options = accounts.sortedBy { if (it.bookId == item.party.bookId) 0 else 1 },
            selected = accounts.firstOrNull { it.id == accountId }, label = { it.name },
            leading = { IconBadge(CategoryIcons.account(it.type), MaterialTheme.colorScheme.primary, size = 32.dp) },
            onPick = { accountId = it.id }, onDismiss = { pickAccount = false },
        )
    }
    if (pickCategory) {
        ChoiceDialog(
            title = stringResource(R.string.category), options = kindCategories, selected = chosenCategory, label = { it.name },
            leading = { IconBadge(CategoryIcons.of(it.icon), androidx.compose.ui.graphics.Color(it.color), size = 32.dp) },
            onPick = { categoryId = it.id }, onDismiss = { pickCategory = false },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_entry_title), text = stringResource(R.string.delete_entry_text),
            confirm = stringResource(R.string.delete), onConfirm = { onDelete(existing); onDismiss() }, onDismiss = { confirmDelete = false },
        )
    }
}

/** A friendly payment reminder (or balance confirmation) by WhatsApp, SMS or any app. */
@Composable
private fun ReminderDialog(item: PartyItem, me: Contact?, onMyDetails: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currency = LocalCurrency.current
    val b = item.balance
    val from = me?.name?.takeIf { it.isNotBlank() }
    val text = buildString {
        append(
            context.getString(
                if (b > 0) R.string.ledger_remind_text else R.string.ledger_balance_text,
                item.contact?.name ?: item.name, Money.format(kotlin.math.abs(b), currency), Dates.medium(LocalDate.now()),
                from ?: context.getString(R.string.ledger_me),
            ),
        )
        if (b > 0 && me?.upi?.isNotBlank() == true) append("\n").append(context.getString(R.string.ledger_remind_upi, me.upi))
        if (b > 0 && me?.bankAccount?.isNotBlank() == true) {
            append("\n").append(context.getString(R.string.ledger_remind_bank, listOf(me.bankName, me.bankAccount, me.ifsc).filter { it.isNotBlank() }.joinToString(" · ")))
        }
        append("\n").append(context.getString(R.string.ledger_remind_thanks))
        if (from != null) append("\n").append(from)
    }
    val phone = item.contact?.phone.orEmpty()
    val wa = Ledgers.whatsAppNumber(phone)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (b > 0) R.string.ledger_remind else R.string.ledger_share_balance)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (b > 0 && me?.upi.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.ledger_remind_add_upi), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp).clickable(onClick = onMyDetails),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (wa != null) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=" + Uri.encode(text)))) }
                        onDismiss()
                    }) { Text(stringResource(R.string.ledger_whatsapp)) }
                }
                if (phone.isNotBlank()) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone))).putExtra("sms_body", text)) }
                        onDismiss()
                    }) { Text(stringResource(R.string.ledger_sms)) }
                }
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
                    onDismiss()
                }) { Text(stringResource(R.string.ledger_share)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
