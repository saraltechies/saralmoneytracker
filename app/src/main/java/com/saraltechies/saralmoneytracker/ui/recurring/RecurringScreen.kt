package com.saraltechies.saralmoneytracker.ui.recurring

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.saraltechies.saralmoneytracker.model.Recurrence
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.EmptyState
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.Lookups
import com.saraltechies.saralmoneytracker.ui.components.frequencyLabel
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.components.typeColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Salary, rent, EMIs, SIPs and subscriptions that are added automatically on their dates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(repository: MoneyRepository, onBack: () -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val scope = rememberCoroutineScope()
    val data by remember {
        repository.watch { repository.recurring() to Lookups(repository.categories(true, book = Books.ALL), repository.accounts(true, book = Books.ALL), repository.loanDirections(), repository.books()) }
    }.collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repeating_entries)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_repeating)) })
        },
    ) { padding ->
        val (rules, lookups) = data ?: return@Scaffold
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            if (rules.isEmpty()) {
                item { EmptyState(Icons.Filled.Repeat, stringResource(R.string.repeating_empty_title), stringResource(R.string.repeating_empty_text)) }
            }
            items(rules, key = { it.id }) { r ->
                val cat = r.categoryId?.let { lookups.categories[it] }
                val ended = r.endDate != null && r.nextDate.isAfter(r.endDate)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        if (r.type == TxnType.TRANSFER) CategoryIcons.transfer else CategoryIcons.of(cat?.icon),
                        cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.note.ifBlank { cat?.name ?: stringResource(R.string.type_transfer) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val next = if (!r.active || ended) stringResource(R.string.paused) else stringResource(R.string.next_on, Dates.relative(r.nextDate))
                        Text(
                            stringResource(frequencyLabel(r.frequency)) + " · " + next,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(money(r.amount), color = typeColor(r.type), fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = r.active && !ended,
                        enabled = !ended,
                        onCheckedChange = { on ->
                            scope.launch {
                                // Resuming skips the dates missed while paused instead of adding them all at once.
                                val today = LocalDate.now()
                                val next = if (on && r.nextDate.isBefore(today)) Recurrence.next(r.startDate, r.frequency, today.minusDays(1)) else r.nextDate
                                repository.saveRecurring(r.copy(active = on, nextDate = next))
                                if (on) repository.processRecurring()
                            }
                        },
                    )
                }
            }
        }
    }
}
