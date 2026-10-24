package `in`.thomas.pocketledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Proves the epoch-bound single-pass summaries match the previous per-entry ZonedDateTime implementation. */
class MonthlySummaryBucketTest {
    private fun at(date: LocalDateTime) = date.atZone(india).toInstant().toEpochMilli()
    private fun entry(id: String, amount: Long, type: String, date: Long, category: String = "Food", deleted: Boolean = false) =
        Entry(id, "account", type = type, amount = amount, date = date, category = category, deleted = deleted)

    /** The implementation this change replaced, kept as the reference. */
    private fun reference(entries: List<Entry>, budgets: List<Budget>, month: YearMonth): MonthlySummary {
        val monthly = entries.filter { !it.deleted && inMonth(it, month) }
        val limits = budgets.filter { it.amount > 0 }
        val categories = limits.map { it.category }.toSet()
        return MonthlySummary(
            income = monthly.filter { it.type == "INCOME" }.sumOf { it.amount },
            spent = monthly.filter { it.type == "EXPENSE" }.sumOf { it.amount },
            budgetLimit = limits.sumOf { it.amount },
            budgetSpent = monthly.filter { it.type == "EXPENSE" && it.category in categories }.sumOf { it.amount }
        )
    }

    private val october = YearMonth.of(2026, 10)
    private val fixture: List<Entry> = listOf(
        // Month boundaries at IST midnight, including the last and first millisecond either side.
        entry("oct-first", 100, "EXPENSE", at(LocalDateTime.of(2026, 10, 1, 0, 0))),
        entry("sep-last-ms", 200, "EXPENSE", at(LocalDateTime.of(2026, 10, 1, 0, 0)) - 1),
        entry("oct-last", 300, "EXPENSE", at(LocalDateTime.of(2026, 10, 31, 23, 59, 59, 999_000_000))),
        entry("nov-first", 400, "EXPENSE", at(LocalDateTime.of(2026, 11, 1, 0, 0))),
        entry("oct-income", 5_000, "INCOME", at(LocalDateTime.of(2026, 10, 15, 12, 0))),
        entry("nov-income", 6_000, "INCOME", at(LocalDateTime.of(2026, 11, 1, 0, 0))),
        // 18:30 UTC on 30 Sep is already 1 Oct in Asia/Kolkata.
        entry("utc-boundary", 700, "EXPENSE", java.time.Instant.parse("2026-09-30T18:30:00Z").toEpochMilli(), category = "Rent"),
        entry("utc-before", 800, "EXPENSE", java.time.Instant.parse("2026-09-30T18:29:59Z").toEpochMilli(), category = "Rent"),
        // Excluded kinds.
        entry("transfer", 900, "TRANSFER", at(LocalDateTime.of(2026, 10, 10, 9, 0))),
        entry("deleted-expense", 1_000, "EXPENSE", at(LocalDateTime.of(2026, 10, 10, 9, 0)), deleted = true),
        entry("deleted-income", 1_100, "INCOME", at(LocalDateTime.of(2026, 10, 10, 9, 0)), deleted = true),
        entry("rent", 1_200, "EXPENSE", at(LocalDateTime.of(2026, 10, 5, 9, 0)), category = "Rent"),
        entry("rent-nov", 1_300, "EXPENSE", at(LocalDateTime.of(2026, 11, 5, 9, 0)), category = "Rent"),
        entry("dec", 1_400, "EXPENSE", at(LocalDateTime.of(2026, 12, 31, 23, 59))),
        entry("jan-income", 1_500, "INCOME", at(LocalDateTime.of(2027, 1, 1, 0, 0)))
    )
    private val budgets = listOf(Budget("Rent", 2_000), Budget("Food", 0), Budget("Travel", 500))

    @Test fun singleMonthMatchesReferenceAcrossBoundaries() {
        for (month in listOf(YearMonth.of(2026, 9), october, YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2020, 1))) {
            assertEquals("month $month", reference(fixture, budgets, month), monthlySummary(fixture, budgets, month))
        }
    }

    @Test fun batchedMonthsMatchReferenceIncludingDuplicatesAndOrder() {
        val months = (-5..0).map { october.plusMonths(it.toLong()) } + october
        val batched = monthlySummaries(fixture, budgets, months)
        assertEquals(months.distinct().toSet(), batched.keys)
        for (month in months) assertEquals("month $month", reference(fixture, budgets, month), batched.getValue(month))
    }

    @Test fun batchedMonthsWithoutBudgetsMatchReference() {
        val months = (-5..0).map { october.plusMonths(it.toLong()) }
        val batched = monthlySummaries(fixture, emptyList(), months)
        for (month in months) assertEquals(reference(fixture, emptyList(), month), batched.getValue(month))
    }

    @Test fun widgetComparisonCrossesYearBoundaryAndUsesWholePreviousMonth() {
        val january = YearMonth.of(2027, 1)
        val previous = january.minusMonths(1)
        val summaries = monthlySummaries(fixture, emptyList(), listOf(january, previous))
        assertEquals(-1_400L, WidgetLogic.comparison(summaries.getValue(january).spent,
            summaries.getValue(previous).spent, income = false).delta)
        assertEquals(1_500L, WidgetLogic.comparison(summaries.getValue(january).income,
            summaries.getValue(previous).income, income = true).delta)
    }

    @Test fun octoberTotalsExcludeTransfersDeletedAndNeighbouringMonths() {
        val summary = monthlySummary(fixture, budgets, october)
        assertEquals(5_000L, summary.income)
        // oct-first 100 + oct-last 300 + utc-boundary 700 + rent 1,200
        assertEquals(2_300L, summary.spent)
        assertEquals(1_900L, summary.budgetSpent)
        assertEquals(2_500L, summary.budgetLimit)
    }

    @Test fun overviewReportAgreesWithSummary() {
        val report = calculateOverviewReport(fixture, budgets, october)
        assertEquals(reference(fixture, budgets, october), report.summary)
        assertEquals(report.summary.spent, report.spendingByCategory.sumOf { it.second })
        assertEquals(listOf("Rent" to 1_900L, "Food" to 400L), report.spendingByCategory)
    }

    @Test fun dailySpendingUsesIndiaDaysAndTheSameExclusions() {
        val daily = dailySpending(fixture, october)
        assertEquals(800L, daily[1]) // oct-first 100 + utc-boundary 700 are both 1 Oct IST; sep-last-ms is a Sep day
        assertEquals(300L, daily[31])
        assertEquals(1_200L, daily[5])
        assertEquals(null, daily[10])
        assertEquals(reference(fixture, emptyList(), october).spent, daily.values.sum())
    }

    @Test fun leapDayBelongsToFebruary() {
        val leap = entry("leap", 50, "EXPENSE", at(LocalDate.of(2028, 2, 29).atTime(23, 59)))
        assertEquals(50L, monthlySummary(listOf(leap), emptyList(), YearMonth.of(2028, 2)).spent)
        assertEquals(0L, monthlySummary(listOf(leap), emptyList(), YearMonth.of(2028, 3)).spent)
    }
}
