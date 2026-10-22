package `in`.thomas.pocketledger
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

class LedgerApp : Application() {
    val db by lazy { Room.databaseBuilder(this, LedgerDatabase::class.java, "ledger.db").build() }
    val repo by lazy { Repository(this, db) }
    override fun onCreate() { super.onCreate(); androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("sms-reconcile", androidx.work.ExistingPeriodicWorkPolicy.KEEP, androidx.work.PeriodicWorkRequestBuilder<SmsWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()) }
}
data class Candidate(val sender: String, val body: String, val received: Long, val parsed: ParsedSms) { val key get() = SmsParser.key(parsed, sender, body, received) }
class Repository(val context: Context, val db: LedgerDatabase) {
    val dao = db.dao()
    private val mutex = Mutex()
    val prefs = context.getSharedPreferences("capture", Context.MODE_PRIVATE)
    suspend fun initialize() = mutex.withLock {
        if (dao.allAccounts().isEmpty()) {
            val start = LocalDate.now(india).withDayOfMonth(1).atStartOfDay(india).toInstant().toEpochMilli()
            db.withTransaction {
                dao.account(Account("sbm", "SBM Bank India", "SBM", "6317", openingDate = start))
                dao.account(Account("axis", "Axis Bank", "AXIS", "0424", openingDate = start))
                dao.account(Account("cash", "Cash", "CASH", "", openingDate = start))
            }
        }
    }
    fun hasSmsPermission() = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    suspend fun scan(from: Long, to: Long): List<Candidate> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) error("SMS permission is required. Manual entries are still available.")
        val result = mutableListOf<Candidate>()
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), "date >= ? AND date < ?", arrayOf(from.toString(), to.toString()), "date ASC")?.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(1).orEmpty(); val parsed = SmsParser.parseSelected(body) ?: continue
                result += Candidate(c.getString(0).orEmpty(), body, c.getLong(2), parsed)
            }
        }
        result.distinctBy { Triple(it.key, it.parsed.amount, it.parsed.date) }
    }
    suspend fun ingest(candidates: List<Candidate>, forceReview: Boolean = false, requireCapture: Boolean = false) = mutex.withLock {
        if (requireCapture && !prefs.getBoolean("enabled", false)) return@withLock
        db.withTransaction {
        val stored = dao.allMessages().filter { !it.key.contains(":conflict:") }
        val legacyBodies = stored.filter { !it.complete }.associateBy { SmsParser.bodyKey(it.sender, it.body, 0) }
        val semanticKeys = stored.associateBy { m ->
            SmsParser.key(m.parsed(), m.sender, m.body, m.received)
        }.toMutableMap()
        for (c in candidates) {
            val p = c.parsed
            // Older parser versions keyed unsupported formats by their original message body.
            // Keep that key (including tombstones) when the upgraded parser recognizes a reference.
            val existing = dao.message(c.key) ?: semanticKeys[c.key] ?: dao.message(SmsParser.bodyKey(c.sender, c.body, c.received))
                ?: if (p.complete) legacyBodies[SmsParser.bodyKey(c.sender, c.body, 0)] else null
            val conflict = existing != null && existing.complete && (existing.amount != p.amount || existing.date != p.date || existing.suffix != p.suffix)
            if (existing != null && !conflict && existing.status != "REVIEW") {
                if (!existing.complete && p.complete) {
                    val upgraded = existing.copy(suffix = p.suffix, amount = p.amount, direction = p.direction, date = p.date, dateOnly = p.dateOnly, payee = p.payee, reference = p.reference, balance = p.balance, complete = true)
                    dao.message(upgraded)
                    semanticKeys[c.key] = upgraded
                }
                continue
            }
            val key = if (conflict) c.key + ":conflict:" + digest(c.body) else existing?.key ?: c.key
            if (conflict && dao.message(key) != null) continue
            val trusted = dao.allSenders().any { it.bank == p.bank && it.sender == normalizedSender(c.sender) }
            val account = dao.allAccounts().filter { it.bank == p.bank && it.suffix == p.suffix }.singleOrNull()
            val reason = when { conflict -> "Conflicts with an existing reference: check for a duplicate"; forceReview -> "Review historical transaction"; !p.complete -> "Incomplete or unfamiliar bank format"; !trusted -> "Confirm this sender ID"; account == null -> "Map this account in Accounts"; p.date != null && p.date < account.openingDate -> "Before account opening date"; p.date != null && p.date > c.received + 86_400_000L -> "Transaction date is after message receipt"; else -> "" }
            val record = SmsRecord(key, c.sender, c.body, c.received, p.bank, p.suffix, p.amount, p.direction, p.date, p.dateOnly, p.payee, p.reference, p.balance, p.complete, reason = reason)
            dao.message(record)
            if (!conflict) semanticKeys[c.key] = record
            if (reason.isEmpty() && account != null) saveParsed(record, account)
        }
        matchTransfers()
    } }
    private suspend fun saveParsed(m: SmsRecord, a: Account) {
        if (dao.entryForSms(m.key) != null) return
        val rule = dao.allRules().find { it.merchant == m.payee.uppercase() }
        dao.entry(Entry(UUID.randomUUID().toString(), a.id, type = requireNotNull(m.direction), amount = requireNotNull(m.amount), date = requireNotNull(m.date), dateOnly = m.dateOnly, payee = m.payee, category = rule?.category ?: "Uncategorized", smsKey = m.key))
        dao.message(m.copy(status = "SAVED", reason = ""))
    }
    suspend fun trust(sender: String, bank: String) { dao.sender(TrustedSender(normalizedSender(sender), bank)); processReview() }
    suspend fun processReview() = mutex.withLock { db.withTransaction {
        val trusted = dao.allSenders(); val accounts = dao.allAccounts()
        dao.allMessages().filter { it.status == "REVIEW" && it.complete && !it.key.contains(":conflict:") && (it.date ?: Long.MAX_VALUE) <= it.received + 86_400_000L }.forEach { m ->
            val a = accounts.singleOrNull { it.bank == m.bank && it.suffix == m.suffix }
            if (a != null && m.date != null && m.date >= a.openingDate && trusted.any { it.bank == m.bank && it.sender == normalizedSender(m.sender) }) saveParsed(m, a)
        }
        matchTransfers()
    } }
    private suspend fun matchTransfers() {
        val messages = dao.allMessages().associateBy { it.key }
        val entries = dao.allEntries().filter { !it.deleted && it.type != "TRANSFER" && it.smsKey != null }
        for (out in entries.filter { it.type == "EXPENSE" }) {
            val ref = messages[out.smsKey]?.reference ?: continue
            val ins = entries.filter { it.type == "INCOME" && it.accountId != out.accountId && it.amount == out.amount && messages[it.smsKey]?.reference == ref && kotlin.math.abs(it.date - out.date) <= 86_400_000L }
            if (ins.size != 1) continue
            val inc = ins.single()
            if (out.date < (dao.allAccounts().singleOrNull { it.id == inc.accountId }?.openingDate ?: Long.MAX_VALUE)) continue
            if (entries.count { it.type == "EXPENSE" && it.amount == inc.amount && messages[it.smsKey]?.reference == ref } != 1) continue
            dao.entry(out.copy(type = "TRANSFER", destinationId = inc.accountId, category = "Transfer"))
            dao.entry(inc.copy(deleted = true))
            messages[inc.smsKey]?.let { dao.message(it.copy(status = "LINKED", reason = "Matched transfer")) }
        }
    }
    suspend fun save(entry: Entry, remember: Boolean = false) = mutex.withLock {
        require(entry.amount > 0) { "Enter an amount above zero" }
        val accounts = dao.allAccounts(); val source = accounts.single { it.id == entry.accountId }
        require(entry.date >= source.openingDate) { "Date is before this account's opening date" }
        require(entry.type in listOf("INCOME", "EXPENSE", "TRANSFER"))
        if (entry.type == "TRANSFER") {
            val target = accounts.singleOrNull { it.id == entry.destinationId }
            require(target != null && target.id != source.id) { "Choose a different destination account" }
            require(entry.date >= target.openingDate) { "Transfer is before destination opening date" }
        }
        db.withTransaction {
            entry.smsKey?.let { key -> require(dao.entryForSms(key)?.let { it.id == entry.id } != false) { "This SMS already has a transaction. Edit it from Entries." } }
            dao.entry(entry)
            if (remember && entry.payee.isNotBlank() && entry.type != "TRANSFER") dao.rule(MerchantRule(entry.payee.trim().uppercase(), entry.category))
            entry.smsKey?.let { key -> dao.message(key)?.let { dao.message(it.copy(status = "SAVED", reason = "")) } }
        }
    }
    suspend fun delete(entry: Entry) = db.withTransaction {
        dao.entry(entry.copy(deleted = true))
        entry.smsKey?.let { key -> dao.message(key)?.let { dao.message(it.copy(status = "DISMISSED")) } }
    }
    suspend fun dismiss(m: SmsRecord) { dao.message(m.copy(status = "DISMISSED")) }
    suspend fun refreshStoredReviews() {
        val candidates = dao.allMessages().filter { it.status == "REVIEW" }.mapNotNull { message ->
            SmsParser.parseSelected(message.body)?.let { Candidate(message.sender, message.body, message.received, it) }
        }
        if (candidates.isNotEmpty()) ingest(candidates, forceReview = true)
    }
    suspend fun linkTransfer(out: Entry, incoming: Entry) = mutex.withLock { db.withTransaction {
        val live = dao.allEntries().filter { !it.deleted }
        val debit = live.singleOrNull { it.id == out.id }; val credit = live.singleOrNull { it.id == incoming.id }
        require(debit != null && credit != null && debit.type == "EXPENSE" && credit.type == "INCOME" && debit.amount == credit.amount && debit.accountId != credit.accountId) { "These entries cannot be linked" }
        val target = dao.allAccounts().single { it.id == credit.accountId }
        require(debit.date >= target.openingDate) { "Transfer date is before destination opening date" }
        dao.entry(debit.copy(type = "TRANSFER", destinationId = credit.accountId, category = "Transfer"))
        dao.entry(credit.copy(deleted = true))
        credit.smsKey?.let { dao.message(it)?.let { m -> dao.message(m.copy(status = "LINKED", reason = "Confirmed transfer")) } }
    } }
    suspend fun replaceData(action: suspend LedgerDao.() -> Unit) = mutex.withLock {
        db.withTransaction { dao.action() }
        prefs.edit().putBoolean("enabled", false).remove("from").commit()
    }
    suspend fun reconcile() {
        if (!prefs.getBoolean("enabled", false) || !hasSmsPermission()) return
        val from = prefs.getLong("from", System.currentTimeMillis()); val now = System.currentTimeMillis()
        ingest(scan(from, now + 1), requireCapture = true)
        if (prefs.getBoolean("enabled", false)) prefs.edit().putLong("from", maxOf(from, now - 86_400_000L)).apply()
    }
}
