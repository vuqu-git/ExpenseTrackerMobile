package com.expensetracker.app.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.AppDb
import com.expensetracker.app.data.Category
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.data.Repo
import com.expensetracker.app.data.Template
import com.expensetracker.app.data.Trip
import com.expensetracker.app.data.Tx
import com.expensetracker.app.util.Backup
import com.expensetracker.app.util.BackupData
import com.expensetracker.app.util.CsvExchange
import com.expensetracker.app.util.CsvExport
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.ImportCheck
import com.expensetracker.app.util.ImportType
import com.expensetracker.app.util.Snapshot
import com.expensetracker.app.util.validHex
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object Tabs { const val OVERVIEW = 0; const val TRANSACTIONS = 1; const val BALANCE = 2; const val SETTINGS = 3 }

data class Data(
    val txs: List<Tx> = emptyList(),
    val categories: List<Category> = emptyList(),
    val templates: List<Template> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val homeCurrency: String = "EUR"
)

data class UiState(
    val tab: Int = Tabs.OVERVIEW,
    val ledgerId: Long = HOME_LEDGER,
    val month: YearMonth = YearMonth.now(),
    val monthMode: Boolean = true,
    val search: String = "",
    val dirFilter: String? = null,
    val catFilter: Long? = null,
    val uncatFilter: Boolean = false
)

sealed class Screen {
    data class TxForm(val id: Long?) : Screen()
    data class CategoryForm(val id: Long?) : Screen()
    data class TemplateForm(val id: Long?) : Screen()
    data class TripForm(val id: Long?) : Screen()
    data class TripView(val id: Long) : Screen()
    data object ManageTemplates : Screen()
    data object ManageCategories : Screen()
    data object ManageTrips : Screen()
    data object CurrencyPicker : Screen()
    data object CsvExport : Screen()
    data object CsvImport : Screen()
    data object RestorePreCheck : Screen()
}

data class TxFormData(
    val txId: Long? = null,
    val ledgerId: Long? = null,
    val direction: String = "expense",   // default per FR-TX-01 / decision 33
    val amount: String = "",
    val categoryId: Long? = null,
    val note: String = "",
    val date: String = "",
    val errLedger: Boolean = false,
    val errAmount: Boolean = false,
    val errCategory: Boolean = false
)

data class TplFormData(
    val id: Long? = null,
    val ledgerId: Long = HOME_LEDGER,
    val name: String = "",
    val direction: String? = null,
    val amount: String = "",
    val categoryId: Long? = null,
    val note: String = "",
    val errName: Boolean = false,
    val errDuplicate: Boolean = false,
    val errAmount: Boolean = false
)

data class CatFormData(
    val id: Long? = null,
    val ledgerId: Long = HOME_LEDGER,
    val name: String = "",
    val color: String? = null,
    val errName: Boolean = false,
    val errDuplicate: Boolean = false,
    val errColor: Boolean = false
)

data class TripFormData(
    val id: Long? = null,
    val name: String = "",
    val currency: String? = null,
    val start: String? = null,
    val end: String? = null,
    val errName: Boolean = false,
    val errDuplicate: Boolean = false,
    val errCurrency: Boolean = false
)

