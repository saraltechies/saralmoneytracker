package com.saraltechies.saralmoneytracker.ui.bills

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.BillView
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.PartyItem
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.Bill
import com.saraltechies.saralmoneytracker.model.BillItem
import com.saraltechies.saralmoneytracker.model.BillMath
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.ui.components.BookChips
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.ChoiceDialog
import com.saraltechies.saralmoneytracker.ui.components.DatePickDialog
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalBooks
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.ui.components.PickerRow
import com.saraltechies.saralmoneytracker.ui.components.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val WALK_IN = 0L
private const val NEW_CUSTOMER = -1L
private const val SEP = "\u001F"

private val ItemsSaver = listSaver<SnapshotStateList<BillItem>, String>(
    save = { list -> list.map { listOf(it.name, it.quantity, it.unit, it.rate, it.gstRate, it.hsn).joinToString(SEP) } },
    restore = { saved ->
        mutableStateListOf<BillItem>().apply {
            saved.forEach { s ->
                val f = s.split(SEP)
                add(BillItem(f[0], f[1].toLong(), f[2], f[3].toLong(), f[4].toLong(), f[5]))
            }
        }
    },
)

/** A new bill ([billId] 0) or changes to one: who it's for, the items, discount and GST, and what was received now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditScreen(
    repository: MoneyRepository,
    settings: SettingsRepository,
    billId: Long,
    initialPartyId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isNew = billId == 0L
    val books = LocalBooks.current
    var bookId by rememberSaveable { mutableStateOf(books.defaultBook) }
    val customers by remember { repository.watch { repository.partyItems(Books.ALL).filter { it.party.role == PartyRole.CUSTOMER } } }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember { repository.watch { repository.accounts(book = Books.ALL) } }.collectAsStateWithLifecycle(emptyList())
    var existing by remember { mutableStateOf<BillView?>(null) }
    val items = rememberSaveable(saver = ItemsSaver) { mutableStateListOf<BillItem>() }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var number by rememberSaveable { mutableStateOf("") }
    var dateDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var partyId by rememberSaveable { mutableStateOf(if (initialPartyId > 0) initialPartyId else WALK_IN) }
    var customerName by rememberSaveable { mutableStateOf("") }
    var customerPhone by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf(0L) }
    var discountText by rememberSaveable { mutableStateOf("") }
    var receivedText by rememberSaveable { mutableStateOf("") }
    var withGst by rememberSaveable { mutableStateOf(false) }
    var includesGst by rememberSaveable { mutableStateOf(false) }
    var interState by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickCustomer by remember { mutableStateOf(false) }
    // The line being added or changed: its place in the list, or -1 for a new one.
    var editingLine by remember { mutableStateOf<Int?>(null) }
    val catalog by remember(bookId) { repository.watch { repository.billCatalog(bookId) } }.collectAsStateWithLifecycle(emptyList())
    val date = LocalDate.ofEpochDay(dateDay)

    LaunchedEffect(Unit) {
        if (loaded) {
            if (!isNew) existing = withContext(Dispatchers.IO) { repository.billView(billId) }
            return@LaunchedEffect
        }
        if (isNew) {
            val fromParty = if (initialPartyId > 0) withContext(Dispatchers.IO) { repository.partyItem(initialPartyId) } else null
            fromParty?.let { bookId = it.party.bookId }
            number = withContext(Dispatchers.IO) { repository.nextBillNumber(bookId) }
            // A shop with a GSTIN in its details bills with GST from the start.
            withGst = withContext(Dispatchers.IO) { repository.myDetails(bookId) }?.taxId?.isNotBlank() == true
        } else {
            val view = withContext(Dispatchers.IO) { repository.billView(billId) } ?: run { onBack(); return@LaunchedEffect }
            existing = view
            val b = view.bill
            number = b.number
            bookId = b.bookId
            dateDay = b.date.toEpochDay()
            partyId = b.partyId ?: WALK_IN
            customerName = b.customerName
            accountId = b.accountId ?: 0
            discountText = if (b.discount > 0) Money.plain(b.discount) else ""
            withGst = view.totals.hasGst
            includesGst = b.pricesIncludeGst
            interState = b.interState
            note = b.note
            items.clear()
            items.addAll(b.items)
        }
        loaded = true
    }
    LaunchedEffect(accounts, bookId) {
        if (accounts.none { it.id == accountId } && accounts.isNotEmpty()) accountId = (accounts.firstOrNull { it.bookId == bookId } ?: accounts.first()).id
    }

    val customer: PartyItem? = customers.firstOrNull { it.party.id == partyId }
    val discount = Money.parse(discountText) ?: 0
    // With GST off, no line carries a rate, even if one was set before it was turned off.
    val lines = if (withGst) items.toList() else items.map { it.copy(gstRate = 0, hsn = "") }
    val totals = BillMath.totals(lines, discount, includesGst && withGst)
    val received = Money.parse(receivedText) ?: 0
    val isWalkIn = partyId == WALK_IN

    fun save() {
        error = when {
            items.isEmpty() -> R.string.error_bill_items
            number.isBlank() -> R.string.error_bill_number
            partyId == NEW_CUSTOMER && customerName.isBlank() -> R.string.error_bill_customer
            discount > totals.subtotal -> R.string.error_bill_discount
            totals.total <= 0 -> R.string.error_bill_total
            isNew && !isWalkIn && received > totals.total -> R.string.error_bill_received
            (isWalkIn || received > 0) && accounts.none { it.id == accountId } -> R.string.error_account
            else -> null
        }
        if (error != null) return
        scope.launch {
            if (withContext(Dispatchers.IO) { repository.billNumberTaken(bookId, number, billId) }) {
                error = R.string.error_bill_number_taken
                return@launch
            }
            val party = when (partyId) {
                WALK_IN -> null
                NEW_CUSTOMER -> repository.createParty(Contact(name = customerName.trim(), phone = customerPhone.trim(), bookId = bookId), PartyRole.CUSTOMER, bookId, 0)
                else -> partyId
            }
            val bill = Bill(
                id = billId, number = number, date = date, bookId = bookId, partyId = party,
                customerName = if (party == null) customerName else "", accountId = if (party == null) accountId else null,
                discount = discount, pricesIncludeGst = includesGst && withGst, interState = interState && withGst, note = note,
                createdAt = existing?.bill?.createdAt ?: 0, items = lines,
            )
            if (isNew) {
                val id = repository.createBill(bill, received, accountId.takeIf { received > 0 })
                settings.recordGoodMoment()
                onSaved(id)
            } else {
                repository.updateBill(bill)
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.bill_new else R.string.bill_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // A customer's bill belongs to their book; only a walk-in sale has a choice.
                if (isNew && partyId <= 0) BookChips(bookId, onSelect = { b ->
                    bookId = b
                    scope.launch { number = withContext(Dispatchers.IO) { repository.nextBillNumber(b) } }
                }, modifier = Modifier.padding(bottom = 4.dp))
                PickerRow(
                    Icons.Filled.Person, stringResource(R.string.bill_customer),
                    when (partyId) {
                        WALK_IN -> stringResource(R.string.bill_walk_in)
                        NEW_CUSTOMER -> stringResource(R.string.bill_new_customer)
                        else -> customer?.name.orEmpty()
                    },
                    onClick = { if (isNew) pickCustomer = true },
                )
                if (partyId <= 0) {
                    OutlinedTextField(
                        value = customerName, onValueChange = { customerName = it.take(60); error = null },
                        label = { Text(stringResource(if (partyId == NEW_CUSTOMER) R.string.ledger_customer_name else R.string.bill_name_optional)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (partyId == NEW_CUSTOMER) {
                    OutlinedTextField(
                        value = customerPhone, onValueChange = { customerPhone = it.take(20) },
                        label = { Text(stringResource(R.string.contact_phone)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                PickerRow(Icons.Filled.CalendarToday, stringResource(R.string.date), Dates.medium(date), onClick = { pickDate = true })
                OutlinedTextField(
                    value = number, onValueChange = { number = it.take(16); error = null },
                    label = { Text(stringResource(R.string.bill_number)) },
                    leadingIcon = { Icon(Icons.Filled.Tag, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Toggle(stringResource(R.string.bill_with_gst), withGst) { withGst = it }
                if (withGst) {
                    Toggle(stringResource(R.string.bill_rates_include_gst), includesGst) { includesGst = it }
                    Toggle(stringResource(R.string.bill_other_state), interState) { interState = it }
                }

                Text(
                    stringResource(R.string.bill_items), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                lines.forEachIndexed { i, line ->
                    Row(Modifier.fillMaxWidth().clickable { editingLine = i }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(lineText(line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(line.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(onClick = { editingLine = -1 }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bill_add_item))
                }

                if (items.isNotEmpty()) {
                    OutlinedTextField(
                        value = discountText, onValueChange = { discountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15); error = null },
                        label = { Text(stringResource(R.string.bill_discount)) },
                        prefix = { Text(LocalCurrency.current.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            TotalLine(stringResource(R.string.bill_subtotal), money(totals.subtotal))
                            if (totals.discount > 0) TotalLine(stringResource(R.string.bill_discount), "-" + money(totals.discount))
                            if (totals.tax > 0) TotalLine(stringResource(if (includesGst) R.string.bill_includes_gst else R.string.bill_gst), money(totals.tax))
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            TotalLine(stringResource(R.string.bill_total), money(totals.total), bold = true)
                        }
                    }
                }

                if (isNew && partyId != WALK_IN) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = receivedText, onValueChange = { receivedText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15); error = null },
                            label = { Text(stringResource(R.string.bill_received_now)) },
                            prefix = { Text(LocalCurrency.current.symbol) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { receivedText = Money.plain(totals.total); error = null }, enabled = totals.total > 0) { Text(stringResource(R.string.bill_full_amount)) }
                    }
                    if (totals.total > 0) {
                        Text(
                            stringResource(R.string.bill_rest_on_credit, money((totals.total - received).coerceAtLeast(0))),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                if ((isWalkIn) || (isNew && received > 0)) {
                    val account = accounts.firstOrNull { it.id == accountId }
                    PickerRow(
                        account?.let { CategoryIcons.account(it.type) } ?: Icons.Filled.AccountBalanceWallet,
                        stringResource(R.string.received_in), account?.name.orEmpty(), onClick = { pickAccount = true },
                    )
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                    placeholder = { Text(stringResource(R.string.bill_note_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            Button(
                onClick = ::save,
                enabled = loaded,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp, top = 4.dp).height(52.dp),
            ) { Text(stringResource(R.string.bill_save), style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (pickDate) DatePickDialog(date, onDismiss = { pickDate = false }, onPick = { dateDay = it.toEpochDay() })
    if (pickAccount) {
        val options: List<Account> = accounts.sortedBy { if (it.bookId == bookId) 0 else 1 }
        ChoiceDialog(
            title = stringResource(R.string.received_in),
            options = options,
            selected = options.firstOrNull { it.id == accountId },
            label = { a -> if (books.multiple) a.name + " · " + books.book(a.bookId)?.name.orEmpty() else a.name },
            leading = { IconBadge(CategoryIcons.account(it.type), MaterialTheme.colorScheme.primary, size = 32.dp) },
            onPick = { accountId = it.id },
            onDismiss = { pickAccount = false },
        )
    }
    if (pickCustomer) {
        val walkIn = stringResource(R.string.bill_walk_in)
        val add = stringResource(R.string.bill_new_customer)
        val options: List<Pair<Long, String>> = listOf(WALK_IN to walkIn, NEW_CUSTOMER to add) +
            customers.sortedBy { it.name.lowercase() }.map { it.party.id to listOf(it.name, it.contact?.phone.orEmpty()).filter { t -> t.isNotBlank() }.joinToString(" · ") }
        ChoiceDialog(
            title = stringResource(R.string.bill_customer),
            options = options,
            selected = options.firstOrNull { it.first == partyId },
            label = { it.second },
            onPick = { (id, _) ->
                partyId = id
                customerName = ""
                error = null
                customers.firstOrNull { it.party.id == id }?.let { c ->
                    if (c.party.bookId != bookId) {
                        bookId = c.party.bookId
                        scope.launch { number = withContext(Dispatchers.IO) { repository.nextBillNumber(c.party.bookId) } }
                    }
                }
            },
            onDismiss = { pickCustomer = false },
        )
    }
    editingLine?.let { index ->
        ItemDialog(
            initial = items.getOrNull(index),
            catalog = catalog + items.toList(),
            withGst = withGst,
            lastGst = items.lastOrNull()?.gstRate ?: 0,
            onDismiss = { editingLine = null },
            onDelete = if (index >= 0) ({ items.removeAt(index) }) else null,
            onSave = { line ->
                error = null
                if (index >= 0) items[index] = line else items.add(line)
            },
        )
    }
}

@Composable
private fun lineText(line: BillItem): String {
    val qty = BillMath.quantityText(line.quantity) + if (line.unit.isBlank()) "" else " " + line.unit
    return qty + " × " + money(line.rate) + if (line.gstRate > 0) " · " + stringResource(R.string.bill_gst) + " " + BillMath.rateText(line.gstRate) + "%" else ""
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

/** One line of a bill. Things sold before come up as you type, with their last price. */
@Composable
private fun ItemDialog(
    initial: BillItem?,
    catalog: List<BillItem>,
    withGst: Boolean,
    lastGst: Long,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BillItem) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var qty by rememberSaveable { mutableStateOf(initial?.let { BillMath.quantityText(it.quantity) } ?: "1") }
    var unit by rememberSaveable { mutableStateOf(initial?.unit.orEmpty()) }
    var rate by rememberSaveable { mutableStateOf(initial?.let { Money.plain(it.rate) }.orEmpty()) }
    var gst by rememberSaveable { mutableStateOf((initial?.gstRate ?: lastGst).let { if (it > 0) BillMath.rateText(it) else "" }) }
    var hsn by rememberSaveable { mutableStateOf(initial?.hsn.orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }
    val suggestions = remember(name, catalog) {
        val q = name.trim()
        if (q.isEmpty()) catalog.distinctBy { it.name.lowercase() }.take(5)
        else catalog.distinctBy { it.name.lowercase() }.filter { it.name.contains(q, true) && !it.name.equals(q, true) }.take(5)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_item)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80); error = null },
                    label = { Text(stringResource(R.string.bill_item_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                suggestions.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            name = s.name
                            unit = s.unit
                            rate = Money.plain(s.rate)
                            if (s.gstRate > 0) gst = BillMath.rateText(s.gstRate)
                            hsn = s.hsn
                            error = null
                        }.padding(vertical = 4.dp),
                    ) {
                        Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(s.rate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10); error = null },
                        label = { Text(stringResource(R.string.bill_quantity)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it.take(8) },
                        label = { Text(stringResource(R.string.bill_unit)) },
                        placeholder = { Text(stringResource(R.string.bill_unit_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15); error = null },
                    label = { Text(stringResource(R.string.bill_rate)) },
                    prefix = { Text(LocalCurrency.current.symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (withGst) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gst, onValueChange = { gst = it.filter { c -> c.isDigit() || c == '.' }.take(5); error = null },
                            label = { Text(stringResource(R.string.bill_gst)) },
                            suffix = { Text("%") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = hsn, onValueChange = { hsn = it.take(10) },
                            label = { Text(stringResource(R.string.bill_hsn)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val quantity = BillMath.parseQuantity(qty)
                val price = Money.parse(rate)
                val tax = if (withGst && gst.isNotBlank()) Money.parse(gst) else 0L
                error = when {
                    name.isBlank() -> R.string.error_bill_item_name
                    quantity == null -> R.string.error_bill_quantity
                    price == null || price < 0 -> R.string.error_amount
                    tax == null || tax > 10_000 -> R.string.error_bill_gst
                    else -> null
                }
                if (error != null || quantity == null || price == null || tax == null) return@TextButton
                onSave(BillItem(name.trim(), quantity, unit.trim(), price, tax, hsn.trim()))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
