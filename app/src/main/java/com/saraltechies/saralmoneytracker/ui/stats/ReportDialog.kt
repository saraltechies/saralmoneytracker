package com.saraltechies.saralmoneytracker.ui.stats

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Period
import com.saraltechies.saralmoneytracker.model.Statements
import com.saraltechies.saralmoneytracker.ui.components.Dates
import com.saraltechies.saralmoneytracker.ui.components.LocalBooks
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.util.ReportPdf
import com.saraltechies.saralmoneytracker.util.StatementFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private enum class ReportRange { SHOWN, THIS_FY, LAST_FY }

/** A PDF report of the book on screen (profit and loss for a business) for the period shown or a financial year. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(repository: MoneyRepository, shown: Period, onMyDetails: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currency = LocalCurrency.current
    val books = LocalBooks.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var range by rememberSaveable { mutableStateOf(ReportRange.SHOWN) }
    var busy by remember { mutableStateOf(false) }
    val business = books.currentBook?.kind == BookKind.BUSINESS
    val (from, to) = when (range) {
        ReportRange.SHOWN -> shown.start to shown.end
        ReportRange.THIS_FY -> Statements.financialYear(today).let { (a, b) -> a to minOf(b, today) }
        ReportRange.LAST_FY -> Statements.financialYear(today.minusYears(1))
    }
    val title = stringResource(if (business) R.string.report_title_business else R.string.report_title_personal)
    val fileName = (books.currentBook?.name ?: context.getString(R.string.books_all)).replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim() +
        " " + title + " " + from + " to " + to + ".pdf"

    suspend fun make(out: java.io.OutputStream) = withContext(Dispatchers.IO) {
        val report = repository.bookReport(from, to)
        val me = books.currentBook?.let { repository.myDetails(it.id) } ?: repository.myDetails()
        ReportPdf(context, currency).write(report, me, out)
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { make(it) } != null }.getOrDefault(false)
            Toast.makeText(context, if (ok) R.string.statement_saved else R.string.statement_failed, Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }

    fun share() {
        busy = true
        scope.launch {
            val file = runCatching {
                val dir = File(context.cacheDir, "statements").apply { mkdirs() }
                File(dir, fileName).also { f -> f.outputStream().use { make(it) } }
            }.getOrNull()
            busy = false
            if (file == null) {
                Toast.makeText(context, R.string.statement_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = StatementFiles.uri(context, file)
            val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(file.name, uri)
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    stringResource(if (business) R.string.report_intro_business else R.string.report_intro_personal, books.currentBook?.name ?: stringResource(R.string.books_all)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(selected = range == ReportRange.SHOWN, onClick = { range = ReportRange.SHOWN }, label = { Text(Dates.period(shown)) })
                    FilterChip(selected = range == ReportRange.THIS_FY, onClick = { range = ReportRange.THIS_FY }, label = { Text(stringResource(R.string.statement_this_fy)) })
                    FilterChip(selected = range == ReportRange.LAST_FY, onClick = { range = ReportRange.LAST_FY }, label = { Text(stringResource(R.string.statement_last_fy)) })
                }
                Text(Dates.medium(from) + " – " + Dates.medium(to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.report_details_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp).clickable(onClick = onMyDetails),
                )
            }
        },
        confirmButton = { TextButton(onClick = ::share, enabled = !busy) { Text(stringResource(R.string.statement_share_pdf)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { save.launch(fileName) }, enabled = !busy) { Text(stringResource(R.string.statement_save_pdf)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