data class ImportUi(
    val type: ImportType? = null,
    val fileName: String? = null,
    val check: ImportCheck? = null,
    val replace: Boolean = false,   // transactions only
    val applied: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo(AppDb.get(app))

    val data: StateFlow<Data> = combine(
        repo.txFlow, repo.catFlow, repo.tplFlow, repo.tripFlow, repo.settingsFlow
    ) { txs, cats, tpls, trips, settings ->
        Data(txs, cats, tpls, trips, settings.firstOrNull { it.key == "home_currency" }?.value ?: "EUR")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Data())

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _stack = MutableStateFlow<List<Screen>>(emptyList())
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _txForm = MutableStateFlow(TxFormData())
    val txForm: StateFlow<TxFormData> = _txForm.asStateFlow()
    private val _tplForm = MutableStateFlow(TplFormData())
    val tplForm: StateFlow<TplFormData> = _tplForm.asStateFlow()
    private val _catForm = MutableStateFlow(CatFormData())
    val catForm: StateFlow<CatFormData> = _catForm.asStateFlow()
    private val _tripForm = MutableStateFlow(TripFormData())
    val tripForm: StateFlow<TripFormData> = _tripForm.asStateFlow()

    enum class CurrencyCtx { HOME, TRIP_FORM }
    private var currencyCtx: CurrencyCtx = CurrencyCtx.HOME

    private val _import = MutableStateFlow(ImportUi())
    val import: StateFlow<ImportUi> = _import.asStateFlow()

    private val _restore = MutableStateFlow<BackupData?>(null)
    val restore: StateFlow<BackupData?> = _restore.asStateFlow()

    private val _dialogError = MutableStateFlow<String?>(null)
    val dialogError: StateFlow<String?> = _dialogError.asStateFlow()

    /** CSV/JSON content staged until the SAF document is created. */
    var pendingExport: String? = null

    private fun toast(msg: String) =
        Toast.makeText(getApplication<Application>(), msg, Toast.LENGTH_SHORT).show()

    init {
        // FR-ST-01: restore last tab + last ledger; month always resets to current.
        viewModelScope.launch {
            val tab = when (repo.setting("last_tab", "overview")) {
                "transactions" -> Tabs.TRANSACTIONS
                "balance" -> Tabs.BALANCE
                "settings" -> Tabs.SETTINGS
                else -> Tabs.OVERVIEW
            }
            var ledger = repo.setting("last_ledger", "0").toLongOrNull() ?: HOME_LEDGER
            if (ledger != HOME_LEDGER) {
                val t = repo.tripById(ledger)
                if (t == null || t.status != "open") ledger = HOME_LEDGER  // closed/deleted -> home
            }
            _ui.update { it.copy(tab = tab, ledgerId = ledger, month = YearMonth.now()) }
        }
    }

    // ---------------- tabs / ledger / month / filters ----------------

    fun setTab(tab: Int) {
        _ui.update { it.copy(tab = tab) }
        viewModelScope.launch { repo.putSetting("last_tab", tabName(tab)) }
    }

    private fun tabName(t: Int) = when (t) {
        Tabs.TRANSACTIONS -> "transactions"
        Tabs.BALANCE -> "balance"
        Tabs.SETTINGS -> "settings"
        else -> "overview"
    }

    fun setLedger(id: Long) {
        _ui.update { it.copy(ledgerId = id, catFilter = null, uncatFilter = false) }
        viewModelScope.launch { repo.putSetting("last_ledger", id.toString()) }
    }

    fun monthShift(delta: Long) = _ui.update { it.copy(month = it.month.plusMonths(delta)) }
    fun setMonth(m: YearMonth) = _ui.update { it.copy(month = m) }
    fun setMode(monthMode: Boolean) = _ui.update { it.copy(monthMode = monthMode) }
    fun setSearch(s: String) = _ui.update { it.copy(search = s) }

    fun cycleDir() = _ui.update {
        it.copy(dirFilter = when (it.dirFilter) { "expense" -> "income"; "income" -> null; else -> "expense" })
    }

    fun setCatFilter(id: Long) = _ui.update { it.copy(catFilter = id, uncatFilter = false) }
    fun setUncatFilter() = _ui.update { it.copy(catFilter = null, uncatFilter = true) }
    fun clearCatFilter() = _ui.update { it.copy(catFilter = null, uncatFilter = false) }
    fun clearFilters() = _ui.update { it.copy(search = "", dirFilter = null, catFilter = null, uncatFilter = false) }

    /** FR-OV-04: legend tap filters the month's transaction list to that category. */
    fun legendTap(catId: Long?) {
        setTab(Tabs.TRANSACTIONS)
        _ui.update {
            it.copy(monthMode = true, catFilter = catId, uncatFilter = catId == null, dirFilter = null, search = "")
        }
    }

    // ---------------- navigation ----------------

    fun push(s: Screen) { _stack.update { it + s } }

    fun pop() {
        val top = _stack.value.lastOrNull()
        if (top is Screen.CsvImport) _import.value = ImportUi()
        if (top is Screen.RestorePreCheck) _restore.value = null
        _stack.update { if (it.isEmpty()) it else it.dropLast(1) }
    }

    fun popAll() { _stack.value = emptyList() }

    // ---------------- transactions ----------------

    fun openTxForm(id: Long?) {
        val d = data.value
        if (id == null) {
            // FR-TR-05 pre-selection: unique open trip covering today -> that trip; none -> home; multiple -> none.
            val today = LocalDate.now().toString()
            val matching = d.trips.filter {
                it.status == "open" &&
                    (it.startDate == null || it.startDate <= today) &&
                    (it.endDate == null || it.endDate >= today)
            }
            val ledger = when (matching.size) {
                1 -> matching[0].id
                0 -> HOME_LEDGER
                else -> null
            }
            _txForm.value = TxFormData(ledgerId = ledger, date = today)
        } else {
            val t = d.txs.firstOrNull { it.id == id } ?: return
            _txForm.value = TxFormData(
                txId = t.id, ledgerId = t.ledgerId, direction = t.direction,
                amount = Currencies.plain(t.amount), categoryId = t.categoryId,
                note = t.note ?: "", date = t.date
            )
        }
        push(Screen.TxForm(id))
    }

    fun updateTxForm(f: (TxFormData) -> TxFormData) = _txForm.update(f)

    /** FR-TX-02 / edge 26: moving ledgers resets the category if it doesn't exist in the target. */
    fun txFormSetLedger(l: Long?) {
        _txForm.update { f ->
            val catValid = f.categoryId != null &&
                data.value.categories.any { it.id == f.categoryId && it.ledgerId == l }
            f.copy(ledgerId = l, categoryId = if (catValid) f.categoryId else null,
                errLedger = false, errCategory = false)
        }
    }

    /** FR-TX-04: pre-fill only defined fields; never the date. */
    fun applyTemplate(id: Long) {
        val tpl = data.value.templates.firstOrNull { it.id == id } ?: return
        _txForm.update { f ->
            f.copy(
                direction = tpl.direction ?: f.direction,
                amount = tpl.amount?.let { Currencies.plain(it) } ?: f.amount,
                categoryId = tpl.categoryId ?: f.categoryId,
                note = tpl.note ?: f.note,
                errAmount = false, errCategory = false
            )
        }
    }

    fun saveTxForm() {
        val f = _txForm.value
        val d = data.value
        val amount = parseAmountInput(f.amount)
        val catOk = f.categoryId != null && d.categories.any { it.id == f.categoryId && it.ledgerId == f.ledgerId }
        val ledgerOk = f.ledgerId != null
        _txForm.update { it.copy(errAmount = amount == null, errCategory = !catOk, errLedger = !ledgerOk) }
        if (amount == null || !catOk || !ledgerOk) return
        val tx = Tx(
            id = f.txId ?: 0,
            ledgerId = f.ledgerId!!,
            amount = amount,
            direction = f.direction,
            date = f.date,
            note = f.note.trim().ifEmpty { null },
            categoryId = f.categoryId
        )
        viewModelScope.launch {
            if (f.txId == null) repo.insertTx(tx) else repo.updateTx(tx)
            pop()
        }
    }

    fun deleteTx(id: Long) {
        viewModelScope.launch {
            data.value.txs.firstOrNull { it.id == id }?.let { repo.deleteTx(it) }
            if (_txForm.value.txId == id) pop()
        }
    }

    // ---------------- categories ----------------

    fun openCategoryForm(id: Long?, ledgerId: Long) {
        if (id == null) _catForm.value = CatFormData(ledgerId = ledgerId)
        else {
            val c = data.value.categories.firstOrNull { it.id == id } ?: return
            _catForm.value = CatFormData(id = c.id, ledgerId = c.ledgerId, name = c.name, color = c.color)
        }
        push(Screen.CategoryForm(id))
    }

    fun updateCatForm(f: (CatFormData) -> CatFormData) = _catForm.update(f)

    fun saveCategoryForm() {
        val f = _catForm.value
        val name = f.name.trim()
        val dup = data.value.categories.any {
            it.ledgerId == f.ledgerId && it.id != f.id && it.name.equals(name, ignoreCase = true)
        }
        val color = f.color
        val colorOk = color != null && validHex(color)
        _catForm.update { it.copy(errName = name.isEmpty(), errDuplicate = dup, errColor = !colorOk) }
        if (name.isEmpty() || dup || !colorOk) return
        viewModelScope.launch {
            if (f.id == null) repo.insertCategory(Category(ledgerId = f.ledgerId, name = name, color = color))
            else repo.updateCategory(Category(id = f.id, ledgerId = f.ledgerId, name = name, color = color))
            pop()
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            data.value.categories.firstOrNull { it.id == id }?.let { repo.deleteCategory(it) }
            if (_catForm.value.id == id) pop()
        }
    }

    // ---------------- transaction templates ----------------

    fun openTemplateForm(id: Long?, ledgerId: Long) {
        if (id == null) _tplForm.value = TplFormData(ledgerId = ledgerId)
        else {
            val t = data.value.templates.firstOrNull { it.id == id } ?: return
            _tplForm.value = TplFormData(
                id = t.id, ledgerId = t.ledgerId, name = t.name, direction = t.direction,
                amount = t.amount?.let { Currencies.plain(it) } ?: "",
                categoryId = t.categoryId, note = t.note ?: ""
            )
        }
        push(Screen.TemplateForm(id))
    }

    fun updateTplForm(f: (TplFormData) -> TplFormData) = _tplForm.update(f)

    fun saveTemplateForm() {
        val f = _tplForm.value
        val d = data.value
        val name = f.name.trim()
        val dup = d.templates.any { it.ledgerId == f.ledgerId && it.id != f.id && it.name.equals(name, ignoreCase = true) }
        val amount: Double? = if (f.amount.isBlank()) null else parseAmountInput(f.amount)
        val catOk = f.categoryId == null || d.categories.any { it.id == f.categoryId && it.ledgerId == f.ledgerId }
        _tplForm.update {
            it.copy(errName = name.isEmpty() || dup, errDuplicate = dup,
                errAmount = f.amount.isNotBlank() && amount == null)
        }
        if (name.isEmpty() || dup) return
        if (amount == null && f.amount.isNotBlank()) return
        if (!catOk) return
        viewModelScope.launch {
            val tpl = Template(
                id = f.id ?: 0, ledgerId = f.ledgerId, name = name, categoryId = f.categoryId,
                note = f.note.trim().ifEmpty { null }, amount = amount, direction = f.direction
            )
            if (f.id == null) repo.insertTemplate(tpl) else repo.updateTemplate(tpl)
            pop()
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            data.value.templates.firstOrNull { it.id == id }?.let { repo.deleteTemplate(it) }
            if (_tplForm.value.id == id) pop()
        }
    }

    // ---------------- trips ----------------

    fun openTripForm(id: Long?) {
        if (id == null) _tripForm.value = TripFormData()
        else {
            val t = data.value.trips.firstOrNull { it.id == id } ?: return
            _tripForm.value = TripFormData(id = t.id, name = t.name, currency = t.currency,
                start = t.startDate, end = t.endDate)
        }
        push(Screen.TripForm(id))
    }

    fun updateTripForm(f: (TripFormData) -> TripFormData) = _tripForm.update(f)

    fun saveTripForm() {
        val f = _tripForm.value
        val d = data.value
        val name = f.name.trim()
        val dup = d.trips.any { it.id != f.id && it.name.equals(name, ignoreCase = true) }
        val nameOk = name.isNotEmpty() && !name.equals("home", ignoreCase = true)
        val curOk = f.currency != null && Currencies.isValid(f.currency)
        _tripForm.update { it.copy(errName = !nameOk, errDuplicate = dup, errCurrency = !curOk) }
        if (!nameOk || dup || !curOk) return
        viewModelScope.launch {
            if (f.id == null) {
                repo.insertTrip(Trip(name = name, currency = f.currency!!, startDate = f.start, endDate = f.end, status = "open"))
            } else {
                val existing = d.trips.first { it.id == f.id }
                // Currency stays untouched (locked when transactions exist; UI enforces).
                repo.updateTrip(existing.copy(name = name, startDate = f.start, endDate = f.end))
            }
            pop()
        }
    }

    /** Deletion allowed only without transactions (blocked in UI with a message). */
    fun deleteTrip(id: Long) {
        viewModelScope.launch {
            repo.deleteTripCascade(id)
            if (_ui.value.ledgerId == id) setLedger(HOME_LEDGER)
            if (_stack.value.lastOrNull() is Screen.TripView) pop()
            if (_tripForm.value.id == id) pop()
        }
    }

    fun toggleTripStatus(id: Long) {
        viewModelScope.launch {
            val t = data.value.trips.firstOrNull { it.id == id } ?: return@launch
            repo.updateTrip(t.copy(status = if (t.status == "open") "closed" else "open"))
        }
    }

    // ---------------- currency ----------------

    fun openCurrencyPicker(ctx: CurrencyCtx) {
        currencyCtx = ctx
        push(Screen.CurrencyPicker)
    }

    fun pickCurrency(code: String) {
        when (currencyCtx) {
            CurrencyCtx.HOME -> viewModelScope.launch { repo.setHomeCurrency(code) } // relabel only (FR-CU-02)
            CurrencyCtx.TRIP_FORM -> _tripForm.update { it.copy(currency = code, errCurrency = false) }
        }
        pop()
    }

    // ---------------- CSV export ----------------

    private fun snap(): Snapshot {
        val d = data.value
        return Snapshot(d.trips, d.categories, d.templates, d.txs)
    }

    fun buildHomeRangeCsv(from: String, to: String): String {
        val d = data.value
        return CsvExport.transactions(d.txs.filter { it.ledgerId == HOME_LEDGER && it.date >= from && it.date <= to }, snap())
    }

    fun buildTripCsv(tripId: Long): String {
        val d = data.value
        return CsvExport.transactions(d.txs.filter { it.ledgerId == tripId }, snap())
    }

    fun buildAllLedgersCsv(): String = CsvExport.transactions(data.value.txs, snap())
    fun buildCategoriesCsv(): String = CsvExport.categories(snap())
    fun buildTemplatesCsv(): String = CsvExport.templates(snap())
    fun buildTripsCsv(): String = CsvExport.trips(snap())
    fun buildJsonBackup(): String = Backup.build(snap(), data.value.homeCurrency)

    // ---------------- CSV import ----------------

    fun startImport(type: ImportType) { _import.value = ImportUi(type = type) }

    fun parseImport(text: String, fileName: String) {
        val type = _import.value.type ?: return
        val check = CsvExchange.check(type, text, snap())
        _import.update { it.copy(fileName = fileName, check = check, replace = false, applied = false) }
    }

    fun setImportReplace(b: Boolean) = _import.update { it.copy(replace = b) }

    fun confirmImport() {
        val imp = _import.value
        val check = imp.check ?: return
        if (!check.isOk || imp.applied) return
        viewModelScope.launch {
            when (check) {
                is ImportCheck.Trips -> repo.insertTrips(check.rows.map {
                    Trip(name = it.name, currency = it.currency, startDate = it.start, endDate = it.end, status = it.status)
                })
                is ImportCheck.Categories -> repo.insertCategories(check.rows.map {
                    Category(ledgerId = it.ledgerId, name = it.name, color = it.color)
                })
                is ImportCheck.Templates -> repo.insertTemplates(check.rows.map {
                    Template(ledgerId = it.ledgerId, name = it.name, categoryId = it.categoryId,
                        note = it.note, amount = it.amount, direction = it.direction)
                })
                is ImportCheck.Transactions -> {
                    val rows = check.rows.map {
                        Tx(ledgerId = it.ledgerId, amount = it.amount, direction = it.direction,
                            date = it.date, note = it.note, categoryId = it.categoryId)
                    }
                    if (imp.replace) repo.replaceTransactions(rows) else repo.insertTransactions(rows)
                }
            }
            _import.update { it.copy(applied = true) }
        }
    }

    fun resetImportUi() { _import.value = ImportUi() }

    // ---------------- JSON restore ----------------

    /** Returns null on success (pre-check screen pushed), or an error reason. */
    fun parseBackupFile(text: String): String? {
        return when (val r = Backup.parse(text)) {
            is Backup.ParseResult.Fail -> r.reason
            is Backup.ParseResult.Ok -> {
                _restore.value = r.data
                push(Screen.RestorePreCheck)
                null
            }
        }
    }

    fun cancelRestore() { _restore.value = null; pop() }

    fun confirmRestore() {
        val b = _restore.value ?: return
        viewModelScope.launch {
            repo.restoreAll(b)
            _restore.value = null
            _ui.update {
                it.copy(tab = Tabs.OVERVIEW, ledgerId = HOME_LEDGER, month = YearMonth.now(),
                    monthMode = true, search = "", dirFilter = null, catFilter = null, uncatFilter = false)
            }
            _stack.value = emptyList()
            toast("Database restored — ${b.transactions.size} transactions imported")
        }
    }

    // ---------------- misc ----------------

    fun showError(msg: String) { _dialogError.value = msg }
    fun clearError() { _dialogError.value = null }
}
