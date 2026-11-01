package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.Book
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Books

fun bookIcon(book: Book?): ImageVector = when (book?.kind) {
    BookKind.BUSINESS -> Icons.Filled.Store
    BookKind.PERSONAL -> Icons.Filled.Person
    null -> Icons.AutoMirrored.Filled.LibraryBooks
}

/**
 * The top bar title. With one book it's just [fallbackTitle]; with more, it names the book on screen and opens a
 * menu to switch books, see all of them together, or manage them.
 */
@Composable
fun BookSwitcher(fallbackTitle: String, onSelect: (Long) -> Unit, onManage: () -> Unit) {
    val state = LocalBooks.current
    if (!state.multiple) {
        Text(fallbackTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clickable { open = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(state.currentBook?.name ?: stringResource(R.string.books_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (state.currentBook == null) R.string.books_all_hint else R.string.books_switch),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.books_switch))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (b in state.books) {
                DropdownMenuItem(
                    text = { Text(b.name) },
                    leadingIcon = { Icon(bookIcon(b), null) },
                    trailingIcon = { if (state.current == b.id) Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) },
                    onClick = { open = false; onSelect(b.id) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.books_all)) },
                leadingIcon = { Icon(bookIcon(null), null) },
                trailingIcon = { if (state.current == Books.ALL) Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) },
                onClick = { open = false; onSelect(Books.ALL) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.books_manage)) },
                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                onClick = { open = false; onManage() },
            )
        }
    }
}

/** Choose a book, for forms (an entry, an account, a loan) when there is more than one. */
@Composable
fun BookChips(selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val state = LocalBooks.current
    if (!state.multiple) return
    androidx.compose.foundation.lazy.LazyRow(
        modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(state.books.size) { i ->
            val b = state.books[i]
            androidx.compose.material3.FilterChip(
                selected = selected == b.id,
                onClick = { onSelect(b.id) },
                enabled = enabled,
                label = { Text(b.name, maxLines = 1) },
                leadingIcon = { Icon(bookIcon(b), null, Modifier.size(16.dp)) },
            )
        }
    }
}
