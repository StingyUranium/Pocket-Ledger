package `in`.thomas.pocketledger

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

@Composable fun IncomeAllocationPlan(entries: List<Entry>, month: YearMonth) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("budget_plan", Context.MODE_PRIVATE) }
    // The first read of a preferences file can touch the disk, so it happens off the main thread.
    val stored by produceState<Long?>(null, preferences) { value = withContext(Dispatchers.IO) { preferences.getLong("monthly_income", 0L) } }
    var saved by remember { mutableStateOf<Long?>(null) }
    val loaded = (saved ?: stored) != null
    val income = saved ?: stored ?: 0L
    var editing by rememberSaveable { mutableStateOf(false) }
    val allocation = remember(income) { incomeAllocation(income) }
    val summary by backgroundCalculation(entries, month, cacheKey = "income-plan-summary") {
        monthlySummary(entries, emptyList(), month)
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = listOf(MaterialTheme.colorScheme.primary, categoryColor(1, dark), MaterialTheme.colorScheme.tertiary)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GroupedBlock {
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
            Button(onClick = { editing = true }, enabled = loaded, modifier = Modifier.fillMaxWidth()) { Text(if (income > 0) "Edit monthly income" else "Set monthly income") }
        }
        GroupedSection("Targets") {
            listOf(
                Triple("Needs", "50%", "Essentials such as rent, groceries, bills and health"),
                Triple("Wants", "30%", "Extras such as dining out, shopping and entertainment"),
                Triple("Savings", "20%", "Set aside for savings, investments and future goals")
            ).forEachIndexed { index, (name, percent, description) ->
                if (index > 0) GroupedDivider()
                GroupedRow(name, subtitle = description, value = money(listOf(allocation.needs, allocation.wants, allocation.savings)[index]),
                    trailing = { StatusPill(percent, colors[index]) })
            }
        }
        GroupedBlock("Selected month") {
            val ready = summary
            if (ready == null) Text("Loading your ledger", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) else {
                Text("Recorded income: " + money(ready.income))
                Text("Expenses: " + money(ready.spent))
                Text("Income remaining: " + money(ready.balance), color = if (ready.balance < 0) danger else MaterialTheme.colorScheme.tertiary)
            }
            Text("Income remaining is recorded income minus expenses, not confirmed savings. Transfers are excluded. Category budgets track individual spending limits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) {
        var amount by rememberSaveable { mutableStateOf(if (income > 0) amountText(income) else "") }
        var error by remember { mutableStateOf<String?>(null) }
        LedgerSheet("Monthly income", { editing = false }, "Save", {
            try {
                val value = parseAmountInput(amount) ?: throw IllegalArgumentException("Enter an amount like 50000 or 50000.50")
                require(value > 0) { "Enter an income greater than zero" }
                preferences.edit().putLong("monthly_income", value).apply()
                saved = value
                editing = false
            } catch (exception: IllegalArgumentException) { error = exception.message?.takeUnless { exception is NumberFormatException } ?: "Enter a valid amount" }
        }) {
            Text("Split your take-home income into 50% needs, 30% wants and 20% savings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(amount, { amount = it }, "Monthly income (INR)", true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
