package com.expensetracker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The implicit home ledger id. Trips get auto-generated ids. */
const val HOME_LEDGER = 0L

@Entity(tableName = "transactions")
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Long,
    val amount: Double,          // always positive; sign semantics live in `direction`
    val direction: String,       // "income" | "expense"
    val date: String,            // "YYYY-MM-DD"
    val note: String? = null,
    val categoryId: Long? = null // null = uncategorized
)

@Entity(tableName = "categories", indices = [Index("ledgerId")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Long,
    val name: String,
    val color: String            // "#RRGGBB"
)

@Entity(tableName = "templates", indices = [Index("ledgerId")])
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Long,
    val name: String,
    val categoryId: Long? = null,
    val note: String? = null,
    val amount: Double? = null,  // positive, or null = no prefill
    val direction: String? = null // "income"|"expense"|null
)

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currency: String,
    val startDate: String? = null, // ISO or null
    val endDate: String? = null,
    val status: String            // "open" | "closed"
)

@Entity(tableName = "app_settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Tx>>

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<Tx>

    @Query("SELECT COUNT(*) FROM transactions WHERE ledgerId = :l")
    suspend fun countByLedger(l: Long): Int

    @Insert
    suspend fun insert(t: Tx): Long

    @Insert
    suspend fun insertAll(list: List<Tx>)

    @Update
    suspend fun update(t: Tx)

    @Delete
    suspend fun delete(t: Tx)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :id")
    suspend fun nullCategory(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>

    @Insert
    suspend fun insert(c: Category): Long

    @Insert
    suspend fun insertAll(list: List<Category>)

    @Update
    suspend fun update(c: Category)

    @Delete
    suspend fun delete(c: Category)

    @Query("DELETE FROM categories WHERE ledgerId = :l")
    suspend fun deleteByLedger(l: Long)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT * FROM templates")
    suspend fun getAll(): List<Template>

    @Insert
    suspend fun insert(t: Template): Long

    @Insert
    suspend fun insertAll(list: List<Template>)

    @Update
    suspend fun update(t: Template)

    @Delete
    suspend fun delete(t: Template)

    @Query("UPDATE templates SET categoryId = NULL WHERE categoryId = :id")
    suspend fun nullCategory(id: Long)

    @Query("DELETE FROM templates WHERE ledgerId = :l")
    suspend fun deleteByLedger(l: Long)

    @Query("DELETE FROM templates")
    suspend fun clear()
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun byId(id: Long): Trip?

    @Insert
    suspend fun insert(t: Trip): Long

    @Insert
    suspend fun insertAll(list: List<Trip>)

    @Update
    suspend fun update(t: Trip)

    @Delete
    suspend fun delete(t: Trip)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trips")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<Setting>>

    @Query("SELECT * FROM app_settings WHERE `key` = :k")
    suspend fun get(k: String): Setting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: Setting)

    @Query("DELETE FROM app_settings")
    suspend fun clear()
}

@Database(
    entities = [Tx::class, Category::class, Template::class, Trip::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun txDao(): TxDao
    abstract fun categoryDao(): CategoryDao
    abstract fun templateDao(): TemplateDao
    abstract fun tripDao(): TripDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDb::class.java, "expense.db"
                ).build().also { INSTANCE = it }
            }
    }
}
