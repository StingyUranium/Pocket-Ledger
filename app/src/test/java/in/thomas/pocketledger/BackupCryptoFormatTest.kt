package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCryptoFormatTest {
    private val pass = "test-password".toCharArray()

    /** Byte-for-byte reimplementation of the original PKTLGR01 writer (fixed 600k iterations, AAD = magic). */
    private fun legacyEncrypt(data: ByteArray): ByteArray {
        val header = "PKTLGR01".toByteArray()
        val random = SecureRandom(); val salt = ByteArray(16).also(random::nextBytes); val iv = ByteArray(12).also(random::nextBytes)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, 600_000, 256)).encoded
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv)); c.updateAAD(header)
        return header + salt + iv + c.doFinal(data)
    }

    @Test fun readsLegacyHeaderBackups() {
        val plain = "legacy ₹100".toByteArray()
        assertArrayEquals(plain, BackupCrypto.decrypt(legacyEncrypt(plain), pass))
    }

    @Test fun newBackupsAreVersion2WithStoredIterations() {
        val encrypted = BackupCrypto.encrypt(byteArrayOf(1, 2, 3), pass)
        assertEquals("PKTLGR02", String(encrypted, 0, 8))
        assertEquals(BackupCrypto.CurrentIterations, ByteBuffer.wrap(encrypted, 8, 4).int)
    }

    @Test fun honoursNonDefaultIterationCountFromHeader() {
        val plain = byteArrayOf(9, 8, 7)
        val encrypted = BackupCrypto.encrypt(plain, pass, 150_000)
        assertEquals(150_000, ByteBuffer.wrap(encrypted, 8, 4).int)
        assertArrayEquals(plain, BackupCrypto.decrypt(encrypted, pass))
    }

    @Test fun rejectsOutOfRangeIterationCounts() {
        for (bad in listOf(0, 1, 99_999, 10_000_001, Int.MAX_VALUE, -1)) {
            val data = BackupCrypto.encrypt(byteArrayOf(1), pass)
            ByteBuffer.wrap(data).putInt(8, bad)
            try { BackupCrypto.decrypt(data, pass); fail("expected rejection for $bad") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun tamperedIterationCountFailsAuthentication() {
        val data = BackupCrypto.encrypt(byteArrayOf(1), pass, 150_000)
        ByteBuffer.wrap(data).putInt(8, 150_001)
        try { BackupCrypto.decrypt(data, pass); fail("expected failure") } catch (_: IllegalStateException) { }
    }

    @Test fun rejectsTruncatedVersion2Backup() {
        val data = "PKTLGR02".toByteArray() + ByteArray(45)
        try { BackupCrypto.decrypt(data, pass); fail("expected rejection") } catch (_: IllegalArgumentException) { }
    }
}
