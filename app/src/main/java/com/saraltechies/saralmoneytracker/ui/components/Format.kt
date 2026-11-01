package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.Period
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.PeriodPrefs
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

val LocalCurrency = staticCompositionLocalOf { Currencies.default }
val LocalPeriodPrefs = staticCompositionLocalOf { PeriodPrefs() }

@Composable
@ReadOnlyComposable
fun money(minor: Long, signed: Boolean = false): String = Money.format(minor, LocalCurrency.current, signed)

@Composable
@ReadOnlyComposable
fun moneyCompact(minor: Long): String = Money.compact(minor, LocalCurrency.current)

@Composable
@ReadOnlyComposable
fun typeColor(type: TxnType): Color = when (type) {
    TxnType.INCOME -> AppTheme.money.income
    TxnType.EXPENSE -> AppTheme.money.expense
    TxnType.TRANSFER -> AppTheme.money.transfer
}

object Dates {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val weekday = DateTimeFormatter.ofPattern("EEEE")

    fun medium(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun short(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (date.year == today.year) date.format(dayMonth) else date.format(dayMonthYear)

    fun weekday(date: LocalDate): String = date.format(weekday)

    /** "Today", "Yesterday", or "Monday, 3 Nov". */
    @Composable
    fun relative(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> stringResource(R.string.today)
            today.minusDays(1) -> stringResource(R.string.yesterday)
            today.plusDays(1) -> stringResource(R.string.tomorrow)
            else -> weekday(date) + ", " + short(date, today)
        }
    }

    @Composable
    fun period(p: Period): String {
        val today = LocalDate.now()
        return when (p.kind) {
            PeriodKind.DAY -> relative(p.start)
            PeriodKind.MONTH -> if (p.start.dayOfMonth == 1) p.start.format(monthYear).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            else range(p.start, p.end, today)
            PeriodKind.YEAR -> p.start.year.toString()
            PeriodKind.WEEK -> if (today in p) stringResource(R.string.this_week) else range(p.start, p.end, today)
            PeriodKind.CUSTOM -> range(p.start, p.end, today)
        }
    }

    private fun range(start: LocalDate, end: LocalDate, today: LocalDate): String =
        if (start.year == end.year && start.year == today.year) "${start.format(dayMonth)} – ${end.format(dayMonth)}"
        else "${start.format(dayMonthYear)} – ${end.format(dayMonthYear)}"
}

fun accountTypeLabel(type: AccountType): Int = when (type) {
    AccountType.CASH -> R.string.account_type_cash
    AccountType.BANK -> R.string.account_type_bank
    AccountType.CARD -> R.string.account_type_card
    AccountType.WALLET -> R.string.account_type_wallet
    AccountType.OTHER -> R.string.account_type_other
    AccountType.LOAN -> R.string.account_type_loan
}

fun frequencyLabel(f: Frequency): Int = when (f) {
    Frequency.DAILY -> R.string.repeat_daily
    Frequency.WEEKLY -> R.string.repeat_weekly
    Frequency.MONTHLY -> R.string.repeat_monthly
    Frequency.YEARLY -> R.string.repeat_yearly
}

fun typeLabel(type: TxnType): Int = when (type) {
    TxnType.EXPENSE -> R.string.type_expense
    TxnType.INCOME -> R.string.type_income
    TxnType.TRANSFER -> R.string.type_transfer
}

fun currencyName(c: CurrencyInfo): String =
    runCatching { java.util.Currency.getInstance(c.code).getDisplayName(Locale.getDefault()) }.getOrDefault(c.code)
