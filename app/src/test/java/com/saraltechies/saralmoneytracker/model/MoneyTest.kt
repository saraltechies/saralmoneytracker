package com.saraltechies.saralmoneytracker.model

import com.saraltechies.saralmoneytracker.ui.components.KeypadInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    private val inr = Currencies.of("INR")
    private val usd = Currencies.of("USD")
    private val jpy = Currencies.of("JPY")

    @Test
    fun formatsRupeesWithIndianGrouping() {
        assertEquals("₹1,23,45,678", Money.format(1_234_567_800, inr))
        assertEquals("₹1,00,000", Money.format(10_000_000, inr))
        assertEquals("₹999", Money.format(99_900, inr))
        assertEquals("₹1,250.50", Money.format(125_050, inr))
        assertEquals("₹0.05", Money.format(5, inr))
    }

    @Test
    fun formatsOtherCurrenciesWithThousands() {
        assertEquals("$12,345,678", Money.format(1_234_567_800, usd))
        assertEquals("¥1,250", Money.format(125_050, jpy))
    }

    @Test
    fun signs() {
        assertEquals("-₹120", Money.format(-12_000, inr))
        assertEquals("+₹120", Money.format(12_000, inr, signed = true))
        assertEquals("₹0", Money.format(0, inr, signed = true))
    }

    @Test
    fun compactLabels() {
        assertEquals("₹1.2L", Money.compact(12_000_000, inr))
        assertEquals("₹3.5Cr", Money.compact(3_500_000_000, inr))
        assertEquals("₹12.5K", Money.compact(1_250_000, inr))
        assertEquals("₹950", Money.compact(95_000, inr))
        assertEquals("$1.2M", Money.compact(120_000_000, usd))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(125_050L, Money.parse("1,250.50"))
        assertEquals(125_050L, Money.parse("1250.5"))
        assertEquals(1L, Money.parse("0.005"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertEquals("1250.50", Money.plain(125_050))
        assertEquals("1250", Money.plain(125_000))
    }

    @Test
    fun keypadExpressions() {
        assertEquals(41_500L, AmountExpression.evaluate("250+120+45"))
        assertEquals(30_000L, AmountExpression.evaluate("1200/4"))
        assertEquals(1_400L, AmountExpression.evaluate("2+3×4"))
        assertEquals(50_000L, AmountExpression.evaluate("1000-500"))
        assertEquals(3_333L, AmountExpression.evaluate("100÷3"))
        assertNull(AmountExpression.evaluate("12+"))
        assertNull(AmountExpression.evaluate("5÷0"))
        assertNull(AmountExpression.evaluate(""))
    }

    @Test
    fun keypadTypingRules() {
        var s = ""
        for (k in "0012.345") s = KeypadInput.press(s, k)
        assertEquals("12.34", s)
        assertEquals("", KeypadInput.press("", '+'))
        assertEquals("12×", KeypadInput.press("12+", '×'))
        assertEquals("12+0.", KeypadInput.press("12+", '.'))
        assertEquals("12.5", KeypadInput.press("12.5", '.'))
    }
}
