package com.saraltechies.saralmoneytracker.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Periods
import com.saraltechies.saralmoneytracker.model.Stats
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.ui.components.LocalPeriodPrefs
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.components.rememberPeriod
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Monthly budget per expense category, with how much is left and a safe daily amount for the rest of the month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(repository: MoneyRepository, bottomBar: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = LocalPeriodPrefs.current
    var period by rememberPeriod(PeriodKind.MONTH)
    val data by remember(period) {
        repository.watch { repository.categories() to repository.txns(period.start, period.end) }
    }.collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_budgets)) }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { period = Periods.shift(period, -1, prefs) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_period))
                    }
                    Text(
                        Dates.period(period), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { period = Periods.shift(period, 1, prefs) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_period))
                    }
                }
            }
            val (categories, txns) = data ?: return@LazyColumn
            val summary = Stats.budget(categories, txns, period, LocalDate.now())
            item { SummaryCard(summary) }

            val expenseCats = categories.filter { it.kind == TxnType.EXPENSE }
            val spentBy = spentByCategory(txns)
            val withBudget = summary.lines
            val without = expenseCats.filter { (it.budget ?: 0) <= 0 }

            if (withBudget.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.budgeted)) }
                items(withBudget, key = { "b:${it.category.id}" }) { line ->
                    BudgetRow(line.category, line.spent, line.budget) { editing = line.category }
                }
            }
            item { SectionTitle(stringResource(if (withBudget.isEmpty()) R.string.set_budgets else R.string.not_budgeted)) }
            if (withBudget.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.budget_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            items(without, key = { "n:${it.id}" }) { c -> BudgetRow(c, spentBy[c.id] ?: 0, null) { editing = c } }
        }
    }

    editing?.let { category ->
        BudgetDialog(
            category = category,
            onDismiss = { editing = null },
            onSave = { amount -> scope.launch { repository.setBudget(category.id, amount) } },
        )
    }
}

private fun spentByCategory(txns: List<Txn>): Map<Long?, Long> {
    val map = HashMap<Long?, Long>()
    for (t in txns) if (t.type == TxnType.EXPENSE) map.merge(t.categoryId, t.amount, Long::plus)
    return map
}

@Composable
private fun SummaryCard(summary: com.saraltechies.saralmoneytracker.model.BudgetSummary) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.monthly_budget), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(summary.budget), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val over = summary.budget > 0 && summary.left < 0
            LinearProgressIndicator(
                progress = { summary.fraction.coerceIn(0f, 1f) },
                color = when {
                    over -> AppTheme.money.expense
                    summary.fraction >= 0.8f -> AppTheme.money.warning
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.budget_spent_of, money(summary.spent)), style = MaterialTheme.typography.bodyMedium)
            if (summary.budget > 0) {
                val text = when {
                    over -> stringResource(R.string.budget_over, money(-summary.left))
                    summary.safePerDay != null -> pluralStringResource(
                        R.plurals.budget_safe_per_day, summary.daysLeft, money(summary.left), money(summary.safePerDay), summary.daysLeft,
                    )
                    else -> stringResource(R.string.budget_left, money(summary.left))
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = if (over) AppTheme.money.expense else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BudgetRow(category: Category, spent: Long, budget: Long?, onClick: () -> Unit) {
    val color = Color(category.color)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(CategoryIcons.of(category.icon), color, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (budget != null) Text(money(spent) + " / " + money(budget), style = MaterialTheme.typography.bodyMedium)
                else Text(stringResource(R.string.set_budget), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (budget != null) {
                val fraction = spent.toFloat() / budget
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    color = when {
                        fraction > 1f -> AppTheme.money.expense
                        fraction >= 0.8f -> AppTheme.money.warning
                        else -> color
                    },
                    trackColor = color.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(2.dp))
                val left = budget - spent
                Text(
                    if (left >= 0) stringResource(R.string.budget_left, money(left)) else stringResource(R.string.budget_over, money(-left)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (left < 0) AppTheme.money.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (spent > 0) {
                Text(stringResource(R.string.spent_amount, money(spent)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BudgetDialog(category: Category, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    val currency = LocalCurrency.current
    var text by remember { mutableStateOf(category.budget?.let(Money::plain).orEmpty()) }
    val parsed = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_for, category.name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(12) },
                    prefix = { Text(currency.symbol) },
                    label = { Text(stringResource(R.string.per_month)) },
                    singleLine = true,
                    isError = text.isNotEmpty() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isEmpty() || parsed != null, onClick = { onSave(parsed); onDismiss() }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (category.budget != null) TextButton(onClick = { onSave(null); onDismiss() }) { Text(stringResource(R.string.remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
