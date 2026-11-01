package com.saraltechies.saralmoneytracker.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.data.PersonLoans
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.StatementData
import com.saraltechies.saralmoneytracker.ui.components.Dates
import java.io.File
import java.time.LocalDate

/**
 * Makes a statement PDF in the app's cache and hands it to whatever the person chooses: WhatsApp, Gmail, Drive.
 * The app itself sends nothing (it has no internet access); the email is only filled in for the mail app.
 */
object StatementFiles {

    private const val DIR = "statements"

    fun fileName(context: Context, item: LoanItem, today: LocalDate = LocalDate.now()): String {
        val safe = item.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifEmpty { "loan" }
        return context.getString(R.string.pdf_file_name, safe, today.toString()) + ".pdf"
    }

    fun create(context: Context, item: LoanItem, me: Contact?, data: StatementData, options: StatementPdf.Options, currency: CurrencyInfo): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, fileName(context, item))
        file.outputStream().use { StatementPdf(context, currency).write(item, me, data, options, it) }
        return file
    }

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** A share sheet with the PDF, and for mail apps the person's email, a subject and a short message. */
    fun shareIntent(context: Context, file: File, item: LoanItem, me: Contact?, data: StatementData, currency: CurrencyInfo): Intent {
        val uri = uri(context, file)
        val lent = item.loan.direction == LoanDirection.LENT
        val subject = context.getString(R.string.statement_email_subject, item.name, Dates.medium(data.to))
        val body = context.getString(
            if (lent) R.string.statement_email_body_lent else R.string.statement_email_body_borrowed,
            item.contact?.name ?: item.name, Dates.medium(data.to), Money.format(data.closing.outstanding, currency),
            me?.name?.takeIf { it.isNotBlank() } ?: "",
        ).trimEnd()
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        item.contact?.email?.takeIf { it.isNotBlank() }?.let { send.putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
        send.clipData = ClipData.newRawUri(file.name, uri)
        return Intent.createChooser(send, context.getString(R.string.statement_share_title)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun accountFileName(context: Context, person: PersonLoans, today: LocalDate = LocalDate.now()): String {
        val safe = person.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifEmpty { "account" }
        return context.getString(R.string.pdf_account_file_name, safe, today.toString()) + ".pdf"
    }

    /** One person's account statement (all their loans) in the cache, ready to share. */
    fun createAccount(context: Context, person: PersonLoans, me: Contact?, currency: CurrencyInfo): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, accountFileName(context, person))
        file.outputStream().use { StatementPdf(context, currency).writeAccount(person, me, it) }
        return file
    }

    fun shareAccountIntent(context: Context, file: File, person: PersonLoans, me: Contact?, currency: CurrencyInfo, today: LocalDate = LocalDate.now()): Intent {
        val uri = uri(context, file)
        val net = person.toReceive - person.toPay
        val body = context.getString(
            if (net >= 0) R.string.statement_email_body_lent else R.string.statement_email_body_borrowed,
            person.name, Dates.medium(today), Money.format(kotlin.math.abs(net), currency), me?.name?.takeIf { it.isNotBlank() } ?: "",
        ).trimEnd()
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.statement_account_subject, person.name, Dates.medium(today)))
            .putExtra(Intent.EXTRA_TEXT, body)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        person.contact?.email?.takeIf { it.isNotBlank() }?.let { send.putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
        send.clipData = ClipData.newRawUri(file.name, uri)
        return Intent.createChooser(send, context.getString(R.string.statement_share_title)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Statements are only needed while being shared; old ones are removed when the app starts. */
    fun clearOld(context: Context, olderThanMs: Long = 24L * 60 * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        File(context.cacheDir, DIR).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}
