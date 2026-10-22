package `in`.thomas.pocketledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun GraphsScreen(entries: List<Entry>, month: YearMonth, changeMonth: (YearMonth) -> Unit, openTransactions: (String, String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val expenses = entries.filter { !it.deleted && it.type == "EXPENSE" && inMonth(it, month) }
    val total = expenses.sumOf { it.amount }
    val categories = expenses.groupBy { it.category }.mapValues { it.value.sumOf { entry -> entry.amount } }.toList().sortedByDescending { it.second }
    val daily = expenses.groupBy { Instant.ofEpochMilli(it.date).atZone(india).dayOfMonth }.mapValues { it.value.sumOf { entry -> entry.amount } }
    val peak = daily.maxByOrNull { it.value }
    val history = (-5..0).map { month.plusMonths(it.toLong()) }.map { it to monthlySummary(entries, emptyList(), it) }
    val scale = history.maxOf { maxOf(it.second.income, it.second.spent) }.coerceAtLeast(1)
    val incomeColor = MaterialTheme.colorScheme.tertiary
    val expenseColor = rust
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    FluidList(contentPadding = PaddingValues(22.dp, 10.dp, 22.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { CardBlock {
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
                            drawArc(categoryColor(index), start, sweep, false, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width))
                            start += sweep
                        }
                    }
                    Column(Modifier.width(154.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(total), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                categories.forEachIndexed { index, (category, amount) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "View $category transactions") { openTransactions("EXPENSE", category) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(9.dp).background(categoryColor(index), CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(category, style = MaterialTheme.typography.titleSmall)
                            Text(String.format(locale, "%.1f%%", amount.toDouble() / total * 100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(amount), style = MaterialTheme.typography.titleSmall)
                        AppIcon("right", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } }
        item { CardBlock {
            SectionTitle("Day by day")
            Text(peak?.let { "Highest spending: ${money(it.value)} on ${it.key} ${month.format(DateTimeFormatter.ofPattern("MMM"))}" } ?: "No daily spending recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Daily spending for $month. ${daily.size} days with expenses. ${peak?.let { "Peak ${money(it.value)} on day ${it.key}." }.orEmpty()}" }) {
                val maximum = (peak?.value ?: 1L).coerceAtLeast(1)
                val step = size.width / month.lengthOfMonth()
                drawLine(trackColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                (1..month.lengthOfMonth()).forEach { day ->
                    val height = ((daily[day] ?: 0L).toDouble() / maximum * (size.height - 4.dp.toPx())).toFloat()
                    if (height > 0f) drawRoundRect(primary, Offset((day - 1) * step + step * .15f, size.height - height), Size(step * .7f, height), CornerRadius(2.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(1, month.lengthOfMonth() / 2, month.lengthOfMonth()).forEach { Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            TextButton(onClick = { openTransactions("EXPENSE", "ALL") }, modifier = Modifier.align(Alignment.End)) { Text("View spending"); AppIcon("right", Modifier.size(12.dp), primary) }
        } }
        item { CardBlock {
            SectionTitle("Six-month view")
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(8.dp).background(incomeColor, CircleShape)); Text("Income", style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(8.dp).background(expenseColor, CircleShape)); Text("Spent", style = MaterialTheme.typography.bodySmall) }
            }
            history.forEach { (period, summary) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "View ${period.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}") { changeMonth(period) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(period.format(DateTimeFormatter.ofPattern("MMM yyyy")), style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { (summary.income.toDouble() / scale).toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = incomeColor, trackColor = trackColor, drawStopIndicator = {})
                    LinearProgressIndicator(progress = { (summary.spent.toDouble() / scale).toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = expenseColor, trackColor = trackColor, drawStopIndicator = {})
                    Text("Income ${money(summary.income)}  ·  Spent ${money(summary.spent)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } }
    }
}
