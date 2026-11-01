package com.saraltechies.saralmoneytracker.util

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.saraltechies.saralmoneytracker.MainActivity
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.SaralMoneyTrackerApp
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Periods
import com.saraltechies.saralmoneytracker.model.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The home-screen widget: what you spent today and this month, with Add expense and Add income buttons. */
class MoneyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Widgets.scope.launch {
            try {
                Widgets.refreshNow(context)
            } finally {
                pending.finish()
            }
        }
    }
}

object Widgets {

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingRefresh: Job? = null

    /** After entries change: refresh the widget once things settle (an import adds many entries at once). */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        if (!hasWidgets(app)) return
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(500)
            refreshNow(app)
        }
    }

    private fun hasWidgets(context: Context): Boolean =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MoneyWidget::class.java)).isNotEmpty() }.getOrDefault(false)

    suspend fun refreshNow(context: Context) {
        val app = context.applicationContext as SaralMoneyTrackerApp
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, MoneyWidget::class.java))
        if (ids.isEmpty()) return
        val prefs = app.settings.current()
        val repo = app.repository
        val books = repo.books()
        // The widget follows the book chosen in the app, like every screen.
        val book = prefs.book.takeIf { id -> books.size > 1 && books.any { it.id == id } } ?: Books.ALL
        val today = LocalDate.now()
        val month = Periods.containing(today, PeriodKind.MONTH, prefs.period)
        val from = minOf(month.start, today)
        val txns = repo.txns(from, maxOf(month.end, today), book = book, profitAndLoss = true)
        val todaySpent = Stats.totals(txns.filter { it.date == today }).expense
        val monthSpent = Stats.totals(txns.filter { it.date in month }).expense
        val bookName = books.firstOrNull { it.id == book }?.name

        val views = RemoteViews(app.packageName, R.layout.widget_money)
        views.setTextViewText(
            R.id.widget_label,
            if (bookName != null) app.getString(R.string.widget_spent_today_in, bookName) else app.getString(R.string.widget_spent_today),
        )
        views.setTextViewText(R.id.widget_today, Money.format(todaySpent, prefs.currency))
        views.setTextViewText(R.id.widget_month, app.getString(R.string.widget_this_month, Money.format(monthSpent, prefs.currency)))
        views.setOnClickPendingIntent(R.id.widget_root, open(app, null, 0))
        views.setOnClickPendingIntent(R.id.widget_add_expense, open(app, AppShortcuts.ID_EXPENSE, 1))
        views.setOnClickPendingIntent(R.id.widget_add_income, open(app, AppShortcuts.ID_INCOME, 2))
        manager.updateAppWidget(ids, views)
    }

    /** Opens the app, or straight into a new expense or income. */
    fun open(context: Context, shortcut: String?, request: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (shortcut != null) intent.setAction(MainActivity.ACTION_SHORTCUT).putExtra(MainActivity.EXTRA_SHORTCUT, shortcut)
        return PendingIntent.getActivity(context, 100 + request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** A quick-settings tile: pull down the notification shade, tap, and add an expense. */
class AddExpenseTile : TileService() {
    override fun onClick() {
        super.onClick()
        val pending = Widgets.open(this, AppShortcuts.ID_EXPENSE, 3)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_SHORTCUT)
                    .putExtra(MainActivity.EXTRA_SHORTCUT, AppShortcuts.ID_EXPENSE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }
}
