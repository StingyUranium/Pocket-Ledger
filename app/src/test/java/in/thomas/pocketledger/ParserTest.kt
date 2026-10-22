package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ParserTest {
    private val sbmCredit = "Your account XX6317 is credited with INR 483.00 on 10-10-2026. Info:UPI/P2P/628368490342/BETRYSAN/.The Curr bal is  22498.83. SBM BANK (INDIA)."
    private val sbmDebit = "Your account XXXXXXXXXX6317 is debited with INR 40. on 2026-10-21 16:03:01 infor :UPI/P2M/666027093219/KOCHI MET/UTIB00001/UPI. The curr bal is INR 15263.74. If not initiated at your end, click on the given link to raise a dispute https://retail.sbm.bank.in/mfmbs/ib/onlineDispute.jsp?number=dummy SBM BANK (INDIA)."
    private val axisCredit = "INR 17099.94 credited\nA/c no. XX0424\n30-08-26, 14:32:50 IST\nUPI/P2A/624273404671/THOMAS JO/STCB/Sala - Axis Bank"
    private val axisDebit = "INR 5000.00 debited\nA/c no. XX0424\n14-10-26, 11:03:22\nUPI/P2M/628728128503/ZERODHA BROKING LIM\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank"
    @Test fun sbmCreditFieldsAndDatePrecision() {
        val p = SmsParser.parse(sbmCredit)!!
        assertTrue(p.complete); assertEquals("SBM", p.bank); assertEquals("6317", p.suffix); assertEquals(48300L, p.amount)
        assertEquals("INCOME", p.direction); assertEquals("BETRYSAN", p.payee); assertEquals("628368490342", p.reference); assertEquals(2249883L, p.balance)
        assertTrue(p.dateOnly); assertEquals(LocalDate.of(2026, 10, 10).atStartOfDay(india).toInstant().toEpochMilli(), p.date)
    }
    @Test fun sbmDebitSeparatesBalanceAndSecurityLink() {
        val p = SmsParser.parse(sbmDebit)!!
        assertTrue(p.complete); assertEquals(4000L, p.amount); assertEquals(1526374L, p.balance); assertEquals("EXPENSE", p.direction); assertEquals("KOCHI MET", p.payee)
        assertFalse(p.dateOnly); assertEquals(LocalDateTime.of(2026, 10, 21, 16, 3, 1).atZone(india).toInstant().toEpochMilli(), p.date)
    }
    @Test fun axisCreditUsesIndianDateAndTwoDigitYear() {
        val p = SmsParser.parse(axisCredit)!!
        assertTrue(p.complete); assertEquals("AXIS", p.bank); assertEquals("0424", p.suffix); assertEquals(1709994L, p.amount); assertNull(p.balance)
        assertEquals("THOMAS JO", p.payee); assertEquals("624273404671", p.reference)
        assertEquals(LocalDateTime.of(2026, 8, 30, 14, 32, 50).atZone(india).toInstant().toEpochMilli(), p.date)
    }
    @Test fun axisDebitExcludesBlockInstructions() {
        val p = SmsParser.parse(axisDebit)!!
        assertTrue(p.complete); assertEquals(500000L, p.amount); assertEquals("ZERODHA BROKING LIM", p.payee); assertEquals("EXPENSE", p.direction)
    }
    @Test fun whitespaceAndThousandsSeparators() { assertEquals(148300L, SmsParser.parse(sbmCredit.replace("483.00", "1,483.00").replace(" ", "  \n"))!!.amount) }
    @Test fun malformedDateRequiresReview() { val p = SmsParser.parse(sbmCredit.replace("10-10-2026", "31-02-2026"))!!; assertFalse(p.complete); assertNull(p.date) }
    @Test fun missingReferenceRequiresReview() { assertFalse(SmsParser.parse(sbmCredit.replace("628368490342", ""))!!.complete) }
    @Test fun incompleteBankFormatStillReviewable() { assertFalse(SmsParser.parse("Your account XX6317 debited. Transaction pending review. SBM BANK (INDIA).")!!.complete) }
    @Test fun ignoresOtpAndFailedAndCreditCard() {
        assertNull(SmsParser.parse("Your OTP is 123456. Axis Bank")); assertNull(SmsParser.parse(axisDebit + " credit card")); assertNull(SmsParser.parse(axisDebit + " failed")); assertNull(SmsParser.parse(axisDebit + " declined"))
    }
    @Test fun ignoresUnrelatedMessages() {
        assertNull(SmsParser.parse("Hello there")); assertNull(SmsParser.parse("Special offers from Axis Bank"))
        assertNull(SmsParser.parse("Your account is eligible for an offer. Axis Bank"))
        assertNull(SmsParser.parse("INR 500 cashback offer. Your account is pre-approved. Axis Bank"))
    }
    @Test fun sameReferenceDeduplicatesAcrossDeliveryTimes() {
        val p = SmsParser.parse(axisDebit)!!
        assertEquals(SmsParser.key(p, "AX-AXIS", axisDebit, 1), SmsParser.key(p, "VM-AXIS", axisDebit.replace("\n", " "), 999))
    }
    @Test fun oppositeDirectionsAreSeparateTransferLegs() {
        val p = SmsParser.parse(axisDebit)!!
        assertNotEquals(SmsParser.key(p, "AX", axisDebit, 1), SmsParser.key(p.copy(direction = "INCOME"), "AX", axisDebit, 1))
    }
    @Test fun zeroAmountRequiresReview() { assertFalse(SmsParser.parse(axisDebit.replace("5000.00", "0.00"))!!.complete) }
    @Test fun exactMoneyConversion() { assertEquals(40_00L, paise("40.")); assertEquals(1L, paise("0.01")); assertEquals(-120050L, paise("-1,200.50")) }
    @Test(expected = ArithmeticException::class) fun rejectsFractionalPaise() { paise("1.001") }
    @Test(expected = ArithmeticException::class) fun rejectsMoneyOverflow() { paise("999999999999999999999999") }
}
