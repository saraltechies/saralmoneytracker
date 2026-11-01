package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.saraltechies.saralmoneytracker.model.Book
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Books

/** The books there are and the one on screen ([Books.ALL] for all), for screens that show or pick a book. */
data class BookState(val books: List<Book> = emptyList(), val current: Long = Books.ALL) {
    /** Book choices only appear once there is more than one book. */
    val multiple: Boolean get() = books.size > 1
    val currentBook: Book? get() = books.firstOrNull { it.id == current }
    val hasBusiness: Boolean get() = books.any { it.kind == BookKind.BUSINESS }

    fun book(id: Long): Book? = books.firstOrNull { it.id == id }

    /** Where something new goes: the book on screen, or the first (Personal) when showing all. */
    val defaultBook: Long get() = currentBook?.id ?: books.firstOrNull()?.id ?: Books.PERSONAL_ID
}

val LocalBooks = staticCompositionLocalOf { BookState() }
