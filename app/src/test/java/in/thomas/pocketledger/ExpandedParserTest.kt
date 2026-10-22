package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ExpandedParserTest {
    private fun sbm(info: String, credit: Boolean = false, date: String = "2026-10-20 12:30:45", amount: String = "123.45."): String =
        "Your account XXXXXXXXXX1111 is ${if (credit) "credited" else "debited"} with INR $amount on $date infor :$info. The curr bal is INR 9000.50. SBM BANK (INDIA)."

    @Test fun sbmNeftParsesReferenceAndPayee() {
        val p = SmsParser.parse(sbm("NEFT/FBPT123456789012/SAMPLE REMITTER", true, "20-10-2026", "123.45"))!!
        assertTrue(p.complete)
        assertEquals("NEFT:FBPT123456789012", p.reference)
        assertEquals("SAMPLE REMITTER", p.payee)
        assertEquals(12345L, p.amount)
        assertTrue(p.dateOnly)
        assertEquals(900050L, p.balance)
    }

    @Test fun sbmImpsSupportsBankRoutingSegment() {
        val p = SmsParser.parse(sbm("IMPS/P2A/BRC/123456789012/SAMPLE PERSON/SAMPLE BANK"))!!
        assertTrue(p.complete)
        assertEquals("IMPS:123456789012", p.reference)
        assertEquals("SAMPLE PERSON", p.payee)
    }

    @Test fun sbmInternalTransferHasStableReferenceAndNeutralDescription() {
        val p = SmsParser.parse(sbm("IFT/ABCDEF1234567890ABCDEF1234567890"))!!
        assertTrue(p.complete)
        assertEquals("EXPENSE", p.direction)
        assertEquals("IFT:ABCDEF1234567890ABCDEF1234567890", p.reference)
        assertEquals("IFT transfer", p.payee)
    }

    @Test fun sbmAchDebitExtractsMandateCounterparty() {
        val p = SmsParser.parse(sbm("ACH_DR/SAMPLE MUTUAL FUND/TPZMUT123456"))!!
        assertTrue(p.complete)
        assertEquals("ACH_DR:TPZMUT123456", p.reference)
        assertEquals("SAMPLE MUTUAL FUND", p.payee)
    }

    @Test fun sbmAchCreditExtractsReferenceBeforeCounterparty() {
        val p = SmsParser.parse(sbm("ACH_CR/TTLFINDIV12345678/SAMPLE COMPANY", true))!!
        assertTrue(p.complete)
        assertEquals("ACH_CR:TTLFINDIV12345678", p.reference)
        assertEquals("SAMPLE COMPANY", p.payee)
    }

    @Test fun achReferenceWithoutTransactionIdentifierNeedsReview() {
        val p = SmsParser.parse(sbm("ACH_CR/SAMPLE/SAMPLE COMPANY", true))!!
        assertFalse(p.complete)
        assertNull(p.reference)
    }

    @Test fun reusedAchReferenceOnDifferentDatesDoesNotDeduplicate() {
        val first = sbm("ACH_DR/SAMPLE FUND/TPZMUT123456")
        val next = first.replace("2026-10-20", "2026-11-20")
        assertNotEquals(SmsParser.key(SmsParser.parse(first)!!, "VM-SAMPLE", first, 1), SmsParser.key(SmsParser.parse(next)!!, "VM-SAMPLE", next, 2))
    }

    @Test fun interestUsesPostingDateRatherThanInterestPeriod() {
        val p = SmsParser.parse(sbm("12345678901234:Int.Pd:01-09-26", true, "20-10-2026", "2.50"))!!
        assertFalse(p.complete)
        assertEquals("Savings interest", p.payee)
        assertNull(p.reference)
        assertEquals(250L, p.amount)
        assertEquals(LocalDate.of(2026, 10, 20).atStartOfDay(india).toInstant().toEpochMilli(), p.date)
    }

    @Test fun axisNeftSupportsAtTimestampAndInfoPrefix() {
        val p = SmsParser.parse("INR 1234.50 credited to A/c no. XX2222 on 20-10-26 at 12:30:45 IST. Info - NEFT/FBPT123456789012/SAMPLE. Chk Bal https://example.invalid - Axis Bank")!!
        assertTrue(p.complete)
        assertEquals("2222", p.suffix)
        assertEquals("NEFT:FBPT123456789012", p.reference)
        assertEquals("SAMPLE", p.payee)
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 30, 45).atZone(india).toInstant().toEpochMilli(), p.date)
    }

    @Test fun axisRtgsDoesNotTreatBalanceLinkAsReference() {
        val p = SmsParser.parse("INR 4567.89 credited to Axis Bank A/c XX2222 on 20-10-26 at 12:30:45 IST. Ref: RTGS/ESMFR1234567890123456.View bal: https://example.invalid")!!
        assertTrue(p.complete)
        assertEquals("RTGS:ESMFR1234567890123456", p.reference)
        assertEquals("RTGS transfer", p.payee)
    }

    @Test fun axisDebitPrefixAndImpsReference() {
        val p = SmsParser.parse("Debit INR 123.45 Axis Bank A/c XX2222 20-10-26 12:30:45 IMPS/P2A/123456789012/Sample Person WhatsApp BAL to 9999999999 Not You? SMS BLOCK CustID to 9999999999")!!
        assertTrue(p.complete)
        assertEquals("EXPENSE", p.direction)
        assertEquals("IMPS:123456789012", p.reference)
        assertEquals("Sample Person", p.payee)
    }

    @Test fun axisMerchantDebitSupportsSixVisibleDigitsAndSeparateBalance() {
        val p = SmsParser.parse("INR 123.45 debited from A/c no. XX992222 on SAMPLE SHOP 20-10-2026 12:30:45 IST. Avl bal: INR 9000.50. Not you? SMS BLOCK XX0000 to +9999999999 - Axis Bank")!!
        assertFalse(p.complete)
        assertEquals("2222", p.suffix)
        assertEquals("SAMPLE SHOP", p.payee)
        assertEquals(12345L, p.amount)
        assertEquals(900050L, p.balance)
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 30, 45).atZone(india).toInstant().toEpochMilli(), p.date)
    }

    @Test fun completedAccountlessMandateIsReviewable() {
        val p = SmsParser.parse("Your A/c has been debited towards Sample Merchant for INR 123.45 on 20-10-26. examplemandate@okaxis - Axis Bank")!!
        assertFalse(p.complete)
        assertNull(p.suffix)
        assertNull(p.reference)
        assertEquals("Sample Merchant", p.payee)
        assertEquals(12345L, p.amount)
        assertTrue(p.dateOnly)
    }

    @Test fun futureMandateAndMoneyRequestAreNotTransactions() {
        assertNull(SmsParser.parse("For the upcoming mandate set for 20-10-26, INR 123.45 will be debited from your A/c towards Sample Merchant - Axis Bank"))
        assertNull(SmsParser.parse("Sample Merchant has requested money from you on Google Pay. On approving the request, INR 123.45 will be debited from your A/c - Axis Bank"))
        assertNull(SmsParser.parse("Your UPI mandate has been successfully created towards Sample Merchant for INR 123.45. Funds blocked from A/c no. XX2222 - Axis Bank"))
    }

    @Test fun upiLiteTopUpIsParsedButNeedsReview() {
        val p = SmsParser.parse("UPI LITE enabled on UPI App for A/c no. XX2222 with successful top-up of INR 123.45 on 20-10-26 12:30:45 IST. Ref no. 123456789012345678 - Axis Bank")!!
        assertFalse(p.complete)
        assertEquals("EXPENSE", p.direction)
        assertEquals("TOPUP:123456789012345678", p.reference)
        assertEquals("UPI Lite top-up", p.payee)
    }

    @Test fun invalidFractionIsNotTruncatedToTwoDigits() {
        assertNull(SmsParser.parse(sbm("IFT/ABCDEF1234567890", amount = "123.456"))!!.amount)
    }

    @Test fun repeatedMessageBodyKeyRemainsCompatibleWithPreviousParser() {
        val text = sbm("IFT/ABCDEF1234567890")
        assertEquals(digest("VM-SAMPLE|$text|123"), SmsParser.bodyKey("vm-sample", text, 123))
    }

    @Test fun upiReferenceKeyRemainsCompatibleWithPreviousParser() {
        val text = sbm("UPI/P2M/123456789012/SAMPLE SHOP/UTIB00001/UPI")
        val p = SmsParser.parse(text)!!
        assertEquals(digest("SBM|1111|123456789012|EXPENSE"), SmsParser.key(p, "VM-SAMPLE", text, 123))
    }

    @Test fun neftAndImpsIdenticalNumericReferencesAreNotConfused() {
        val neft = sbm("NEFT/123456789012/SAMPLE")
        val imps = sbm("IMPS/P2A/123456789012/SAMPLE")
        assertNotEquals(SmsParser.key(SmsParser.parse(neft)!!, "VM-SAMPLE", neft, 1), SmsParser.key(SmsParser.parse(imps)!!, "VM-SAMPLE", imps, 1))
    }

    @Test fun bankServiceNoticesDoNotBecomeTransactions() {
        assertNull(SmsParser.parse("Your request to modify limit/manage usage facility on Debit Card no. XX2222 is processed - Axis Bank"))
        assertNull(SmsParser.parse("Your SBM Niyo Global Debit card ending with XX1111 has been blocked. SBM Bank India"))
    }
}
