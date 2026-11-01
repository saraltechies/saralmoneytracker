package com.saraltechies.saralmoneytracker.model

import java.time.LocalDate

enum class TxnType { EXPENSE, INCOME, TRANSFER }

/** [LOAN] accounts belong to a [Loan]: they're kept out of the account lists and shown on the Loans screen instead. */
enum class AccountType {
    CASH, BANK, CARD, WALLET, OTHER, LOAN,

    /** A customer's or supplier's running account in the credit ledger ([Party]). */
    PARTY,
    ;

    /** Loan and party accounts belong to their own screens, not to account lists and pickers. */
    val isHidden: Boolean get() = this == LOAN || this == PARTY
}

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** Where money sits: cash, a bank account, a credit card (its balance goes negative as you spend), a UPI wallet. */
data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** Balance on the day tracking started, in hundredths. */
    val opening: Long = 0,
    val archived: Boolean = false,
    val sort: Int = 0,
    /** The [Book] it belongs to. */
    val bookId: Long = Books.PERSONAL_ID,
)

data class Category(
    val id: Long = 0,
    val name: String,
    /** [TxnType.EXPENSE] or [TxnType.INCOME]. */
    val kind: TxnType,
    val icon: String,
    /** ARGB. */
    val color: Int,
    /** Monthly budget in hundredths, expense categories only. */
    val budget: Long? = null,
    val archived: Boolean = false,
    val sort: Int = 0,
    /** The [Book] it belongs to; its income or expense counts there. */
    val bookId: Long = Books.PERSONAL_ID,
)

/**
 * One entry. Transfers move money between two of your own accounts (paying a credit card bill, withdrawing cash,
 * moving to savings), so they never count as spending or earning.
 */
data class Txn(
    val id: Long = 0,
    val type: TxnType,
    /** Always positive, in hundredths. */
    val amount: Long,
    val date: LocalDate,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long? = null,
    val note: String = "",
    val recurringId: Long? = null,
    val createdAt: Long = 0,
    /** Set on entries that belong to a [Loan]: money given, paid back, or interest. */
    val loanId: Long? = null,
)

/** A repeating entry (salary, rent, EMI, SIP, subscriptions). Due copies are added automatically. */
data class Recurring(
    val id: Long = 0,
    val type: TxnType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long? = null,
    val note: String = "",
    val frequency: Frequency,
    val startDate: LocalDate,
    /** The next date a copy will be added. */
    val nextDate: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    /** Days before each date to send a reminder (to pay the rent, the EMI, the card bill); 0 for none. */
    val remindDays: Int = 0,
)
