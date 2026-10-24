package com.expensetracker.app.data

import androidx.room.withTransaction
import com.expensetracker.app.util.BackupData
import kotlinx.coroutines.flow.Flow

class Repo(private val db: AppDb) {

    val txFlow: Flow<List<Tx>> = db.txDao().observeAll()
    val catFlow: Flow<List<Category>> = db.categoryDao().observeAll()
    val tplFlow: Flow<List<Template>> = db.templateDao().observeAll()
    val tripFlow: Flow<List<Trip>> = db.tripDao().observeAll()
    val settingsFlow: Flow<List<Setting>> = db.settingsDao().observeAll()

    suspend fun setting(key: String, default: String): String =
        db.settingsDao().get(key)?.value ?: default

    suspend fun putSetting(key: String, value: String) = db.settingsDao().put(Setting(key, value))
    suspend fun setHomeCurrency(code: String) = putSetting("home_currency", code)
    suspend fun tripById(id: Long): Trip? = db.tripDao().byId(id)

    suspend fun insertTx(t: Tx): Long = db.txDao().insert(t)
    suspend fun updateTx(t: Tx) = db.txDao().update(t)
    suspend fun deleteTx(t: Tx) = db.txDao().delete(t)
    suspend fun insertTransactions(list: List<Tx>) = db.txDao().insertAll(list)

    suspend fun insertCategory(c: Category): Long = db.categoryDao().insert(c)
    suspend fun updateCategory(c: Category) = db.categoryDao().update(c)
    suspend fun insertCategories(list: List<Category>) = db.categoryDao().insertAll(list)

    /** Deleting a category keeps transactions/templates; their category becomes uncategorized. */
    suspend fun deleteCategory(c: Category) {
        db.withTransaction {
            db.categoryDao().delete(c)
            db.txDao().nullCategory(c.id)
            db.templateDao().nullCategory(c.id)
        }
    }

    suspend fun insertTemplate(t: Template): Long = db.templateDao().insert(t)
    suspend fun updateTemplate(t: Template) = db.templateDao().update(t)
    suspend fun deleteTemplate(t: Template) = db.templateDao().delete(t)
    suspend fun insertTemplates(list: List<Template>) = db.templateDao().insertAll(list)

    suspend fun insertTrip(t: Trip): Long = db.tripDao().insert(t)
    suspend fun updateTrip(t: Trip) = db.tripDao().update(t)
    suspend fun insertTrips(list: List<Trip>) = db.tripDao().insertAll(list)

    /** Only reachable for trips without transactions: removes trip + its categories + templates. */
    suspend fun deleteTripCascade(tripId: Long) {
        db.withTransaction {
            db.tripDao().deleteById(tripId)
            db.categoryDao().deleteByLedger(tripId)
            db.templateDao().deleteByLedger(tripId)
        }
    }

    /** Replace mode for transactions CSV import: atomic delete-then-insert. */
    suspend fun replaceTransactions(list: List<Tx>) {
        db.withTransaction {
            db.txDao().clear()
            db.txDao().insertAll(list)
        }
    }

    /** Atomic Strict-Replace JSON restore (FR-BK-02). Rolls back fully on any failure. */
    suspend fun restoreAll(b: BackupData) {
        db.withTransaction {
            db.txDao().clear()
            db.categoryDao().clear()
            db.templateDao().clear()
            db.tripDao().clear()
            db.settingsDao().clear()
            db.tripDao().insertAll(b.trips)
            db.categoryDao().insertAll(b.categories)
            db.templateDao().insertAll(b.templates)
            db.txDao().insertAll(b.transactions)
            db.settingsDao().put(Setting("home_currency", b.homeCurrency))
            db.settingsDao().put(Setting("last_tab", "overview"))
            db.settingsDao().put(Setting("last_ledger", "0"))
        }
    }
}
