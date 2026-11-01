package com.saraltechies.saralmoneytracker.model

/** A personal book, or a business's books (a proprietor's shop or firm). */
enum class BookKind { PERSONAL, BUSINESS }

/**
 * A separate set of accounts, categories, budgets and loans: your personal money, and each business you run.
 * Income and expenses count in the book of their category; balances in the book of their account. So a business
 * bill paid from your personal account is a business expense, and money moved between books is capital you put in
 * or money you took out, never income or spending.
 */
data class Book(
    val id: Long = 0,
    val name: String,
    val kind: BookKind = BookKind.PERSONAL,
    val sort: Int = 0,
)

object Books {
    /** The first book, created with the app; everything from before books existed belongs to it. */
    const val PERSONAL_ID = 1L

    /** Means "every book" where a book id is expected. */
    const val ALL = 0L

    /** What a transfer between two books is, for the person who owns both. */
    enum class Flow { SAME_BOOK, CAPITAL_IN, DRAWINGS, BETWEEN }

    fun flow(from: Book?, to: Book?): Flow = when {
        from == null || to == null || from.id == to.id -> Flow.SAME_BOOK
        from.kind == BookKind.PERSONAL && to.kind == BookKind.BUSINESS -> Flow.CAPITAL_IN
        from.kind == BookKind.BUSINESS && to.kind == BookKind.PERSONAL -> Flow.DRAWINGS
        else -> Flow.BETWEEN
    }
}
