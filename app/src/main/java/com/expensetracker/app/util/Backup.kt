package com.expensetracker.app.util

import com.expensetracker.app.data.Category
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.data.Template
import com.expensetracker.app.data.Trip
import com.expensetracker.app.data.Tx
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

data class BackupData(
    val exportedAt: String,
    val appVersion: String,
    val schemaVersion: Int,
    val homeCurrency: String,
    val trips: List<Trip>,
    val categories: List<Category>,
    val templates: List<Template>,
    val transactions: List<Tx>
)

/** Full relational JSON backup & Strict-Replace restore (FR-BK-01 / FR-BK-02). */
object Backup {
    const val SCHEMA_VERSION = 1
    const val APP_VERSION = "1.0"

    fun build(snap: Snapshot, homeCurrency: String): String {
        val root = JSONObject()
        root.put("schema_version", SCHEMA_VERSION)
        root.put("exported_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
        root.put("app_version", APP_VERSION)
        root.put("settings", JSONObject().put("home_currency", homeCurrency))

        root.put("trips", JSONArray().apply { snap.trips.forEach {
            put(JSONObject()
                .put("id", it.id).put("name", it.name).put("currency", it.currency)
                .put("start_date", it.startDate ?: JSONObject.NULL)
                .put("end_date", it.endDate ?: JSONObject.NULL)
                .put("status", it.status))
        } })

        root.put("categories", JSONArray().apply { snap.categories.forEach {
            put(JSONObject().put("id", it.id).put("ledger_id", it.ledgerId)
                .put("name", it.name).put("color", it.color))
        } })

        root.put("transaction_templates", JSONArray().apply { snap.templates.forEach {
            put(JSONObject().put("id", it.id).put("ledger_id", it.ledgerId).put("name", it.name)
                .put("category_id", it.categoryId ?: JSONObject.NULL)
                .put("note", it.note ?: JSONObject.NULL)
                .put("amount", it.amount ?: JSONObject.NULL)
                .put("direction", it.direction ?: JSONObject.NULL))
        } })

        root.put("transactions", JSONArray().apply { snap.txs.forEach {
            put(JSONObject().put("id", it.id).put("ledger_id", it.ledgerId)
                .put("date", it.date).put("amount", it.amount).put("direction", it.direction)
                .put("note", it.note ?: JSONObject.NULL)
                .put("category_id", it.categoryId ?: JSONObject.NULL))
        } })

        return root.toString(2)
    }

    sealed class ParseResult {
        data class Ok(val data: BackupData) : ParseResult()
        data class Fail(val reason: String) : ParseResult()
    }

    private fun bad(msg: String): Nothing = throw IllegalStateException(msg)

    private fun isoOrNull(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        val s = o.optString(key).trim()
        if (s.isEmpty()) return null
        return parseIsoDate(s) ?: bad("$key '$s' is not a valid ISO date")
    }

    private fun idOrNull(o: JSONObject, key: String): Long? =
        if (!o.has(key) || o.isNull(key)) null else o.optLong(key, -1)

    private fun strOrNull(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.optString(key).ifBlank { null }

    private fun doubleOrNull(o: JSONObject, key: String): Double? =
        if (!o.has(key) || o.isNull(key)) null else o.optDouble(key, Double.NaN).takeIf { !it.isNaN() }

    fun parse(text: String): ParseResult {
        val root = try { JSONObject(text) } catch (e: Exception) {
            return ParseResult.Fail("Malformed JSON: ${e.message}")
        }
        return try {
            val sv = root.optInt("schema_version", -1)
            if (sv != SCHEMA_VERSION)
                bad("Unsupported schema version: $sv. This app supports schema version $SCHEMA_VERSION — the file may come from a newer, incompatible app version.")
            val exportedAt = root.optString("exported_at", "")
            val appVersion = root.optString("app_version", "")
            val settings = root.optJSONObject("settings") ?: bad("Missing 'settings' object")
            val homeCurrency = settings.optString("home_currency", "")
            if (!Currencies.isValid(homeCurrency)) bad("Invalid home currency '$homeCurrency'")

            // ---- trips ----
            val tripsArr = root.optJSONArray("trips") ?: bad("Missing 'trips' array")
            val trips = ArrayList<Trip>()
            val tripIds = HashSet<Long>()
            val tripNames = HashSet<String>()
            for (i in 0 until tripsArr.length()) {
                val o = tripsArr.getJSONObject(i)
                val id = o.optLong("id", -1)
                val name = o.optString("name").trim()
                val cur = o.optString("currency")
                val status = o.optString("status")
                if (id <= 0 || !tripIds.add(id)) bad("trips[$i]: invalid or duplicate id")
                if (name.isEmpty()) bad("trips[$i]: empty name")
                if (name.equals("home", ignoreCase = true)) bad("trips[$i]: name must not be 'home'")
                if (!tripNames.add(name.lowercase())) bad("trips[$i]: duplicate trip name '$name'")
                if (!Currencies.isValid(cur)) bad("trips[$i]: invalid currency '$cur'")
                if (status != "open" && status != "closed") bad("trips[$i]: status must be 'open' or 'closed'")
                trips.add(Trip(id, name, cur, isoOrNull(o, "start_date"), isoOrNull(o, "end_date"), status))
            }

            // ---- categories ----
            val catsArr = root.optJSONArray("categories") ?: bad("Missing 'categories' array")
            val categories = ArrayList<Category>()
            val catIds = HashSet<Long>()
            val catKeys = HashSet<String>()
            for (i in 0 until catsArr.length()) {
                val o = catsArr.getJSONObject(i)
                val id = o.optLong("id", -1)
                val ledgerId = o.optLong("ledger_id", -1)
                val name = o.optString("name").trim()
                val color = o.optString("color")
                if (id <= 0 || !catIds.add(id)) bad("categories[$i]: invalid or duplicate id")
                if (ledgerId != HOME_LEDGER && ledgerId !in tripIds) bad("categories[$i]: references unknown ledger $ledgerId")
                if (name.isEmpty()) bad("categories[$i]: empty name")
                if (!catKeys.add("$ledgerId|${name.lowercase()}")) bad("categories[$i]: duplicate name '$name' in ledger")
                if (!validHex(color)) bad("categories[$i]: invalid color '$color'")
                categories.add(Category(id, ledgerId, name, color))
            }

            // ---- transaction templates ----
            val tplArr = root.optJSONArray("transaction_templates") ?: bad("Missing 'transaction_templates' array")
            val templates = ArrayList<Template>()
            val tplKeys = HashSet<String>()
            for (i in 0 until tplArr.length()) {
                val o = tplArr.getJSONObject(i)
                val id = o.optLong("id", -1)
                val ledgerId = o.optLong("ledger_id", -1)
                val name = o.optString("name").trim()
                if (id <= 0) bad("transaction_templates[$i]: invalid id")
                if (ledgerId != HOME_LEDGER && ledgerId !in tripIds) bad("transaction_templates[$i]: references unknown ledger $ledgerId")
                if (name.isEmpty()) bad("transaction_templates[$i]: empty name")
                if (!tplKeys.add("$ledgerId|${name.lowercase()}")) bad("transaction_templates[$i]: duplicate name '$name' in ledger")
                val categoryId = idOrNull(o, "category_id")
                if (categoryId != null) {
                    val cat = categories.firstOrNull { it.id == categoryId }
                    if (cat == null || cat.ledgerId != ledgerId)
                        bad("transaction_templates[$i]: category reference $categoryId is invalid in this ledger")
                }
                val amount = doubleOrNull(o, "amount")
                if (amount != null && (amount <= 0 || !amount.isFinite())) bad("transaction_templates[$i]: amount must be a positive number")
                val direction = strOrNull(o, "direction")
                if (direction != null && direction != "income" && direction != "expense") bad("transaction_templates[$i]: invalid direction")
                templates.add(Template(id, ledgerId, name, categoryId, strOrNull(o, "note"), amount, direction))
            }

            // ---- transactions ----
            val txArr = root.optJSONArray("transactions") ?: bad("Missing 'transactions' array")
            val transactions = ArrayList<Tx>()
            for (i in 0 until txArr.length()) {
                val o = txArr.getJSONObject(i)
                val id = o.optLong("id", -1)
                val ledgerId = o.optLong("ledger_id", -1)
                val date = o.optString("date")
                if (id <= 0) bad("transactions[$i]: invalid id")
                if (parseIsoDate(date) == null) bad("transactions[$i]: invalid date '$date'")
                if (ledgerId != HOME_LEDGER && ledgerId !in tripIds) bad("transactions[$i]: references unknown ledger $ledgerId")
                val amount = o.optDouble("amount", Double.NaN)
                if (amount.isNaN() || amount <= 0 || !amount.isFinite()) bad("transactions[$i]: amount must be a positive number")
                val direction = o.optString("direction")
                if (direction != "income" && direction != "expense") bad("transactions[$i]: invalid direction")
                val categoryId = idOrNull(o, "category_id")
                if (categoryId != null) {
                    val cat = categories.firstOrNull { it.id == categoryId }
                    if (cat == null || cat.ledgerId != ledgerId)
                        bad("transactions[$i]: category reference is invalid within its ledger")
                }
                transactions.add(Tx(id, ledgerId, amount, direction, date, strOrNull(o, "note"), categoryId))
            }

            ParseResult.Ok(BackupData(exportedAt, appVersion, sv, homeCurrency, trips, categories, templates, transactions))
        } catch (e: IllegalStateException) {
            ParseResult.Fail("Backup rejected: ${e.message}")
        } catch (e: Exception) {
            ParseResult.Fail("Malformed backup file: ${e.message}")
        }
    }
}
