package `in`.thomas.pocketledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter

fun displayDate(date: Long): String = Instant.ofEpochMilli(date).atZone(india).format(DateTimeFormatter.ofPattern("d MMM yyyy"))

@Composable fun MonthPicker(month: YearMonth, change: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { change(month.minusMonths(1)) }, modifier = Modifier.size(42.dp).semantics { contentDescription = "Previous month" }) { AppIcon("left", Modifier.size(15.dp), MaterialTheme.colorScheme.primary) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { change(month.plusMonths(1)) }, modifier = Modifier.size(42.dp).semantics { contentDescription = "Next month" }) { AppIcon("right", Modifier.size(15.dp), MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (month != YearMonth.now(india)) TextButton(onClick = { change(YearMonth.now(india)) }) { Text("Today", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable private fun BalanceCard(total: Long, accountCount: Int) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF172131), Color(0xFF2B3C54))))) {
        Box(Modifier.size(180.dp).align(Alignment.TopEnd).offset(x = 64.dp, y = (-72).dp).clip(CircleShape).background(Color(0xFF568BCA).copy(alpha = .14f)))
        Box(Modifier.size(144.dp).align(Alignment.BottomEnd).offset(x = 68.dp, y = 68.dp).clip(CircleShape).background(Color.White.copy(alpha = .04f)))
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total balance", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCDD6E4), modifier = Modifier.weight(1f))
                AppIcon("wallet", Modifier.size(22.dp), Color(0xFFCDD6E4))
            }
            Text(money(total), color = Color.White, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(5.dp).background(Color(0xFF73DBAC), CircleShape))
                    Text("$accountCount accounts · all recorded dates", color = Color(0xFFCDD6E4), style = MaterialTheme.typography.bodySmall)
                }
                Text("INR", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = .1f)).padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable private fun MoneyStat(label: String, amount: Long, income: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppIcon(if (income) "income" else "expense", Modifier.size(16.dp), if (income) MaterialTheme.colorScheme.tertiary else rust)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(amount), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable fun Overview(accounts: List<Account>, entries: List<Entry>, messages: List<SmsRecord>, month: YearMonth, budgets: List<Budget>, setup: () -> Unit, review: () -> Unit, edit: (Entry) -> Unit) {
    val monthly = entries.filter { inMonth(it, month) }
    val income = monthly.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = monthly.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val pending = messages.count { it.status == "REVIEW" }
    val totals = monthly.filter { it.type == "EXPENSE" }.groupBy { it.category }.mapValues { it.value.sumOf { entry -> entry.amount } }.toList().sortedByDescending { it.second }
    LazyColumn(contentPadding = PaddingValues(22.dp, 10.dp, 22.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { BalanceCard(accounts.sumOf { balance(it, entries) }, accounts.size) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MoneyStat("Income", income, true, Modifier.weight(1f)); MoneyStat("Spent", expense, false, Modifier.weight(1f)) } }
        if (pending > 0) item {
            Surface(onClick = review, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    AppIcon("sms", tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) { Text("A quick review", style = MaterialTheme.typography.titleSmall); Text("$pending bank ${if (pending == 1) "message needs" else "messages need"} your attention", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    AppIcon("right", Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                }
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
                    Button(onClick = setup, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) { Text("Set up accounts", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(onClick = review, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) { Text("Import SMS", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SectionTitle("Spending breakdown"); Spacer(Modifier.weight(1f)); Text("${totals.size} categories", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (totals.isEmpty()) item { Empty("Add an expense to see where your money goes.", "Every little detail, in focus", "chart") }
        else item {
            CardBlock {
                totals.forEachIndexed { index, (category, amount) ->
                    val color = categoryColor(index)
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(8.dp).background(color, CircleShape))
                            Text(category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(money(amount), style = MaterialTheme.typography.titleSmall)
                        }
                        LinearProgressIndicator(progress = { if (expense == 0L) 0f else (amount.toDouble() / expense).toFloat() }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = color, trackColor = color.copy(alpha = .1f), drawStopIndicator = {})
                        budgets.find { it.month == month.toString() && it.category == category && it.amount > 0 }?.let {
                            Text("${money(it.amount - amount)} left of ${money(it.amount)}", style = MaterialTheme.typography.bodySmall, color = if (amount > it.amount) rust else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index != totals.lastIndex) Spacer(Modifier.height(5.dp))
                }
            }
        }
        if (monthly.isNotEmpty()) {
            item { SectionTitle("Recent activity") }
            item { Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column { monthly.take(5).forEachIndexed { index, entry -> EntryRow(entry, accounts, divider = index < minOf(monthly.size, 5) - 1) { edit(entry) } } }
            } }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { AppIcon("lock", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(6.dp)); Text("Your finances stay on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

private fun categoryColor(index: Int) = listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF9500), Color(0xFF32ADE6), Color(0xFF34C759), Color(0xFFFF6482))[index % 6]

@Composable fun EntryRow(e: Entry, accounts: List<Account>, divider: Boolean = false, click: () -> Unit) {
    val tint = when (e.type) { "INCOME" -> MaterialTheme.colorScheme.tertiary; "TRANSFER" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface }
    Column(Modifier.fillMaxWidth().clickable(onClick = click)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(when (e.type) { "INCOME" -> "income"; "TRANSFER" -> "transfer"; else -> "receipt" }, if (e.type == "EXPENSE") MaterialTheme.colorScheme.primary else tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.payee.ifBlank { if (e.type == "TRANSFER") "Account transfer" else e.category }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (e.type == "TRANSFER") "${accounts.find { it.id == e.accountId }?.name.orEmpty()} → ${accounts.find { it.id == e.destinationId }?.name.orEmpty()}" else "${e.category} · ${accounts.find { it.id == e.accountId }?.name.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 140.dp)) {
                Text((if (e.type == "INCOME") "+" else if (e.type == "EXPENSE") "−" else "") + money(e.amount), style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Instant.ofEpochMilli(e.date).atZone(india).format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 72.dp), thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable fun Transactions(entries: List<Entry>, accounts: List<Account>, month: YearMonth, edit: (Entry) -> Unit) {
    var search by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ALL") }
    var account by remember { mutableStateOf("ALL") }
    var category by remember { mutableStateOf("ALL") }
    val filtered = entries.filter { inMonth(it, month) && (type == "ALL" || it.type == type) && (account == "ALL" || it.accountId == account || it.destinationId == account) && (category == "ALL" || it.category == category) && "${it.payee} ${it.note} ${it.category}".contains(search, true) }
    val groups = filtered.groupBy { dayText(it.date) }
    Column {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
            TextField(search, { search = it }, placeholder = { Text("Search transactions", color = MaterialTheme.colorScheme.onSurfaceVariant) }, leadingIcon = { AppIcon("search", Modifier.size(19.dp), MaterialTheme.colorScheme.onSurfaceVariant) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors())
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ChoiceChip("Type", type, listOf("ALL", "EXPENSE", "INCOME", "TRANSFER"), { if (it == "ALL") "All types" else it.lowercase().replaceFirstChar(Char::titlecase) }) { type = it } }
            item { ChoiceChip("Account", account, listOf("ALL") + accounts.map { it.id }, { value -> accounts.find { it.id == value }?.name ?: "All accounts" }) { account = it } }
            item { ChoiceChip("Category", category, listOf("ALL") + (defaultCategories + entries.map { it.category }).distinct(), { if (it == "ALL") "All categories" else it }) { category = it } }
        }
        LazyColumn(contentPadding = PaddingValues(22.dp, 8.dp, 22.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (filtered.isEmpty()) item { Empty("Tap + to add a transaction, or bring in your bank alerts from the SMS tab.", "A fresh page", "receipt") }
            groups.forEach { (date, group) ->
                item(key = "date-$date") { SectionLabel(displayDate(dayMillis(date))) }
                item(key = date) {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                        Column { group.forEachIndexed { index, entry -> EntryRow(entry, accounts, index < group.lastIndex) { edit(entry) } } }
                    }
                }
            }
            if (filtered.isNotEmpty()) item { Text("${filtered.size} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
    }
}

@Composable fun BankMark(bank: String) {
    val color = when (bank) { "AXIS" -> Color(0xFF951E4A); "SBM" -> Color(0xFF1263B1); else -> Color(0xFF248A3D) }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(color), contentAlignment = Alignment.Center) {
        if (bank == "CASH") AppIcon("wallet", tint = Color.White) else Text(if (bank == "AXIS") "AX" else "SBM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = .5.sp)
    }
}

@Composable fun AccountsScreen(accounts: List<Account>, entries: List<Entry>, messages: List<SmsRecord>, edit: (Account) -> Unit, add: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(22.dp, 12.dp, 22.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Everything, in one place.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(accounts, key = { it.id }) { a ->
            CardBlock(Modifier.clickable { edit(a) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BankMark(a.bank)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (a.bank == "CASH") "Cash account" else "Account •••• ${a.suffix}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppIcon("right", Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(balance(a, entries)), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 5.dp))
                HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Opening balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(a.opening), style = MaterialTheme.typography.labelMedium)
                }
                Text("From ${displayDate(a.openingDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                messages.filter { it.bank == a.bank && it.suffix == a.suffix && it.balance != null && it.status in listOf("SAVED", "LINKED") }.maxByOrNull { it.date ?: it.received }?.let {
                    Text("Bank reported ${money(it.balance!!)} · ${displayDate(it.date ?: it.received)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Button(onClick = add, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { AppIcon("plus", Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)); Text("Add account") } }
        item { Text("Opening balances represent your money just before the chosen date. Bank-reported balances are shown separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable fun BudgetScreen(budgets: List<Budget>, entries: List<Entry>, month: YearMonth, save: (Budget) -> Unit) {
    var edit by remember { mutableStateOf<Budget?>(null) }
    val current = budgets.filter { it.month == month.toString() && it.amount > 0 }
    val spentByCategory = entries.filter { it.type == "EXPENSE" && inMonth(it, month) }.groupBy { it.category }.mapValues { it.value.sumOf { entry -> entry.amount } }
    val planned = current.sumOf { it.amount }
    val spent = current.sumOf { spentByCategory[it.category] ?: 0L }
    LazyColumn(contentPadding = PaddingValues(22.dp, 10.dp, 22.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { CardBlock {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconTile("budgets", MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Room for what matters", style = MaterialTheme.typography.titleMedium); Text("Your monthly spending plan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(if (planned > 0) money((planned - spent).coerceAtLeast(0)) else "Make a little room", style = MaterialTheme.typography.headlineMedium)
            Text(if (planned > 0) "Left across your budgeted categories" else "Set a limit. Give every category a little direction.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (planned > 0) LinearProgressIndicator(progress = { (spent.toDouble() / planned).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = if (spent > planned) rust else MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
            Button(onClick = { edit = Budget(month.toString(), "Food", 0) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp)) { AppIcon("plus", Modifier.size(17.dp), MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(7.dp)); Text("Set a budget") }
        } }
        item { SectionTitle("Your categories") }
        if (current.isEmpty()) item { Empty("Create your first category budget to track spending as the month unfolds.", "Good things start with a plan", "budgets") }
        items(current, key = { it.category }) { b ->
            val amount = spentByCategory[b.category] ?: 0L
            val over = amount > b.amount
            CardBlock(Modifier.clickable { edit = b }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(b.category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); StatusPill(if (over) "Over budget" else "${((amount.toDouble() / b.amount) * 100).toInt()}% used", if (over) rust else MaterialTheme.colorScheme.primary) }
                Row(verticalAlignment = Alignment.Bottom) { Text(money(amount), style = MaterialTheme.typography.titleLarge); Text(" / ${money(b.amount)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                LinearProgressIndicator(progress = { (amount.toDouble() / b.amount).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = if (over) rust else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
                Text(if (over) "${money(amount - b.amount)} over your limit" else "${money(b.amount - amount)} remaining", style = MaterialTheme.typography.bodySmall, color = if (over) rust else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    edit?.let { b -> BudgetEditor(b, { edit = null }) { save(it); edit = null } }
}
