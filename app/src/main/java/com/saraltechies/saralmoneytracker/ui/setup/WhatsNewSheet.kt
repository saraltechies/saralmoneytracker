package com.saraltechies.saralmoneytracker.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.ui.components.IconBadge

/** Bump this with each release that has something worth showing; it is shown once to people updating. */
const val WHATS_NEW_VERSION = 7

/** Where a "What's new" item takes you. */
enum class WhatsNewTarget { BOOKS, LEDGER, LOANS, BACKUP, FORECAST, NONE }

private data class Item(val icon: ImageVector, val title: Int, val text: Int, val target: WhatsNewTarget, val action: Int?)

private val items = listOf(
    Item(Icons.AutoMirrored.Filled.TrendingUp, R.string.whatsnew_forecast_title, R.string.whatsnew_forecast_text, WhatsNewTarget.FORECAST, R.string.whatsnew_open),
    Item(Icons.Filled.NotificationsActive, R.string.whatsnew_alerts_title, R.string.whatsnew_alerts_text, WhatsNewTarget.LEDGER, R.string.whatsnew_alerts_action),
    Item(Icons.Filled.Backup, R.string.whatsnew_backup_title, R.string.whatsnew_backup_text, WhatsNewTarget.BACKUP, R.string.whatsnew_backup_action),
    Item(Icons.Filled.Store, R.string.whatsnew_books_title, R.string.whatsnew_books_text, WhatsNewTarget.BOOKS, R.string.whatsnew_books_action),
    Item(Icons.Filled.Storefront, R.string.whatsnew_ledger_title, R.string.whatsnew_ledger_text, WhatsNewTarget.LEDGER, R.string.whatsnew_open),
    Item(Icons.Filled.Handshake, R.string.whatsnew_loans_title, R.string.whatsnew_loans_text, WhatsNewTarget.LOANS, R.string.whatsnew_open),
    Item(Icons.Filled.PictureAsPdf, R.string.whatsnew_pdf_title, R.string.whatsnew_pdf_text, WhatsNewTarget.NONE, null),
    Item(Icons.Filled.Widgets, R.string.whatsnew_widget_title, R.string.whatsnew_widget_text, WhatsNewTarget.NONE, null),
)

/** What's arrived since the first version, with a way to try each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(onOpen: (WhatsNewTarget) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.whatsnew_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.whatsnew_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 8.dp)) {
            items(items) { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    IconBadge(item.icon, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(item.text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.action != null) {
                            TextButton(onClick = { onOpen(item.target) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(stringResource(item.action))
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.whatsnew_got_it))
        }
    }
}
