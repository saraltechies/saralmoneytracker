package com.saraltechies.saralmoneytracker.ui.books

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.Book
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.ui.components.IconBadge
import com.saraltechies.saralmoneytracker.ui.components.LocalBooks
import com.saraltechies.saralmoneytracker.ui.components.bookIcon
import kotlinx.coroutines.launch

/** Personal and business books: add a business, rename a book, or delete one that has nothing in it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    repository: MoneyRepository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val books by remember { repository.watch { repository.books() } }.collectAsStateWithLifecycle(emptyList())
    val current = LocalBooks.current.current
    var editing by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.books_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Book(name = "", kind = BookKind.BUSINESS) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.books_add)) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.books_intro_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.books_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(books, key = { it.id }) { b ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(b.id) }.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(bookIcon(b), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (b.kind == BookKind.BUSINESS) R.string.book_kind_business else R.string.book_kind_personal),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (current == b.id) Icon(Icons.Filled.Check, stringResource(R.string.books_showing), tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { editing = b }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                }
            }
        }
    }

    editing?.let { book ->
        BookDialog(
            book = book,
            onDismiss = { editing = null },
            onSave = { name, kind ->
                scope.launch {
                    if (book.id == 0L) {
                        val id = repository.createBook(name, kind)
                        onSelect(id)
                    } else {
                        repository.renameBook(book.id, name, kind)
                    }
                }
            },
            onDelete = if (book.id != 0L && book.id != Books.PERSONAL_ID) ({
                scope.launch {
                    val deleted = repository.deleteBook(book.id)
                    snackbar.showSnackbar(context.getString(if (deleted) R.string.books_deleted else R.string.books_has_history))
                }
            }) else null,
        )
    }
}

@Composable
private fun BookDialog(book: Book, onDismiss: () -> Unit, onSave: (String, BookKind) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(book.name) }
    var kind by remember { mutableStateOf(book.kind) }
    val isNew = book.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.books_add else R.string.books_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(if (kind == BookKind.BUSINESS) R.string.books_business_name else R.string.books_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == BookKind.BUSINESS, onClick = { kind = BookKind.BUSINESS }, label = { Text(stringResource(R.string.book_kind_business)) })
                    FilterChip(selected = kind == BookKind.PERSONAL, onClick = { kind = BookKind.PERSONAL }, label = { Text(stringResource(R.string.book_kind_personal)) })
                }
                if (isNew) {
                    Text(
                        stringResource(if (kind == BookKind.BUSINESS) R.string.books_new_business_hint else R.string.books_new_personal_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, kind); onDismiss() }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
