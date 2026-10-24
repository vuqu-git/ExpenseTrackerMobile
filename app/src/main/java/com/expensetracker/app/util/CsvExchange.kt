package com.expensetracker.app.util

import com.expensetracker.app.data.Category
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.data.Template
import com.expensetracker.app.data.Trip
import com.expensetracker.app.data.Tx

data class Snapshot(
    val trips: List<Trip>,
    val categories: List<Category>,
    val templates: List<Template>,
    val txs: List<Tx>
)

enum class ImportType(val label: String) {
    TRIPS("Trips"),
    CATEGORIES("Categories"),
    TEMPLATES("Transaction templates"),
    TRANSACTIONS("Transactions")
}

data class RowError(val row: Int, val reason: String)

data class ParsedTrip(val name: String, val currency: String, val start: String?, val end: String?, val status: String)
data class ParsedCategory(val ledgerId: Long, val name: String, val color: String)
data class ParsedTemplate(val ledgerId: Long, val name: String, val categoryId: Long?, val note: String?, val amount: Double?, val direction: String?)
data class ParsedTx(val ledgerId: Long, val date: String, val amount: Double, val direction: String, val categoryId: Long?, val note: String?)

sealed class ImportCheck {
    abstract val errors: List<RowError>
    abstract val fileError: String?
    abstract val validCount: Int
    val isOk: Boolean get() = fileError == null && errors.isEmpty() && validCount > 0

    data class Trips(val rows: List<ParsedTrip>, override val errors: List<RowError>, override val fileError: String? = null) : ImportCheck() {
        override val validCount: Int get() = rows.size
    }
    data class Categories(val rows: List<ParsedCategory>, override val errors: List<RowError>, override val fileError: String? = null) : ImportCheck() {
        override val validCount: Int get() = rows.size
    }
    data class Templates(val rows: List<ParsedTemplate>, override val errors: List<RowError>, override val fileError: String? = null) : ImportCheck() {
        override val validCount: Int get() = rows.size
    }
    data class Transactions(val rows: List<ParsedTx>, override val errors: List<RowError>, override val fileError: String? = null) : ImportCheck() {
        override val validCount: Int get() = rows.size
    }
}

private fun resolveLedgerLenient(name: String, snap: Snapshot): Long? {
    val t = name.trim()
    if (t.isEmpty() || t.equals("home", ignoreCase = true)) return HOME_LEDGER
    return snap.trips.firstOrNull { it.name.equals(t, ignoreCase = true) }?.id
}

private fun resolveLedgerStrict(name: String, snap: Snapshot): Long? {
    val t = name.trim()
    if (t.isEmpty()) return null
    if (t.equals("home", ignoreCase = true)) return HOME_LEDGER
    return snap.trips.firstOrNull { it.name.equals(t, ignoreCase = true) }?.id
}

/** Validation + export builders. All-or-nothing rule: any invalid row rejects the whole file. */
object CsvExchange {

    fun check(type: ImportType, raw: String, snap: Snapshot): ImportCheck = when (type) {
        ImportType.TRIPS -> checkTrips(raw, snap)
        ImportType.CATEGORIES -> checkCategories(raw, snap)
        ImportType.TEMPLATES -> checkTemplates(raw, snap)
        ImportType.TRANSACTIONS -> checkTransactions(raw, snap)
    }

    private fun headerOf(table: List<List<String>>): List<String> =
        if (table.isEmpty()) emptyList() else table[0].map { it.trim().lowercase() }

    private fun isBlankRow(cols: List<String>): Boolean = cols.size == 1 && cols[0].isBlank()

