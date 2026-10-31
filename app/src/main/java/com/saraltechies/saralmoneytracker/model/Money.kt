package com.saraltechies.saralmoneytracker.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue

/**
 * A currency the app can show. Amounts are always stored as whole hundredths ("paise"), whatever the
 * currency, so switching currency never changes stored numbers. [decimals] 0 hides the fraction.
 */
data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val decimals: Int = 2,
    /** 1,00,000 instead of 100,000. */
    val indianGrouping: Boolean = false,
)

object Currencies {
    val all = listOf(
        CurrencyInfo("INR", "₹", indianGrouping = true),
        CurrencyInfo("USD", "$"),
        CurrencyInfo("EUR", "€"),
        CurrencyInfo("GBP", "£"),
        CurrencyInfo("AED", "AED "),
        CurrencyInfo("SAR", "SAR "),
        CurrencyInfo("QAR", "QAR "),
        CurrencyInfo("KWD", "KWD "),
        CurrencyInfo("OMR", "OMR "),
        CurrencyInfo("BHD", "BHD "),
        CurrencyInfo("SGD", "S$"),
        CurrencyInfo("AUD", "A$"),
        CurrencyInfo("CAD", "C$"),
        CurrencyInfo("NZD", "NZ$"),
        CurrencyInfo("HKD", "HK$"),
        CurrencyInfo("JPY", "¥", decimals = 0),
        CurrencyInfo("CNY", "CN¥"),
        CurrencyInfo("CHF", "CHF "),
        CurrencyInfo("NPR", "Rs "),
        CurrencyInfo("BDT", "৳"),
        CurrencyInfo("LKR", "Rs "),
        CurrencyInfo("PKR", "Rs "),
        CurrencyInfo("MYR", "RM "),
        CurrencyInfo("THB", "฿"),
        CurrencyInfo("IDR", "Rp ", decimals = 0),
        CurrencyInfo("PHP", "₱"),
        CurrencyInfo("ZAR", "R "),
        CurrencyInfo("KES", "KSh "),
        CurrencyInfo("NGN", "₦"),
    )

    val default: CurrencyInfo = all.first()

    fun of(code: String?): CurrencyInfo = all.firstOrNull { it.code == code } ?: default

    fun isKnown(code: String?): Boolean = all.any { it.code == code }
}

object Money {
    const val SCALE = 100L

    /** "₹1,25,000", "₹1,250.50", "-$12.40"; [signed] adds "+" to positive amounts. */
    fun format(minor: Long, currency: CurrencyInfo, signed: Boolean = false): String {
        val sign = when {
            minor < 0 -> "-"
            signed && minor > 0 -> "+"
            else -> ""
        }
        val abs = minor.absoluteValue
        val whole = group((abs / SCALE).toString(), currency.indianGrouping)
        val fraction = (abs % SCALE).toInt()
        val decimals = if (currency.decimals == 0 || fraction == 0) "" else "." + fraction.toString().padStart(2, '0')
        return sign + currency.symbol + whole + decimals
    }

    /** Number only, for typing: "1250.5" stays editable as "1250.50". */
    fun plain(minor: Long): String {
        val abs = minor.absoluteValue
        val fraction = (abs % SCALE).toInt()
        val text = (abs / SCALE).toString() + if (fraction == 0) "" else "." + fraction.toString().padStart(2, '0')
        return if (minor < 0) "-$text" else text
    }

    /** Short form for chart labels: "₹1.2L", "₹3.5Cr", "$12.5K", "$1.2M". */
    fun compact(minor: Long, currency: CurrencyInfo): String {
        val value = minor.absoluteValue.toDouble() / SCALE
        val sign = if (minor < 0) "-" else ""
        val (divisor, suffix) = when {
            currency.indianGrouping && value >= 1e7 -> 1e7 to "Cr"
            currency.indianGrouping && value >= 1e5 -> 1e5 to "L"
            !currency.indianGrouping && value >= 1e9 -> 1e9 to "B"
            !currency.indianGrouping && value >= 1e6 -> 1e6 to "M"
            value >= 1e3 -> 1e3 to "K"
            else -> return sign + currency.symbol + group(value.toLong().toString(), currency.indianGrouping)
        }
        val scaled = BigDecimal(value / divisor).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return sign + currency.symbol + scaled + suffix
    }

    /** Parses "1,250.50" or "1250" to hundredths; null when it isn't a number. */
    fun parse(text: String): Long? {
        val cleaned = text.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        return runCatching { toMinor(BigDecimal(cleaned)) }.getOrNull()
    }

    fun toMinor(value: BigDecimal): Long = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

    fun toMinor(value: Double): Long = toMinor(BigDecimal.valueOf(value))

    fun group(digits: String, indian: Boolean): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val size = if (indian) 2 else 3
        val groups = rest.reversed().chunked(size).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + last3
    }
}
