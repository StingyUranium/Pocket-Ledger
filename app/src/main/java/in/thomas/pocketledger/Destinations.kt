package `in`.thomas.pocketledger

internal enum class Destination(val legacy: Int, val title: String, val icon: String) {
    Overview(0, "Overview", "home"), Activity(1, "Activity", "entries"),
    Accounts(2, "Accounts", "accounts"), Budgets(3, "Budgets", "budgets"),
    Inbox(4, "Inbox", "sms"), Insights(5, "Insights", "chart"),
    Management(6, "Management", "more"), IncomePlan(7, "Income plan", "budgets");
    companion object {
        fun restore(value: String): Destination = entries.firstOrNull { it.name == value || it.legacy.toString() == value } ?: Overview
        val primary = listOf(Overview, Activity, Budgets, Inbox)
    }
}
