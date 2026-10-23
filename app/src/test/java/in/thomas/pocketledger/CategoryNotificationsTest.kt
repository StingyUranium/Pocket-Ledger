package `in`.thomas.pocketledger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryNotificationsTest {
    private val parsed = Entry("entry", "account", type = "EXPENSE", amount = 12500, date = 1, smsKey = "sms")

    @Test fun uncategorizedSmsIncomeAndExpensesNeedReminder() {
        assertTrue(needsCategoryNotification(parsed))
        assertTrue(needsCategoryNotification(parsed.copy(type = "INCOME")))
    }

    @Test fun categorizedManualDeletedAndTransferEntriesDoNotNeedReminder() {
        assertFalse(needsCategoryNotification(parsed.copy(category = "Food")))
        assertFalse(needsCategoryNotification(parsed.copy(smsKey = null)))
        assertFalse(needsCategoryNotification(parsed.copy(deleted = true)))
        assertFalse(needsCategoryNotification(parsed.copy(type = "TRANSFER")))
    }
}
