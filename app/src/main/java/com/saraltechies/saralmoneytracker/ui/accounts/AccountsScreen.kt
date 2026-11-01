package com.saraltechies.saralmoneytracker.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.accountTypeLabel
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Cash, bank accounts, cards and wallets with their balances, and the total (net worth). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    repository: MoneyRepository,
    snackbar: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onTransfer: () -> Unit,
    onOpenLoans: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val data by remember { repository.watch { Triple(repository.accounts(includeArchived = true, includeLoans = true), repository.balances(), repository.loanItems()) } }
        .collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_accounts)) },
                actions = { IconButton(onClick = onTransfer) { Icon(Icons.Filled.SwapHoriz, stringResource(R.string.new_transfer)) } },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Account(name = "", type = AccountType.BANK) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_account)) },
            )
        },
    ) { padding ->
        val (allAccounts, balances, loans) = data ?: return@Scaffold
        // Loans have their own screen; open ones still count in net worth (money owed to you, or by you).
        val accounts = allAccounts.filter { it.type != AccountType.LOAN }
        val openLoanAccounts = loans.filter { !it.loan.closed }.map { it.loan.accountId }.toSet()
        val loanBalances = allAccounts.filter { it.id in openLoanAccounts }.map { balances[it.id] ?: 0 }
        val active = accounts.filter { !it.archived }
        val hidden = accounts.filter { it.archived }
        val assets = active.sumOf { (balances[it.id] ?: 0).coerceAtLeast(0) } + loanBalances.sumOf { it.coerceAtLeast(0) }
        val owed = active.sumOf { (balances[it.id] ?: 0).coerceAtMost(0) } + loanBalances.sumOf { it.coerceAtMost(0) }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.net_worth), style = MaterialTheme.typography.labelLarge)
                        Text(money(assets + owed), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text(stringResource(R.string.you_have), style = MaterialTheme.typography.labelMedium)
                                Text(money(assets), color = AppTheme.money.income, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text(stringResource(R.string.you_owe), style = MaterialTheme.typography.labelMedium)
                                Text(money(-owed), color = AppTheme.money.expense, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.accounts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(active, key = { it.id }) { a -> AccountRow(a, balances[a.id] ?: 0, onClick = { onOpenAccount(a.id) }, onEdit = { editing = a }) }
            item {
                val open = loans.filter { !it.loan.closed }
                val toReceive = open.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.summary.outstanding }
                val toPay = open.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.summary.outstanding }
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenLoans).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Filled.Handshake, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.loans_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (open.isEmpty()) stringResource(R.string.loans_row_empty)
                            else stringResource(R.string.loans_row_summary, money(toReceive), money(toPay)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hidden.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.hidden_accounts)) }
                items(hidden, key = { it.id }) { a ->
                    AccountRow(a, balances[a.id] ?: 0, onClick = { onOpenAccount(a.id) }, onEdit = { editing = a }, modifier = Modifier.alpha(0.6f))
                }
            }
        }
    }

    editing?.let { account ->
        AccountDialog(
            account = account,
            onDismiss = { editing = null },
            onSave = { scope.launch { repository.saveAccount(it) } },
            onRemove = {
                scope.launch {
                    val hidden = repository.removeAccount(account.id)
                    snackbar.showSnackbar(context.getString(if (hidden) R.string.account_hidden else R.string.account_deleted))
                }
            },
        )
    }
}

@Composable
private fun AccountRow(account: Account, balance: Long, onClick: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(CategoryIcons.account(account.type), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(accountTypeLabel(account.type)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(money(balance), fontWeight = FontWeight.SemiBold, color = if (balance < 0) AppTheme.money.expense else MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountDialog(account: Account, onDismiss: () -> Unit, onSave: (Account) -> Unit, onRemove: () -> Unit) {
    val currency = LocalCurrency.current
    var name by remember { mutableStateOf(account.name) }
    var type by remember { mutableStateOf(account.type) }
    var opening by remember { mutableStateOf(if (account.opening == 0L) "" else Money.plain(account.opening)) }
    val openingValue = if (opening.isBlank()) 0L else Money.parse(opening)
    val isNew = account.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.add_account else R.string.edit_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.filter { it != AccountType.LOAN }.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(accountTypeLabel(t))) })
                    }
                }
                OutlinedTextField(
                    value = opening,
                    onValueChange = { v -> opening = v.filterIndexed { i, c -> c.isDigit() || c == '.' || (c == '-' && i == 0) }.take(14) },
                    prefix = { Text(currency.symbol) },
                    label = { Text(stringResource(R.string.opening_balance)) },
                    supportingText = { Text(stringResource(if (type == AccountType.CARD) R.string.opening_balance_card_hint else R.string.opening_balance_hint)) },
                    isError = openingValue == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                if (account.archived) {
                    TextButton(onClick = { onSave(account.copy(archived = false)); onDismiss() }) { Text(stringResource(R.string.show_again)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && openingValue != null,
                onClick = { onSave(account.copy(name = name.trim(), type = type, opening = openingValue ?: 0)); onDismiss() },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (!isNew && !account.archived) TextButton(onClick = { onRemove(); onDismiss() }) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
