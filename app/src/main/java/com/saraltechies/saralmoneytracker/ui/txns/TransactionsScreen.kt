package com.saraltechies.saralmoneytracker.ui.txns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Stats
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.DayHeader
import com.saraltechies.saralmoneytracker.ui.components.EmptyState
import com.saraltechies.saralmoneytracker.ui.components.Lookups
import com.saraltechies.saralmoneytracker.ui.components.PeriodBar
import com.saraltechies.saralmoneytracker.ui.components.TxnRow
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.components.rememberPeriod
import com.saraltechies.saralmoneytracker.ui.components.typeLabel
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme

/**
 * Every entry for the chosen day, week, month, year or range, grouped by day. Also used for one account's history
 * ([accountId]) and one category's ([categoryId]), which open from the accounts and stats screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    repository: MoneyRepository,
    snackbar: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onOpenTxn: (Long) -> Unit,
    onAdd: () -> Unit,
    startSearching: Boolean = false,
    accountId: Long? = null,
    categoryId: Long? = null,
    initialKind: PeriodKind = PeriodKind.MONTH,
    onBack: (() -> Unit)? = null,
) {
    var period by rememberPeriod(initialKind)
    var searching by rememberSaveable { mutableStateOf(startSearching) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<TxnType?>(null) }

    val lookups by remember { repository.watch { Lookups(repository.categories(true), repository.accounts(true), repository.loanDirections()) } }
        .collectAsStateWithLifecycle(Lookups(emptyList(), emptyList()))
    val searchText = query.trim().takeIf { searching && it.isNotEmpty() }
    val txns by remember(period, searchText, accountId, categoryId) {
        repository.watch {
            // A search looks through everything, not just the shown period.
            if (searchText != null) repository.txns(accountId = accountId, categoryId = categoryId, query = searchText, limit = 500)
            else repository.txns(period.start, period.end, accountId = accountId, categoryId = categoryId)
        }
    }.collectAsStateWithLifecycle(null)
    val shown = txns.orEmpty().filter { typeFilter == null || it.type == typeFilter }
    val title = when {
        accountId != null -> lookups.accounts[accountId]?.name.orEmpty()
        categoryId != null -> lookups.categories[categoryId]?.name.orEmpty()
        else -> stringResource(R.string.tab_transactions)
    }

    Scaffold(
        topBar = {
            if (searching) SearchBar(query, onQuery = { query = it }, onClose = { searching = false; query = "" })
            else TopAppBar(
                title = { Text(title) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) } },
            )
        },
        bottomBar = { if (onBack == null) bottomBar() },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, stringResource(R.string.add_entry)) }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            if (searchText == null) item { PeriodBar(period, onChange = { period = it }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text(stringResource(R.string.all)) }) }
                    items(TxnType.entries) { t ->
                        FilterChip(selected = typeFilter == t, onClick = { typeFilter = if (typeFilter == t) null else t }, label = { Text(stringResource(typeLabel(t))) })
                    }
                }
            }
            item { TotalsRow(shown, accountId) }
            when {
                txns == null -> Unit
                shown.isEmpty() -> item {
                    if (searchText != null) EmptyState(Icons.Filled.SearchOff, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_text))
                    else EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.list_empty_title), stringResource(R.string.list_empty_text))
                }
                else -> {
                    val byDay = shown.groupBy { it.date }
                    for ((date, dayTxns) in byDay) {
                        item(key = "day:$date") { DayHeader(date, Stats.totals(dayTxns).net) }
                        items(dayTxns, key = { it.id }) { txn -> TxnRow(txn, lookups, onClick = { onOpenTxn(txn.id) }, accountFocus = accountId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsRow(txns: List<Txn>, accountId: Long?) {
    var income = 0L
    var expense = 0L
    for (t in txns) when {
        t.type == TxnType.INCOME -> income += t.amount
        t.type == TxnType.EXPENSE -> expense += t.amount
        // In one account's history, transfers count as money in or out of it.
        accountId != null && t.accountId == accountId -> expense += t.amount
        accountId != null && t.toAccountId == accountId -> income += t.amount
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Total(stringResource(if (accountId != null) R.string.money_in else R.string.income), money(income), AppTheme.money.income)
        Total(stringResource(if (accountId != null) R.string.money_out else R.string.expense), money(expense), AppTheme.money.expense)
        Total(stringResource(R.string.net), money(income - expense, signed = true), if (income >= expense) AppTheme.money.income else AppTheme.money.expense)
    }
}

@Composable
private fun Total(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        actions = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
        },
    )
}
