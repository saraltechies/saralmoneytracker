package com.saraltechies.saralmoneytracker.data

import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.Loan
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEvent
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanMath
import com.saraltechies.saralmoneytracker.model.Pledge
import com.saraltechies.saralmoneytracker.model.PledgeKind
import com.saraltechies.saralmoneytracker.model.RateUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** One person pledging gold and silver several times, each time at its own rate (girvi). */
class PersonLoansTest {

    private val asOf = LocalDate.of(2026, 7, 1)
    private fun rupees(r: Long) = r * 100
    private val sunita = Contact(id = 7, name = "Sunita")
    private val ramesh = Contact(id = 8, name = "Ramesh")

    private fun loan(
        id: Long, contact: Contact, start: LocalDate, amount: Long, rate: Long, pledge: Pledge?,
        direction: LoanDirection = LoanDirection.LENT, closed: Boolean = false,
    ): LoanItem {
        val l = Loan(
            id = id, accountId = 100 + id, direction = direction, startDate = start, rate = rate, rateUnit = RateUnit.YEAR,
            contactId = contact.id, pledge = pledge, closed = closed,
        )
        val events = listOf(LoanEvent(start, LoanEventKind.GIVEN, amount))
        // A second loan's account is "Sunita (2)"; the account still reads as Sunita.
        return LoanItem(l, contact.name + if (id > 1) " ($id)" else "", events, LoanMath.summary(l, events, asOf), emptyList(), contact)
    }

    private val chain = Pledge(PledgeKind.GOLD, items = "1 chain", weightMg = 10_250, purity = "22K", value = rupees(70_000))
    private val bangles = Pledge(PledgeKind.GOLD, items = "2 bangles", weightMg = 15_000, purity = "22K", value = rupees(1_00_000))
    private val anklets = Pledge(PledgeKind.SILVER, items = "Anklets", weightMg = 200_000, value = rupees(18_000))

    private val items = listOf(
        loan(1, sunita, LocalDate.of(2026, 1, 1), rupees(50_000), 1200, chain),
        loan(2, ramesh, LocalDate.of(2026, 2, 1), rupees(5_000), 0, null),
        loan(3, sunita, LocalDate.of(2026, 3, 1), rupees(30_000), 1800, bangles),
        loan(4, sunita, LocalDate.of(2026, 4, 1), rupees(20_000), 1000, anklets),
    )

    @Test
    fun eachPledgeKeepsItsOwnRate() {
        val s = PersonLoans.group(items).first()
        assertEquals("Sunita", s.name)
        assertEquals(3, s.loans.size)
        // 12% a year on 50,000 for 6 months, 18% on 30,000 for 4, 10% on 20,000 for 3.
        assertEquals(listOf(rupees(3_000), rupees(1_800), rupees(500)), s.loans.map { it.summary.interestDue })
        assertEquals(rupees(1_00_000), s.principalLeft)
        assertEquals(rupees(5_300), s.interestDue)
        assertEquals(rupees(1_05_300), s.toReceive)
        assertEquals(0L, s.toPay)
    }

    @Test
    fun goldAndSilverHeldAddUp() {
        val held = PersonLoans.group(items).first().heldByYou
        assertEquals(25_250L, held.goldMg)
        assertEquals(200_000L, held.silverMg)
        assertEquals(rupees(1_88_000), held.value)
        assertEquals(3, held.count)
        assertTrue(PersonLoans.group(items).first().pledgedByYou.isEmpty)
    }

    @Test
    fun returnedItemsAndClosedLoansAreNotHeld() {
        val list = listOf(
            loan(1, sunita, LocalDate.of(2026, 1, 1), rupees(50_000), 1200, chain.copy(returnedOn = LocalDate.of(2026, 6, 1))),
            loan(3, sunita, LocalDate.of(2026, 3, 1), rupees(30_000), 1800, bangles, closed = true),
            loan(4, sunita, LocalDate.of(2026, 4, 1), rupees(20_000), 1000, anklets),
        )
        val p = PersonLoans.group(list).single()
        assertEquals(0L, p.heldByYou.goldMg)
        assertEquals(200_000L, p.heldByYou.silverMg)
        assertEquals(2, p.openCount)
    }

    @Test
    fun peopleAreGroupedByContact() {
        val groups = PersonLoans.group(items)
        assertEquals(listOf("Sunita", "Ramesh"), groups.map { it.name })
        assertEquals(listOf("c7", "c8"), groups.map { it.key })
        assertEquals(1, groups[1].loans.size)
    }

    @Test
    fun givenAndTakenStayApart() {
        val list = items + loan(5, sunita, LocalDate.of(2026, 5, 1), rupees(10_000), 0, null, direction = LoanDirection.BORROWED)
        val s = PersonLoans.group(list).first()
        assertEquals(rupees(1_05_300), s.toReceive)
        assertEquals(rupees(10_000), s.toPay)
    }

    @Test
    fun gramsReadAndWrite() {
        assertEquals(10_250L, Pledge.parseGrams("10.25"))
        assertEquals(10_500L, Pledge.parseGrams("10,5"))
        assertEquals(0L, Pledge.parseGrams(" "))
        assertNull(Pledge.parseGrams("ten"))
        assertNull(Pledge.parseGrams("1.2.3"))
        assertEquals("10.25", Pledge.grams(10_250))
        assertEquals("8", Pledge.grams(8_000))
        assertEquals("0.5", Pledge.grams(500))
    }
}
