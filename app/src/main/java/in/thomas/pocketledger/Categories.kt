package `in`.thomas.pocketledger

val expenseCategories = listOf(
    "Uncategorized", "Food", "Dining out", "Groceries", "Transport", "Uber/Bus", "Fuel",
    "Rent", "Shopping", "Bills", "Utilities", "Mobile & internet", "Health", "Medicines",
    "Insurance", "Education", "Entertainment", "Subscriptions", "Travel", "Personal care",
    "Home & repairs", "Family", "Gifts", "Donations", "Pets", "Loan payments", "Fees & charges",
    "Taxes", "Investment", "Other"
)

val incomeCategories = listOf(
    "Uncategorized", "Salary", "Bonus", "Freelance", "Business income", "Interest", "Dividends",
    "Investment returns", "Rental income", "Pension", "Refunds", "Reimbursements", "Gifts received",
    "Cashback", "Other income"
)

fun categoriesForType(type: String): List<String> = when (type) {
    "EXPENSE" -> expenseCategories
    "INCOME" -> incomeCategories
    "TRANSFER" -> listOf("Transfer")
    else -> (expenseCategories + incomeCategories).distinct()
}

/** Historical/custom categories remain discoverable only under their recorded transaction type. */
fun categoryOptions(type: String, entries: List<Entry>): List<String> =
    (categoriesForType(type) + entries.asSequence()
        .filter { !it.deleted && (type == "ALL" || it.type == type) }
        .map { it.category }.filter { it.isNotBlank() }.toList()).distinct()

/** Old rules had no direction. Preserve the latest matching correction, falling back to spending. */
fun legacyRuleType(merchant: String, category: String, entries: List<Entry>): String =
    entries.filter { it.type in listOf("INCOME", "EXPENSE") &&
        it.payee.trim().equals(merchant.trim(), ignoreCase = true) && it.category == category }
        .maxByOrNull { it.date }?.type ?: if (category in incomeCategories && category !in expenseCategories) "INCOME" else "EXPENSE"
