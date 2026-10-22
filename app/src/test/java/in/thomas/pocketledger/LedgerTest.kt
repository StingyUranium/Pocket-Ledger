package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test

class LedgerTest {
    private val a = Account("a", "A", "SBM", "1111", 10000, 100)
    private val b = Account("b", "B", "AXIS", "2222", 5000, 100)
    @Test fun balancesCountTransferOncePerAccount() {
        val entries = listOf(Entry("1", "a", "b", "TRANSFER", 2000, 200), Entry("2", "a", type = "INCOME", amount = 1000, date = 200), Entry("3", "b", type = "EXPENSE", amount = 500, date = 200))
        assertEquals(9000L, balance(a, entries)); assertEquals(6500L, balance(b, entries)); assertEquals(15500L, balance(a, entries) + balance(b, entries))
    }
    @Test fun deletedAndBeforeOpeningEntriesDoNotAffectBalance() {
        val entries = listOf(Entry("1", "a", type = "EXPENSE", amount = 2000, date = 90), Entry("2", "a", type = "EXPENSE", amount = 1000, date = 200, deleted = true))
        assertEquals(10000L, balance(a, entries))
    }
    @Test fun encryptionRoundTripsUnicode() {
        val bytes = "₹123.45 · बैंक".toByteArray(); val encrypted = BackupCrypto.encrypt(bytes, "test-password".toCharArray())
        assertArrayEquals(bytes, BackupCrypto.decrypt(encrypted, "test-password".toCharArray())); assertFalse(String(encrypted).contains("123.45"))
    }
    @Test fun encryptionHasRandomSaltAndNonce() {
        val pass = "test-password".toCharArray(); assertFalse(BackupCrypto.encrypt(byteArrayOf(1), pass).contentEquals(BackupCrypto.encrypt(byteArrayOf(1), pass)))
    }
    @Test(expected = IllegalStateException::class) fun wrongPasswordDoesNotDecrypt() { BackupCrypto.decrypt(BackupCrypto.encrypt(byteArrayOf(1), "test-password".toCharArray()), "wrong-password".toCharArray()) }
    @Test(expected = IllegalStateException::class) fun tamperedBackupDoesNotDecrypt() {
        val pass = "test-password".toCharArray(); val data = BackupCrypto.encrypt(byteArrayOf(1, 2), pass); data[data.lastIndex] = (data.last().toInt() xor 1).toByte(); BackupCrypto.decrypt(data, pass)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsShortPassword() { BackupCrypto.encrypt(byteArrayOf(1), "short".toCharArray()) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidHeader() { BackupCrypto.decrypt(ByteArray(64), "test-password".toCharArray()) }
}
