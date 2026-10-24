package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

class ReliabilityHelpersTest {
    private val sender = "VM-AXISBK"
    private val completeBody = "INR 100.00 debited A/c XX0424 UPI/P2M/111111111111/SYNTHETIC SHOP"
    private val incompleteBody = "Some unfamiliar bank format"

    private fun record(body: String, complete: Boolean, reference: String? = "111111111111", status: String = "SAVED") = SmsRecord(
        "stored-key", sender, body, 1_000L, "AXIS", if (complete) "0424" else null, if (complete) 10_000L else null,
        if (complete) "EXPENSE" else null, if (complete) 5_000L else null, false, "SHOP", if (complete) reference else null, null, complete, status, "")

    @Test fun purgedBodyIsAHashOfSenderAndBodyAndRecognizable() {
        val purged = purgedBody(sender, completeBody)
        assertTrue(isPurgedBody(purged))
        assertFalse(purged.contains("SYNTHETIC"))
        assertEquals(PurgedBodyPrefix + SmsParser.stableBodyKey(sender, completeBody), purged)
        assertFalse(isPurgedBody(completeBody))
        assertFalse(isPurgedBody("purged:not-a-hash"))
    }

    @Test fun purgingIsIdempotent() {
        val once = record(completeBody, true).purged()
        assertEquals(once, once.purged())
        assertTrue(once.bodyPurged())
    }

    @Test fun completeRecordKeepsItsSemanticKeyAfterPurge() {
        val original = record(completeBody, true)
        val before = original.semanticKey()
        assertEquals(SmsParser.key(original.parsed(), sender, completeBody, 1_000L), before)
        assertEquals(before, original.purged().semanticKey())
    }

    @Test fun incompleteRecordKeepsItsBodyIdentityAfterPurge() {
        val original = record(incompleteBody, false)
        assertEquals(SmsParser.stableBodyKey(sender, incompleteBody), original.semanticKey())
        assertEquals(original.semanticKey(), original.purged().semanticKey())
    }

    @Test fun purgeKeepsParsedFieldsAndIdentityUntouched() {
        val original = record(completeBody, true)
        val purged = original.purged()
        assertEquals(original.copy(body = purged.body), purged)
    }

    @Test fun seedAccountsOnlyIncludesConfiguredBankSuffixes() {
        assertEquals(listOf("cash"), seedAccounts(null, 5).map { it.id })
        assertEquals(listOf("cash"), seedAccounts(Properties().apply { setProperty("sbm.suffix", "12") }, 5).map { it.id })
        val local = Properties().apply { setProperty("sbm.suffix", "1111"); setProperty("axis.suffix", " 2222 ") }
        val accounts = seedAccounts(local, 5)
        assertEquals(listOf("sbm", "axis", "cash"), accounts.map { it.id })
        assertEquals(listOf("1111", "2222", ""), accounts.map { it.suffix })
        assertTrue(accounts.all { it.openingDate == 5L })
    }

    @Test fun smallBatchesNotifyIndividuallyAndBulkImportsCollapseToOneSummary() {
        val entries = (1..5).map { Entry("e$it", "a", type = "EXPENSE", amount = 100L * it, date = it.toLong(), smsKey = "k$it") }
        assertEquals(entries.take(3) to 0, coalesceNotifications(entries.take(3)))
        assertEquals(emptyList<Entry>() to 4, coalesceNotifications(entries.take(4)))
        assertEquals(emptyList<Entry>() to 5, coalesceNotifications(entries))
        assertEquals(emptyList<Entry>() to 0, coalesceNotifications(emptyList()))
    }

    @Test fun verifyRejectsMismatchedReadBackAndWrongPassword() {
        val password = "correct horse".toCharArray()
        val bytes = BackupCrypto.encrypt("{}".toByteArray(), password, BackupCrypto.MinIterations)
        Backup.verify(bytes, bytes, password, validateContent = false)
        val corrupted = bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(IllegalStateException::class.java) { Backup.verify(corrupted, bytes, password, validateContent = false) }
        assertThrows(IllegalStateException::class.java) { Backup.verify(bytes, bytes, "wrong password".toCharArray(), validateContent = false) }
    }

    @Test fun verifyRejectsContentThatCannotBeParsed() {
        val password = "correct horse".toCharArray()
        val bytes = BackupCrypto.encrypt("{}".toByteArray(), password, BackupCrypto.MinIterations)
        assertThrows(IllegalArgumentException::class.java) { Backup.verify(bytes, bytes, password) }
    }
}
