package com.saraltechies.saralmoneytracker

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralmoneytracker.data.LoanTerms
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.ui.setup.WHATS_NEW_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Taps through the real screens: change a loan's date both ways and read the interest it shows. */
@RunWith(AndroidJUnit4::class)
class LoanDatesUiTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as SaralMoneyTrackerApp
    private val today = LocalDate.now()
    private fun s(id: Int) = context.getString(id)

    @Test
    fun interestFollowsTheDateOnScreen() {
        val id = runBlocking {
            val repo = app.repository
            repo.deleteAll()
            app.settings.setCurrency("INR")
            app.settings.setSetupDone()
            app.settings.setWhatsNewSeen(WHATS_NEW_VERSION)
            app.settings.setBook(Books.ALL)
            val cash = repo.accounts(book = Books.ALL).first { it.type == AccountType.CASH }.id
            // ₹10,000 at 12% a year is ₹100 a month.
            repo.createLoan(
                LoanTerms(
                    name = "Date Test", direction = LoanDirection.LENT, startDate = today.minusMonths(2), rate = 1200, rateUnit = RateUnit.YEAR,
                    plan = LoanPlan.FLEXIBLE, installments = 0, installment = 0, note = "",
                ),
                1_000_000, cash, Contact(name = "Date Test"),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            click(s(R.string.tab_accounts))
            click(s(R.string.loans_title))
            click("Date Test")
            see("₹200")
            shot("loan-date-1-two-months")

            // The history's "Given" entry, moved to five months ago.
            click(s(R.string.loan_event_given))
            click(s(R.string.date))
            pickDate(today.minusMonths(5))
            click(s(R.string.save))
            see("₹500")
            shot("loan-date-2-entry-moved")
            assertEquals(today.minusMonths(5), app.repository.loanItem(id)!!.loan.startDate)

            // The date on the loan's own form, moved to three months ago.
            click(s(R.string.edit))
            click(s(R.string.loan_date_given))
            pickDate(today.minusMonths(3))
            click(s(R.string.save))
            see("₹300")
            shot("loan-date-3-form-moved")
            assertEquals(today.minusMonths(3), app.repository.loanItem(id)!!.txns.single().date)
        }
    }

    private fun pickDate(date: LocalDate) {
        click("Switch to text input mode")
        val field = hasSetTextAction() and hasText("Date")
        wait(field)
        compose.onAllNodes(field).onFirst().performTextReplacement(date.format(DateTimeFormatter.ofPattern("MMddyyyy")))
        compose.waitForIdle()
        click(s(R.string.ok))
    }

    private fun see(text: String) = wait(hasText(text))

    private fun click(text: String) {
        val matcher = (hasText(text) or hasContentDescription(text)) and hasClickAction()
        wait(matcher)
        compose.onAllNodes(matcher).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun wait(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("loan-date-failed")
            val dump = compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n\n") { i ->
                runCatching { compose.onAllNodes(isRoot())[i].printToString(maxDepth = 30) }.getOrElse { it.toString() }
            }
            File(dir, "loan-date-failed.txt").writeText("Looking for: ${matcher.description}\n\n$dump")
            throw e
        }
    }

    private val dir get() = File(context.getExternalFilesDir(null), "preview").apply { mkdirs() }

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
