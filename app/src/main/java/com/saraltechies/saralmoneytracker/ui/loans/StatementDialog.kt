package com.saraltechies.saralmoneytracker.ui.loans

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.Statements
import com.saraltechies.saralmoneytracker.ui.components.DateRangeDialog
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.model.Period
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.util.StatementFiles
import com.saraltechies.saralmoneytracker.util.StatementPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class StatementRange { ALL, THIS_FY, LAST_FY, CUSTOM }

/**
 * Choose what goes into a loan statement, then share it as a PDF (WhatsApp, email with their address filled in),
 * save it to the phone, or send a plain-text summary.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatementDialog(
    item: LoanItem,
    me: Contact?,
    accountNames: Map<Long, String>,
    onMyDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currency = LocalCurrency.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val loan = item.loan
    var range by rememberSaveable { mutableStateOf(StatementRange.ALL) }
    var customFrom by rememberSaveable { mutableStateOf(loan.startDate.toEpochDay()) }
    var customTo by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var pickRange by remember { mutableStateOf(false) }
    val monthly = loan.plan == LoanPlan.MONTHLY && loan.installments > 0
    var includePlan by rememberSaveable { mutableStateOf(monthly) }
    val canQr = loan.direction == LoanDirection.LENT && currency.code == "INR" && me?.upi?.isNotBlank() == true
    var includeQr by rememberSaveable { mutableStateOf(canQr) }
    var busy by remember { mutableStateOf(false) }

    val (from, to) = when (range) {
        StatementRange.ALL -> loan.startDate to today
        StatementRange.THIS_FY -> Statements.financialYear(today).let { (a, b) -> a to minOf(b, today) }
        StatementRange.LAST_FY -> Statements.financialYear(today.minusYears(1))
        StatementRange.CUSTOM -> LocalDate.ofEpochDay(customFrom) to LocalDate.ofEpochDay(customTo)
    }
    fun data() = Statements.build(loan, item.events, from, to, accountNames)
    val options = StatementPdf.Options(includePlan = includePlan && monthly, includeQr = includeQr && canQr)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { StatementPdf(context, currency).write(item, me, data(), options, it) } != null
                }.getOrDefault(false)
            }
            Toast.makeText(context, if (ok) R.string.statement_saved else R.string.statement_failed, Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }

    fun share() {
        busy = true
        scope.launch {
            val d = data()
            val file = withContext(Dispatchers.IO) { runCatching { StatementFiles.create(context, item, me, d, options, currency) }.getOrNull() }
            busy = false
            if (file == null) {
                Toast.makeText(context, R.string.statement_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            runCatching { context.startActivity(StatementFiles.shareIntent(context, file, item, me, d, currency)) }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statement_title_dialog)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (me == null || me.name.isBlank()) {
                    Card(
                        onClick = onMyDetails,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.statement_add_my_details), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(stringResource(R.string.statement_period), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = range == StatementRange.ALL, onClick = { range = StatementRange.ALL }, label = { Text(stringResource(R.string.statement_all)) })
                    FilterChip(selected = range == StatementRange.THIS_FY, onClick = { range = StatementRange.THIS_FY }, label = { Text(stringResource(R.string.statement_this_fy)) })
                    FilterChip(selected = range == StatementRange.LAST_FY, onClick = { range = StatementRange.LAST_FY }, label = { Text(stringResource(R.string.statement_last_fy)) })
                    FilterChip(selected = range == StatementRange.CUSTOM, onClick = { pickRange = true }, label = { Text(stringResource(R.string.period_custom)) })
                }
                Text(
                    Dates.medium(from) + " – " + Dates.medium(to), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
                )
                if (monthly) Toggle(stringResource(R.string.statement_include_plan), includePlan) { includePlan = it }
                if (loan.direction == LoanDirection.LENT) {
                    Toggle(stringResource(R.string.statement_include_qr), includeQr && canQr, enabled = canQr) { includeQr = it }
                    if (!canQr) {
                        Text(
                            stringResource(if (currency.code != "INR") R.string.statement_qr_inr_only else R.string.statement_qr_needs_upi),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(enabled = currency.code == "INR", onClick = onMyDetails),
                        )
                    }
                }
                item.contact?.email?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        stringResource(R.string.statement_email_hint, it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(
                    onClick = {
                        val text = LoanStatement.text(context, item, currency)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.TextSnippet, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.statement_share_text))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::share, enabled = !busy) { Text(stringResource(R.string.statement_share_pdf)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { save.launch(StatementFiles.fileName(context, item)) }, enabled = !busy) { Text(stringResource(R.string.statement_save_pdf)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickRange) {
        DateRangeDialog(
            initial = Period(PeriodKind.CUSTOM, LocalDate.ofEpochDay(customFrom), LocalDate.ofEpochDay(customTo)),
            onDismiss = { pickRange = false },
            onPick = { a, b ->
                customFrom = a.toEpochDay()
                customTo = b.toEpochDay()
                range = StatementRange.CUSTOM
            },
        )
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
