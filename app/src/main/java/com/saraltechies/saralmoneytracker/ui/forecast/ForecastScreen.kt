package com.saraltechies.saralmoneytracker.ui.forecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.ForecastItem
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Periods
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.EmptyState
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalPeriodPrefs
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import java.time.LocalDate

private enum class Horizon { MONTH_END, DAYS_30, DAYS_90 }

/**
 * Where the money of the book on screen is heading: today's balance plus repeating income and bills, loan EMIs and
 * interest, and customers' and suppliers' bills as they fall due, with the balance after each.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(repository: MoneyRepository, onBack: () -> Unit) {
    val prefs = LocalPeriodPrefs.current
    val today = LocalDate.now()
    var horizon by rememberSaveable { mutableStateOf(Horizon.MONTH_END) }
    val to = when (horizon) {
        Horizon.MONTH_END -> Periods.containing(today, PeriodKind.MONTH, prefs).end
        Horizon.DAYS_30 -> today.plusDays(30)
        Horizon.DAYS_90 -> today.plusDays(90)
    }
    val forecast by remember(to) { repository.watch { repository.forecast(to) } }.collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forecast_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val f = forecast ?: return@Scaffold
        val running = f.running()
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = horizon == Horizon.MONTH_END, onClick = { horizon = Horizon.MONTH_END }, label = { Text(stringResource(R.string.forecast_month_end)) })
                    FilterChip(selected = horizon == Horizon.DAYS_30, onClick = { horizon = Horizon.DAYS_30 }, label = { Text(stringResource(R.string.forecast_days, 30)) })
                    FilterChip(selected = horizon == Horizon.DAYS_90, onClick = { horizon = Horizon.DAYS_90 }, label = { Text(stringResource(R.string.forecast_days, 90)) })
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.forecast_expected_on, Dates.medium(f.to)), style = MaterialTheme.typography.labelLarge)
                        Text(
                            money(f.endBalance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = if (f.endBalance < 0) AppTheme.money.expense else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Figure(stringResource(R.string.forecast_now), money(f.startBalance))
                            Figure(stringResource(R.string.forecast_in), money(f.moneyIn), AppTheme.money.income)
                            Figure(stringResource(R.string.forecast_out), money(f.moneyOut), AppTheme.money.expense)
                        }
                        val (lowDate, low) = f.lowest
                        if (low < f.startBalance && low < f.endBalance) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(if (low < 0) R.string.forecast_low_negative else R.string.forecast_low, money(low), Dates.relative(lowDate)),
                                style = MaterialTheme.typography.bodySmall, color = if (low < 0) AppTheme.money.expense else MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = if (low < 0) FontWeight.SemiBold else null,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.forecast_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (running.isEmpty()) {
                item { EmptyState(Icons.AutoMirrored.Filled.TrendingUp, stringResource(R.string.forecast_empty_title), stringResource(R.string.forecast_empty_text)) }
            }
            itemsIndexed(running, key = { i, _ -> i }) { _, (item, balance) -> ForecastRow(item, balance) }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ForecastRow(item: ForecastItem, balance: Long) {
    val incoming = item.amount > 0
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            when (item.kind) {
                ForecastItem.Kind.REPEATING -> Icons.Filled.Repeat
                ForecastItem.Kind.LOAN -> Icons.Filled.Handshake
                ForecastItem.Kind.LEDGER -> Icons.Filled.Storefront
            },
            if (incoming) AppTheme.money.income else AppTheme.money.expense, size = 36.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Dates.relative(item.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(item.amount, signed = true), fontWeight = FontWeight.SemiBold, color = if (incoming) AppTheme.money.income else AppTheme.money.expense)
            Text(money(balance), style = MaterialTheme.typography.bodySmall, color = if (balance < 0) AppTheme.money.expense else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
