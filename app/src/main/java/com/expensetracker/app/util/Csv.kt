package com.expensetracker.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** RFC 4180 helpers with semicolon delimiter (spec: `;` delimiter, point decimal, ISO dates). */
object Csv {

    fun escape(field: String): String =
        if (field.any { it == ';' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\""
        else field

    fun line(row: List<String>): String = row.joinToString(";") { escape(it) }

    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var field = StringBuilder()
        var row = ArrayList<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> if (field.isEmpty()) inQuotes = true else field.append(c)
                ';' -> { row.add(field.toString()); field = StringBuilder() }
                '\r' -> { /* CRLF tolerated */ }
                '\n' -> { row.add(field.toString()); rows.add(row); row = ArrayList(); field = StringBuilder() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}

fun parseIsoDate(s: String): String? {
    val t = s.trim()
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(t)) return null
    return try { LocalDate.parse(t).toString() } catch (e: Exception) { null }
}

fun validHex(s: String): Boolean = Regex("^#[0-9A-Fa-f]{6}$").matches(s.trim())

fun parsePositiveAmount(s: String): Double? {
    val t = s.trim()
    if (!Regex("^\\d+(\\.\\d+)?$").matches(t)) return null
    val v = t.toDouble()
    return if (v > 0.0) v else null
}

/** Standardized export filenames (FR-CS-01 / FR-BK-01). */
object FileNames {
    private val D = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private fun today() = LocalDate.now().format(D)

    fun allLedgers() = "expense_all_ledgers_${today()}.csv"
    fun homeRange(from: String, to: String) = "expense_home_${from.replace("-", "")}_to_${to.replace("-", "")}.csv"
    fun trip(name: String) = "expense_trip_${sanitize(name)}_${today()}.csv"
    fun categories() = "expense_categories_${today()}.csv"
    fun templates() = "expense_templates_${today()}.csv"
    fun tripsCsv() = "expense_trips_${today()}.csv"
    fun backup() = "expense_backup_${LocalDateTime.now().format(DT)}.json"

    private fun sanitize(s: String) = buildString { s.forEach { ch -> append(if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_') } }
}
