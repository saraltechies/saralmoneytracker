package com.saraltechies.saralmoneytracker.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AmountExpression
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.Recurring
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.ChoiceDialog
import com.saraltechies.saralmoneytracker.ui.components.ConfirmDialog
import com.saraltechies.saralmoneytracker.ui.components.DatePickDialog
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.Keypad
import com.saraltechies.saralmoneytracker.ui.components.KeypadInput
import com.saraltechies.saralmoneytracker.ui.components.PickerRow
import com.saraltechies.saralmoneytracker.ui.components.frequencyLabel
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.components.typeColor
import com.saraltechies.saralmoneytracker.ui.components.typeLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Add or edit an entry, or a repeating entry ([ruleId] != 0). The amount is typed on an on-screen keypad that also
 * adds up sums ("250+120"), so the common case is: type, pick a category, Save.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    repository: MoneyRepository,
    settings: SettingsRepository,
    txnId: Long,
    ruleId: Long,
    initialType: TxnType,
    onBack: () -> Unit,
    onDeleted: (Txn) -> Unit,
    onManageCategories: () -> Unit,
    onOpenLoan: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val categories by remember { repository.watch { repository.categories() } }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember { repository.watch { repository.accounts() } }.collectAsStateWithLifecycle(emptyList())

    var loaded by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var expr by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var note by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf<Frequency?>(null) }
    var original by remember { mutableStateOf<Txn?>(null) }
    var originalRule by remember { mutableStateOf<Recurring?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(0) } // 1 from, 2 to
    var pickRepeat by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateDay)
    val isRule = ruleId != 0L

    // Load the entry being edited, or pick smart defaults (the last used account and category) for a new one.
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val (txn, rule, recent) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Triple(
                if (txnId != 0L) repository.txn(txnId) else null,
                if (isRule) repository.recurringRule(ruleId) else null,
                repository.txns(limit = 30),
            )
        }
        when {
            // Loan entries are changed on the loan's own screen, where the interest and balance are worked out.
            txn?.loanId != null -> {
                onOpenLoan(txn.loanId)
                return@LaunchedEffect
            }
            txn != null -> {
                original = txn
                type = txn.type
                expr = Money.plain(txn.amount)
                categoryId = txn.categoryId
                accountId = txn.accountId
                toAccountId = txn.toAccountId
                dateDay = txn.date.toEpochDay()
                note = txn.note
            }
            rule != null -> {
                originalRule = rule
                type = rule.type
                expr = Money.plain(rule.amount)
                categoryId = rule.categoryId
                accountId = rule.accountId
                toAccountId = rule.toAccountId
                dateDay = rule.nextDate.toEpochDay()
                note = rule.note
                repeat = rule.frequency
            }
            else -> {
                val own = recent.filter { it.loanId == null }
                val last = own.firstOrNull { it.type == type }
                accountId = last?.accountId ?: own.firstOrNull()?.accountId
                toAccountId = last?.toAccountId
                categoryId = last?.categoryId
                if (isRule) repeat = Frequency.MONTHLY
            }
        }
        loaded = true
    }

    val kindCategories = categories.filter { it.kind == type }
    // The categories you use most come first, so the usual one is a tap away and the full list stays out of the way.
    val usage by remember(type) { repository.watch { repository.categoryUsage(type, LocalDate.now().minusDays(120)) } }
        .collectAsStateWithLifecycle(emptyMap())
    var allCategories by remember { mutableStateOf(false) }
    // Keep a valid category for the current type.
    LaunchedEffect(type, categories, loaded) {
        if (!loaded || type == TxnType.TRANSFER || categories.isEmpty()) return@LaunchedEffect
        if (kindCategories.none { it.id == categoryId }) categoryId = kindCategories.firstOrNull()?.id
    }
    LaunchedEffect(accounts, loaded) {
        if (!loaded || accounts.isEmpty()) return@LaunchedEffect
        if (accounts.none { it.id == accountId }) accountId = accounts.first().id
        if (toAccountId == null || toAccountId == accountId || accounts.none { it.id == toAccountId }) {
            toAccountId = accounts.firstOrNull { it.id != accountId }?.id
        }
    }

    val amount = AmountExpression.evaluate(expr)
    val color = typeColor(type)

    fun save() {
        val value = amount
        error = when {
            value == null || value <= 0 -> R.string.error_amount
            type == TxnType.TRANSFER && (toAccountId == null || toAccountId == accountId) -> R.string.error_transfer_accounts
            accountId == null -> R.string.error_account
            else -> null
        }
        if (error != null || value == null) return
        val cat = if (type == TxnType.TRANSFER) null else categoryId
        val to = if (type == TxnType.TRANSFER) toAccountId else null
        scope.launch {
            val frequency = repeat
            if (isRule || (original == null && frequency != null)) {
                val base = originalRule
                val rule = Recurring(
                    id = base?.id ?: 0, type = type, amount = value, categoryId = cat, accountId = accountId!!, toAccountId = to,
                    note = note.trim(), frequency = frequency ?: Frequency.MONTHLY, startDate = date, nextDate = date,
                    endDate = base?.endDate, active = base?.active ?: true,
                )
                repository.saveRecurring(rule)
                repository.processRecurring()
            } else {
                val txn = (original ?: Txn(type = type, amount = value, date = date, categoryId = cat, accountId = accountId!!)).copy(
                    type = type, amount = value, date = date, categoryId = cat, accountId = accountId!!, toAccountId = to, note = note.trim(),
                )
                repository.saveTxn(txn)
            }
            settings.recordGoodMoment()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                isRule && originalRule != null -> R.string.edit_repeating
                                isRule -> R.string.new_repeating
                                original != null -> R.string.edit_entry
                                else -> R.string.new_entry
                            },
                        ),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (original != null || originalRule != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val types = TxnType.entries
                types.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { type = t; error = null },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                    ) { Text(stringResource(typeLabel(t)), maxLines = 1) }
                }
            }

            AmountDisplay(expr, amount, color)
            error?.let {
                Text(
                    stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = TextAlign.Center,
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (type != TxnType.TRANSFER) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = onManageCategories) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.manage))
                        }
                    }
                    QuickCategories(kindCategories, usage, categoryId, onMore = { allCategories = true }) { categoryId = it; error = null }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                AccountRows(type, accounts, accountId, toAccountId, onPickFrom = { pickAccount = 1 }, onPickTo = { pickAccount = 2 }, onSwap = {
                    val a = accountId
                    accountId = toAccountId
                    toAccountId = a
                })
                PickerRow(Icons.Filled.CalendarToday, stringResource(if (isRule || repeat != null) R.string.starts_on else R.string.date), Dates.relative(date) + " · " + Dates.medium(date), onClick = { pickDate = true })
                if (!isRule && original == null) {
                    Row(Modifier.padding(start = 60.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val today = LocalDate.now()
                        FilterChip(selected = date == today, onClick = { dateDay = today.toEpochDay() }, label = { Text(stringResource(R.string.today)) })
                        FilterChip(selected = date == today.minusDays(1), onClick = { dateDay = today.minusDays(1).toEpochDay() }, label = { Text(stringResource(R.string.yesterday)) })
                    }
                }
                if (isRule || original == null) {
                    PickerRow(
                        Icons.Filled.Repeat, stringResource(R.string.repeat),
                        repeat?.let { stringResource(frequencyLabel(it)) } ?: stringResource(R.string.repeat_never),
                        onClick = { pickRepeat = true },
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                    placeholder = { Text(stringResource(R.string.note_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (!WindowInsets.isImeVisible) {
                Keypad(
                    onKey = { expr = KeypadInput.press(expr, it); error = null },
                    onBackspace = { expr = KeypadInput.backspace(expr) },
                )
            }
            Button(
                onClick = ::save,
                enabled = loaded,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).height(52.dp),
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (pickDate) DatePickDialog(date, onDismiss = { pickDate = false }, onPick = { dateDay = it.toEpochDay() })
    if (pickAccount != 0) {
        ChoiceDialog(
            title = stringResource(if (pickAccount == 2) R.string.to_account else if (type == TxnType.TRANSFER) R.string.from_account else R.string.account),
            options = accounts,
            selected = accounts.firstOrNull { it.id == (if (pickAccount == 2) toAccountId else accountId) },
            label = { it.name },
            leading = { IconBadge(CategoryIcons.account(it.type), MaterialTheme.colorScheme.primary, size = 32.dp) },
            onPick = { if (pickAccount == 2) toAccountId = it.id else accountId = it.id },
            onDismiss = { pickAccount = 0 },
        )
    }
    if (allCategories) {
        CategorySheet(
            categories = kindCategories,
            selected = categoryId,
            onSelect = { categoryId = it; error = null },
            onManage = { allCategories = false; onManageCategories() },
            onDismiss = { allCategories = false },
        )
    }
    if (pickRepeat) {
        val options: List<Frequency?> = (if (isRule) emptyList() else listOf<Frequency?>(null)) + Frequency.entries
        ChoiceDialog(
            title = stringResource(R.string.repeat),
            options = options,
            selected = repeat,
            label = { f -> f?.let { stringResource(frequencyLabel(it)) } ?: stringResource(R.string.repeat_never) },
            onPick = { repeat = it },
            onDismiss = { pickRepeat = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(if (originalRule != null) R.string.delete_repeating_title else R.string.delete_entry_title),
            text = stringResource(if (originalRule != null) R.string.delete_repeating_text else R.string.delete_entry_text),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    original?.let {
                        repository.deleteTxn(it.id)
                        onDeleted(it)
                    }
                    originalRule?.let { repository.deleteRecurring(it.id) }
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AmountDisplay(expr: String, amount: Long?, color: Color) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shown = expr.replace('-', '−').ifEmpty { "0" }
        Text(
            shown,
            fontSize = if (shown.length > 14) 28.sp else 40.sp,
            fontWeight = FontWeight.Bold,
            color = if (expr.isEmpty()) MaterialTheme.colorScheme.outline else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val result = if (AmountExpression.hasOperator(expr) && amount != null) "= " + money(amount) else if (amount != null) money(amount) else " "
        Text(result, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How many categories the entry form shows before "More". Two rows of four. */
private const val QUICK_COUNT = 8

/**
 * Your most used categories as two short rows, with "More" for the rest. The chosen category is always shown, so
 * picking one from the full list keeps it in view.
 */
@Composable
private fun QuickCategories(categories: List<Category>, usage: Map<Long, Int>, selected: Long?, onMore: () -> Unit, onSelect: (Long) -> Unit) {
    val needsMore = categories.size > QUICK_COUNT
    val shown = if (!needsMore) categories else {
        // Most used first; ties keep your own order from Manage categories.
        val ranked = categories.withIndex().sortedWith(compareByDescending<IndexedValue<Category>> { usage[it.value.id] ?: 0 }.thenBy { it.index }).map { it.value }
        val top = ranked.take(QUICK_COUNT - 1).toMutableList()
        val chosen = categories.firstOrNull { it.id == selected }
        if (chosen != null && chosen !in top) top[top.lastIndex] = chosen
        top
    }
    val tiles: List<Category?> = if (needsMore) shown + null else shown
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c ->
                    if (c == null) MoreTile(Modifier.weight(1f), onMore)
                    else CategoryTile(c, c.id == selected, Modifier.weight(1f)) { onSelect(c.id) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(c: Category, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = Color(c.color)
    Column(
        modifier
            .background(if (isSelected) tint.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(12.dp))
            .border(if (isSelected) 1.5.dp else 0.dp, if (isSelected) tint else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(CategoryIcons.of(c.icon), tint, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            c.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center, minLines = 2,
            overflow = TextOverflow.Ellipsis, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MoreTile(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Filled.Apps, MaterialTheme.colorScheme.primary, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.more_categories), style = MaterialTheme.typography.labelSmall, maxLines = 2, minLines = 2, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
    }
}

/** Every category, with search, in a sheet that opens over the form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(categories: List<Category>, selected: Long?, onSelect: (Long) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = categories.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    fun close(after: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss(); after() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.choose_category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { close(onManage) }) {
                Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.manage))
            }
        }
        if (categories.size > QUICK_COUNT) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.search_categories)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(shown, key = { it.id }) { c ->
                CategoryTile(c, c.id == selected) { onSelect(c.id); close() }
            }
        }
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.no_matching_categories), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun AccountRows(
    type: TxnType,
    accounts: List<Account>,
    accountId: Long?,
    toAccountId: Long?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
) {
    val from = accounts.firstOrNull { it.id == accountId }?.name.orEmpty()
    if (type == TxnType.TRANSFER) {
        val to = accounts.firstOrNull { it.id == toAccountId }?.name.orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PickerRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.from_account), from, onPickFrom)
                PickerRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.to_account), to, onPickTo)
            }
            IconButton(onClick = onSwap, modifier = Modifier.padding(end = 8.dp)) { Icon(Icons.Filled.SwapVert, stringResource(R.string.swap_accounts)) }
        }
        Text(
            stringResource(R.string.transfer_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    } else {
        PickerRow(
            Icons.Filled.AccountBalanceWallet,
            stringResource(if (type == TxnType.INCOME) R.string.received_in else R.string.paid_from),
            from, onPickFrom,
        )
    }
}
