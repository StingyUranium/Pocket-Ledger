package `in`.thomas.pocketledger

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wire formats. Legacy "PKTLGR01": magic(8) salt(16) iv(12) ciphertext, fixed 600k PBKDF2 iterations, AAD = magic.
 * Current "PKTLGR02": magic(8) iterations(4, big endian) salt(16) iv(12) ciphertext; AAD = magic + iterations,
 * so the stored iteration count is authenticated. Keep reading every version ever written.
 */
object BackupCrypto {
    private const val MaxBackupBytes = 33_554_432
    private const val TagBytes = 16
    private const val LegacyIterations = 600_000
    const val CurrentIterations = 600_000
    const val MinIterations = 100_000
    const val MaxIterations = 10_000_000
    private const val LegacyOverhead = 36
    private const val CurrentOverhead = 40
    private val legacyMagic = "PKTLGR01".toByteArray()
    private val currentMagic = "PKTLGR02".toByteArray()

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val raw = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try { SecretKeySpec(raw, "AES") } finally { raw.fill(0) }
    }

    fun encrypt(data: ByteArray, password: CharArray): ByteArray = encrypt(data, password, CurrentIterations)

    internal fun encrypt(data: ByteArray, password: CharArray, iterations: Int): ByteArray {
        require(password.size >= 8) { "Use a password of at least 8 characters" }
        require(iterations in MinIterations..MaxIterations) { "Unsupported key strength" }
        require(data.size <= MaxBackupBytes - CurrentOverhead - TagBytes) { "Backup is too large" }
        val random = SecureRandom(); val salt = ByteArray(16).also(random::nextBytes); val iv = ByteArray(12).also(random::nextBytes)
        val header = currentMagic + ByteBuffer.allocate(4).putInt(iterations).array()
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv)); c.updateAAD(header)
        return header + salt + iv + c.doFinal(data)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(data.size in (LegacyOverhead + TagBytes)..MaxBackupBytes) { "This is not a Ledger backup" }
        val magic = data.copyOfRange(0, 8)
        val iterations: Int; val aadEnd: Int; val overhead: Int
        when {
            magic.contentEquals(currentMagic) -> {
                require(data.size >= CurrentOverhead + TagBytes) { "This is not a Ledger backup" }
                iterations = ByteBuffer.wrap(data, 8, 4).int; aadEnd = 12; overhead = CurrentOverhead
                require(iterations in MinIterations..MaxIterations) { "This backup uses an unsupported key strength" }
            }
            magic.contentEquals(legacyMagic) -> { iterations = LegacyIterations; aadEnd = 8; overhead = LegacyOverhead }
            else -> throw IllegalArgumentException("This is not a Ledger backup")
        }
        val salt = data.copyOfRange(aadEnd, aadEnd + 16); val iv = data.copyOfRange(aadEnd + 16, overhead)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv)); c.updateAAD(data, 0, aadEnd)
        return try { c.doFinal(data, overhead, data.size - overhead) } catch (_: javax.crypto.AEADBadTagException) { error("Incorrect password or damaged backup") }
    }
}

// Backup keys are a wire format: never derive them from R8-obfuscatable field names.
private fun json(vararg fields: Pair<String, Any?>) = JSONObject().also { obj ->
    fields.forEach { (name, value) -> obj.put(name, value ?: JSONObject.NULL) }
}
private fun Account.backupJson() = json("id" to id, "name" to name, "bank" to bank, "suffix" to suffix,
    "opening" to opening, "openingDate" to openingDate)
private fun Entry.backupJson() = json("id" to id, "accountId" to accountId, "destinationId" to destinationId,
    "type" to type, "amount" to amount, "date" to date, "dateOnly" to dateOnly, "payee" to payee,
    "category" to category, "note" to note, "smsKey" to smsKey, "deleted" to deleted)
private fun SmsRecord.backupJson() = json("key" to key, "sender" to sender, "body" to body,
    "received" to received, "bank" to bank, "suffix" to suffix, "amount" to amount, "direction" to direction,
    "date" to date, "dateOnly" to dateOnly, "payee" to payee, "reference" to reference,
    "balance" to balance, "complete" to complete, "status" to status, "reason" to reason)
private fun TrustedSender.backupJson() = json("sender" to sender, "bank" to bank)
private fun Budget.backupJson() = json("category" to category, "amount" to amount)
private fun MerchantRule.backupJson() = json("merchant" to merchant, "category" to category, "type" to type)
private fun objects(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)

