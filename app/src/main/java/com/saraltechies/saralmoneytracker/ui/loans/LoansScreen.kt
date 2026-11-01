package com.saraltechies.saralmoneytracker.ui.loans

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.data.PersonLoans
import com.saraltechies.saralmoneytracker.model.PledgeTotals
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.EmptyState
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import java.math.BigDecimal
import java.time.LocalDate

/** Everyone you lent money to or borrowed from: what's still to receive and to pay, and what's due. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    repository: MoneyRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: (LoanDirection) -> Unit,
    onOpenPerson: (String) -> Unit = {},
) {
    val items by remember { repository.watch { repository.loanItems() } }.collectAsStateWithLifecycle(null)
    var filter by rememberSaveable { mutableStateOf<LoanDirection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.loans_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(filter ?: LoanDirection.LENT) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_loan)) },
            )
        },
    ) { padding ->
        val all = items ?: return@Scaffold
        val open = all.filter { !it.loan.closed }
        val toReceive = open.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.summary.outstanding }
        val toPay = open.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.summary.outstanding }
        val shown = all.filter { filter == null || it.loan.direction == filter }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            item { LoanTotalsCard(toReceive, toPay, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            val held = PledgeTotals.held(all.filter { it.loan.direction == LoanDirection.LENT }.map { it.loan })
            val pledged = PledgeTotals.held(all.filter { it.loan.direction == LoanDirection.BORROWED }.map { it.loan })
            if (!held.isEmpty || !pledged.isEmpty) {
                item { PledgeTotalsLine(held, pledged, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) }
            }
            if (all.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Handshake,
                        stringResource(R.string.loans_empty_title),
                        stringResource(R.string.loans_empty_text),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onAdd(LoanDirection.LENT) }) { Text(stringResource(R.string.loan_i_gave)) }
                            TextButton(onClick = { onAdd(LoanDirection.BORROWED) }) { Text(stringResource(R.string.loan_i_took)) }
                        }
                    }
                }
                return@LazyColumn
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.loans_all)) })
                    FilterChip(selected = filter == LoanDirection.LENT, onClick = { filter = LoanDirection.LENT }, label = { Text(stringResource(R.string.loans_lent)) })
                    FilterChip(selected = filter == LoanDirection.BORROWED, onClick = { filter = LoanDirection.BORROWED }, label = { Text(stringResource(R.string.loans_borrowed)) })
                }
            }
            // Someone with several loans (each girvi at its own rate, say) is one row that opens their whole account.
            val people = PersonLoans.group(shown)
            val (active, closed) = people.partition { p -> p.loans.any { !it.loan.closed } }
            val byDue = compareBy<PersonLoans>(
                { p -> p.loans.filter { !it.loan.closed }.mapNotNull { it.summary.nextDueDate }.minOrNull() ?: LocalDate.MAX },
                { p -> -(p.toReceive + p.toPay) },
            )
            items(active.sortedWith(byDue), key = { it.key }) { p ->
                if (p.loans.size == 1) LoanRow(p.loans.first(), onClick = { onOpen(p.loans.first().loan.id) })
                else PersonRow(p, onClick = { onOpenPerson(p.key) })
            }
            if (closed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.loans_closed)) }
                items(closed, key = { it.key }) { p ->
                    if (p.loans.size == 1) LoanRow(p.loans.first(), onClick = { onOpen(p.loans.first().loan.id) }, dim = true)
                    else PersonRow(p, onClick = { onOpenPerson(p.key) }, dim = true)
                }
            }
        }
    }
}

@Composable
fun LoanTotalsCard(toReceive: Long, toPay: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.loans_to_receive), style = MaterialTheme.typography.labelLarge)
                Text(money(toReceive), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.money.income, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.loans_to_pay), style = MaterialTheme.typography.labelLarge)
                Text(money(toPay), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.money.expense, maxLines = 1)
            }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { content() }
    else Card(modifier = modifier.fillMaxWidth(), colors = colors) { content() }
}

@Composable
fun LoanRow(item: LoanItem, onClick: () -> Unit, dim: Boolean = false) {
    val s = item.summary
    val lent = item.loan.direction == LoanDirection.LENT
    val color = if (lent) AppTheme.money.income else AppTheme.money.expense
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Handshake, if (dim) MaterialTheme.colorScheme.outline else color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(money(s.outstanding), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else color)
            }
            Text(
                loanStatus(item),
                style = MaterialTheme.typography.bodySmall,
                color = if (s.overdue > 0 && !dim) AppTheme.money.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!dim && s.given > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { s.progress },
                    color = color,
                    trackColor = color.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

/** Someone with several loans: what they owe (or you owe them) in all, how many loans, and the items held. */
@Composable
fun PersonRow(person: PersonLoans, onClick: () -> Unit, dim: Boolean = false) {
    val context = LocalContext.current
    val net = person.toReceive - person.toPay
    val color = when {
        dim -> MaterialTheme.colorScheme.outline
        net >= 0 -> AppTheme.money.income
        else -> AppTheme.money.expense
    }
    val held = person.heldByYou.takeIf { !it.isEmpty } ?: person.pledgedByYou
    val status = listOfNotNull(
        pluralStringResource(R.plurals.person_loans_count, person.loans.size, person.loans.size),
        person.openCount.takeIf { it > 0 && it < person.loans.size }?.let { stringResource(R.string.person_open_count, it) },
        held.takeIf { !it.isEmpty }?.let { Pledges.totals(context, it) },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Groups, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(money(kotlin.math.abs(net)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else color)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Kept with you: Gold 45.5 g · Pledged by you: Silver 200 g". */
@Composable
fun PledgeTotalsLine(held: PledgeTotals, pledged: PledgeTotals, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Diamond, null, Modifier.size(16.dp), tint = pledgeColor(com.saraltechies.saralmoneytracker.model.PledgeKind.GOLD))
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(
                held.takeIf { !it.isEmpty }?.let { stringResource(R.string.pledge_held_lent) + ": " + Pledges.totals(context, it) },
                pledged.takeIf { !it.isEmpty }?.let { stringResource(R.string.pledge_held_borrowed) + ": " + Pledges.totals(context, it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One line about a loan: "Lent · 2% a month · Gold 10 g · Next ₹500 on 5 Dec", "Overdue ₹1,000", "Settled". */
@Composable
fun loanStatus(item: LoanItem, withDirection: Boolean = true): String {
    val s = item.summary
    val loan = item.loan
    val parts = mutableListOf<String>()
    if (withDirection) parts += stringResource(if (loan.direction == LoanDirection.LENT) R.string.loans_lent else R.string.loans_borrowed)
    parts += if (loan.rate > 0) rateText(loan.rate, loan.rateUnit) else stringResource(R.string.loan_no_interest)
    loan.pledge?.takeIf { !it.returned }?.let { parts += Pledges.summary(LocalContext.current, it, withItems = false) }
    when {
        loan.closed -> parts += stringResource(R.string.loan_closed)
        s.settled -> parts += stringResource(R.string.loan_settled)
        s.overdue > 0 -> parts += stringResource(R.string.loan_overdue, money(s.overdue))
        s.nextDueDate != null -> parts += stringResource(R.string.loan_next_due, money(s.nextDueAmount), Dates.short(s.nextDueDate))
        loan.plan == LoanPlan.MONTHLY -> Unit
    }
    return parts.joinToString(" · ")
}

/** "2% a month", "12% a year". */
@Composable
@ReadOnlyComposable
fun rateText(rate: Long, unit: RateUnit): String =
    stringResource(if (unit == RateUnit.MONTH) R.string.rate_per_month else R.string.rate_per_year, ratePlain(rate))

fun ratePlain(rate: Long): String = BigDecimal.valueOf(rate, 2).stripTrailingZeros().toPlainString()

/** Colour for money coming to you from a loan, or going out to one. */
@Composable
@ReadOnlyComposable
fun loanColor(direction: LoanDirection): Color = if (direction == LoanDirection.LENT) AppTheme.money.income else AppTheme.money.expense
