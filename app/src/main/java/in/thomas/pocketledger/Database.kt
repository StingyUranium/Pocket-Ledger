package `in`.thomas.pocketledger
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity data class Account(@PrimaryKey val id: String, val name: String, val bank: String, val suffix: String, val opening: Long = 0, val openingDate: Long = 0)
@Entity data class Entry(@PrimaryKey val id: String, val accountId: String, val destinationId: String? = null, val type: String, val amount: Long, val date: Long, val dateOnly: Boolean = false, val payee: String = "", val category: String = "Uncategorized", val note: String = "", val smsKey: String? = null, val deleted: Boolean = false)
@Entity data class SmsRecord(@PrimaryKey val key: String, val sender: String, val body: String, val received: Long, val bank: String, val suffix: String?, val amount: Long?, val direction: String?, val date: Long?, val dateOnly: Boolean, val payee: String, val reference: String?, val balance: Long?, val complete: Boolean, val status: String = "REVIEW", val reason: String = "Confirm sender and account")
@Entity(primaryKeys = ["sender", "bank"]) data class TrustedSender(val sender: String, val bank: String)
@Entity data class Budget(@PrimaryKey val category: String, val amount: Long)
@Entity data class MerchantRule(@PrimaryKey val merchant: String, val category: String)
fun SmsRecord.parsed() = ParsedSms(bank, suffix, amount, direction, date, dateOnly, payee, reference, balance, complete)
@Dao interface LedgerDao {
    @Query("SELECT * FROM Account ORDER BY name") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM Entry ORDER BY date DESC") fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM SmsRecord ORDER BY received DESC") fun messages(): Flow<List<SmsRecord>>
    @Query("SELECT * FROM Budget") fun budgets(): Flow<List<Budget>>
    @Query("SELECT * FROM TrustedSender") fun senders(): Flow<List<TrustedSender>>
    @Query("SELECT * FROM MerchantRule") fun rules(): Flow<List<MerchantRule>>
    @Query("SELECT * FROM Account") suspend fun allAccounts(): List<Account>
    @Query("SELECT * FROM Entry") suspend fun allEntries(): List<Entry>
    @Query("SELECT * FROM SmsRecord") suspend fun allMessages(): List<SmsRecord>
    @Query("SELECT * FROM TrustedSender") suspend fun allSenders(): List<TrustedSender>
    @Query("SELECT * FROM Budget") suspend fun allBudgets(): List<Budget>
    @Query("SELECT * FROM MerchantRule") suspend fun allRules(): List<MerchantRule>
    @Query("SELECT * FROM SmsRecord WHERE `key` = :key") suspend fun message(key: String): SmsRecord?
    @Query("SELECT * FROM Entry WHERE smsKey = :key LIMIT 1") suspend fun entryForSms(key: String): Entry?
    @Upsert suspend fun account(v: Account)
    @Upsert suspend fun entry(v: Entry)
    @Upsert suspend fun message(v: SmsRecord)
    @Upsert suspend fun sender(v: TrustedSender)
    @Upsert suspend fun budget(v: Budget)
    @Upsert suspend fun rule(v: MerchantRule)
    @Query("DELETE FROM TrustedSender WHERE sender = :sender AND bank = :bank") suspend fun untrust(sender: String, bank: String)
    @Query("DELETE FROM MerchantRule WHERE merchant = :merchant") suspend fun removeRule(merchant: String)
    @Query("DELETE FROM Entry") suspend fun clearEntries()
    @Query("DELETE FROM SmsRecord") suspend fun clearMessages()
    @Query("DELETE FROM Account") suspend fun clearAccounts()
    @Query("DELETE FROM TrustedSender") suspend fun clearSenders()
    @Query("DELETE FROM Budget") suspend fun clearBudgets()
    @Query("DELETE FROM MerchantRule") suspend fun clearRules()
}
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE Budget_new (category TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(category))")
        db.execSQL("INSERT INTO Budget_new (category, amount) SELECT b.category, b.amount FROM Budget b WHERE b.month = (SELECT MAX(b2.month) FROM Budget b2 WHERE b2.category = b.category)")
        db.execSQL("DROP TABLE Budget")
        db.execSQL("ALTER TABLE Budget_new RENAME TO Budget")
    }
}
@Database(entities = [Account::class, Entry::class, SmsRecord::class, TrustedSender::class, Budget::class, MerchantRule::class], version = 2, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() { abstract fun dao(): LedgerDao }
fun balance(account: Account, entries: List<Entry>): Long = entries.filter { !it.deleted && it.date >= account.openingDate }.fold(account.opening) { sum, e -> sum + when { e.type == "TRANSFER" && e.destinationId == account.id -> e.amount; e.accountId != account.id -> 0; e.type == "INCOME" -> e.amount; else -> -e.amount } }
val defaultCategories = listOf("Uncategorized", "Food", "Groceries", "Transport", "Uber/Bus", "Fuel", "Rent", "Shopping", "Bills", "Health", "Entertainment", "Salary", "Investment", "Other")