object Backup {
    suspend fun export(db: LedgerDatabase, password: CharArray): ByteArray {
        val d = db.dao()
        val root = db.withTransaction {
            JSONObject().put("version", 2).put("accounts", JSONArray(d.allAccounts().map { it.backupJson() }))
                .put("entries", JSONArray(d.allEntries().map { it.backupJson() })).put("messages", JSONArray(d.allMessages().map { it.backupJson() }))
                .put("senders", JSONArray(d.allSenders().map { it.backupJson() })).put("budgets", JSONArray(d.allBudgets().map { it.backupJson() })).put("rules", JSONArray(d.allRules().map { it.backupJson() }))
        }
        val plain = root.toString().toByteArray()
        return try { BackupCrypto.encrypt(plain, password) } finally { plain.fill(0) }
    }
    private const val MaxPaise = 10_000_000_000_000L
    private val banks = setOf("SBM", "AXIS", "CASH")
    private val statuses = setOf("REVIEW", "SAVED", "DISMISSED", "LINKED")

    class Data(val accounts: List<Account>, val entries: List<Entry>, val messages: List<SmsRecord>, val senders: List<TrustedSender>, val budgets: List<Budget>, val rules: List<MerchantRule>)

    /** Parses and validates decrypted backup JSON (formats 1 and 2). Every failure is an IllegalArgumentException with a user-readable message. */
    internal fun parse(json: String): Data {
        try {
            return validate(read(JSONObject(json)))
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("This backup is damaged or incomplete", e)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException("This backup is damaged or incomplete", e)
        }
    }

    private fun read(root: JSONObject): Data {
        require(root.getInt("version") in 1..2) { "Unsupported backup version" }
        val accounts = objects(root.getJSONArray("accounts")).map { Account(it.getString("id"), it.getString("name"), it.getString("bank"), it.getString("suffix"), it.getLong("opening"), it.getLong("openingDate")) }
        val entries = objects(root.getJSONArray("entries")).map { Entry(it.getString("id"), it.getString("accountId"), it.str("destinationId"), it.getString("type"), it.getLong("amount"), it.getLong("date"), it.getBoolean("dateOnly"), it.getString("payee"), it.getString("category"), it.getString("note"), it.str("smsKey"), it.getBoolean("deleted")) }
        val messages = objects(root.getJSONArray("messages")).map { SmsRecord(it.getString("key"), it.getString("sender"), it.getString("body"), it.getLong("received"), it.getString("bank"), it.str("suffix"), it.longOrNull("amount"), it.str("direction"), it.longOrNull("date"), it.getBoolean("dateOnly"), it.getString("payee"), it.str("reference"), it.longOrNull("balance"), it.getBoolean("complete"), it.getString("status"), it.getString("reason")) }
        val senders = objects(root.getJSONArray("senders")).map { TrustedSender(it.getString("sender"), it.getString("bank")) }
        val budgets = objects(root.getJSONArray("budgets")).groupBy { it.getString("category") }.map { (category, records) ->
            val latest = records.maxByOrNull { if (it.has("month") && !it.isNull("month")) it.getString("month") else "" }!!
            Budget(category, latest.getLong("amount"))
        }
        val rules = objects(root.getJSONArray("rules")).map {
            val merchant = it.getString("merchant"); val category = it.getString("category")
            MerchantRule(merchant, category, if (it.has("type")) it.getString("type") else legacyRuleType(merchant, category, entries))
        }
        return Data(accounts, entries, messages, senders, budgets, rules)
    }

