package `in`.thomas.pocketledger
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity data class Account(@PrimaryKey val id: String, val name: String, val bank: String, val suffix: String, val opening: Long = 0, val openingDate: Long = 0)
@Entity(indices = [Index(value = ["smsKey"], unique = true), Index(value = ["date"]), Index(value = ["accountId"])])
data class Entry(@PrimaryKey val id: String, val accountId: String, val destinationId: String? = null, val type: String, val amount: Long, val date: Long, val dateOnly: Boolean = false, val payee: String = "", val category: String = "Uncategorized", val note: String = "", val smsKey: String? = null, val deleted: Boolean = false)
@Entity data class SmsRecord(@PrimaryKey val key: String, val sender: String, val body: String, val received: Long, val bank: String, val suffix: String?, val amount: Long?, val direction: String?, val date: Long?, val dateOnly: Boolean, val payee: String, val reference: String?, val balance: Long?, val complete: Boolean, val status: String = "REVIEW", val reason: String = "Confirm sender and account")
@Entity(primaryKeys = ["sender", "bank"]) data class TrustedSender(val sender: String, val bank: String)
@Entity data class Budget(@PrimaryKey val category: String, val amount: Long)
@Entity(primaryKeys = ["merchant", "type"]) data class MerchantRule(val merchant: String, val category: String, val type: String = "EXPENSE")
/** Entry joined with its source SMS reference, used by transfer matching. */
data class EntryWithReference(@Embedded val entry: Entry, val reference: String?)

/**
 * Resolved (saved, dismissed, linked) messages keep only a SHA-256 of sender plus normalized body, never the text.
 * The hash equals [SmsParser.stableBodyKey], so legacy identity lookups for incomplete messages keep working.
 */
const val PurgedBodyPrefix = "purged:"
fun purgedBody(sender: String, body: String): String = PurgedBodyPrefix + SmsParser.stableBodyKey(sender, body)
fun isPurgedBody(body: String): Boolean = body.length == PurgedBodyPrefix.length + 64 && body.startsWith(PurgedBodyPrefix) &&
    body.substring(PurgedBodyPrefix.length).all { it in '0'..'9' || it in 'a'..'f' }
