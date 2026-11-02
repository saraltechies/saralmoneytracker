package com.saraltechies.saralmoneytracker.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
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
import com.saraltechies.saralmoneytracker.data.BillView
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.EmptyState
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import java.time.LocalDate

/** Every bill: today's and this month's sales, what's still to collect, and each bill with what's due on it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    repository: MoneyRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val bills by remember { repository.watch { repository.billViews() } }.collectAsStateWithLifecycle(null)
    var unpaidOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bills_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.bill_new)) },
            )
        },
    ) { padding ->
        val all = bills ?: return@Scaffold
        val today = LocalDate.now()
        val thisMonth = all.filter { it.bill.date.year == today.year && it.bill.date.month == today.month }
        val shown = all.filter { !unpaidOnly || !it.payment.isPaid }.filter {
            val q = query.trim()
            q.isEmpty() || it.bill.number.contains(q, true) || it.customerName.contains(q, true) || it.bill.items.any { i -> i.name.contains(q, true) }
        }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Figure(stringResource(R.string.bills_today), money(all.filter { it.bill.date == today }.sumOf { it.totals.total }), Modifier.weight(1f))
                            Figure(stringResource(R.string.bills_this_month), money(thisMonth.sumOf { it.totals.total }), Modifier.weight(1f))
                        }
                        val due = all.sumOf { it.payment.due }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Figure(stringResource(R.string.bills_to_collect), money(due), Modifier.weight(1f), if (due > 0) AppTheme.money.expense else null)
                            val gst = thisMonth.sumOf { it.totals.tax }
                            if (gst > 0) Figure(stringResource(R.string.bills_gst_this_month), money(gst), Modifier.weight(1f))
                            else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (all.isEmpty()) {
                item { EmptyState(Icons.Filled.Receipt, stringResource(R.string.bills_empty_title), stringResource(R.string.bills_empty_text)) }
                return@LazyColumn
            }
            if (all.size > 5) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(40) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(stringResource(R.string.bills_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !unpaidOnly, onClick = { unpaidOnly = false }, label = { Text(stringResource(R.string.loans_all)) })
                    FilterChip(selected = unpaidOnly, onClick = { unpaidOnly = true }, label = { Text(stringResource(R.string.bills_unpaid)) })
                }
            }
            items(shown, key = { it.bill.id }) { BillRow(it) { onOpen(it.bill.id) } }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
    }
}

@Composable
fun BillRow(view: BillView, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.Receipt, if (view.payment.isPaid) AppTheme.money.income else AppTheme.money.expense)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.bill_title, view.bill.number) + " · " + view.customerName.ifBlank { stringResource(R.string.bill_walk_in) },
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                Dates.short(view.bill.date) + " · " + billStatus(view),
                style = MaterialTheme.typography.bodySmall,
                color = if (view.payment.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else AppTheme.money.expense,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(money(view.totals.total), fontWeight = FontWeight.SemiBold)
    }
}

/** "Paid", "Due ₹500", or "Part paid, due ₹500". */
@Composable
fun billStatus(view: BillView): String = when {
    view.payment.isPaid -> stringResource(R.string.bill_status_paid)
    view.payment.isPartPaid -> stringResource(R.string.bill_status_part, money(view.payment.due))
    else -> stringResource(R.string.bill_status_due, money(view.payment.due))
}