    // ---------- TRIPS: name;currency;start_date;end_date;status ----------
    private fun checkTrips(raw: String, snap: Snapshot): ImportCheck.Trips {
        val table = Csv.parse(raw)
        if (table.isEmpty()) return ImportCheck.Trips(emptyList(), emptyList(), "File is empty")
        if (headerOf(table) != listOf("name", "currency", "start_date", "end_date", "status"))
            return ImportCheck.Trips(emptyList(), emptyList(),
                "Unexpected header — expected 'name;currency;start_date;end_date;status'")
        val rows = ArrayList<ParsedTrip>()
        val errors = ArrayList<RowError>()
        val existing = snap.trips.map { it.name.lowercase() }.toSet()
        for (idx in 1 until table.size) {
            val cols = table[idx]
            if (isBlankRow(cols)) continue
            val probs = ArrayList<String>()
            if (cols.size != 5) probs.add("expected 5 columns, found ${cols.size}")
            var row: ParsedTrip? = null
            if (cols.size == 5) {
                val name = cols[0].trim()
                val cur = cols[1].trim()
                val status = cols[4].trim()
                if (name.isEmpty()) probs.add("name is empty")
                if (name.lowercase() == "home") probs.add("trip name must not be 'home' (reserved)")
                if (name.lowercase() in existing) probs.add("a trip with this name already exists")
                if (!Currencies.isValid(cur)) probs.add("currency '$cur' is not in the app's currency list")
                var s: String? = null
                var e: String? = null
                val sv = cols[2].trim()
                if (sv.isNotEmpty()) { s = parseIsoDate(sv); if (s == null) probs.add("start_date is not a valid ISO date (YYYY-MM-DD)") }
                val ev = cols[3].trim()
                if (ev.isNotEmpty()) { e = parseIsoDate(ev); if (e == null) probs.add("end_date is not a valid ISO date (YYYY-MM-DD)") }
                if (status != "open" && status != "closed") probs.add("status must be 'open' or 'closed'")
                if (probs.isEmpty()) row = ParsedTrip(name, cur, s, e, status)
            }
            if (row != null) rows.add(row) else errors.add(RowError(idx + 1, probs.joinToString("; ")))
        }
        if (rows.isEmpty() && errors.isEmpty()) return ImportCheck.Trips(rows, errors, "File contains no data rows")
        return ImportCheck.Trips(rows, errors)
    }

    // ---------- CATEGORIES: name;color;ledger ----------
    private fun checkCategories(raw: String, snap: Snapshot): ImportCheck.Categories {
        val table = Csv.parse(raw)
        if (table.isEmpty()) return ImportCheck.Categories(emptyList(), emptyList(), "File is empty")
        if (headerOf(table) != listOf("name", "color", "ledger"))
            return ImportCheck.Categories(emptyList(), emptyList(), "Unexpected header — expected 'name;color;ledger'")
        val rows = ArrayList<ParsedCategory>()
        val errors = ArrayList<RowError>()
        for (idx in 1 until table.size) {
            val cols = table[idx]
            if (isBlankRow(cols)) continue
            val probs = ArrayList<String>()
            if (cols.size != 3) probs.add("expected 3 columns, found ${cols.size}")
            var row: ParsedCategory? = null
            if (cols.size == 3) {
                val name = cols[0].trim()
                val color = cols[1].trim()
                val ledger = resolveLedgerStrict(cols[2], snap)
                if (name.isEmpty()) probs.add("name is empty")
                if (ledger == null) probs.add("ledger '${cols[2].trim()}' is not 'home' and does not match an existing trip")
                else if (snap.categories.any { it.ledgerId == ledger && it.name.equals(name, ignoreCase = true) })
                    probs.add("a category with this name already exists in this ledger")
                if (!validHex(color)) probs.add("color '$color' is not a valid hex code (#RRGGBB)")
                if (probs.isEmpty()) row = ParsedCategory(ledger!!, name, color)
            }
            if (row != null) rows.add(row) else errors.add(RowError(idx + 1, probs.joinToString("; ")))
        }
        if (rows.isEmpty() && errors.isEmpty()) return ImportCheck.Categories(rows, errors, "File contains no data rows")
        return ImportCheck.Categories(rows, errors)
    }

