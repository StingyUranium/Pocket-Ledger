package `in`.thomas.pocketledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM")

private data class GraphReport(
    val total: Long, val categories: List<Pair<String, Long>>, val daily: Map<Int, Long>,
    val history: List<Pair<YearMonth, MonthlySummary>>
)

@Composable fun GraphsScreen(entries: List<Entry>, month: YearMonth, changeMonth: (YearMonth) -> Unit, openTransactions: (String, String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val report by backgroundCalculation(entries, month, cacheKey = "graphs") {
        // Shared report functions keep these totals identical to the Overview and widgets.
        val overview = calculateOverviewReport(entries, emptyList(), month)
        val months = (-5..0).map { month.plusMonths(it.toLong()) }
        val summaries = monthlySummaries(entries, emptyList(), months)
        GraphReport(overview.summary.spent, overview.spendingByCategory, dailySpending(entries, month), months.map { it to summaries.getValue(it) })
    }
    val ready = report ?: run { LedgerSkeleton(); return }
    val total = ready.total
    val categories = ready.categories
    val daily = ready.daily
    val peak = remember(daily) { daily.maxByOrNull { it.value } }
    val history = ready.history
    val scale = remember(history) { history.maxOf { maxOf(it.second.income, it.second.spent) }.coerceAtLeast(1) }
    val incomeColor = MaterialTheme.colorScheme.tertiary
    val expenseColor = danger
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val largeText = LocalDensity.current.fontScale > 1.3f
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f

    FluidList(contentPadding = PaddingValues(LedgerSpace.margin, 10.dp, LedgerSpace.margin, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "category-chart", contentType = "chart") { GroupedBlock {
            SectionTitle("Where it goes")
            Text("Spending by category", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (total == 0L) {
                Empty("Your charts will fill in as you record expenses this month.", "No spending yet", "chart")
            } else {
                Box(Modifier.align(Alignment.CenterHorizontally).size(220.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Spending by category, total ${money(total)}. Category amounts listed below." }) {
                        val width = 26.dp.toPx()
                        var start = -90f
                        categories.forEachIndexed { index, (_, amount) ->
                            val sweep = (amount.toDouble() / total * 360).toFloat()
                            drawArc(categoryColor(index, dark), start, sweep, false, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width))
                            start += sweep
                        }
                    }
                    if (!largeText) Column(Modifier.width(154.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(total), style = MaterialTheme.typography.titleLarge)
                    }
                }
                if (largeText) Column(verticalArrangement = Arrangement.spacedBy(LedgerSpace.xs)) {
                    Text("Total spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(total), style = MaterialTheme.typography.titleLarge)
                }
            }
        } }
        if (total != 0L) item(key = "categories", contentType = "categories") {
            GroupedSection("Categories") {
                categories.forEachIndexed { index, (category, amount) ->
                    if (index > 0) GroupedDivider(inset = 36.dp)
                    GroupedRow(category, subtitle = spendingShareText(amount, total, locale),
                        leading = { Box(Modifier.size(9.dp).background(categoryColor(index, dark), CircleShape)) },
                        value = money(amount), chevron = true, onClickLabel = "View $category transactions", onClick = { openTransactions("EXPENSE", category) })
                }
            }
        }
        item(key = "daily-chart", contentType = "chart") { GroupedBlock {
            SectionTitle("Day by day")
            Text(peak?.let { "Highest spending: ${money(it.value)} on ${it.key} ${month.format(shortMonthFormat)}" } ?: "No daily spending recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DailySpendingChart(month, daily)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(1, month.lengthOfMonth() / 2, month.lengthOfMonth()).forEach { Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            TextButton(onClick = { openTransactions("EXPENSE", "ALL") }, modifier = Modifier.align(Alignment.End)) { Text("View spending"); AppIcon("right", Modifier.size(12.dp), primary) }
        } }
        item(key = "history", contentType = "history") { GroupedSection("Six-month view", footer = "Tap a month to open it.") {
            Row(Modifier.padding(horizontal = LedgerSpace.margin, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(8.dp).background(incomeColor, CircleShape)); Text("Income", style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(8.dp).background(expenseColor, CircleShape)); Text("Spent", style = MaterialTheme.typography.bodySmall) }
            }
            history.forEach { (period, summary) ->
                GroupedDivider()
                GroupedItem(onClick = { changeMonth(period) }, onClickLabel = "View ${period.format(monthYearFormat)}") {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(period.format(shortMonthYearFormat), style = MaterialTheme.typography.titleSmall)
                        ProportionBar(progress = (summary.income.toDouble() / scale).toFloat(), modifier = Modifier.fillMaxWidth().height(6.dp), color = incomeColor, trackColor = trackColor)
                        ProportionBar(progress = (summary.spent.toDouble() / scale).toFloat(), modifier = Modifier.fillMaxWidth().height(6.dp), color = expenseColor, trackColor = trackColor)
                        Text("Income ${money(summary.income)}  \u00b7  Spent ${money(summary.spent)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } }
    }
}
