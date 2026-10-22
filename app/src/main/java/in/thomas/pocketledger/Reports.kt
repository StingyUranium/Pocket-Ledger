package `in`.thomas.pocketledger

import java.time.YearMonth

data class MonthlySummary(val income: Long, val spent: Long, val budgetLimit: Long, val budgetSpent: Long) {
    val balance: Long get() = income - spent
    val budgetRemaining: Long get() = budgetLimit - budgetSpent
}

fun monthlySummary(entries: List<Entry>, budgets: List<Budget>, month: YearMonth): MonthlySummary {
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
