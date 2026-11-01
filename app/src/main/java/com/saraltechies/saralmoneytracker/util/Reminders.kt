package com.saraltechies.saralmoneytracker.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saraltechies.saralmoneytracker.MainActivity
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.SaralMoneyTrackerApp
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The optional daily "add today's entries" reminder. An inexact daily alarm (no exact-alarm permission needed);
 * it stays quiet on days something was already added.
 */
object Reminders {

    private const val CHANNEL = "daily_reminder"
    private const val NOTIFICATION_ID = 1
    private const val LOANS_NOTIFICATION_ID = 2
    private const val REQUEST = 10

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, on: Boolean, minuteOfDay: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(pending)
        if (!on) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val triggerMs = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerMs, AlarmManager.INTERVAL_DAY, pending)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // checked by canNotify
    fun show(context: Context) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHORTCUT)
                .putExtra(MainActivity.EXTRA_SHORTCUT, AppShortcuts.ID_EXPENSE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    /** On the day a loan instalment or interest is due, says who pays whom how much. */
    @SuppressLint("MissingPermission") // checked by canNotify
    fun showLoansDue(context: Context, loans: List<LoanItem>, currency: CurrencyInfo, today: LocalDate = LocalDate.now()) {
        if (!canNotify(context)) return
        val due = loans.filter { !it.loan.closed && it.summary.nextDueDate == today && it.summary.nextDueAmount > 0 }
        if (due.isEmpty()) return
        val lines = due.map {
            val amount = Money.format(it.summary.nextDueAmount, currency)
            context.getString(if (it.loan.direction == LoanDirection.LENT) R.string.loan_due_receive else R.string.loan_due_pay, it.name, amount)
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(context.getString(R.string.loan_due_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(LOANS_NOTIFICATION_ID, notification) }
    }
}

/** The daily alarm: reminds only if nothing was added today. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SaralMoneyTrackerApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.settings.current()
                if (prefs.reminderOn && !app.repository.hasTxnOn(LocalDate.now())) Reminders.show(context)
                if (prefs.reminderOn) Reminders.showLoansDue(context, app.repository.loanItems(book = com.saraltechies.saralmoneytracker.model.Books.ALL), prefs.currency)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are cleared by a restart or an app update; set the reminder again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SaralMoneyTrackerApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.settings.current()
                Reminders.schedule(context, prefs.reminderOn, prefs.reminderMinute)
            } finally {
                pending.finish()
            }
        }
    }
}