fun SmsRecord.bodyPurged(): Boolean = isPurgedBody(body)
fun SmsRecord.purged(): SmsRecord = if (bodyPurged()) this else copy(body = purgedBody(sender, body))
/** Identity this row would be found under by a freshly parsed copy of the same message; safe after the body is purged. */
fun SmsRecord.semanticKey(): String =
    if (!complete || reference == null) { if (bodyPurged()) body.removePrefix(PurgedBodyPrefix) else SmsParser.stableBodyKey(sender, body) }
    else SmsParser.key(parsed(), sender, "", received)
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
    @Query("SELECT * FROM Entry WHERE date >= :from AND date < :to AND deleted = 0 ORDER BY date DESC") fun entriesBetween(from: Long, to: Long): Flow<List<Entry>>
    @Query("SELECT * FROM SmsRecord WHERE reference IN (:references) AND instr(`key`, 'conflict') = 0") suspend fun messagesByReference(references: List<String>): List<SmsRecord>
    @Query("SELECT * FROM SmsRecord WHERE (complete = 0 OR reference IS NULL) AND instr(`key`, 'conflict') = 0") suspend fun unreferencedMessages(): List<SmsRecord>
    @Query("SELECT * FROM SmsRecord WHERE status = 'REVIEW'") suspend fun reviewMessages(): List<SmsRecord>
    @Query("SELECT * FROM SmsRecord WHERE status IN ('SAVED', 'DISMISSED', 'LINKED') AND body NOT LIKE 'purged:%'") suspend fun resolvedWithBody(): List<SmsRecord>
    @Query("SELECT DISTINCT reference FROM SmsRecord WHERE `key` IN (:keys) AND reference IS NOT NULL") suspend fun referencesForKeys(keys: List<String>): List<String>
    @Query("SELECT e.*, m.reference AS reference FROM Entry e JOIN SmsRecord m ON m.`key` = e.smsKey WHERE m.reference IN (:references) AND e.deleted = 0 AND e.type <> 'TRANSFER'")
    suspend fun transferCandidates(references: List<String>): List<EntryWithReference>
    @Query("SELECT e.*, m.reference AS reference FROM Entry e JOIN SmsRecord m ON m.`key` = e.smsKey WHERE m.reference IS NOT NULL AND e.deleted = 0 AND e.type <> 'TRANSFER'")
    suspend fun allTransferCandidates(): List<EntryWithReference>
    @Query("SELECT * FROM Entry WHERE smsKey = :key LIMIT 1") suspend fun entryForSms(key: String): Entry?
    @Query("SELECT * FROM Entry WHERE id = :id") suspend fun entry(id: String): Entry?
    @Upsert suspend fun account(v: Account)
    @Upsert suspend fun entry(v: Entry)
    @Upsert suspend fun message(v: SmsRecord)
    @Upsert suspend fun sender(v: TrustedSender)
    @Upsert suspend fun budget(v: Budget)
    @Query("DELETE FROM Budget WHERE category = :category") suspend fun removeBudget(category: String)
    @Upsert suspend fun rule(v: MerchantRule)
    @Query("DELETE FROM TrustedSender WHERE sender = :sender AND bank = :bank") suspend fun untrust(sender: String, bank: String)
    @Query("DELETE FROM MerchantRule WHERE merchant = :merchant AND type = :type") suspend fun removeRule(merchant: String, type: String)
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
private val Migration23IncomeOnlyCategories = listOf(
    "Salary", "Bonus", "Freelance", "Business income", "Interest", "Dividends", "Investment returns",
    "Rental income", "Pension", "Refunds", "Reimbursements", "Gifts received", "Cashback", "Other income"
)
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE MerchantRule_new (merchant TEXT NOT NULL, category TEXT NOT NULL, type TEXT NOT NULL, PRIMARY KEY(merchant, type))")
        // Frozen: the income-only category names as of schema 3. Later edits to Categories.kt must not change this migration.
        val incomeOnly = Migration23IncomeOnlyCategories.joinToString(",") { "'${it.replace("'", "''")}'" }
        db.execSQL("""INSERT INTO MerchantRule_new (merchant, category, type)
            SELECT r.merchant, r.category, COALESCE(
                (SELECT e.type FROM Entry e WHERE UPPER(TRIM(e.payee)) = UPPER(TRIM(r.merchant))
                 AND e.category = r.category AND e.type IN ('INCOME', 'EXPENSE') ORDER BY e.date DESC LIMIT 1),
                CASE WHEN r.category IN ($incomeOnly) THEN 'INCOME' ELSE 'EXPENSE' END)
            FROM MerchantRule r""")
        db.execSQL("DROP TABLE MerchantRule")
        db.execSQL("ALTER TABLE MerchantRule_new RENAME TO MerchantRule")
    }
}
/**
 * 3 to 4: unique SMS identity per entry, lookup indices, and SMS body purge for resolved messages.
 * Duplicate entries for one smsKey are never deleted: the preferred row (live, then transfer, then categorized, then oldest)
 * keeps the key; the others are detached from the SMS and soft-deleted so they stop counting but remain in backups.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val duplicated = db.query("SELECT smsKey FROM Entry WHERE smsKey IS NOT NULL GROUP BY smsKey HAVING COUNT(*) > 1").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        for (key in duplicated) {
            val rows = db.query("SELECT rowid FROM Entry WHERE smsKey = ? ORDER BY deleted ASC, (type = 'TRANSFER') DESC, (category <> 'Uncategorized') DESC, rowid ASC", arrayOf<Any>(key)).use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0)) }
            }
            rows.drop(1).forEach { db.execSQL("UPDATE Entry SET smsKey = NULL, deleted = 1 WHERE rowid = ?", arrayOf<Any>(it)) }
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Entry_smsKey` ON `Entry` (`smsKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Entry_date` ON `Entry` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Entry_accountId` ON `Entry` (`accountId`)")
        val resolved = db.query("SELECT `key`, sender, body FROM SmsRecord WHERE status IN ('SAVED', 'DISMISSED', 'LINKED')").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2))) }
        }
        for ((key, sender, body) in resolved) {
            if (!isPurgedBody(body)) db.execSQL("UPDATE SmsRecord SET body = ? WHERE `key` = ?", arrayOf<Any>(purgedBody(sender, body), key))
        }
    }
}
val LedgerMigrations = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
@Database(entities = [Account::class, Entry::class, SmsRecord::class, TrustedSender::class, Budget::class, MerchantRule::class], version = 4, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() { abstract fun dao(): LedgerDao }
fun balance(account: Account, entries: List<Entry>): Long = entries.filter { !it.deleted && it.date >= account.openingDate }.fold(account.opening) { sum, e -> sum + when { e.type == "TRANSFER" && e.destinationId == account.id -> e.amount; e.accountId != account.id -> 0; e.type == "INCOME" -> e.amount; else -> -e.amount } }
