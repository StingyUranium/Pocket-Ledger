package `in`.thomas.pocketledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportsTest {
    private fun entry(id: String, amount: Long, type: String = "EXPENSE", date: String = "2026-10-22", deleted: Boolean = false) =
        Entry(id, "account", type = type, amount = amount, date = LocalDate.parse(date).atStartOfDay(india).toInstant().toEpochMilli(), category = "Rent", deleted = deleted)

    @Test fun overspendingKeepsNegativeRemaining() {
        val result = monthlySummary(listOf(entry("rent", 800_000)), listOf(Budget("Rent", 700_000)), YearMonth.of(2026, 10))
        assertEquals(-100_000L, result.budgetRemaining)
    }

    @Test fun newMonthKeepsLimitsAndStartsTotalsAtZero() {
        val result = monthlySummary(listOf(entry("rent", 700_000)), listOf(Budget("Rent", 700_000)), YearMonth.of(2026, 11))
        assertEquals(0L, result.income)
        assertEquals(0L, result.spent)
        assertEquals(0L, result.balance)
        assertEquals(700_000L, result.budgetRemaining)
    }

    @Test fun summariesExcludeTransfersDeletedEntriesAndOtherMonths() {
        val result = monthlySummary(listOf(entry("salary", 2_500_000, "INCOME"), entry("rent", 700_000), entry("transfer", 100_000, "TRANSFER"), entry("deleted", 500_000, deleted = true), entry("previous", 500_000, date = "2026-09-30")), listOf(Budget("Rent", 700_000)), YearMonth.of(2026, 10))
        assertEquals(2_500_000L, result.income)
        assertEquals(700_000L, result.spent)
        assertEquals(1_800_000L, result.balance)
        assertEquals(0L, result.budgetRemaining)
    }
}