    private fun validate(raw: Data): Data {
        // Older builds could leave a stale destination on entries edited away from TRANSFER; drop it rather than reject the backup.
        val d = Data(raw.accounts, raw.entries.map { if (it.type != "TRANSFER" && it.destinationId != null) it.copy(destinationId = null) else it }, raw.messages, raw.senders, raw.budgets, raw.rules)
        val ids = d.accounts.map { it.id }.toSet(); val keys = d.messages.map { it.key }.toSet()
        require(d.accounts.isNotEmpty() && ids.size == d.accounts.size && d.entries.map { it.id }.distinct().size == d.entries.size && keys.size == d.messages.size) { "Backup contains duplicate or missing records" }
        require(d.accounts.all { it.id.isNotBlank() && it.name.isNotBlank() && it.bank in banks && (it.bank == "CASH" || it.suffix.matches(Regex("\\d{4}"))) && it.opening in -MaxPaise..MaxPaise && it.openingDate >= 0 }) { "Backup contains invalid accounts" }
        val mapped = d.accounts.filter { it.bank != "CASH" }.map { it.bank to it.suffix }
        require(mapped.distinct().size == mapped.size) { "Backup contains duplicate accounts" }
        val smsKeys = d.entries.mapNotNull { it.smsKey }
        require(smsKeys.distinct().size == smsKeys.size) { "Backup contains duplicate SMS transactions" }
        require(d.messages.all { m ->
            m.status in statuses && m.bank in banks && m.received >= 0 && (m.amount == null || m.amount in 1..MaxPaise) &&
                (m.direction == null || m.direction in listOf("INCOME", "EXPENSE")) &&
                (!m.complete || m.amount != null && m.amount > 0 && m.date != null &&
                    m.direction != null && m.suffix != null && m.reference != null)
        }) { "Backup contains invalid SMS records" }
        require(d.entries.all {
            it.amount in 1..MaxPaise && it.date >= 0 && it.accountId in ids && it.type in listOf("INCOME", "EXPENSE", "TRANSFER") &&
                (if (it.type == "TRANSFER") it.destinationId in ids && it.destinationId != it.accountId else it.destinationId == null) &&
                (it.smsKey == null || it.smsKey in keys)
        }) { "Backup contains invalid transactions" }
        require(d.senders.all { it.sender.isNotBlank() && it.bank in banks }) { "Backup contains invalid trusted senders" }
        require(d.budgets.all { it.category.isNotBlank() && it.amount in 0..MaxPaise }) { "Backup contains invalid budgets" }
        require(d.rules.all { it.type in listOf("INCOME", "EXPENSE") && it.category.isNotBlank() } &&
            d.rules.map { it.merchant to it.type }.distinct().size == d.rules.size) { "Backup contains invalid category rules" }
        return d
    }

    /** Reads the written bytes back and proves they equal what was encrypted and still decrypt and parse with [password]. */
    internal fun verify(readBack: ByteArray, expected: ByteArray, password: CharArray, validateContent: Boolean = true) {
        if (!readBack.contentEquals(expected)) error("The saved backup could not be verified. Choose another location and try again")
        val plain = BackupCrypto.decrypt(readBack, password)
        try { if (validateContent) parse(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
    }

    /** Writes a backup to a SAF document (truncating any old content) and verifies it by reading it back. */
    suspend fun writeVerified(resolver: android.content.ContentResolver, uri: android.net.Uri, bytes: ByteArray, password: CharArray) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            (resolver.openOutputStream(uri, "wt") ?: error("Cannot write this file")).use { it.write(bytes); it.flush() }
            val readBack = (resolver.openInputStream(uri) ?: error("The saved backup could not be verified")).use { it.readBytes() }
            verify(readBack, bytes, password)
        }

    /** Encrypted copy of the current data kept in app-private storage; only the most recent one is retained. */
    private suspend fun writeSafetyCopy(repo: Repository, password: CharArray) {
        val bytes = export(repo.db, password)
        val dir = java.io.File(repo.context.filesDir, "backup-safety").also { it.mkdirs() }
        val temp = java.io.File(dir, "pre-restore.tmp"); val target = java.io.File(dir, "pre-restore.plbackup")
        try {
            temp.writeBytes(bytes)
            verify(temp.readBytes(), bytes, password, validateContent = false)
            if (!temp.renameTo(target)) { target.delete(); check(temp.renameTo(target)) { "rename failed" } }
        } catch (e: Exception) {
            temp.delete()
            throw IllegalStateException("Could not save a safety copy of your current data, so nothing was restored", e)
        }
    }

    suspend fun restore(repo: Repository, bytes: ByteArray, password: CharArray) {
        val plain = BackupCrypto.decrypt(bytes, password)
        val d = try { parse(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
        // The file is valid; keep the data about to be replaced recoverable (encrypted with the same password).
        writeSafetyCopy(repo, password)
        repo.replaceData {
            clearEntries(); clearMessages(); clearAccounts(); clearSenders(); clearBudgets(); clearRules()
            d.accounts.forEach { account(it) }; d.messages.forEach { message(it) }; d.entries.forEach { entry(it) }
            d.senders.forEach { sender(it) }; d.budgets.forEach { budget(it) }; d.rules.forEach { rule(it) }
        }
    }
}
