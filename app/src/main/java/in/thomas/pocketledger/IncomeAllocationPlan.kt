package `in`.thomas.pocketledger

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable fun IncomeAllocationPlan(entries: List<Entry>, month: YearMonth) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("budget_plan", Context.MODE_PRIVATE) }
    var income by remember { mutableLongStateOf(preferences.getLong("monthly_income", 0L)) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val allocation = incomeAllocation(income)
    val summary = monthlySummary(entries, emptyList(), month)
    val colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFFAF52DE), MaterialTheme.colorScheme.tertiary)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CardBlock {
            Text("50 / 30 / 20", style = MaterialTheme.typography.headlineMedium)
            Text("Give your income a purpose", style = MaterialTheme.typography.titleMedium)
            Text("Monthly income", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(income), style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                listOf(.5f, .3f, .2f).forEachIndexed { index, share ->
                    Box(Modifier.weight(share).fillMaxHeight().background(colors[index]))
                }
            }
            Text("Your entered income carries forward until you change it. These amounts are planning targets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text(if (income > 0) "Edit monthly income" else "Set monthly income") }
        }
        listOf(
            Triple("Needs", "50%", "Essentials such as rent, groceries, bills and health"),
            Triple("Wants", "30%", "Extras such as dining out, shopping and entertainment"),
            Triple("Savings", "20%", "Set aside for savings, investments and future goals")
        ).forEachIndexed { index, (name, percent, description) ->
            CardBlock {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    StatusPill(percent, colors[index])
                }
                Text(money(listOf(allocation.needs, allocation.wants, allocation.savings)[index]), style = MaterialTheme.typography.headlineSmall)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CardBlock {
            Text("Selected month", style = MaterialTheme.typography.titleMedium)
            Text("Recorded income: "+money(summary.income))
            Text("Expenses: "+money(summary.spent))
            Text("Income remaining: "+money(summary.balance), color = if (summary.balance < 0) rust else MaterialTheme.colorScheme.tertiary)
            Text("Income remaining is recorded income minus expenses, not confirmed savings. Transfers are excluded. Category budgets track individual spending limits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) {
        var amount by rememberSaveable { mutableStateOf(if (income > 0) amountText(income) else "") }
        var error by remember { mutableStateOf<String?>(null) }
        LedgerSheet("Monthly income", { editing = false }, "Save", {
            try {
                val value = paise(amount)
                require(value > 0) { "Enter an income greater than zero" }
                preferences.edit().putLong("monthly_income", value).apply()
                income = value
                editing = false
            } catch (exception: Exception) { error = exception.message ?: "Enter a valid amount" }
        }) {
            Text("Split your take-home income into 50% needs, 30% wants and 20% savings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(amount, { amount = it }, "Monthly income (INR)", true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
