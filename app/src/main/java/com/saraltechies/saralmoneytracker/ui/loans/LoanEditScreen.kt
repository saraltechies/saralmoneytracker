package com.saraltechies.saralmoneytracker.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.data.LoanTerms
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanMath
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.ui.components.CategoryIcons
import com.saraltechies.saralmoneytracker.ui.contacts.ContactFields
import com.saraltechies.saralmoneytracker.ui.contacts.ContactSaver
import com.saraltechies.saralmoneytracker.ui.components.ChoiceDialog
import com.saraltechies.saralmoneytracker.ui.components.DatePickDialog
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.PickerRow
import com.saraltechies.saralmoneytracker.ui.components.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Add a loan (money given or taken), or change its terms ([loanId] != 0). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanEditScreen(
    repository: MoneyRepository,
    settings: SettingsRepository,
    loanId: Long,
    initialDirection: LoanDirection,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val accounts by remember { repository.watch { repository.accounts() } }.collectAsStateWithLifecycle(emptyList())
    var existing by remember { mutableStateOf<LoanItem?>(null) }
    var loaded by rememberSaveable { mutableStateOf(loanId == 0L) }
    var direction by rememberSaveable { mutableStateOf(initialDirection) }
    var contact by rememberSaveable(stateSaver = ContactSaver) { mutableStateOf(Contact(name = "")) }
    var pickSaved by remember { mutableStateOf(false) }
    val savedContacts by remember { repository.watch { repository.contacts() } }.collectAsStateWithLifecycle(emptyList())
    var amountText by rememberSaveable { mutableStateOf("") }
    var dateDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    // -1 = not from my accounts (a loan from before), 0 = not chosen yet.
    var accountId by rememberSaveable { mutableStateOf(0L) }
    var withInterest by rememberSaveable { mutableStateOf(false) }
    var rateText by rememberSaveable { mutableStateOf("") }
    var rateUnit by rememberSaveable { mutableStateOf(RateUnit.MONTH) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    var monthsText by rememberSaveable { mutableStateOf("") }
    var installmentText by rememberSaveable { mutableStateOf("") }
    var installmentEdited by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateDay)
    val isNew = loanId == 0L

    LaunchedEffect(Unit) {
        if (isNew) return@LaunchedEffect
        val item = withContext(Dispatchers.IO) { repository.loanItem(loanId) } ?: run { onBack(); return@LaunchedEffect }
        existing = item
        if (loaded) return@LaunchedEffect
        val l = item.loan
        direction = l.direction
        contact = item.contact ?: Contact(name = item.name)
        dateDay = l.startDate.toEpochDay()
        withInterest = l.rate > 0
        rateText = if (l.rate > 0) ratePlain(l.rate) else ""
        rateUnit = l.rateUnit
        monthly = l.plan == LoanPlan.MONTHLY
        monthsText = if (l.installments > 0) l.installments.toString() else ""
        installmentText = if (l.installment > 0) Money.plain(l.installment) else ""
        installmentEdited = l.installment > 0
        note = l.note
        loaded = true
    }
    LaunchedEffect(accounts) {
        if (accountId == 0L && accounts.isNotEmpty()) accountId = accounts.first().id
    }

    val principal = if (isNew) Money.parse(amountText) else existing?.summary?.given
    val rate = if (withInterest) Money.parse(rateText) ?: 0 else 0
    val months = monthsText.toIntOrNull() ?: 0
    val computedInstallment = if (principal != null && months > 0) LoanMath.emi(principal, LoanMath.monthlyRate(rate, rateUnit), months) else 0L
    val installment = if (installmentEdited) Money.parse(installmentText) ?: 0 else computedInstallment
    val lent = direction == LoanDirection.LENT

    fun save() {
        error = when {
            contact.name.isBlank() -> R.string.error_loan_name
            contact.email.isNotBlank() && !Contact.isEmail(contact.email) -> R.string.error_email
            contact.upi.isNotBlank() && !Contact.isUpiId(contact.upi) -> R.string.error_upi
            principal == null || principal <= 0 -> R.string.error_amount
            withInterest && (rate <= 0 || rate > 10_000) -> R.string.error_rate
            monthly && (months !in 1..600) -> R.string.error_months
            monthly && installment <= 0 -> R.string.error_installment
            isNew && accountId == 0L -> R.string.error_account
            else -> null
        }
        if (error != null || principal == null) return
        val terms = LoanTerms(
            name = contact.name.trim(), direction = direction, startDate = date, rate = rate, rateUnit = rateUnit,
            plan = if (monthly) LoanPlan.MONTHLY else LoanPlan.FLEXIBLE,
            installments = if (monthly) months else 0, installment = if (monthly) installment else 0, note = note,
        )
        scope.launch {
            val item = existing
            if (item == null) {
                val id = repository.createLoan(terms, principal, accountId.takeIf { it > 0 }, contact)
                settings.recordGoodMoment()
                onSaved(id)
            } else {
                repository.updateLoan(item.loan, terms, contact)
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.add_loan else R.string.edit_loan)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    val options = listOf(LoanDirection.LENT to R.string.loan_i_gave, LoanDirection.BORROWED to R.string.loan_i_took)
                    options.forEachIndexed { i, (d, label) ->
                        SegmentedButton(
                            selected = direction == d,
                            onClick = { direction = d },
                            enabled = isNew,
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                Text(
                    stringResource(if (lent) R.string.loan_gave_hint else R.string.loan_took_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                OutlinedTextField(
                    value = contact.name, onValueChange = { contact = contact.copy(name = it.take(60)); error = null },
                    label = { Text(stringResource(if (lent) R.string.loan_given_to else R.string.loan_taken_from)) },
                    leadingIcon = { Icon(Icons.Filled.Person, null) },
                    trailingIcon = if (savedContacts.isNotEmpty()) ({
                        IconButton(onClick = { pickSaved = true }) { Icon(Icons.Filled.Contacts, stringResource(R.string.contact_choose_saved)) }
                    }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (isNew) {
                    OutlinedTextField(
                        value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15); error = null },
                        label = { Text(stringResource(R.string.amount)) },
                        prefix = { Text(com.saraltechies.saralmoneytracker.ui.components.LocalCurrency.current.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                PickerRow(Icons.Filled.CalendarToday, stringResource(if (lent) R.string.loan_date_given else R.string.loan_date_taken), Dates.medium(date), onClick = { pickDate = true })
                if (isNew) {
                    val accountName = when (accountId) {
                        -1L -> stringResource(R.string.loan_not_from_accounts)
                        else -> accounts.firstOrNull { it.id == accountId }?.name.orEmpty()
                    }
                    PickerRow(
                        Icons.Filled.AccountBalanceWallet,
                        stringResource(if (lent) R.string.paid_from else R.string.received_in),
                        accountName, onClick = { pickAccount = true },
                    )
                    if (accountId == -1L) {
                        Text(
                            stringResource(R.string.loan_not_from_accounts_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 60.dp, end = 20.dp, bottom = 4.dp),
                        )
                    }
                }

                SectionLabel(stringResource(R.string.loan_interest))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !withInterest, onClick = { withInterest = false }, label = { Text(stringResource(R.string.loan_no_interest)) })
                    FilterChip(selected = withInterest, onClick = { withInterest = true }, label = { Text(stringResource(R.string.loan_with_interest)) })
                }
                if (withInterest) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rateText, onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' }.take(6); error = null },
                            label = { Text(stringResource(R.string.loan_rate)) },
                            suffix = { Text("%") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            FilterChip(selected = rateUnit == RateUnit.MONTH, onClick = { rateUnit = RateUnit.MONTH }, label = { Text(stringResource(R.string.loan_per_month)) })
                            FilterChip(selected = rateUnit == RateUnit.YEAR, onClick = { rateUnit = RateUnit.YEAR }, label = { Text(stringResource(R.string.loan_per_year)) })
                        }
                    }
                    Text(
                        stringResource(R.string.loan_interest_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                SectionLabel(stringResource(R.string.loan_repayment))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !monthly, onClick = { monthly = false }, label = { Text(stringResource(R.string.loan_any_time)) })
                    FilterChip(selected = monthly, onClick = { monthly = true }, label = { Text(stringResource(R.string.loan_monthly)) })
                }
                if (monthly) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = monthsText, onValueChange = { monthsText = it.filter(Char::isDigit).take(3); error = null },
                            label = { Text(stringResource(R.string.loan_months)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = if (installmentEdited) installmentText else if (computedInstallment > 0) Money.plain(computedInstallment) else "",
                            onValueChange = {
                                installmentText = it.filter { c -> c.isDigit() || c == '.' }.take(15)
                                installmentEdited = installmentText.isNotEmpty()
                                error = null
                            },
                            label = { Text(stringResource(R.string.loan_installment)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                    Text(
                        stringResource(if (installmentEdited) R.string.loan_installment_own else R.string.loan_installment_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                if (principal != null && principal > 0) PlanPreview(principal, rate, rateUnit, monthly, months, installment, date, lent)

                SectionLabel(stringResource(R.string.contact_details))
                Text(
                    stringResource(R.string.contact_details_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                )
                ContactFields(contact = contact, onChange = { contact = it; error = null }, showName = false)

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                    placeholder = { Text(stringResource(R.string.note_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            error?.let {
                Text(
                    stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Button(
                onClick = ::save,
                enabled = loaded,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp, top = 4.dp).height(52.dp),
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (pickSaved) {
        ChoiceDialog(
            title = stringResource(R.string.contact_choose_saved),
            options = savedContacts,
            selected = savedContacts.firstOrNull { it.id == contact.id },
            label = { c -> listOf(c.name, c.phone).filter { it.isNotBlank() }.joinToString(" · ") },
            onPick = { contact = it; error = null },
            onDismiss = { pickSaved = false },
        )
    }
    if (pickDate) DatePickDialog(date, onDismiss = { pickDate = false }, onPick = { dateDay = it.toEpochDay() })
    if (pickAccount) {
        val none = Account(id = -1, name = stringResource(R.string.loan_not_from_accounts), type = com.saraltechies.saralmoneytracker.model.AccountType.OTHER)
        val options = accounts + none
        ChoiceDialog(
            title = stringResource(if (lent) R.string.paid_from else R.string.received_in),
            options = options,
            selected = options.firstOrNull { it.id == accountId },
            label = { it.name },
            leading = { IconBadge(if (it.id == -1L) Icons.Filled.Person else CategoryIcons.account(it.type), MaterialTheme.colorScheme.primary, size = 32.dp) },
            onPick = { accountId = it.id },
            onDismiss = { pickAccount = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** What the terms work out to: interest each month, or the instalment plan's total. */
@Composable
private fun PlanPreview(principal: Long, rate: Long, unit: RateUnit, monthly: Boolean, months: Int, installment: Long, start: LocalDate, lent: Boolean) {
    val monthlyRate = LoanMath.monthlyRate(rate, unit)
    val lines = mutableListOf<String>()
    if (monthly && months > 0 && installment > 0) {
        val plan = LoanMath.schedule(principal, monthlyRate, months, installment, start)
        val total = plan.sumOf { it.amount }
        lines += stringResource(R.string.loan_preview_plan, money(installment), plan.size, Dates.short(plan.firstOrNull()?.date ?: start))
        lines += stringResource(if (lent) R.string.loan_preview_total_receive else R.string.loan_preview_total_pay, money(total), money(total - principal))
    } else if (rate > 0) {
        lines += stringResource(R.string.loan_preview_interest, money(Math.round(principal * monthlyRate)), money(Math.round(principal * monthlyRate * 12)))
    } else {
        return
    }
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            lines.forEachIndexed { i, line ->
                Text(line, style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, fontWeight = if (i == 0) FontWeight.SemiBold else null)
            }
        }
    }
}
