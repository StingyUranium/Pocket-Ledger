package `in`.thomas.pocketledger

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter

// Pattern parsing is costly and these run per row, so build each formatter once.
private val dayMonthYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM")
internal val monthYearFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
internal val shortMonthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy")

fun displayDate(date: Long): String = Instant.ofEpochMilli(date).atZone(india).format(dayMonthYearFormat)

@Composable fun MonthPicker(month: YearMonth, change: (YearMonth) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { change(month.minusMonths(1)) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous month" }) { AppIcon("left", tint = MaterialTheme.colorScheme.primary) }
            Text(month.format(monthYearFormat), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { change(month.plusMonths(1)) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Next month" }) { AppIcon("right", tint = MaterialTheme.colorScheme.primary) }
        }
        if (month != YearMonth.now(india)) TextButton(onClick = { change(YearMonth.now(india)) }, modifier = Modifier.align(Alignment.End)) { Text("Current month") }
    }
}

@Composable private fun BalanceCard(total: Long, accountCount: Int) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Monthly balance", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(remember(total) { money(total) }, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Income minus spending \u00b7 selected month", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("$accountCount accounts \u00b7 INR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable private fun MoneyStat(label: String, amount: Long, income: Boolean, month: YearMonth, modifier: Modifier = Modifier, open: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(onClick = open, interactionSource = interaction, modifier = modifier.premiumPressEffect(interaction).metricBounds("${if (income) "INCOME" else "EXPENSE"}:$month"), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppIcon(if (income) "income" else "expense", Modifier.size(16.dp), if (income) MaterialTheme.colorScheme.tertiary else rust)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                AppIcon("right", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(amount), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable fun Overview(accounts: List<Account>, entries: List<Entry>, messages: List<SmsRecord>, month: YearMonth, budgets: List<Budget>, report: OverviewReport?, setup: () -> Unit, review: () -> Unit, openTransactions: (String, String) -> Unit, edit: (Entry) -> Unit) {
    androidx.activity.compose.ReportDrawnWhen { report != null }
    val ready = report ?: run { LedgerSkeleton(); return }
    val monthly = ready.monthly
    val income = ready.summary.income
    val expense = ready.summary.spent
    val pending = remember(messages) { messages.count { it.status == "REVIEW" } }
    val totals = ready.spendingByCategory
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val largeText = LocalDensity.current.fontScale > 1.3f
    FluidList(contentPadding = PaddingValues(LedgerSpace.margin, 10.dp, LedgerSpace.margin, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { BalanceCard(income - expense, accounts.size) }
        item {
            if (LocalDensity.current.fontScale > 1.3f) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyStat("Income", income, true, month, Modifier.fillMaxWidth()) { openTransactions("INCOME", "ALL") }
                MoneyStat("Spent", expense, false, month, Modifier.fillMaxWidth()) { openTransactions("EXPENSE", "ALL") }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyStat("Income", income, true, month, Modifier.weight(1f)) { openTransactions("INCOME", "ALL") }
                MoneyStat("Spent", expense, false, month, Modifier.weight(1f)) { openTransactions("EXPENSE", "ALL") }
            }
        }
        if (ready.summary.budgetLimit > 0) item {
            CardBlock {
                Text("Budget remaining", style = MaterialTheme.typography.titleMedium)
                Text(money(ready.summary.budgetRemaining), style = MaterialTheme.typography.headlineMedium,
                    color = if (ready.summary.budgetRemaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text("Across categories with recurring limits", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (pending > 0) item {
            GroupedSection {
                GroupedRow("A quick review", subtitle = "$pending bank ${if (pending == 1) "message needs" else "messages need"} your attention",
                    leading = { AppIcon("sms", tint = MaterialTheme.colorScheme.primary) }, chevron = true, onClick = review)
            }
        }
        if (entries.isEmpty()) item {
            CardBlock {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile("shield", MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f)) { Text("Your money. A clearer picture.", style = MaterialTheme.typography.titleMedium); Text("A few details to get you started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text("Set your opening balances, then bring in your bank transactions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = setup, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) { Text("Set up accounts", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(onClick = review, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) { Text("Import SMS", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        if (totals.isEmpty()) {
            item { GroupedSection("Spending breakdown") {} }
            item { Empty("Add an expense to see where your money goes.", "Every little detail, in focus", "chart") }
        } else item {
            GroupedSection("Spending breakdown", footer = "Share of total spending \u00b7 ${totals.size} ${if (totals.size == 1) "category" else "categories"}") {
                totals.forEachIndexed { index, (category, amount) ->
                    if (index > 0) GroupedDivider()
                    val color = categoryColor(index, dark)
                    val share = remember(amount, expense) { spendingShareText(amount, expense) }
                    GroupedItem(onClick = { openTransactions("EXPENSE", category) }, onClickLabel = "View $category transactions") {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Box(Modifier.size(8.dp).background(color, CircleShape))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LedgerSpace.xs)) {
                                    Text(category, style = MaterialTheme.typography.bodyLarge)
                                    if (largeText) Text(money(amount), style = MaterialTheme.typography.titleSmall)
                                }
                                if (!largeText) Text(money(amount), style = MaterialTheme.typography.titleSmall)
                                AppIcon("right", Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$share of spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ProportionBar(progress = if (expense == 0L) 0f else (amount.toDouble() / expense).toFloat(), modifier = Modifier.fillMaxWidth().height(LedgerSpace.xs), color = color, trackColor = color.copy(alpha = .12f))
                            budgets.find { it.category == category && it.amount > 0 }?.let {
                                Text("${money(it.amount - amount)} left of ${money(it.amount)}", style = MaterialTheme.typography.bodySmall, color = if (amount > it.amount) rust else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (monthly.isNotEmpty()) item {
            GroupedSection("Recent activity") {
                monthly.take(5).forEachIndexed { index, entry -> EntryRow(entry, accounts, divider = index < minOf(monthly.size, 5) - 1) { edit(entry) } }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { AppIcon("lock", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(6.dp)); Text("Your finances stay on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

private val categoryColors = listOf(Color(0xFF005AC1), Color(0xFF8540B0), Color(0xFF9A5700), Color(0xFF006B87), Color(0xFF146C2E), Color(0xFFAC325B))
private val darkCategoryColors = listOf(Color(0xFF64ADFF), Color(0xFFC58AF9), Color(0xFFFFB74D), Color(0xFF4DD0E1), Color(0xFF66BB6A), Color(0xFFF48FB1))
fun categoryColor(index: Int, dark: Boolean) = (if (dark) darkCategoryColors else categoryColors).let { it[index % it.size] }

@Composable fun EntryRow(e: Entry, accounts: List<Account>, divider: Boolean = false, click: () -> Unit) {
    val tint = when (e.type) { "INCOME" -> MaterialTheme.colorScheme.tertiary; "TRANSFER" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface }
    val title = remember(e) { e.payee.ifBlank { if (e.type == "TRANSFER") "Account transfer" else e.category } }
    val subtitle = remember(e, accounts) {
        if (e.type == "TRANSFER") "${accounts.find { it.id == e.accountId }?.name.orEmpty()} \u2192 ${accounts.find { it.id == e.destinationId }?.name.orEmpty()}"
        else "${e.category} \u00b7 ${accounts.find { it.id == e.accountId }?.name.orEmpty()}"
    }
    val amount = remember(e.type, e.amount) { (if (e.type == "INCOME") "+" else if (e.type == "EXPENSE") "\u2212" else "") + money(e.amount) }
    val date = remember(e.date) { Instant.ofEpochMilli(e.date).atZone(india).format(dayMonthFormat) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).premiumPressEffect(interaction, pressedScale = .993f).clickable(interactionSource = interaction, indication = null, onClickLabel = "Edit transaction", onClick = click)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(when (e.type) { "INCOME" -> "income"; "TRANSFER" -> "transfer"; else -> "receipt" })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (largeText) {
                    Text(amount, style = MaterialTheme.typography.titleMedium, color = tint)
                    Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!largeText) Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 156.dp)) {
                Text(amount, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable fun Transactions(entries: List<Entry>, accounts: List<Account>, month: YearMonth, initialType: String = "ALL", initialCategory: String = "ALL", leading: (@Composable () -> Unit)? = null, delete: ((Entry) -> Unit)? = null, edit: (Entry) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable(initialType) { mutableStateOf(initialType) }
    var account by rememberSaveable { mutableStateOf("ALL") }
    var category by rememberSaveable(initialCategory) { mutableStateOf(initialCategory) }
    val result by backgroundCalculation(entries, month, type, account, category, search, cacheKey = "transactions-$initialType-$initialCategory") {
        entries.filter { !it.deleted && inMonth(it, month) && (type == "ALL" || it.type == type) &&
            (account == "ALL" || it.accountId == account || it.destinationId == account) &&
            (category == "ALL" || it.category == category) && "${it.payee} ${it.note} ${it.category}".contains(search, true) }
            .groupBy { dayText(it.date) }
    }
    androidx.activity.compose.ReportDrawnWhen { result != null }
    val groups = result.orEmpty()
    val savedCategories by backgroundCalculation(entries, type, cacheKey = "filter-categories-$type") { categoryOptions(type, entries) }
    val categoryOptions = savedCategories ?: categoriesForType(type)
    // Search and filters live in the list so the overlaid page header never leaves a gap above them.
    FluidList(contentPadding = PaddingValues(bottom = 28.dp)) {
        leading?.let { header -> item(key = "leading", contentType = "leading") { header() } }
        item(key = "search", contentType = "search") { Box(Modifier.padding(horizontal = LedgerSpace.margin, vertical = 6.dp)) {
            TextField(search, { search = it }, label = { Text("Search transactions") }, placeholder = { Text("Payee, category or note", color = MaterialTheme.colorScheme.onSurfaceVariant) }, leadingIcon = { AppIcon("search", Modifier.size(19.dp), MaterialTheme.colorScheme.onSurfaceVariant) }, singleLine = true, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { AppIcon("close", Modifier.semantics { contentDescription = "Clear search" }) } }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = fieldColors())
        } }
        item(key = "filters", contentType = "filters") { FluidRow(contentPadding = PaddingValues(horizontal = LedgerSpace.margin, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ChoiceChip("Type", type, listOf("ALL", "EXPENSE", "INCOME", "TRANSFER"), { if (it == "ALL") "All types" else it.lowercase().replaceFirstChar(Char::titlecase) }) { if (type != it) { type = it; category = "ALL" } } }
            item { ChoiceChip("Account", account, listOf("ALL") + accounts.map { it.id }, { value -> accounts.find { it.id == value }?.name ?: "All accounts" }) { account = it } }
            item { ChoiceChip("Category", category, listOf("ALL") + categoryOptions, { if (it == "ALL") "All categories" else it }) { category = it } }
        } }
        run {
            if (result == null) item(key = "loading", contentType = "loading") { Text("Filtering transactions...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = LedgerSpace.margin, vertical = 8.dp)) }
            else if (groups.isEmpty()) item(key = "empty", contentType = "empty") { Column(Modifier.padding(horizontal = LedgerSpace.margin, vertical = 8.dp)) {
                Empty("Try another month or clear the filters. Add an entry with +, or review bank alerts in Inbox.", "No matching transactions", "receipt")
                if (search.isNotEmpty() || type != "ALL" || category != "ALL" || account != "ALL") TextButton(onClick = { search = ""; type = "ALL"; category = "ALL"; account = "ALL" }) { Text("Clear filters") }
            } }
            groups.forEach { (date, group) ->
                item(key = "date-$date", contentType = "heading") { Box(Modifier.padding(start = LedgerSpace.margin, end = LedgerSpace.margin, top = LedgerSpace.section, bottom = LedgerSpace.small)) { SectionLabel(displayDate(dayMillis(date))) } }
                itemsIndexed(group, key = { _, entry -> entry.id }, contentType = { _, _ -> "entry" }) { index, entry ->
                    val top = if (index == 0) 12.dp else 0.dp
                    val bottom = if (index == group.lastIndex) 12.dp else 0.dp
                    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                    val row: @Composable () -> Unit = {
                        Surface(shape = shape, color = MaterialTheme.colorScheme.surface) {
                            EntryRow(entry, accounts, divider = index < group.lastIndex) { edit(entry) }
                        }
                    }
                    Box((if (LocalReducedMotion.current) Modifier else Modifier.animateItem()).padding(horizontal = LedgerSpace.margin)) {
                        if (delete != null) EntrySwipe(entry, shape, delete, row) else row()
                    }
                }
            }
            if (groups.isNotEmpty()) item(key = "count", contentType = "footer") { Text("${groups.values.sumOf { it.size }} transactions", modifier = Modifier.padding(start = LedgerSpace.margin, end = LedgerSpace.margin, top = LedgerSpace.content), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable fun BankMark(bank: String) {
    val color = when (bank) { "AXIS" -> Color(0xFF951E4A); "SBM" -> Color(0xFF1263B1); else -> Color(0xFF248A3D) }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
        // The bank abbreviation is logo artwork, like AppIcon, rather than scalable body copy.
        if (bank == "CASH") AppIcon("wallet", tint = Color.White) else Text(if (bank == "AXIS") "AX" else "SBM", fontSize = with(LocalDensity.current) { 12.dp.toSp() }, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = with(LocalDensity.current) { .5.dp.toSp() })
    }
}

@Composable fun AccountsScreen(accounts: List<Account>, entries: List<Entry>, messages: List<SmsRecord>, edit: (Account) -> Unit, add: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val calculatedBalances by backgroundCalculation(accounts, entries, cacheKey = "balances") { accounts.associate { it.id to balance(it, entries) } }
    val balances = calculatedBalances ?: run { LedgerSkeleton(); return }
    val bankBalances = remember(messages) { messages.filter { it.balance != null && it.status in listOf("SAVED", "LINKED") }
        .groupBy { it.bank to it.suffix }.mapValues { (_, records) -> records.maxByOrNull { it.date ?: it.received } } }
    FluidList(contentPadding = PaddingValues(LedgerSpace.margin, 12.dp, LedgerSpace.margin, 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Everything, in one place.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (accounts.isNotEmpty()) item(key = "accounts", contentType = "accounts") {
            GroupedSection(footer = "Opening balances represent your money just before the chosen date. Bank-reported balances are shown separately.") {
                accounts.forEachIndexed { index, a ->
                    if (index > 0) GroupedDivider(inset = 72.dp)
                    GroupedItem(onClick = { edit(a) }, onClickLabel = "Edit ${a.name}") {
                        BankMark(a.bank)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            if (largeText) Text(money(balances[a.id] ?: a.opening), style = MaterialTheme.typography.titleMedium)
                            Text(if (a.bank == "CASH") "Cash account" else "Account \u2022\u2022\u2022\u2022 ${a.suffix}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Opening ${money(a.opening)} \u00b7 from ${displayDate(a.openingDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            bankBalances[a.bank to a.suffix]?.let {
                                Text("Bank reported ${money(it.balance!!)} \u00b7 ${displayDate(it.date ?: it.received)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (!largeText) Text(money(balances[a.id] ?: a.opening), style = MaterialTheme.typography.titleMedium)
                        AppIcon("right", Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Button(onClick = add, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) { AppIcon("plus", Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)); Text("Add account") } }
    }
}

@Composable fun BudgetScreen(budgets: List<Budget>, entries: List<Entry>, month: YearMonth, save: (Budget, String?, () -> Unit) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    var edit by remember { mutableStateOf<Budget?>(null) }
    var originalCategory by remember { mutableStateOf<String?>(null) }
    var allocationView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val current = remember(budgets) { budgets.filter { it.amount > 0 } }
    val spending by backgroundCalculation(entries, month, cacheKey = "budget-spending") { entries.filter { !it.deleted && it.type == "EXPENSE" && inMonth(it, month) }.groupBy { it.category }.mapValues { it.value.sumOf { entry -> entry.amount } } }
    val spentByCategory = spending ?: run { LedgerSkeleton(); return }
    val planned = current.sumOf { it.amount }
    val spent = current.sumOf { spentByCategory[it.category] ?: 0L }
    FluidList(contentPadding = PaddingValues(LedgerSpace.margin, 10.dp, LedgerSpace.margin, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Segments(listOf("PLAN" to "50 / 30 / 20", "BUDGETS" to "Category budgets"),
                if (allocationView) "PLAN" else "BUDGETS") { allocationView = it == "PLAN" }
        }
        if (allocationView) {
            item { IncomeAllocationPlan(entries, month) }
        } else {
            item { CardBlock {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconTile("budgets", MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Room for what matters", style = MaterialTheme.typography.titleMedium); Text("Recurring limits \u00b7 spending for this month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(if (planned > 0) money(planned - spent) else "Make a little room", style = MaterialTheme.typography.headlineMedium, color = if (spent > planned) rust else MaterialTheme.colorScheme.onSurface)
                Text(if (planned > 0) "Left across your budgeted categories" else "Set a limit. Give every category a little direction.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (planned > 0) ProportionBar(progress = (spent.toDouble() / planned).toFloat(), modifier = Modifier.fillMaxWidth().height(6.dp), color = if (spent > planned) rust else MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Button(onClick = { originalCategory = null; edit = Budget("Food", 0) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) { AppIcon("plus", Modifier.size(17.dp), MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(7.dp)); Text("Set a budget") }
            } }
            if (current.isEmpty()) {
                item { GroupedSection("Your categories") {} }
                item { Empty("Create your first category budget to track spending as the month unfolds.", "Good things start with a plan", "budgets") }
            } else item(key = "budgets", contentType = "budgets") {
                GroupedSection("Your categories") {
                    current.forEachIndexed { index, b ->
                        if (index > 0) GroupedDivider()
                        val amount = spentByCategory[b.category] ?: 0L
                        val over = amount > b.amount
                        GroupedItem(onClick = { originalCategory = b.category; edit = b }, onClickLabel = "Edit ${b.category} budget") {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (largeText) Column(verticalArrangement = Arrangement.spacedBy(LedgerSpace.xs)) {
                                    Text(b.category, style = MaterialTheme.typography.bodyLarge)
                                    StatusPill(if (over) "Over budget" else "${((amount.toDouble() / b.amount) * 100).toInt()}% used", if (over) rust else MaterialTheme.colorScheme.primary)
                                } else Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    StatusPill(if (over) "Over budget" else "${((amount.toDouble() / b.amount) * 100).toInt()}% used", if (over) rust else MaterialTheme.colorScheme.primary)
                                }
                                if (largeText) Column(verticalArrangement = Arrangement.spacedBy(LedgerSpace.xs)) {
                                    Text(money(amount), style = MaterialTheme.typography.titleMedium)
                                    Text("Budget ${money(b.amount)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else Row(verticalAlignment = Alignment.Bottom) { Text(money(amount), style = MaterialTheme.typography.titleMedium); Text(" / ${money(b.amount)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                ProportionBar(progress = (amount.toDouble() / b.amount).toFloat(), modifier = Modifier.fillMaxWidth().height(LedgerSpace.xs), color = if (over) rust else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                Text("${money(b.amount - amount)} remaining", style = MaterialTheme.typography.bodySmall, color = if (over) rust else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { b -> BudgetEditor(b, { edit = null }, knownEntries = entries) { budget -> save(budget, originalCategory) { edit = null } } }
}
