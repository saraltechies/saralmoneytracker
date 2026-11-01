package com.saraltechies.saralmoneytracker.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.AppPrefs
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Periods
import com.saraltechies.saralmoneytracker.model.Recurrence
import com.saraltechies.saralmoneytracker.model.Stats
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.Lookups
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.TxnRow
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.loans.LoanRow
import com.saraltechies.saralmoneytracker.ui.loans.LoanTotalsCard
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import com.saraltechies.saralmoneytracker.ui.theme.BrandBlue
import com.saraltechies.saralmoneytracker.ui.theme.BrandNavy
import kotlinx.coroutines.launch
import java.time.LocalDate

private class HomeData(
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val todayExpense: Long = 0,
    val weekExpense: Long = 0,
    val totalBalance: Long = 0,
    val txnCount: Int = 0,
    val budget: com.saraltechies.saralmoneytracker.model.BudgetSummary? = null,
    val top: List<com.saraltechies.saralmoneytracker.model.CategoryShare> = emptyList(),
    val recent: List<com.saraltechies.saralmoneytracker.model.Txn> = emptyList(),
    val upcoming: List<Pair<LocalDate, com.saraltechies.saralmoneytracker.model.Recurring>> = emptyList(),
    val lookups: Lookups = Lookups(emptyList(), emptyList()),
    val loans: List<com.saraltechies.saralmoneytracker.data.LoanItem> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MoneyRepository,
    settings: SettingsRepository,
    prefs: AppPrefs,
    snackbar: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onAdd: (TxnType) -> Unit,
    onOpenTxn: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onSearch: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenRecurring: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenLoan: (Long) -> Unit,
    updateReady: Boolean,
    onRestartForUpdate: () -> Unit,
    onReviewMoment: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val month = Periods.containing(today, PeriodKind.MONTH, prefs.period)
    val week = Periods.containing(today, PeriodKind.WEEK, prefs.period)
    val data by remember(month, week) {
        repository.watch {
            val from = minOf(month.start, week.start)
            val to = maxOf(month.end, week.end)
            val txns = repository.txns(from, to)
            val monthTxns = txns.filter { it.date in month }
            val totals = Stats.totals(monthTxns)
            val categories = repository.categories(includeArchived = true)
            val accounts = repository.accounts(includeArchived = true)
            val balances = repository.balances()
            HomeData(
                monthIncome = totals.income,
                monthExpense = totals.expense,
                todayExpense = Stats.totals(txns.filter { it.date == today }).expense,
                weekExpense = Stats.totals(txns.filter { it.date in week }).expense,
                totalBalance = accounts.filter { !it.archived && it.type != com.saraltechies.saralmoneytracker.model.AccountType.LOAN }.sumOf { balances[it.id] ?: 0 },
                txnCount = repository.txnCount(),
                budget = Stats.budget(categories.filter { !it.archived }, monthTxns, month, today),
                top = Stats.byCategory(monthTxns, TxnType.EXPENSE).take(4),
                recent = repository.txns(limit = 6),
                upcoming = repository.recurring()
                    .flatMap { r -> Recurrence.upcoming(r, today, 7).map { it to r } }
                    .sortedBy { it.first }.take(5),
                lookups = Lookups(categories, accounts, repository.loanDirections()),
                loans = repository.loanItems(today).filter { !it.loan.closed },
                loaded = true,
            )
        }
    }.collectAsStateWithLifecycle(HomeData())

    LaunchedEffect(Unit) {
        if (settings.takeReviewDue()) onReviewMoment()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenLoans) { Icon(Icons.Filled.Handshake, stringResource(R.string.loans_title)) }
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(TxnType.EXPENSE) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_entry)) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            if (updateReady) item { UpdateBanner(onRestartForUpdate) }
            if (data.loaded && data.txnCount == 0 && !prefs.welcomeDismissed) {
                item {
                    WelcomeCard(
                        onAccounts = onOpenAccounts,
                        onImport = onImport,
                        onDismiss = { scope.launch { settings.setWelcomeDismissed() } },
                    )
                }
            }
            item {
                SummaryCard(
                    title = Dates.period(month),
                    income = data.monthIncome,
                    expense = data.monthExpense,
                    today = data.todayExpense,
                    week = data.weekExpense,
                    balance = data.totalBalance,
                    onClick = onOpenStats,
                    onBalanceClick = onOpenAccounts,
                )
            }
            item { BudgetCard(data.budget, onOpenBudgets) }

            if (data.loans.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.loans_title)) { TextButton(onClick = onOpenLoans) { Text(stringResource(R.string.see_all)) } }
                }
                item {
                    LoanTotalsCard(
                        toReceive = data.loans.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.summary.outstanding },
                        toPay = data.loans.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.summary.outstanding },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = onOpenLoans,
                    )
                }
                // Payments that are late or due this week.
                val soon = today.plusDays(7)
                val due = data.loans.filter { it.summary.overdue > 0 || it.summary.nextDueDate?.let { d -> !d.isAfter(soon) } == true }
                    .sortedBy { it.summary.nextDueDate ?: today }.take(3)
                items(due, key = { "loan:${it.loan.id}" }) { LoanRow(it, onClick = { onOpenLoan(it.loan.id) }) }
            }

            if (data.top.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.top_spending)) { TextButton(onClick = onOpenStats) { Text(stringResource(R.string.see_details)) } } }
                items(data.top, key = { "top:${it.categoryId}" }) { share ->
                    val cat = share.categoryId?.let { data.lookups.categories[it] }
                    TopCategoryRow(
                        name = cat?.name ?: stringResource(R.string.uncategorized),
                        icon = CategoryIcons.of(cat?.icon),
                        color = cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline,
                        amount = share.amount,
                        fraction = share.fraction,
                        onClick = onOpenStats,
                    )
                }
            }

            if (data.upcoming.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.coming_up)) }
                items(data.upcoming, key = { "up:${it.second.id}:${it.first}" }) { (date, rule) ->
                    val cat = rule.categoryId?.let { data.lookups.categories[it] }
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenRecurring(rule.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            if (rule.type == TxnType.TRANSFER) CategoryIcons.transfer else CategoryIcons.of(cat?.icon),
                            cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, size = 36.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rule.note.ifBlank { cat?.name ?: stringResource(R.string.type_transfer) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Dates.relative(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(rule.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                SectionTitle(stringResource(R.string.recent)) {
                    if (data.recent.isNotEmpty()) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
                }
            }
            if (data.loaded && data.recent.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_entries_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(data.recent, key = { "recent:${it.id}" }) { txn ->
                TxnRow(txn, data.lookups, onClick = { onOpenTxn(txn.id) }, showDate = true)
            }
            if (data.txnCount > 0) {
                item {
                    Text(
                        pluralStringResource(R.plurals.entries_total, data.txnCount, data.txnCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    income: Long,
    expense: Long,
    today: Long,
    week: Long,
    balance: Long,
    onClick: () -> Unit,
    onBalanceClick: () -> Unit,
) {
    val net = income - expense
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(BrandBlue, BrandNavy)))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.spent), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        Text(money(expense), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStat(stringResource(R.string.income), money(income), Modifier.weight(1f))
            HeroStat(stringResource(R.string.saved), money(net, signed = true), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStat(stringResource(R.string.spent_today), money(today), Modifier.weight(1f))
            HeroStat(stringResource(R.string.spent_this_week), money(week), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onBalanceClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.total_balance), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(money(balance), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun BudgetCard(budget: com.saraltechies.saralmoneytracker.model.BudgetSummary?, onClick: () -> Unit) {
    budget ?: return
    Card(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (budget.budget <= 0) {
                Text(stringResource(R.string.budget_none_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.budget_none_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val over = budget.left < 0
            val barColor = when {
                over -> AppTheme.money.expense
                budget.fraction >= 0.8f -> AppTheme.money.warning
                else -> MaterialTheme.colorScheme.primary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.monthly_budget), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(money(budget.spent) + " / " + money(budget.budget), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { budget.fraction.coerceIn(0f, 1f) },
                color = barColor,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            val text = when {
                over -> stringResource(R.string.budget_over, money(-budget.left))
                budget.safePerDay != null -> pluralStringResource(R.plurals.budget_safe_per_day, budget.daysLeft, money(budget.left), money(budget.safePerDay), budget.daysLeft)
                else -> stringResource(R.string.budget_left, money(budget.left))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (over) AppTheme.money.expense else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopCategoryRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, amount: Long, fraction: Float, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(money(amount), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                color = color,
                trackColor = color.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun WelcomeCard(onAccounts: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.dismiss)) }
            }
            Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAccounts) { Text(stringResource(R.string.welcome_balances)) }
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.welcome_import)) }
            }
        }
    }
}

@Composable
private fun UpdateBanner(onRestart: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, null, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.update_ready), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRestart) { Text(stringResource(R.string.restart)) }
        }
    }
}
