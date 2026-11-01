package com.saraltechies.saralmoneytracker.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.saraltechies.saralmoneytracker.data.PartyItem
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.EmptyState
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme

/** Customers who buy on credit and suppliers you buy from on credit, with what each owes or is owed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    repository: MoneyRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: (PartyRole) -> Unit,
) {
    val items by remember { repository.watch { repository.partyItems() } }.collectAsStateWithLifecycle(null)
    var role by rememberSaveable { mutableStateOf(PartyRole.CUSTOMER) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ledger_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(role) },
                icon = { Icon(Icons.Filled.PersonAdd, null) },
                text = { Text(stringResource(if (role == PartyRole.CUSTOMER) R.string.ledger_add_customer else R.string.ledger_add_supplier)) },
            )
        },
    ) { padding ->
        val all = items ?: return@Scaffold
        val toGet = all.sumOf { it.balance.coerceAtLeast(0) }
        val toGive = -all.sumOf { it.balance.coerceAtMost(0) }
        val shown = all.filter { it.party.role == role && (query.isBlank() || it.name.contains(query.trim(), true) || it.contact?.phone?.contains(query.trim()) == true) }
            .sortedWith(compareByDescending<PartyItem> { kotlin.math.abs(it.balance) }.thenBy { it.name.lowercase() })
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ledger_you_will_get), style = MaterialTheme.typography.labelLarge)
                            Text(money(toGet), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.money.income, maxLines = 1)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ledger_you_will_give), style = MaterialTheme.typography.labelLarge)
                            Text(money(toGive), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.money.expense, maxLines = 1)
                        }
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    listOf(PartyRole.CUSTOMER to R.string.ledger_customers, PartyRole.SUPPLIER to R.string.ledger_suppliers).forEachIndexed { i, (r, label) ->
                        SegmentedButton(selected = role == r, onClick = { role = r }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                            Text(stringResource(label) + " (" + all.count { it.party.role == r } + ")")
                        }
                    }
                }
            }
            if (all.count { it.party.role == role } > 5) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(40) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(stringResource(R.string.ledger_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (all.none { it.party.role == role }) {
                item {
                    EmptyState(
                        Icons.Filled.Store,
                        stringResource(if (role == PartyRole.CUSTOMER) R.string.ledger_empty_customers else R.string.ledger_empty_suppliers),
                        stringResource(R.string.ledger_empty_text),
                    )
                }
            }
            items(shown, key = { it.party.id }) { PartyRow(it) { onOpen(it.party.id) } }
        }
    }
}

@Composable
fun PartyRow(item: PartyItem, onClick: () -> Unit) {
    val b = item.balance
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(item.name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.lastDate?.let { Dates.relative(it) } ?: stringResource(R.string.ledger_no_entries),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                money(kotlin.math.abs(b)), fontWeight = FontWeight.SemiBold,
                color = when {
                    b > 0 -> AppTheme.money.income
                    b < 0 -> AppTheme.money.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                stringResource(
                    when {
                        b > 0 -> R.string.ledger_you_will_get_short
                        b < 0 -> R.string.ledger_you_will_give_short
                        else -> R.string.ledger_settled
                    },
                ),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
