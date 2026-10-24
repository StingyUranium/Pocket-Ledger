package `in`.thomas.pocketledger

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val MaxBackupBytes = 33_554_432
    private val header = "PKTLGR01".toByteArray()
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }
    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "Use a password of at least 8 characters" }
        require(data.size <= MaxBackupBytes - 52) { "Backup is too large" }
        val random = SecureRandom(); val salt = ByteArray(16).also(random::nextBytes); val iv = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)); c.updateAAD(header)
        return header + salt + iv + c.doFinal(data)
    }
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(data.size in 52..MaxBackupBytes && data.copyOfRange(0, 8).contentEquals(header)) { "This is not a Ledger backup" }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(password, data.copyOfRange(8, 24)), GCMParameterSpec(128, data.copyOfRange(24, 36))); c.updateAAD(header)
        return try { c.doFinal(data, 36, data.size - 36) } catch (_: javax.crypto.AEADBadTagException) { error("Incorrect password or damaged backup") }
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
        return BackupCrypto.encrypt(root.toString().toByteArray(), password)
    }
    suspend fun restore(repo: Repository, bytes: ByteArray, password: CharArray) {
        val root = JSONObject(String(BackupCrypto.decrypt(bytes, password), Charsets.UTF_8))
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
        val ids = accounts.map { it.id }.toSet(); val keys = messages.map { it.key }.toSet()
        require(accounts.isNotEmpty() && ids.size == accounts.size && entries.map { it.id }.distinct().size == entries.size && keys.size == messages.size) { "Backup contains duplicate or missing records" }
        val smsKeys = entries.mapNotNull { it.smsKey }
        require(smsKeys.distinct().size == smsKeys.size) { "Backup contains duplicate SMS transactions" }
        require(messages.all { m ->
            m.status in listOf("REVIEW", "SAVED", "DISMISSED", "LINKED") &&
                (!m.complete || m.amount != null && m.amount > 0 && m.date != null &&
                    m.direction in listOf("INCOME", "EXPENSE") && m.suffix != null && m.reference != null)
        }) { "Backup contains invalid SMS records" }
        require(entries.all { it.amount > 0 && it.accountId in ids && it.type in listOf("INCOME", "EXPENSE", "TRANSFER") && (it.type != "TRANSFER" || it.destinationId in ids && it.destinationId != it.accountId) && (it.smsKey == null || it.smsKey in keys) }) { "Backup contains invalid transactions" }
        require(budgets.all { it.category.isNotBlank() && it.amount >= 0 }) { "Backup contains invalid budgets" }
        require(rules.all { it.type in listOf("INCOME", "EXPENSE") && it.category.isNotBlank() } &&
            rules.map { it.merchant to it.type }.distinct().size == rules.size) { "Backup contains invalid category rules" }
        repo.replaceData {
            clearEntries(); clearMessages(); clearAccounts(); clearSenders(); clearBudgets(); clearRules()
            accounts.forEach { account(it) }; messages.forEach { message(it) }; entries.forEach { entry(it) }
            senders.forEach { sender(it) }; budgets.forEach { budget(it) }; rules.forEach { rule(it) }
        }
    }
}
