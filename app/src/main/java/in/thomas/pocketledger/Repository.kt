package `in`.thomas.pocketledger
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Properties
import java.util.UUID

/** Schedules the 15-minute SMS reconcile only while capture is enabled; call after changing the "enabled" preference. */
object ReconcileWork {
    private const val NAME = "sms-reconcile"
    fun sync(context: Context, enabled: Boolean) {
        try {
            val work = androidx.work.WorkManager.getInstance(context)
            if (enabled) work.enqueueUniquePeriodicWork(NAME, androidx.work.ExistingPeriodicWorkPolicy.KEEP, androidx.work.PeriodicWorkRequestBuilder<SmsWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build())
            else work.cancelUniqueWork(NAME)
        } catch (e: Exception) { Log.w("LedgerSms", "Could not update reconcile schedule", e) }
    }
}

class LedgerApp : Application() {
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val widgetsPresent = MutableStateFlow(false)
    private val captureListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "enabled") ReconcileWork.sync(this, prefs.getBoolean("enabled", false))
    }
    val db by lazy { Room.databaseBuilder(this, LedgerDatabase::class.java, "ledger.db").addMigrations(*LedgerMigrations).build() }
    val repo by lazy { Repository(this, db) }
    override fun onCreate() {
        super.onCreate()
        CategoryNotifications.ensureChannel(this)
        val capture = getSharedPreferences("capture", MODE_PRIVATE)
        capture.registerOnSharedPreferenceChangeListener(captureListener)
        ReconcileWork.sync(this, capture.getBoolean("enabled", false))
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) = updateWidgetPresence()
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        updateWidgetPresence()
        widgetScope.launch { collectWidgetData() }
    }
    /** Cheap check; widget providers and the app call it when widgets may have been added or removed. */
    fun updateWidgetPresence() { widgetsPresent.value = runCatching { LedgerWidgets.hasWidgets(this) }.getOrDefault(false) }
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun collectWidgetData() {
        // The database is only observed while at least one widget is placed; changes are coalesced and unchanged data is skipped.
        widgetsPresent.flatMapLatest { present -> if (present) currentMonthData() else emptyFlow() }
            .debounce(500).distinctUntilChanged().collect { (entries, budgets) ->
                if (!LedgerWidgets.hasWidgets(this)) { widgetsPresent.value = false; return@collect }
                runCatching { LedgerWidgets.render(this, entries, budgets) }
                    .onFailure { if (it is CancellationException) throw it; Log.e("LedgerWidgets", "Could not refresh widgets", it) }
            }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun currentMonthData(): Flow<Pair<List<Entry>, List<Budget>>> = monthTicks().flatMapLatest { month ->
        val from = month.atDay(1).atStartOfDay(india).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(india).toInstant().toEpochMilli()
        combine(db.dao().entriesBetween(from, to), db.dao().budgets()) { entries, budgets -> entries to budgets }
    }
    private fun monthTicks(): Flow<YearMonth> = flow {
        while (true) {
            val month = YearMonth.now(india); emit(month)
            val next = month.plusMonths(1).atDay(1).atStartOfDay(india).toInstant().toEpochMilli()
            delay((next - System.currentTimeMillis()).coerceAtLeast(1_000L))
        }
    }
}
/** Seeded accounts come from the git-ignored assets/local-accounts.properties; without it only Cash is created. */
internal fun loadLocalSeed(context: Context): Properties? = runCatching {
    context.assets.open("local-accounts.properties").use { Properties().apply { load(it) } }
}.getOrNull()
internal fun seedAccounts(local: Properties?, openingDate: Long): List<Account> = buildList {
    fun suffix(key: String) = local?.getProperty(key)?.trim()?.takeIf { it.matches(Regex("\\d{4}")) }
    suffix("sbm.suffix")?.let { add(Account("sbm", "SBM Bank India", "SBM", it, openingDate = openingDate)) }
    suffix("axis.suffix")?.let { add(Account("axis", "Axis Bank", "AXIS", it, openingDate = openingDate)) }
    add(Account("cash", "Cash", "CASH", "", openingDate = openingDate))
}
data class Candidate(val sender: String, val body: String, val received: Long, val parsed: ParsedSms) { val key get() = SmsParser.key(parsed, sender, body, received) }
class Repository(val context: Context, val db: LedgerDatabase) {
    val dao = db.dao()
    private val mutex = Mutex()
    val prefs = context.getSharedPreferences("capture", Context.MODE_PRIVATE)
    suspend fun initialize(local: Properties? = loadLocalSeed(context)) = mutex.withLock {
        if (dao.allAccounts().isEmpty()) {
            val start = LocalDate.now(india).withDayOfMonth(1).atStartOfDay(india).toInstant().toEpochMilli()
            db.withTransaction { seedAccounts(local, start).forEach { dao.account(it) } }
        }
        // Resolved messages keep only a hash of their text. Dismissed rows wait until now so an undo can still restore the body.
        db.withTransaction { dao.resolvedWithBody().forEach { dao.message(it.purged()) } }
    }
    fun hasSmsPermission() = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    suspend fun scan(from: Long, to: Long): List<Candidate> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) error("SMS permission is required. Manual entries are still available.")
        val result = mutableListOf<Candidate>()
        val cursor = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), "date >= ? AND date < ?", arrayOf(from.toString(), to.toString()), "date ASC")
            ?: error("Could not read SMS. Try again later.")
        cursor.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(1).orEmpty(); val parsed = SmsParser.parseSelected(body) ?: continue
                result += Candidate(c.getString(0).orEmpty(), body, c.getLong(2), parsed)
            }
        }
        result.distinctBy { Triple(it.key, it.parsed.amount, it.parsed.date) }
    }
    suspend fun ingest(candidates: List<Candidate>, forceReview: Boolean = false, requireCapture: Boolean = false) = mutex.withLock {
        if (requireCapture && !prefs.getBoolean("enabled", false)) return@withLock
        val toNotify = db.withTransaction {
        val created = mutableListOf<Entry>()
        val trustedSenders = dao.allSenders()
        val accounts = dao.allAccounts()
        val rules = dao.allRules()
        // Only rows that can share an identity with these candidates are loaded: same reference, or unreferenced rows keyed by body.
        val references = candidates.mapNotNull { it.parsed.reference }.distinct()
        val stored = (references.chunked(500).flatMap { dao.messagesByReference(it) } + dao.unreferencedMessages()).distinctBy { it.key }
        val legacyBodies = stored.filter { !it.complete }.associateBy { it.semanticKey() }
        val semanticKeys = stored.associateBy { it.semanticKey() }.toMutableMap()
        val savedKeys = mutableListOf<String>()
        for (c in candidates) {
            val p = c.parsed
            // Older parser versions keyed unsupported formats by their original message body.
            // Keep that key (including tombstones) when the upgraded parser recognizes a reference.
            val existing = dao.message(c.key) ?: semanticKeys[c.key] ?: dao.message(SmsParser.bodyKey(c.sender, c.body, c.received))
                ?: if (p.complete) legacyBodies[SmsParser.stableBodyKey(c.sender, c.body)] else null
            // Resolved rows (saved, dismissed, linked) only conflict on money-critical fields; a re-parsed
            // timestamp alone must not resurrect them. Rows still in review also compare the date.
            val conflict = existing != null && existing.complete &&
                (existing.amount != p.amount || existing.suffix != p.suffix || (existing.status == "REVIEW" && existing.date != p.date))
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
            val trusted = trustedSenders.any { it.bank == p.bank && it.sender == normalizedSender(c.sender) }
            val account = accounts.filter { it.bank == p.bank && it.suffix == p.suffix }.singleOrNull()
            val reason = when { conflict -> "Conflicts with an existing reference: check for a duplicate"; forceReview && existing != null && existing.status == "REVIEW" && existing.complete && existing.reason.isNotBlank() && !conflict -> existing.reason; forceReview -> "Review historical transaction"; !p.complete -> "Incomplete or unfamiliar bank format"; !trusted -> "Confirm this sender ID"; account == null -> "Map this account in Accounts"; p.date != null && p.date < account.openingDate -> "Before account opening date"; p.date != null && p.date > c.received + 86_400_000L -> "Transaction date is after message receipt"; else -> "" }
            val record = SmsRecord(key, c.sender, c.body, if (conflict) c.received else existing?.received ?: c.received, p.bank, p.suffix, p.amount, p.direction, p.date, p.dateOnly, p.payee, p.reference, p.balance, p.complete, reason = reason)
            dao.message(record)
            if (!conflict) semanticKeys[c.key] = record
            if (reason.isEmpty() && account != null) saveParsed(record, account, rules)?.let { created += it; savedKeys += record.key }
        }
        // Only references touched by this batch can form a new transfer pair.
        val touched = savedKeys.chunked(500).flatMap { dao.referencesForKeys(it) }
        if (touched.isNotEmpty()) matchTransfers(accounts, touched)
        created.mapNotNull { dao.entry(it.id) }.filter(::needsCategoryNotification)
    }
        notifySafely(toNotify)
    }
    /** Runs after the transaction commits; a notification failure must never undo or fail ingestion. */
    private fun notifySafely(entries: List<Entry>) {
        if (entries.isEmpty()) return
        try {
            val (individual, summary) = coalesceNotifications(entries)
            individual.forEach { CategoryNotifications.show(context, it) }
            if (summary > 0) CategoryNotifications.showSummary(context, summary)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { Log.w("LedgerNotify", "Could not post category reminders", e) }
    }
    private suspend fun saveParsed(m: SmsRecord, a: Account, rules: List<MerchantRule>): Entry? {
        if (dao.entryForSms(m.key) != null) return null
        val rule = rules.find { it.merchant == m.payee.trim().uppercase() && it.type == m.direction }
        val entry = Entry(UUID.randomUUID().toString(), a.id, type = requireNotNull(m.direction), amount = requireNotNull(m.amount), date = requireNotNull(m.date), dateOnly = m.dateOnly, payee = m.payee, category = rule?.category ?: "Uncategorized", smsKey = m.key)
        dao.entry(entry)
        dao.message(m.copy(status = "SAVED", reason = "").purged())
        return entry
    }
    suspend fun trust(sender: String, bank: String) { dao.sender(TrustedSender(normalizedSender(sender), bank)); processReview() }
    suspend fun processReview() = mutex.withLock {
        val toNotify = db.withTransaction {
        val created = mutableListOf<Entry>()
        val trusted = dao.allSenders(); val accounts = dao.allAccounts(); val rules = dao.allRules()
        dao.reviewMessages().filter { it.complete && !it.key.contains(":conflict:") && (it.date ?: Long.MAX_VALUE) <= it.received + 86_400_000L }.forEach { m ->
            val a = accounts.singleOrNull { it.bank == m.bank && it.suffix == m.suffix }
            if (a != null && m.date != null && m.date >= a.openingDate && trusted.any { it.bank == m.bank && it.sender == normalizedSender(m.sender) }) saveParsed(m, a, rules)?.let { created += it }
        }
        // Account or sender changes can unlock older pairs, so this pass considers every referenced entry (one join, grouped once).
        matchTransfers(accounts, null)
        created.mapNotNull { dao.entry(it.id) }.filter(::needsCategoryNotification)
        }
        notifySafely(toNotify)
    }
    /** [references] null means every referenced entry; otherwise only those references. Accounts are loaded once by the caller. */
    private suspend fun matchTransfers(accounts: List<Account>, references: List<String>?) {
        val rows = if (references == null) dao.allTransferCandidates() else references.distinct().chunked(500).flatMap { dao.transferCandidates(it) }
        if (rows.isEmpty()) return
        val openings = accounts.associate { it.id to it.openingDate }
        for ((_, group) in rows.groupBy { it.reference }) {
            val entries = group.map { it.entry }
            val expenses = entries.filter { it.type == "EXPENSE" }
            for (out in expenses) {
                val ins = entries.filter { it.type == "INCOME" && it.accountId != out.accountId && it.amount == out.amount && kotlin.math.abs(it.date - out.date) <= 86_400_000L }
                if (ins.size != 1) continue
                val inc = ins.single()
                if (out.date < (openings[inc.accountId] ?: Long.MAX_VALUE)) continue
                if (expenses.count { it.amount == inc.amount } != 1) continue
                dao.entry(out.copy(type = "TRANSFER", destinationId = inc.accountId, category = "Transfer"))
                dao.entry(inc.copy(deleted = true))
                inc.smsKey?.let { key -> dao.message(key)?.let { dao.message(it.copy(status = "LINKED", reason = "Matched transfer").purged()) } }
            }
        }
    }
    suspend fun save(entry: Entry, remember: Boolean = false) = mutex.withLock {
        require(entry.amount > 0) { "Enter an amount above zero" }
        val accounts = dao.allAccounts(); val source = accounts.singleOrNull { it.id == entry.accountId }
        require(source != null) { "Choose an account for this transaction" }
        require(entry.date >= source.openingDate) { "Date is before this account's opening date" }
        require(entry.type in listOf("INCOME", "EXPENSE", "TRANSFER"))
        if (entry.type == "TRANSFER") {
            val target = accounts.singleOrNull { it.id == entry.destinationId }
            require(target != null && target.id != source.id) { "Choose a different destination account" }
            require(entry.date >= target.openingDate) { "Transfer is before destination opening date" }
        }
        db.withTransaction {
            val current = dao.entry(entry.id)
            require(current?.deleted != true) { "This transaction was deleted. Reopen the ledger before editing." }
            require(current == null || current.type != "TRANSFER" || entry.type == "TRANSFER") { "This transfer is linked to another account. Delete it and add the entries again to change its type." }
            entry.smsKey?.let { key -> require(dao.message(key) != null) { "This source SMS is no longer in the ledger" } }
            entry.smsKey?.let { key -> require(dao.entryForSms(key)?.let { it.id == entry.id } != false) { "This SMS already has a transaction. Edit it from Entries." } }
            dao.entry(entry)
            if (remember && entry.payee.isNotBlank() && entry.type != "TRANSFER") dao.rule(MerchantRule(entry.payee.trim().uppercase(), entry.category, entry.type))
            entry.smsKey?.let { key -> dao.message(key)?.let { dao.message(it.copy(status = "SAVED", reason = "").purged()) } }
        }
        if (!needsCategoryNotification(entry)) CategoryNotifications.cancel(context, entry.id)
    }
    suspend fun updateCategory(entryId: String, category: String, remember: Boolean = false) = mutex.withLock {
        require(category.isNotBlank()) { "Choose a category" }
        db.withTransaction {
            val current = dao.entry(entryId)
            require(current != null && !current.deleted && current.type != "TRANSFER") { "This transaction is no longer available for categorization" }
            dao.entry(current.copy(category = category.trim()))
            if (remember && current.payee.isNotBlank()) dao.rule(MerchantRule(current.payee.trim().uppercase(), category.trim(), current.type))
        }
        if (category.trim() != "Uncategorized") CategoryNotifications.cancel(context, entryId)
    }

    suspend fun removeBudget(category: String) = mutex.withLock {
        dao.removeBudget(category)
    }

    suspend fun delete(entry: Entry) = mutex.withLock {
        db.withTransaction {
            val current = dao.entry(entry.id) ?: return@withTransaction
            dao.entry(current.copy(deleted = true))
            current.smsKey?.let { key -> dao.message(key)?.let { dao.message(it.copy(status = "DISMISSED")) } }
        }
        CategoryNotifications.cancel(context, entry.id)
    }
    suspend fun dismiss(m: SmsRecord) = mutex.withLock {
        db.withTransaction {
            val current = dao.message(m.key)
            if (current?.status == "REVIEW") dao.message(current.copy(status = "DISMISSED"))
        }
    }
    suspend fun undoDismiss(m: SmsRecord) = mutex.withLock {
        val current = dao.message(m.key)
        if (m.status == "REVIEW" && current?.status == "DISMISSED") {
            dao.message(current.copy(status = "REVIEW", reason = m.reason, body = m.body))
        }
    }
    suspend fun refreshStoredReviews() {
        val candidates = dao.reviewMessages().mapNotNull { message ->
            SmsParser.parseSelected(message.body)?.let { Candidate(message.sender, message.body, message.received, it) }
        }
        if (candidates.isNotEmpty()) ingest(candidates, forceReview = true)
    }
    suspend fun linkTransfer(out: Entry, incoming: Entry) = mutex.withLock { db.withTransaction {
        val live = dao.allEntries().filter { !it.deleted }
        val debit = live.singleOrNull { it.id == out.id }; val credit = live.singleOrNull { it.id == incoming.id }
        require(debit != null && credit != null && debit.type == "EXPENSE" && credit.type == "INCOME" && debit.amount == credit.amount && debit.accountId != credit.accountId) { "These entries cannot be linked" }
        val target = requireNotNull(dao.allAccounts().singleOrNull { it.id == credit.accountId }) { "These entries cannot be linked" }
        require(debit.date >= target.openingDate) { "Transfer date is before destination opening date" }
        dao.entry(debit.copy(type = "TRANSFER", destinationId = credit.accountId, category = "Transfer"))
        dao.entry(credit.copy(deleted = true))
        credit.smsKey?.let { dao.message(it)?.let { m -> dao.message(m.copy(status = "LINKED", reason = "Confirmed transfer").purged()) } }
    } }
    /** Validates and saves an account atomically, then re-evaluates inbox reviews against it. */
    suspend fun saveAccount(account: Account) {
        mutex.withLock {
            val name = account.name.trim()
            require(name.isNotBlank()) { "Name is required" }
            require(account.bank == "CASH" || account.suffix.matches(Regex("\\d{4}"))) { "Enter the account's last four digits" }
            db.withTransaction {
                val accounts = dao.allAccounts()
                require(account.bank == "CASH" || accounts.none { it.id != account.id && it.bank == account.bank && it.suffix == account.suffix }) { "This account is already mapped" }
                require(dao.allEntries().none { !it.deleted && (it.accountId == account.id || it.destinationId == account.id) && it.date < account.openingDate }) { "Opening date cannot be after an existing transaction" }
                dao.account(account.copy(name = name))
            }
        }
        processReview()
    }
    suspend fun replaceData(action: suspend LedgerDao.() -> Unit) = mutex.withLock {
        db.withTransaction { dao.action(); dao.resolvedWithBody().forEach { dao.message(it.purged()) } }
        prefs.edit().putBoolean("enabled", false).remove("from").commit()
    }
    suspend fun saveBudget(budget: Budget, originalCategory: String? = null) = mutex.withLock {
        require(budget.category.isNotBlank() && budget.amount >= 0) { "Enter a category and a non-negative limit" }
        db.withTransaction {
            if (originalCategory != null && originalCategory != budget.category) {
                val current = dao.allBudgets()
                require(current.any { it.category == originalCategory }) { "This budget no longer exists. Reopen budgets." }
                require(current.none { it.category == budget.category }) { "This category already has a budget. Edit it from Budgets." }
                dao.removeBudget(originalCategory)
            }
            dao.budget(budget)
        }
    }
    suspend fun reconcile() {
        if (!prefs.getBoolean("enabled", false) || !hasSmsPermission()) return
        val from = prefs.getLong("from", System.currentTimeMillis()); val now = System.currentTimeMillis()
        ingest(scan(from, now + 1), requireCapture = true)
        // Re-check under the mutex so a concurrent restore (which disables capture) is not overwritten.
        mutex.withLock { if (prefs.getBoolean("enabled", false)) prefs.edit().putLong("from", maxOf(from, now - 86_400_000L)).apply() }
    }
}
