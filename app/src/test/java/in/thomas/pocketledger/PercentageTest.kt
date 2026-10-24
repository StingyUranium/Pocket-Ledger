package `in`.thomas.pocketledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PercentageTest {
    @Test fun categoryShareUsesTotalSpendingAndKeepsTinyAmountsVisibleInTheLabel() {
        assertEquals("60%", spendingShareText(600000, 1000000, Locale.US))
        assertEquals("30%", spendingShareText(300000, 1000000, Locale.US))
        assertEquals("<0.1%", spendingShareText(1, 1000000, Locale.US))
        assertEquals("0%", spendingShareText(0, 1000000, Locale.US))
        assertEquals("0%", spendingShareText(1, 0, Locale.US))
        assertEquals("100%", spendingShareText(100, 100, Locale.US))
    }
}
