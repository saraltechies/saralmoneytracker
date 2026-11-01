package com.saraltechies.saralmoneytracker.ui.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.util.StatementPdf

/**
 * The details of a person, bank or vendor (or yours): phone, email, address, PAN/GSTIN, bank account and UPI.
 * Details fold away until needed, so adding a loan to a friend stays quick.
 */
@Composable
fun ContactFields(
    contact: Contact,
    onChange: (Contact) -> Unit,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    showKind: Boolean = true,
    startExpanded: Boolean = contact.hasDetails,
    nameLabel: String = stringResource(R.string.contact_name),
) {
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPhoneContact(context, uri)?.let { (name, phone) ->
            onChange(contact.copy(name = name.ifBlank { contact.name }, phone = phone.ifBlank { contact.phone }))
        }
    }

    Column(modifier) {
        if (showName) {
            OutlinedTextField(
                value = contact.name, onValueChange = { onChange(contact.copy(name = it.take(60))) },
                label = { Text(nameLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (showKind) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(listOf(ContactKind.PERSON, ContactKind.BANK, ContactKind.COMPANY, ContactKind.VENDOR)) { k ->
                    FilterChip(selected = contact.kind == k, onClick = { onChange(contact.copy(kind = k)) }, label = { Text(stringResource(StatementPdf.kindLabel(k))) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (expanded) R.string.contact_hide_details else R.string.contact_add_details))
            }
            Spacer(Modifier.weight(1f))
            if (contact.kind != ContactKind.SELF) {
                TextButton(onClick = {
                    runCatching { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                }) {
                    Icon(Icons.Filled.ContactPhone, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.contact_pick))
                }
            }
        }
        if (expanded) {
            val field = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            Field(contact.phone, R.string.contact_phone, field, KeyboardType.Phone) { onChange(contact.copy(phone = it.take(20))) }
            Field(contact.email, R.string.contact_email, field, KeyboardType.Email, error = contact.email.isNotBlank() && !Contact.isEmail(contact.email)) {
                onChange(contact.copy(email = it.take(80)))
            }
            Field(contact.address, R.string.contact_address, field, singleLine = false, capitalization = KeyboardCapitalization.Words) { onChange(contact.copy(address = it.take(200))) }
            Field(contact.taxId, R.string.contact_tax_id, field, capitalization = KeyboardCapitalization.Characters) { onChange(contact.copy(taxId = it.take(20))) }
            Text(
                stringResource(R.string.contact_bank_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
            )
            Field(contact.bankName, R.string.contact_bank, field, capitalization = KeyboardCapitalization.Words) { onChange(contact.copy(bankName = it.take(60))) }
            Field(contact.bankAccount, R.string.contact_bank_account, field, KeyboardType.Number) { onChange(contact.copy(bankAccount = it.filter { c -> c.isLetterOrDigit() }.take(24))) }
            Field(contact.ifsc, R.string.contact_ifsc, field, capitalization = KeyboardCapitalization.Characters, error = contact.ifsc.isNotBlank() && !Contact.isIfsc(contact.ifsc)) {
                onChange(contact.copy(ifsc = it.filter { c -> c.isLetterOrDigit() }.take(11).uppercase()))
            }
            Field(contact.upi, R.string.contact_upi, field, KeyboardType.Email, error = contact.upi.isNotBlank() && !Contact.isUpiId(contact.upi)) {
                onChange(contact.copy(upi = it.trim().take(80)))
            }
            if (contact.kind != ContactKind.SELF) {
                Field(contact.reference, R.string.contact_reference, field) { onChange(contact.copy(reference = it.take(40))) }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: Int,
    modifier: Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    error: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        isError = error,
        supportingText = if (error) ({ Text(stringResource(R.string.contact_check_format)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        modifier = modifier,
    )
}

/** Name and number of the phone contact picked; the picker grants access to just that one entry. */
private fun readPhoneContact(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
    )?.use { c -> if (c.moveToFirst()) (c.getString(0).orEmpty() to c.getString(1).orEmpty()) else null }
}.getOrNull()

/** Keeps a contact being edited across rotation and process death. */
val ContactSaver = androidx.compose.runtime.saveable.Saver<Contact, List<Any>>(
    save = { listOf(it.id, it.name, it.kind.name, it.phone, it.email, it.address, it.taxId, it.bankName, it.bankAccount, it.ifsc, it.upi, it.reference, it.note) },
    restore = {
        Contact(
            id = it[0] as Long, name = it[1] as String, kind = ContactKind.valueOf(it[2] as String), phone = it[3] as String, email = it[4] as String,
            address = it[5] as String, taxId = it[6] as String, bankName = it[7] as String, bankAccount = it[8] as String, ifsc = it[9] as String,
            upi = it[10] as String, reference = it[11] as String, note = it[12] as String,
        )
    },
)
