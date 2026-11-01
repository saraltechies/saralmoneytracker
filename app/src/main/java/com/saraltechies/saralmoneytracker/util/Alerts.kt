package com.saraltechies.saralmoneytracker.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saraltechies.saralmoneytracker.MainActivity
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.SaralMoneyTrackerApp
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.Ledgers
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Periods
import com.saraltechies.saralmoneytracker.model.Stats
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.components.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Morning alerts, at 9: bills coming up (repeating entries with a reminder), loan payments and customers' or
 * suppliers' bills due today, and on the first day of a month a summary of the month before. Only on days there is
 * something to say.
 */
object Alerts {

    private const val CHANNEL_DUES = "dues"
    private const val CHANNEL_SUMMARY = "monthly_summary"
    private const val ID_BILLS = 3
    private const val ID_DUES = 2
    private const val ID_SUMMARY = 4
    private const val REQUEST = 30

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DUES, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SUMMARY, context.getString(R.string.summary_channel), NotificationManager.IMPORTANCE_LOW))
    }

    fun schedule(context: Context, on: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(context, REQUEST, Intent(context, AlertsReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        if (!on) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(9, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, pending)
    }

    suspend fun run(context: Context, today: LocalDate = LocalDate.now()) {
        val app = context.applicationContext as SaralMoneyTrackerApp
        val prefs = app.settings.current()
        if (!Reminders.canNotify(app)) return
        val currency = prefs.currency
        val repo = app.repository
        fun m(v: Long) = Money.format(v, currency)

        if (prefs.alertsOn) {
            // Bills coming up: repeating entries due in a few days, as set on each.
            val categories = repo.categories(includeArchived = true, book = Books.ALL).associateBy { it.id }
            val accounts = repo.accounts(includeArchived = true, includeLoans = true, book = Books.ALL).associateBy { it.id }
            val bills = repo.recurring(book = Books.ALL).filter { it.active && it.remindDays > 0 && it.nextDate == today.plusDays(it.remindDays.toLong()) }
                .map { r ->
                    val what = r.note.ifBlank { r.categoryId?.let { categories[it]?.name } ?: accounts[r.accountId]?.name.orEmpty() }
                    app.getString(if (r.type == TxnType.INCOME) R.string.alert_bill_income else R.string.alert_bill, what, m(r.amount), Dates.medium(r.nextDate), r.remindDays)
                }
            if (bills.isNotEmpty()) notify(app, CHANNEL_DUES, ID_BILLS, app.getString(R.string.alert_bills_title), bills)

            // Due today: loan payments, and customers' and suppliers' bills.
            val dues = mutableListOf<String>()
            for (l in repo.loanItems(today, Books.ALL)) {
                val s = l.summary
                if (l.loan.closed || s.nextDueDate != today || s.nextDueAmount <= 0) continue
                dues += app.getString(if (l.loan.direction == LoanDirection.LENT) R.string.loan_due_receive else R.string.loan_due_pay, l.name, m(s.nextDueAmount))
            }
            for (p in repo.partyItems(Books.ALL)) {
                val d = Ledgers.dues(p.party, p.entries, today)
                if (d.dueToday <= 0) continue
                dues += app.getString(if (p.party.role == PartyRole.CUSTOMER) R.string.loan_due_receive else R.string.loan_due_pay, p.name, m(d.dueToday))
            }
            if (dues.isNotEmpty()) notify(app, CHANNEL_DUES, ID_DUES, app.getString(R.string.alert_dues_title), dues)
        }

        // On the first day of a new month (as set: it may start on the 25th), how the last one went.
        val month = Periods.containing(today, PeriodKind.MONTH, prefs.period)
        if (prefs.monthlySummaryOn && today == month.start && prefs.lastSummaryFor != month.start.toEpochDay()) {
            val last = Periods.shift(month, -1, prefs.period)
            val books = repo.books()
            val book = prefs.book.takeIf { id -> books.size > 1 && books.any { it.id == id } } ?: Books.ALL
            val txns = repo.txns(last.start, last.end, book = book, profitAndLoss = true)
            if (txns.isNotEmpty()) {
                val totals = Stats.totals(txns)
                val top = Stats.byCategory(txns, TxnType.EXPENSE).firstOrNull()
                val topName = top?.categoryId?.let { id -> repo.categories(includeArchived = true, book = Books.ALL).firstOrNull { it.id == id }?.name }
                val toCollect = repo.partyItems(book).sumOf { it.balance.coerceAtLeast(0) }
                val lines = buildList {
                    add(app.getString(R.string.summary_line_totals, m(totals.expense), m(totals.income)))
                    add(app.getString(if (totals.net >= 0) R.string.summary_line_saved else R.string.summary_line_overspent, m(kotlin.math.abs(totals.net))))
                    if (top != null && topName != null) add(app.getString(R.string.summary_line_top, topName, m(top.amount)))
                    if (toCollect > 0) add(app.getString(R.string.summary_line_collect, m(toCollect)))
                }
                val label = if (last.start.dayOfMonth == 1) last.start.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                else Dates.short(last.start) + " – " + Dates.short(last.end)
                val title = app.getString(R.string.summary_title, label, books.firstOrNull { it.id == book }?.name ?: "")
                notify(app, CHANNEL_SUMMARY, ID_SUMMARY, title.trim().removeSuffix("·").trim(), lines)
            }
            app.settings.setLastSummaryFor(month.start.toEpochDay())
        }
    }

    @SuppressLint("MissingPermission") // checked by Reminders.canNotify
    private fun notify(context: Context, channel: String, id: Int, title: String, lines: List<String>) {
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { s -> lines.forEach(s::addLine) })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

/** The 9 am alarm. */
class AlertsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { Alerts.run(context) }
            } finally {
                pending.finish()
            }
        }
    }
}
