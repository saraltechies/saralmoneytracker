package com.saraltechies.saralmoneytracker.model

import java.math.BigDecimal
import java.math.MathContext

/**
 * Evaluates what was typed on the amount keypad, so "250+120+45" or "1200/4" can be entered directly.
 * Supports + - × ÷ (and * /) with the usual precedence. Returns hundredths, or null for an incomplete or
 * invalid expression (a trailing operator, division by zero).
 */
object AmountExpression {

    const val OPERATORS = "+-×÷*/"

    fun evaluate(expression: String): Long? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty()) return null
        // Shunting-yard is overkill for two precedence levels: fold × and ÷ first, then + and -.
        val terms = mutableListOf<BigDecimal>()
        val signs = mutableListOf<Char>()
        var current: BigDecimal? = null
        var pendingMul: Char? = null
        var expectNumber = true
        for (token in tokens) {
            if (expectNumber) {
                val number = token as? BigDecimal ?: return null
                current = when (pendingMul) {
                    null -> number
                    '×', '*' -> current!!.multiply(number)
                    else -> if (number.signum() == 0) return null else current!!.divide(number, MathContext.DECIMAL64)
                }
                pendingMul = null
                expectNumber = false
            } else {
                val op = token as? Char ?: return null
                when (op) {
                    '×', '*', '÷', '/' -> pendingMul = op
                    else -> {
                        terms += current!!
                        signs += op
                        current = null
                    }
                }
                expectNumber = true
            }
        }
        if (expectNumber) return null
        terms += current!!
        var total = terms.first()
        for (i in signs.indices) total = if (signs[i] == '+') total.add(terms[i + 1]) else total.subtract(terms[i + 1])
        return runCatching { Money.toMinor(total) }.getOrNull()
    }

    /** True when there's an operator in it, so the keypad can show the running result. */
    fun hasOperator(expression: String): Boolean = expression.drop(1).any { it in OPERATORS }

    private fun tokenize(expression: String): List<Any>? {
        val tokens = mutableListOf<Any>()
        val number = StringBuilder()
        fun flush(): Boolean {
            if (number.isEmpty()) return true
            val value = number.toString().toBigDecimalOrNull() ?: return false
            tokens += value
            number.clear()
            return true
        }
        for ((index, c) in expression.replace(",", "").withIndex()) {
            when {
                c.isDigit() || c == '.' -> number.append(c)
                c.isWhitespace() -> Unit
                c in OPERATORS -> {
                    // A leading minus belongs to the number.
                    if (index == 0 && c == '-') number.append(c) else {
                        if (!flush()) return null
                        tokens += c
                    }
                }
                else -> return null
            }
        }
        return if (flush()) tokens else null
    }
}