    // ---------- TEMPLATES: name;category;note;amount;direction;ledger ----------
    private fun checkTemplates(raw: String, snap: Snapshot): ImportCheck.Templates {
        val table = Csv.parse(raw)
        if (table.isEmpty()) return ImportCheck.Templates(emptyList(), emptyList(), "File is empty")
        if (headerOf(table) != listOf("name", "category", "note", "amount", "direction", "ledger"))
            return ImportCheck.Templates(emptyList(), emptyList(),
                "Unexpected header — expected 'name;category;note;amount;direction;ledger'")
        val rows = ArrayList<ParsedTemplate>()
        val errors = ArrayList<RowError>()
        for (idx in 1 until table.size) {
            val cols = table[idx]
            if (isBlankRow(cols)) continue
            val probs = ArrayList<String>()
            if (cols.size != 6) probs.add("expected 6 columns, found ${cols.size}")
            var row: ParsedTemplate? = null
            if (cols.size == 6) {
                val name = cols[0].trim()
                val ledger = resolveLedgerStrict(cols[5], snap)
                if (name.isEmpty()) probs.add("name is empty")
                if (ledger == null) probs.add("ledger '${cols[5].trim()}' is not 'home' and does not match an existing trip")
                else if (snap.templates.any { it.ledgerId == ledger && it.name.equals(name, ignoreCase = true) })
                    probs.add("a template with this name already exists in this ledger")
                var catId: Long? = null
                val catName = cols[1].trim()
                if (catName.isNotEmpty()) {
                    val found = ledger?.let { l -> snap.categories.firstOrNull { it.ledgerId == l && it.name.equals(catName, ignoreCase = true) } }
                    if (found == null) probs.add("category '$catName' does not exist in this ledger") else catId = found.id
                }
                val amountStr = cols[3].trim()
                var amount: Double? = null
                if (amountStr.isNotEmpty()) {
                    amount = parsePositiveAmount(amountStr)
                    if (amount == null) probs.add("amount must be a positive number")
                }
                val dir = cols[4].trim()
                if (dir.isNotEmpty() && dir != "income" && dir != "expense") probs.add("direction must be 'income' or 'expense'")
                if (probs.isEmpty()) row = ParsedTemplate(ledger!!, name, catId, cols[2].ifBlank { null }, amount, dir.ifEmpty { null })
            }
            if (row != null) rows.add(row) else errors.add(RowError(idx + 1, probs.joinToString("; ")))
        }
        if (rows.isEmpty() && errors.isEmpty()) return ImportCheck.Templates(rows, errors, "File contains no data rows")
        return ImportCheck.Templates(rows, errors)
    }

