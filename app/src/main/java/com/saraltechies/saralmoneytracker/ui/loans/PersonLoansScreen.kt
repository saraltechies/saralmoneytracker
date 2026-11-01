package com.saraltechies.saralmoneytracker.ui.loans

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.PersonLoans
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.ui.components.SectionTitle
import com.saraltechies.saralmoneytracker.ui.components.money
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import com.saraltechies.saralmoneytracker.util.StatementFiles
import com.saraltechies.saralmoneytracker.util.StatementPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One person's account: every loan with them (each girvi or amount at its own rate), added up, with the gold,
 * silver and other items held, and a statement of the whole account to send them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonLoansScreen(
    repository: MoneyRepository,
    personKey: String,
    onBack: () -> Unit,
    onOpenLoan: (Long) -> Unit,
    onAddLoan: (LoanDirection, Long) -> Unit,
    onMyDetails: () -> Unit,
) {
    val context = LocalContext.current
    val person by remember { repository.watch { repository.personLoans(personKey) ?: PersonLoans(personKey, "", null, emptyList()) } }
        .collectAsStateWithLifecycle(null)
    val me by remember { repository.watch { repository.personLoans(personKey)?.loans?.firstOrNull()?.let { repository.myDetails(it.loan.bookId) } } }
        .collectAsStateWithLifecycle(null)
    var statement by rememberSaveable { mutableStateOf(false) }

    // Every loan with them was deleted: nothing left to show.
    LaunchedEffect(person) { if (person?.loans?.isEmpty() == true) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (person?.loans?.isNotEmpty() == true) {
                        IconButton(onClick = { statement = true }) { Icon(Icons.Filled.PictureAsPdf, stringResource(R.string.person_statement)) }
                    }
                },
            )
        },
    ) { padding ->
        val p = person?.takeIf { it.loans.isNotEmpty() } ?: return@Scaffold
        val net = p.toReceive - p.toPay
        val lastDirection = p.loans.maxBy { it.loan.startDate }.loan.direction
        val contactId = p.loans.firstNotNullOfOrNull { it.loan.contactId } ?: 0L
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { AccountCard(p, net) }
            item {
                OutlinedButton(
                    onClick = { onAddLoan(lastDirection, contactId) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.person_add_loan))
                }
            }
            item {
                Text(
                    stringResource(R.string.person_account_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            val (open, closed) = p.loans.sortedBy { it.loan.startDate }.partition { !it.loan.closed }
            items(open, key = { it.loan.id }) { PersonLoanRow(it, onClick = { onOpenLoan(it.loan.id) }) }
            if (closed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.loans_closed)) }
                items(closed, key = { it.loan.id }) { PersonLoanRow(it, onClick = { onOpenLoan(it.loan.id) }, dim = true) }
            }
        }
    }

    val p = person
    if (statement && p != null && p.loans.isNotEmpty()) {
        AccountStatementDialog(p, me, onMyDetails = { statement = false; onMyDetails() }, onDismiss = { statement = false })
    }
}

@Composable
private fun AccountCard(p: PersonLoans, net: Long) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(if (net >= 0) R.string.person_net_receive else R.string.person_net_pay), style = MaterialTheme.typography.labelLarge)
            Text(
                money(kotlin.math.abs(net)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (net >= 0) AppTheme.money.income else AppTheme.money.expense,
            )
            Spacer(Modifier.height(8.dp))
            if (p.toReceive > 0 && p.toPay > 0) {
                Figure(stringResource(R.string.loans_to_receive), money(p.toReceive))
                Figure(stringResource(R.string.loans_to_pay), money(p.toPay))
            }
            Figure(stringResource(R.string.person_open_loans), "${p.openCount} / ${p.loans.size}")
            Figure(stringResource(R.string.loan_principal_left), money(p.principalLeft))
            Figure(stringResource(R.string.loan_interest_due), money(p.interestDue), bold = true)
            if (p.monthlyInterest > 0) Figure(stringResource(R.string.loan_interest_per_month), money(p.monthlyInterest))
            val held = p.heldByYou
            val pledged = p.pledgedByYou
            if (!held.isEmpty || !pledged.isEmpty) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (!held.isEmpty) Figure(stringResource(R.string.pledge_held_lent), Pledges.totals(context, held))
                if (!pledged.isEmpty) Figure(stringResource(R.string.pledge_held_borrowed), Pledges.totals(context, pledged))
                val value = held.value + pledged.value
                if (value > 0) Figure(stringResource(R.string.pledge_value), money(value))
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null)
    }
}

/** A loan inside someone's account: when and how much, its rate and items, and what's left on it. */
@Composable
private fun PersonLoanRow(item: LoanItem, onClick: () -> Unit, dim: Boolean = false) {
    val context = LocalContext.current
    val loan = item.loan
    val lent = loan.direction == LoanDirection.LENT
    val color = if (dim) MaterialTheme.colorScheme.outline else loanColor(loan.direction)
    val pledge = loan.pledge
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pledge != null) IconBadge(Icons.Filled.Diamond, if (dim || pledge.returned) MaterialTheme.colorScheme.outline else pledgeColor(pledge.kind))
        else IconBadge(Icons.Filled.Handshake, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (lent) R.string.person_loan_lent else R.string.person_loan_borrowed, money(item.summary.given), Dates.medium(loan.startDate)),
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(money(item.summary.outstanding), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else color)
            }
            Text(
                loanStatus(item, withDirection = false), style = MaterialTheme.typography.bodySmall,
                color = if (item.summary.overdue > 0 && !dim) AppTheme.money.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (pledge != null && (pledge.items.isNotBlank() || pledge.returned)) {
                Text(
                    listOfNotNull(pledge.items.takeIf { it.isNotBlank() }, pledge.returnedOn?.let { Pledges.status(context, pledge, lent) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Share or save the statement of the whole account as a PDF. */
@Composable
private fun AccountStatementDialog(person: PersonLoans, me: Contact?, onMyDetails: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currency = LocalCurrency.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { StatementPdf(context, currency).writeAccount(person, me, it) } != null }.getOrDefault(false)
            }
            Toast.makeText(context, if (ok) R.string.statement_saved else R.string.statement_failed, Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }

    fun share() {
        busy = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { StatementFiles.createAccount(context, person, me, currency) }.getOrNull() }
            busy = false
            if (file == null) {
                Toast.makeText(context, R.string.statement_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            runCatching { context.startActivity(StatementFiles.shareAccountIntent(context, file, person, me, currency)) }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_statement)) },
        text = {
            Column {
                Text(stringResource(R.string.pdf_as_on, Dates.medium(java.time.LocalDate.now())), style = MaterialTheme.typography.bodyMedium)
                if (me == null || me.name.isBlank()) {
                    Text(
                        stringResource(R.string.statement_add_my_details), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp).clickable(onClick = onMyDetails),
                    )
                }
                person.contact?.email?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        stringResource(R.string.statement_email_hint, it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = ::share, enabled = !busy) { Text(stringResource(R.string.statement_share_pdf)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { save.launch(StatementFiles.accountFileName(context, person)) }, enabled = !busy) { Text(stringResource(R.string.statement_save_pdf)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
