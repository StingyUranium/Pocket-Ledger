package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test

class SmsIdentityTest {
    // Reference is not numeric, so the parser keeps this message incomplete.
    private val incomplete = "Your account XX6317 is debited with INR 40. on 2026-10-21 16:03:01 infor :UPI/P2M/NOREF/SHOP/. The curr bal is INR 100.00. SBM BANK (INDIA)."
    private val complete = "Your account XX6317 is credited with INR 483.00 on 10-10-2026. Info:UPI/P2P/628368490342/SAMPLE/.The Curr bal is  22498.83. SBM BANK (INDIA)."

    @Test fun incompleteKeyIgnoresReceivedTimestamp() {
        val p = SmsParser.parseSelected(incomplete)!!
        assertFalse(p.complete)
        assertEquals(SmsParser.key(p, "VM-SBMBNK", incomplete, 1_000L), SmsParser.key(p, "VM-SBMBNK", incomplete, 9_999_000L))
    }

    @Test fun incompleteKeyNormalizesSenderAndWhitespace() {
        val p = SmsParser.parseSelected(incomplete)!!
        assertEquals(SmsParser.key(p, " vm-sbmbnk ", incomplete, 1L), SmsParser.key(p, "VM-SBMBNK", incomplete.replace(" ", "  "), 2L))
    }

    @Test fun legacyTimestampKeyIsStillComputableForTombstoneFallback() {
        val legacy = SmsParser.bodyKey("VM-SBMBNK", incomplete, 1_000L)
        assertNotEquals(legacy, SmsParser.stableBodyKey("VM-SBMBNK", incomplete))
        assertEquals(SmsParser.bodyKey("VM-SBMBNK", incomplete, 0), SmsParser.stableBodyKey("VM-SBMBNK", incomplete))
    }

    @Test fun storedLegacyRowMapsToTheStableCandidateKey() {
        val p = SmsParser.parseSelected(incomplete)!!
        val stored = SmsRecord(SmsParser.bodyKey("VM-SBMBNK", incomplete, 1_000L), "VM-SBMBNK", incomplete, 1_000L, p.bank, p.suffix, p.amount, p.direction, p.date, p.dateOnly, p.payee, p.reference, p.balance, p.complete, status = "DISMISSED")
        // Repository.ingest indexes stored rows by this semantic key, so a dismissed legacy row still matches a rescan.
        assertEquals(SmsParser.key(p, "VM-SBMBNK", incomplete, 777L), SmsParser.key(stored.parsed(), stored.sender, stored.body, stored.received))
    }

    @Test fun completeKeysStayReferenceBased() {
        val p = SmsParser.parseSelected(complete)!!
        assertTrue(p.complete)
        assertEquals(SmsParser.key(p, "A", complete, 1L), SmsParser.key(p, "B", "other", 2L))
    }
}
