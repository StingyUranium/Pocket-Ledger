package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test

class CategoriesTest {
    private fun entry(type: String, category: String, deleted: Boolean = false) =
        Entry("$type-$category", "cash", type = type, category = category, amount = 100, date = 1, deleted = deleted)

    @Test fun incomeAndSpendingHaveDifferentSuggestions() {
        assertTrue("Salary" in categoriesForType("INCOME"))
        assertFalse("Salary" in categoriesForType("EXPENSE"))
        assertTrue("Groceries" in categoriesForType("EXPENSE"))
        assertFalse("Groceries" in categoriesForType("INCOME"))
        assertEquals(listOf("Transfer"), categoriesForType("TRANSFER"))
    }

    @Test fun customAndLegacyCategoriesFollowTheirRecordedType() {
        val entries = listOf(entry("INCOME", "Tutoring"), entry("EXPENSE", "Salary"),
            entry("EXPENSE", "Repairs"), entry("EXPENSE", "Removed", deleted = true))
        assertTrue("Tutoring" in categoryOptions("INCOME", entries))
        assertFalse("Tutoring" in categoryOptions("EXPENSE", entries))
        assertTrue("Salary" in categoryOptions("EXPENSE", entries))
        assertFalse("Repairs" in categoryOptions("INCOME", entries))
        assertFalse("Removed" in categoryOptions("ALL", entries))
    }

    @Test fun legacyRulesUseLatestMatchingCorrectionOrIncomeFallback() {
        val entries = listOf(entry("INCOME", "Custom").copy(payee = " Employer ", date = 10),
            entry("EXPENSE", "Custom").copy(payee = "Employer", date = 5))
        assertEquals("INCOME", legacyRuleType("EMPLOYER", "Custom", entries))
        assertEquals("INCOME", legacyRuleType("EMPLOYER", "Salary", emptyList()))
        assertEquals("EXPENSE", legacyRuleType("SHOP", "Other", emptyList()))
    }
}
