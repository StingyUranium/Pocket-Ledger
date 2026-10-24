package `in`.thomas.pocketledger

import java.time.YearMonth

data class MonthlySummary(val income: Long, val spent: Long, val budgetLimit: Long, val budgetSpent: Long) {
    val balance: Long get() = income - spent
    val budgetRemaining: Long get() = budgetLimit - budgetSpent
}

data class OverviewReport(
    val monthly: List<Entry>,
    val summary: MonthlySummary,
    val spendingByCategory: List<Pair<String, Long>>
)

/** Half-open epoch-millisecond range [start, endExclusive) covering a calendar month in Asia/Kolkata. */
internal class MonthBounds(val start: Long, val endExclusive: Long) {
    operator fun contains(date: Long): Boolean = date >= start && date < endExclusive
}

internal fun monthBounds(month: YearMonth) = MonthBounds(
    month.atDay(1).atStartOfDay(india).toInstant().toEpochMilli(),
    month.plusMonths(1).atDay(1).atStartOfDay(india).toInstant().toEpochMilli()
)

fun calculateOverviewReport(entries: List<Entry>, budgets: List<Budget>, month: YearMonth): OverviewReport {
    val bounds = monthBounds(month)
    val monthly = entries.filter { !it.deleted && it.date in bounds }
    return OverviewReport(monthly, summarize(monthly, budgets),
        monthly.filter { it.type == "EXPENSE" }.groupBy { it.category }
            .mapValues { (_, group) -> group.sumOf { it.amount } }.toList().sortedByDescending { it.second })
}

/** Income, spending and recurring-limit totals for [month]; transfers and deleted entries never count. */
fun monthlySummary(entries: List<Entry>, budgets: List<Budget>, month: YearMonth): MonthlySummary =
    monthlySummaries(entries, budgets, listOf(month)).getValue(month)

/**
 * Summaries for several months from a single pass over [entries]. Months are matched with
 * epoch-millisecond bounds instead of converting every entry to a zoned date.
 */
fun monthlySummaries(entries: List<Entry>, budgets: List<Budget>, months: List<YearMonth>): Map<YearMonth, MonthlySummary> {
    val limits = budgets.filter { it.amount > 0 }
    val categories = limits.mapTo(HashSet()) { it.category }
    val limit = limits.sumOf { it.amount }
    val distinct = months.distinct()
    val bounds = distinct.map { monthBounds(it) }
    val income = LongArray(distinct.size)
    val spent = LongArray(distinct.size)
    val budgetSpent = LongArray(distinct.size)
    for (e in entries) {
        if (e.deleted) continue
        val isIncome = e.type == "INCOME"
        val isExpense = e.type == "EXPENSE"
        if (!isIncome && !isExpense) continue
        for (i in bounds.indices) {
            if (e.date !in bounds[i]) continue
            if (isIncome) income[i] += e.amount
            else {
                spent[i] += e.amount
                if (e.category in categories) budgetSpent[i] += e.amount
            }
            break
        }
    }
    return distinct.indices.associate { distinct[it] to MonthlySummary(income[it], spent[it], limit, budgetSpent[it]) }
}

private fun summarize(monthly: List<Entry>, budgets: List<Budget>): MonthlySummary {
    val limits = budgets.filter { it.amount > 0 }
    val categories = limits.mapTo(HashSet()) { it.category }
    var income = 0L; var spent = 0L; var budgetSpent = 0L
    for (e in monthly) {
        if (e.type == "INCOME") income += e.amount
        else if (e.type == "EXPENSE") { spent += e.amount; if (e.category in categories) budgetSpent += e.amount }
    }
    return MonthlySummary(income, spent, limits.sumOf { it.amount }, budgetSpent)
}

/** Expense totals per day of month (Asia/Kolkata) for the chart; transfers and deleted entries are excluded. */
fun dailySpending(entries: List<Entry>, month: YearMonth): Map<Int, Long> {
    val bounds = monthBounds(month)
    val totals = HashMap<Int, Long>()
    for (e in entries) {
        if (e.deleted || e.type != "EXPENSE" || e.date !in bounds) continue
        val day = java.time.Instant.ofEpochMilli(e.date).atZone(india).dayOfMonth
        totals[day] = (totals[day] ?: 0L) + e.amount
    }
    return totals
}

/** Whole-paise targets; the final bucket keeps the rounding remainder. */
data class IncomeAllocation(val needs: Long, val wants: Long, val savings: Long)

fun incomeAllocation(income: Long): IncomeAllocation {
    val base = income.coerceAtLeast(0)
    val needs = base / 2
    val wants = (base / 10) * 3 + (base % 10) * 3 / 10
    return IncomeAllocation(needs, wants, base - needs - wants)
}