    // ---------- TRANSACTIONS: date;amount;category;note;direction;ledger ----------
    // A 5-column header (missing ledger column) is accepted; those rows map to the home ledger.
    private fun checkTransactions(raw: String, snap: Snapshot): ImportCheck.Transactions {
        val table = Csv.parse(raw)
        if (table.isEmpty()) return ImportCheck.Transactions(emptyList(), emptyList(), "File is empty")
        val header = headerOf(table)
        val nCols = when (header) {
            listOf("date", "amount", "category", "note", "direction", "ledger") -> 6
            listOf("date", "amount", "category", "note", "direction") -> 5
            else -> return ImportCheck.Transactions(emptyList(), emptyList(),
                "Unexpected header — expected 'date;amount;category;note;direction;ledger'")
        }
        val rows = ArrayList<ParsedTx>()
        val errors = ArrayList<RowError>()
        for (idx in 1 until table.size) {
            val cols = table[idx]
            if (isBlankRow(cols)) continue
            val probs = ArrayList<String>()
            if (cols.size != nCols) probs.add("expected $nCols columns, found ${cols.size}")
            var row: ParsedTx? = null
            if (cols.size == nCols) {
                val dateStr = cols[0].trim()
                var date: String? = null
                if (parseIsoDate(dateStr) == null) probs.add("date is not a valid ISO calendar date (YYYY-MM-DD)") else date = dateStr
                val amountStr = cols[1].trim()
                var amount: Double? = null
                val am = parsePositiveAmount(amountStr)
                if (am == null) probs.add("amount must be a positive number") else amount = am
                val dir = cols[4].trim()
                if (dir != "income" && dir != "expense") probs.add("direction must be 'income' or 'expense'")
                val ledgerName = if (nCols == 6) cols[5].trim() else ""
                val ledger = resolveLedgerLenient(ledgerName, snap)
                if (ledger == null) probs.add("ledger '$ledgerName' is not 'home' and does not match an existing trip")
                val catName = cols[2].trim()
                var catId: Long? = null
                if (catName.isNotEmpty()) {
                    val found = ledger?.let { l -> snap.categories.firstOrNull { it.ledgerId == l && it.name.equals(catName, ignoreCase = true) } }
                    if (found == null) probs.add("category '$catName' does not exist in this ledger") else catId = found.id
                }
                if (probs.isEmpty() && date != null && amount != null && ledger != null)
                    row = ParsedTx(ledger, date, amount, dir, catId, cols[3].ifBlank { null })
            }
            if (row != null) rows.add(row) else errors.add(RowError(idx + 1, probs.joinToString("; ")))
        }
        if (rows.isEmpty() && errors.isEmpty()) return ImportCheck.Transactions(rows, errors, "File contains no data rows")
        return ImportCheck.Transactions(rows, errors)
    }
}

/** CSV export builders (complete sets / scoped transaction exports). */
object CsvExport {

    private fun ledgerNameOf(ledgerId: Long, trips: List<Trip>): String =
        if (ledgerId == HOME_LEDGER) "home" else (trips.firstOrNull { it.id == ledgerId }?.name ?: "home")

    fun transactions(txs: List<Tx>, snap: Snapshot): String {
        val sb = StringBuilder("date;amount;category;note;direction;ledger\n")
        val catNames = snap.categories.associate { it.id to it.name }
        txs.sortedWith(compareBy({ it.date }, { it.id })).forEach { t ->
            sb.append(Csv.line(listOf(
                t.date,
                Currencies.plain(t.amount),
                t.categoryId?.let { catNames[it] } ?: "",
                t.note ?: "",
                t.direction,
                ledgerNameOf(t.ledgerId, snap.trips)
            ))).append('\n')
        }
        return sb.toString()
    }

    fun categories(snap: Snapshot): String {
        val sb = StringBuilder("name;color;ledger\n")
        snap.categories.sortedBy { it.name.lowercase() }.forEach {
            sb.append(Csv.line(listOf(it.name, it.color, ledgerNameOf(it.ledgerId, snap.trips)))).append('\n')
        }
        return sb.toString()
    }

    fun templates(snap: Snapshot): String {
        val sb = StringBuilder("name;category;note;amount;direction;ledger\n")
        val catNames = snap.categories.associate { it.id to it.name }
        snap.templates.sortedBy { it.name.lowercase() }.forEach {
            sb.append(Csv.line(listOf(
                it.name,
                it.categoryId?.let { c -> catNames[c] } ?: "",
                it.note ?: "",
                it.amount?.let { a -> Currencies.plain(a) } ?: "",
                it.direction ?: "",
                ledgerNameOf(it.ledgerId, snap.trips)
            ))).append('\n')
        }
        return sb.toString()
    }

    fun trips(snap: Snapshot): String {
        val sb = StringBuilder("name;currency;start_date;end_date;status\n")
        snap.trips.sortedBy { it.name.lowercase() }.forEach {
            sb.append(Csv.line(listOf(it.name, it.currency, it.startDate ?: "", it.endDate ?: "", it.status))).append('\n')
        }
        return sb.toString()
    }
}
