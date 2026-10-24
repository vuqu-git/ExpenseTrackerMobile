package com.expensetracker.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** ISO 4217 currency list + money formatting (decimals per currency, e.g. 2 EUR / 0 VND). */
object Currencies {
    val all: List<Currency> = Currency.getAvailableCurrencies().sortedBy { it.currencyCode }
    private val byCode: Map<String, Currency> = all.associateBy { it.currencyCode }

    fun isValid(code: String): Boolean = byCode.containsKey(code)
    fun display(code: String): String = byCode[code]?.getDisplayName(Locale.ENGLISH) ?: code
    fun symbol(code: String): String = byCode[code]?.getSymbol(Locale.US) ?: code
    fun decimals(code: String): Int {
        val d = byCode[code]?.defaultFractionDigits ?: 2
        return if (d < 0) 2 else d
    }

    private val suffixSymbols = setOf("zł", "kr", "Kč", "Ft", "lei", "лв", "дин")

    fun format(amount: Double, code: String): String {
        val sym = symbol(code)
        val dec = decimals(code)
        val nf = NumberFormat.getNumberInstance(Locale.US)
        nf.isGroupingUsed = true
        nf.minimumFractionDigits = dec
        nf.maximumFractionDigits = dec
        val s = nf.format(kotlin.math.abs(amount))
        val body = if (suffixSymbols.contains(sym)) "$s $sym" else "$sym$s"
        return if (amount < 0) "−$body" else body
    }

    fun formatSigned(amount: Double, code: String): String =
        (if (amount < 0) "−" else "+") + format(kotlin.math.abs(amount), code)

    fun short(amount: Double, code: String): String {
        val sym = symbol(code)
        val a = kotlin.math.abs(amount)
        return when {
            a >= 1_000_000 -> "$sym${BigDecimal(a / 1_000_000).setScale(1, RoundingMode.HALF_UP).toPlainString()}M"
            a >= 10_000 -> "$sym${BigDecimal(a / 1_000).setScale(1, RoundingMode.HALF_UP).toPlainString()}k"
            else -> format(a, code)
        }
    }

    fun roundTo(v: Double, code: String): Double =
        BigDecimal(v).setScale(decimals(code), RoundingMode.HALF_UP).toDouble()

    /** Plain positive number string for CSV (no symbol, point decimal separator). */
    fun plain(v: Double): String = BigDecimal(v).stripTrailingZeros().toPlainString()
}

object Dates {
    val DAY_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
    val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    val DAY_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    val MONTH_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December")
    val MONTHS_SHORT = MONTHS.map { it.take(3) }

    fun fmtDayLong(iso: String): String = runCatching { DAY_LONG.format(LocalDate.parse(iso)) }.getOrDefault(iso)
    fun fmtDayShort(iso: String): String = runCatching { DAY_SHORT.format(LocalDate.parse(iso)) }.getOrDefault(iso)
    fun fmtDayFull(iso: String): String = runCatching { DAY_FULL.format(LocalDate.parse(iso)) }.getOrDefault(iso)
    fun fmtMonth(ym: String): String = runCatching { MONTH_YEAR.format(YearMonth.parse(ym)) }.getOrDefault(ym)
    fun fmtMonthShort(ym: String): String = runCatching { MONTH_SHORT.format(YearMonth.parse(ym)) }.getOrDefault(ym)
}
