package com.saraltechies.saralmoneytracker.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Periods
import com.saraltechies.saralmoneytracker.model.Stats
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.BarChart
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.DonutChart
import com.saraltechies.saralmoneytracker.ui.components.EmptyState
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalPeriodPrefs
import com.saraltechies.saralmoneytracker.ui.components.Lookups
import com.saraltechies.saralmoneytracker.ui.components.PeriodBar
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.Slice
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.components.moneyCompact
import com.saraltechies.saralmoneytracker.ui.components.rememberPeriod
import com.saraltechies.saralmoneytracker.ui.components.typeColor
import com.saraltechies.saralmoneytracker.ui.components.typeLabel
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private class StatsData(val txns: List<Txn>, val previous: List<Txn>, val lookups: Lookups)

/** Where the money went (or came from): a donut by category, the trend over the period, and comparisons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    repository: MoneyRepository,
    bottomBar: @Composable () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onMyDetails: () -> Unit,
) {
    val prefs = LocalPeriodPrefs.current
    var period by rememberPeriod(PeriodKind.MONTH)
    var type by rememberSaveable { mutableStateOf(TxnType.EXPENSE) }
    var report by rememberSaveable { mutableStateOf(false) }
    val data by remember(period) {
        repository.watch {
            val previous = Periods.shift(period, -1, prefs)
            StatsData(
                repository.txns(period.start, period.end, profitAndLoss = true),
                repository.txns(previous.start, previous.end, profitAndLoss = true),
                Lookups(repository.categories(true, book = Books.ALL), repository.accounts(true, book = Books.ALL), repository.loanDirections(), repository.books()),
            )
        }
    }.collectAsStateWithLifecycle(null)

    if (report) ReportDialog(repository, period, onMyDetails = { report = false; onMyDetails() }, onDismiss = { report = false })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_stats)) },
                actions = { IconButton(onClick = { report = true }) { Icon(Icons.Filled.PictureAsPdf, stringResource(R.string.report_action)) } },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { PeriodBar(period, onChange = { period = it }) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    listOf(TxnType.EXPENSE, TxnType.INCOME).forEachIndexed { i, t ->
                        SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                            Text(stringResource(typeLabel(t)))
                        }
                    }
                }
            }
            val d = data ?: return@LazyColumn
            val totals = Stats.totals(d.txns)
            val total = if (type == TxnType.EXPENSE) totals.expense else totals.income
            val previousTotals = Stats.totals(d.previous)
            val previous = if (type == TxnType.EXPENSE) previousTotals.expense else previousTotals.income
            val shares = Stats.byCategory(d.txns, type)

            item { OverviewCard(totals.income, totals.expense) }

            if (total == 0L) {
                item {
                    EmptyState(
                        Icons.Filled.PieChart,
                        stringResource(if (type == TxnType.EXPENSE) R.string.stats_empty_expense else R.string.stats_empty_income),
                        stringResource(R.string.stats_empty_text),
                    )
                }
                return@LazyColumn
            }

            item {
                val today = LocalDate.now()
                val elapsed = when {
                    today.isBefore(period.start) -> period.days
                    today.isAfter(period.end) -> period.days
                    else -> (ChronoUnit.DAYS.between(period.start, today) + 1).toInt()
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Fact(stringResource(R.string.stats_total), money(total), Modifier.weight(1f))
                    if (period.days > 1) Fact(stringResource(R.string.stats_per_day), money(total / elapsed.coerceAtLeast(1)), Modifier.weight(1f))
                    if (previous > 0) {
                        val change = ((total - previous) * 100.0 / previous).roundToInt()
                        val good = if (type == TxnType.EXPENSE) change <= 0 else change >= 0
                        Fact(
                            stringResource(R.string.stats_vs_previous),
                            (if (change > 0) "+" else "") + "$change%",
                            Modifier.weight(1f),
                            color = if (good) AppTheme.money.income else AppTheme.money.expense,
                        )
                    }
                }
            }

            item {
                val slices = shares.map { s ->
                    Slice(s.categoryId?.let { d.lookups.categories[it] }?.let { Color(it.color) } ?: Color.Gray, s.fraction)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    DonutChart(
                        slices = slices,
                        centerTitle = stringResource(typeLabel(type)),
                        centerValue = moneyCompact(total),
                        description = stringResource(R.string.chart_by_category),
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.by_category)) }
            items(shares, key = { "share:${it.categoryId}" }) { s ->
                val cat = s.categoryId?.let { d.lookups.categories[it] }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = cat != null) { cat?.let { onOpenCategory(it.id) } }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(CategoryIcons.of(cat?.icon), cat?.let { Color(it.color) } ?: Color.Gray, size = 36.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat?.name ?: stringResource(R.string.uncategorized), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${(s.fraction * 100).roundToInt()}% · " + androidx.compose.ui.res.pluralStringResource(R.plurals.entries, s.count, s.count),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(money(s.amount), fontWeight = FontWeight.SemiBold)
                }
            }

            if (period.days > 1) {
                item {
                    val series = Stats.series(d.txns, period, type)
                    val fmt = DateTimeFormatter.ofPattern(if (series.monthly) "MMM" else "d MMM")
                    val today = LocalDate.now()
                    val highlight = series.buckets.indexOfFirst { if (series.monthly) it.year == today.year && it.month == today.month else it == today }
                    SectionTitle(stringResource(R.string.trend))
                    BarChart(
                        values = series.values,
                        labels = series.buckets.map { it.format(fmt) },
                        color = typeColor(type),
                        highlight = highlight,
                        maxLabel = moneyCompact(series.values.maxOrNull() ?: 0),
                        description = stringResource(R.string.chart_trend),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(income: Long, expense: Long) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Fact(stringResource(R.string.income), money(income), color = AppTheme.money.income)
            Fact(stringResource(R.string.expense), money(expense), color = AppTheme.money.expense)
            Fact(stringResource(R.string.saved), money(income - expense, signed = true))
        }
        if (income > 0) {
            val rate = ((income - expense) * 100.0 / income).roundToInt()
            Text(
                stringResource(R.string.savings_rate, rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}
