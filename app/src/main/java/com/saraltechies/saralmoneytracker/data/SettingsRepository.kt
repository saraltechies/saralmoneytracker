package com.saraltechies.saralmoneytracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.PeriodPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Currency
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppPrefs(
    val currency: CurrencyInfo,
    val period: PeriodPrefs,
    val theme: ThemeMode,
    val appLock: Boolean,
    val hideInRecents: Boolean,
    val reminderOn: Boolean,
    /** Minutes after midnight. */
    val reminderMinute: Int,
    val welcomeDismissed: Boolean,
    /** The book on screen: a book id, or 0 for all books. */
    val book: Long = 0,
)

class SettingsRepository(private val context: Context) {

    private val currencyKey = stringPreferencesKey("currency")
    private val weekStartKey = intPreferencesKey("week_start")
    private val monthStartKey = intPreferencesKey("month_start_day")
    private val themeKey = stringPreferencesKey("theme")
    private val appLockKey = booleanPreferencesKey("app_lock")
    private val hideRecentsKey = booleanPreferencesKey("hide_in_recents")
    private val reminderOnKey = booleanPreferencesKey("reminder_on")
    private val reminderMinuteKey = intPreferencesKey("reminder_minute")
    private val welcomeDismissedKey = booleanPreferencesKey("welcome_dismissed")
    private val bookKey = longPreferencesKey("book")
    private val goodMomentsKey = intPreferencesKey("good_moments")
    private val reviewPromptsKey = intPreferencesKey("review_prompts")
    private val reviewLastAtKey = longPreferencesKey("review_last_at")
    private val reviewDueKey = booleanPreferencesKey("review_due")
    private val updateDeclinedAtKey = longPreferencesKey("update_declined_at")

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            currency = Currencies.of(p[currencyKey] ?: defaultCurrency()),
            period = PeriodPrefs(
                weekStart = p[weekStartKey]?.let { DayOfWeek.of(it) } ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek,
                monthStartDay = (p[monthStartKey] ?: 1).coerceIn(1, 28),
            ),
            theme = ThemeMode.entries.firstOrNull { it.name == p[themeKey] } ?: ThemeMode.SYSTEM,
            appLock = p[appLockKey] ?: false,
            hideInRecents = p[hideRecentsKey] ?: false,
            reminderOn = p[reminderOnKey] ?: false,
            reminderMinute = p[reminderMinuteKey] ?: DEFAULT_REMINDER_MINUTE,
            welcomeDismissed = p[welcomeDismissedKey] ?: false,
            book = p[bookKey] ?: 0L,
        )
    }.distinctUntilChanged()

    suspend fun current(): AppPrefs = prefs.first()

    suspend fun setCurrency(code: String) = context.dataStore.edit { it[currencyKey] = code }
    suspend fun setWeekStart(day: DayOfWeek) = context.dataStore.edit { it[weekStartKey] = day.value }
    suspend fun setMonthStartDay(day: Int) = context.dataStore.edit { it[monthStartKey] = day.coerceIn(1, 28) }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[appLockKey] = on }
    suspend fun setHideInRecents(on: Boolean) = context.dataStore.edit { it[hideRecentsKey] = on }
    suspend fun setWelcomeDismissed() = context.dataStore.edit { it[welcomeDismissedKey] = true }
    suspend fun setBook(id: Long) = context.dataStore.edit { it[bookKey] = id }

    suspend fun setReminder(on: Boolean, minute: Int) = context.dataStore.edit {
        it[reminderOnKey] = on
        it[reminderMinuteKey] = minute
    }

    /**
     * Counts a good moment (an entry saved). When [ReviewPolicy] allows, marks a Play review as due; the home screen
     * asks once the person is back there, never while they're typing an entry.
     */
    suspend fun recordGoodMoment(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            val moments = (it[goodMomentsKey] ?: 0) + 1
            it[goodMomentsKey] = moments
            if (ReviewPolicy.shouldAsk(moments, it[reviewPromptsKey] ?: 0, it[reviewLastAtKey] ?: 0L, nowMs)) it[reviewDueKey] = true
        }
    }

    /** True once when a review is due; records the prompt so it isn't asked again too soon. */
    suspend fun takeReviewDue(nowMs: Long = System.currentTimeMillis()): Boolean {
        var due = false
        context.dataStore.edit {
            due = it[reviewDueKey] == true
            if (due) {
                it[reviewDueKey] = false
                it[reviewPromptsKey] = (it[reviewPromptsKey] ?: 0) + 1
                it[reviewLastAtKey] = nowMs
            }
        }
        return due
    }

    suspend fun updateDeclinedAt(): Long = context.dataStore.data.first()[updateDeclinedAtKey] ?: 0L

    suspend fun setUpdateDeclinedAt(nowMs: Long) {
        context.dataStore.edit { it[updateDeclinedAtKey] = nowMs }
    }

    companion object {
        const val DEFAULT_REMINDER_MINUTE = 21 * 60

        /** Rupees for India, otherwise the phone's region currency when the app knows it. */
        fun defaultCurrency(locale: Locale = Locale.getDefault()): String {
            val code = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
            return if (Currencies.isKnown(code)) code!! else Currencies.default.code
        }
    }
}
