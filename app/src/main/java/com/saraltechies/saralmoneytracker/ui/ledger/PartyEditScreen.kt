package com.saraltechies.saralmoneytracker.ui.ledger

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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.PartyItem
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.ui.components.BookChips
import com.saraltechies.saralmoneytracker.ui.components.LocalBooks
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.ui.contacts.ContactFields
import com.saraltechies.saralmoneytracker.ui.contacts.ContactSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Add a customer or supplier ([partyId] = 0), or change their details and old balance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyEditScreen(
    repository: MoneyRepository,
    partyId: Long,
    initialRole: PartyRole,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val books = LocalBooks.current
    val isNew = partyId == 0L
    var existing by remember { mutableStateOf<PartyItem?>(null) }
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var role by rememberSaveable { mutableStateOf(initialRole) }
    var bookId by rememberSaveable { mutableStateOf(books.defaultBook) }
    var contact by rememberSaveable(stateSaver = ContactSaver) { mutableStateOf(Contact(name = "")) }
    var openingText by rememberSaveable { mutableStateOf("") }
    // True: they owe you this old balance; false: you owe them.
    var theyOwe by rememberSaveable { mutableStateOf(initialRole == PartyRole.CUSTOMER) }
    var error by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        if (isNew) return@LaunchedEffect
        val item = withContext(Dispatchers.IO) { repository.partyItem(partyId) } ?: run { onBack(); return@LaunchedEffect }
        existing = item
        if (loaded) return@LaunchedEffect
        role = item.party.role
        bookId = item.party.bookId
        contact = item.contact ?: Contact(name = item.name)
        openingText = if (item.opening != 0L) Money.plain(kotlin.math.abs(item.opening)) else ""
        theyOwe = item.opening >= 0 && (item.opening != 0L || role == PartyRole.CUSTOMER)
        loaded = true
    }

    fun save() {
        val opening = if (openingText.isBlank()) 0L else Money.parse(openingText)
        error = when {
            contact.name.isBlank() -> R.string.error_loan_name
            opening == null || opening < 0 -> R.string.error_amount
            contact.email.isNotBlank() && !Contact.isEmail(contact.email) -> R.string.error_email
            contact.upi.isNotBlank() && !Contact.isUpiId(contact.upi) -> R.string.error_upi
            else -> null
        }
        if (error != null || opening == null) return
        val signed = if (theyOwe) opening else -opening
        scope.launch {
            val item = existing
            if (item == null) onSaved(repository.createParty(contact, role, bookId, signed))
            else {
                repository.updateParty(item.party, contact, role, bookId, signed)
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                !isNew -> R.string.ledger_edit
                                role == PartyRole.CUSTOMER -> R.string.ledger_add_customer
                                else -> R.string.ledger_add_supplier
                            },
                        ),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf(PartyRole.CUSTOMER to R.string.ledger_customer, PartyRole.SUPPLIER to R.string.ledger_supplier).forEachIndexed { i, (r, label) ->
                        SegmentedButton(selected = role == r, onClick = { role = r }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(stringResource(label)) }
                    }
                }
                Text(
                    stringResource(if (role == PartyRole.CUSTOMER) R.string.ledger_customer_hint else R.string.ledger_supplier_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                BookChips(bookId, onSelect = { bookId = it }, modifier = Modifier.padding(bottom = 4.dp))
                if (loaded) {
                    ContactFields(
                        contact = contact, onChange = { contact = it; error = null },
                        nameLabel = stringResource(if (role == PartyRole.CUSTOMER) R.string.ledger_customer_name else R.string.ledger_supplier_name),
                    )
                }
                Text(
                    stringResource(R.string.ledger_old_balance), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = theyOwe, onClick = { theyOwe = true }, label = { Text(stringResource(R.string.ledger_they_owe_me)) })
                    FilterChip(selected = !theyOwe, onClick = { theyOwe = false }, label = { Text(stringResource(R.string.ledger_i_owe_them)) })
                }
                OutlinedTextField(
                    value = openingText,
                    onValueChange = { openingText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15); error = null },
                    label = { Text(stringResource(R.string.amount)) },
                    prefix = { Text(LocalCurrency.current.symbol) },
                    supportingText = { Text(stringResource(R.string.ledger_old_balance_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            Button(
                onClick = ::save, enabled = loaded,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp, top = 4.dp).height(52.dp),
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    }
}
