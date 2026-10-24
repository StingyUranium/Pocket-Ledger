package `in`.thomas.pocketledger

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    private fun account(id: String = "a", bank: String = "SBM", suffix: String = "1111") =
        JSONObject().put("id", id).put("name", "Acct $id").put("bank", bank).put("suffix", suffix).put("opening", 0).put("openingDate", 0)
    private fun entry(id: String = "e1", type: String = "EXPENSE", amount: Long = 100, accountId: String = "a", destinationId: String? = null) =
        JSONObject().put("id", id).put("accountId", accountId).put("destinationId", destinationId ?: JSONObject.NULL).put("type", type)
            .put("amount", amount).put("date", 10).put("dateOnly", false).put("payee", "").put("category", "Food").put("note", "").put("smsKey", JSONObject.NULL).put("deleted", false)
    private fun root(version: Int = 2, accounts: List<JSONObject> = listOf(account()), entries: List<JSONObject> = listOf(entry())) =
        JSONObject().put("version", version).put("accounts", JSONArray(accounts)).put("entries", JSONArray(entries))
            .put("messages", JSONArray()).put("senders", JSONArray()).put("budgets", JSONArray()).put("rules", JSONArray())

    private fun rejected(json: String): String = try { Backup.parse(json); fail("expected rejection"); "" } catch (e: IllegalArgumentException) { e.message.orEmpty() }

    @Test fun validBackupParsesInBothVersions() {
        assertEquals(1, Backup.parse(root(1).toString()).entries.size)
        assertEquals(1, Backup.parse(root(2).toString()).accounts.size)
    }

    @Test fun rejectsUnsupportedVersion() { assertTrue(rejected(root(3).toString()).contains("version")) }
    @Test fun rejectsUnknownBank() { rejected(root(accounts = listOf(account(bank = "HDFC"))).toString()) }
    @Test fun rejectsBadSuffix() { rejected(root(accounts = listOf(account(suffix = "12"))).toString()) }
    @Test fun rejectsDuplicateMappedAccounts() {
        assertTrue(rejected(root(accounts = listOf(account("a"), account("b")), entries = emptyList()).toString()).contains("duplicate accounts"))
    }
    @Test fun allowsMultipleCashAccounts() {
        Backup.parse(root(accounts = listOf(account("a", "CASH", ""), account("b", "CASH", "")), entries = emptyList()).toString())
    }
    @Test fun rejectsInvalidEntryType() { rejected(root(entries = listOf(entry(type = "GIFT"))).toString()) }
    @Test fun rejectsNonPositiveAndHugeAmounts() {
        rejected(root(entries = listOf(entry(amount = 0))).toString())
        rejected(root(entries = listOf(entry(amount = Long.MAX_VALUE))).toString())
    }
    @Test fun rejectsUnknownAccountReference() { rejected(root(entries = listOf(entry(accountId = "zzz"))).toString()) }
    @Test fun rejectsTransferToSelfOrMissingDestination() {
        rejected(root(entries = listOf(entry(type = "TRANSFER", destinationId = "a"))).toString())
        rejected(root(entries = listOf(entry(type = "TRANSFER"))).toString())
    }
    @Test fun dropsStaleDestinationOnNonTransfer() {
        val d = Backup.parse(root(accounts = listOf(account("a"), account("b", suffix = "2222")), entries = listOf(entry(destinationId = "b"))).toString())
        assertNull(d.entries.single().destinationId)
    }
    @Test fun malformedStructureGetsFriendlyMessage() {
        assertTrue(rejected("{\"version\":2}").contains("damaged or incomplete"))
        assertTrue(rejected("not json").contains("damaged or incomplete"))
        assertTrue(rejected(JSONObject(root().toString()).put("accounts", "oops").toString()).contains("damaged or incomplete"))
    }
}
