package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test

class SmsHardeningTest {
    @Test fun veryLongBodyIsRejectedQuicklyWithoutParsing() {
        val body = "Your account XX6317 is debited with INR 40. on 2026-10-21 UPI/P2M/628368490342/SHOP/ SBM BANK (INDIA). " + "debited from a/c no. ".repeat(50_000)
        val start = System.nanoTime()
        SmsParser.parseSelected(body)
        SmsParser.parse(body)
        assertTrue("parsing took too long", (System.nanoTime() - start) / 1_000_000 < 2_000)
    }

    @Test fun pathologicalSpacingDoesNotHang() {
        val body = "debited from a/c no. " + " on ".repeat(200_000) + "axis bank"
        val start = System.nanoTime()
        SmsParser.parseSelected(body)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
    }

    @Test fun normalMessagesStillParseAfterCap() {
        val body = "Your account XX6317 is credited with INR 483.00 on 10-10-2026. Info:UPI/P2P/628368490342/SAMPLE/.The Curr bal is  22498.83. SBM BANK (INDIA)."
        assertTrue(body.length < SmsParser.MaxBodyChars)
        assertTrue(SmsParser.parseSelected(body)!!.complete)
    }

    @Test fun identityKeyStillWorksForOversizedBodies() {
        val body = "x".repeat(50_000)
        assertEquals(SmsParser.stableBodyKey("A", body), SmsParser.stableBodyKey(" a ", body))
    }
}
