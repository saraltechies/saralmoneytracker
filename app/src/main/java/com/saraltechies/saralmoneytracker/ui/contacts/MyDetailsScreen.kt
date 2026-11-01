package com.saraltechies.saralmoneytracker.ui.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.ui.components.BookChips
import com.saraltechies.saralmoneytracker.ui.components.LocalBooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Your name or business, address and bank/UPI details, printed at the top of the statements and reports you send.
 * Each book has its own, so a business's statements show the business.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDetailsScreen(repository: MoneyRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val books = LocalBooks.current
    var bookId by rememberSaveable { mutableStateOf(books.defaultBook) }
    var loadedFor by rememberSaveable { mutableStateOf(-1L) }
    var details by rememberSaveable(stateSaver = ContactSaver) { mutableStateOf(Contact(name = "", kind = ContactKind.SELF, bookId = bookId)) }
    val business = books.book(bookId)?.kind == BookKind.BUSINESS

    LaunchedEffect(bookId) {
        if (loadedFor == bookId) return@LaunchedEffect
        details = withContext(Dispatchers.IO) { repository.myDetails(bookId) }
            ?: Contact(name = if (business) books.book(bookId)?.name.orEmpty() else "", kind = ContactKind.SELF, bookId = bookId)
        loadedFor = bookId
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_details)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                BookChips(bookId, onSelect = { id ->
                    // Switching book keeps what was typed for the other one only if saved.
                    bookId = id
                })
                Text(
                    stringResource(if (business) R.string.my_details_intro_business else R.string.my_details_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (loadedFor == bookId) {
                    key(bookId) {
                        ContactFields(
                            contact = details, onChange = { details = it }, showKind = false, startExpanded = true,
                            nameLabel = stringResource(if (business) R.string.my_details_business_name else R.string.my_details_name),
                        )
                    }
                }
            }
            Button(
                onClick = { scope.launch { repository.saveMyDetails(details.copy(bookId = bookId)); onBack() } },
                enabled = loadedFor == bookId,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    }
}
