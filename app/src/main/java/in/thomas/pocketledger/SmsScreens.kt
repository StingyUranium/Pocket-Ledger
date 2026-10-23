package `in`.thomas.pocketledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SmsScreen(messages: List<SmsRecord>, entries: List<Entry>, accounts: List<Account>, senders: List<TrustedSender>, permission: Boolean, capture: Boolean, from: String, to: String, setFrom: (String) -> Unit, setTo: (String) -> Unit, request: () -> Unit, toggle: (Boolean) -> Unit, scan: () -> Unit, trust: (TrustedSender) -> Unit, review: (SmsRecord) -> Unit, dismiss: (SmsRecord) -> Unit, link: (Pair<Entry, Entry>) -> Unit, refreshing: Boolean = false, refresh: () -> Unit = {}) {
    val reducedMotion = LocalReducedMotion.current
    var showAll by remember { mutableStateOf(false) }
    val pending = remember(messages) { messages.count { it.status == "REVIEW" } }
    val senderCandidates = remember(messages, senders) { messages.map { TrustedSender(normalizedSender(it.sender), it.bank) }.distinct().filter { it !in senders } }
    val transfers by backgroundCalculation(entries, cacheKey = "transfer-candidates") {
        val credits = entries.filter { it.type == "INCOME" }.groupBy { it.amount }
        entries.asSequence().filter { it.type == "EXPENSE" }.flatMap { out ->
            credits[out.amount].orEmpty().asSequence().filter { it.accountId != out.accountId && kotlin.math.abs(it.date - out.date) <= 86_400_000L }.map { out to it }
        }.take(10).toList()
    }
    val messageList = remember(messages, showAll) { messages.filter { showAll || it.status == "REVIEW" } }
    val refreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val headerInset = LocalLedgerHeaderInset.current
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, state = refreshState, modifier = Modifier.fillMaxSize(),
        indicator = { androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(state = refreshState, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter).padding(top = headerInset)) }) {
    FluidList(contentPadding = PaddingValues(LedgerSpace.margin, 12.dp, LedgerSpace.margin, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("shield", MaterialTheme.colorScheme.tertiary, 48)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Less typing. More living.", style = MaterialTheme.typography.titleMedium); Text("SBM Bank India & Axis Bank", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("Turn bank alerts into everyday clarity. Processed privately on your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!permission) Button(onClick = request, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) { Text("Enable SMS access") }
        } }
        item { GroupedSection(footer = if (capture && !permission) "Grant SMS access in Settings to resume capture." else null) {
            GroupedRow("Automatic capture", subtitle = if (capture && permission) "Ready for your next bank alert" else "Capture new bank transactions",
                trailing = { PocketSwitch(capture && permission, "Automatic capture", toggle) })
        } }
        item { SectionLabel("Bring in your history") }
        item { CardBlock {
            Text("Start with a date range", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { DateField(from, setFrom, "From") }
                Box(Modifier.weight(1f)) { DateField(to, setTo, "Through") }
            }
            Button(onClick = scan, enabled = permission, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) { AppIcon("search", Modifier.size(17.dp), if (permission) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); Text("Preview bank SMS") }
            Text("Check account opening dates and balances before importing older alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        val candidates = senderCandidates
        if (candidates.isNotEmpty()) item { SectionLabel("Confirm bank senders") }
        items(candidates, key = { "sender-${it.bank}-${it.sender}" }, contentType = { "sender" }) { t -> CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { BankMark(t.bank); Column(Modifier.weight(1f)) { Text(t.sender, style = MaterialTheme.typography.titleMedium); Text(t.bank, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            messages.firstOrNull { normalizedSender(it.sender) == t.sender && it.bank == t.bank }?.let { Text(it.body, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = { trust(t) }, contentPadding = PaddingValues(0.dp)) { Text("Review and trust sender"); Spacer(Modifier.width(5.dp)); AppIcon("right", Modifier.size(12.dp), MaterialTheme.colorScheme.primary) }
        } }
        val possible = transfers.orEmpty()
        if (possible.isNotEmpty()) item { SectionLabel("Possible transfers") }
        items(possible, key = { "transfer-${it.first.id}-${it.second.id}" }, contentType = { "transfer" }) { (out, inc) -> CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { IconTile("transfer"); Column(Modifier.weight(1f)) { Text(money(out.amount), style = MaterialTheme.typography.titleLarge); Text(displayDate(out.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text("${accounts.find { it.id == out.accountId }?.name} → ${accounts.find { it.id == inc.accountId }?.name}", style = MaterialTheme.typography.bodyMedium)
            Text("${out.payee} / ${inc.payee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { link(out to inc) }, contentPadding = PaddingValues(0.dp)) { Text("Link as transfer") }
        } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SectionTitle("Bank messages"); Spacer(Modifier.weight(1f)); if (pending > 0) StatusPill("$pending to review") }
                Segments(listOf("REVIEW" to "Needs review", "ALL" to "All messages"), if (showAll) "ALL" else "REVIEW") { showAll = it == "ALL" }
            }
        }
        val list = messageList
        if (list.isEmpty()) item { Empty(if (showAll) "Preview your bank alerts to start building a clearer picture." else "You're all caught up. Alerts that need a closer look will appear here.", if (showAll) "Your inbox, simplified" else "A little peace of mind", "sms") }
        items(list, key = { it.key }, contentType = { "message" }) { m ->
            var expanded by remember(m.key) { mutableStateOf(false) }
            MessageSwipe(m, dismiss) {
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BankMark(m.bank)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(m.payee.ifBlank { if (m.bank == "SBM") "SBM Bank India" else "Axis Bank" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${m.bank} · ${m.suffix?.let { "•••• $it" } ?: "Choose account"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.amount?.let(::money) ?: "Check amount", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    StatusPill(if (m.status == "REVIEW") "Review" else m.status.lowercase().replaceFirstChar(Char::titlecase), if (m.status == "REVIEW") Color(0xFFAC6500) else MaterialTheme.colorScheme.tertiary)
                }
                Text("${m.direction?.lowercase()?.replaceFirstChar(Char::titlecase) ?: "Check type"} · ${m.date?.let(::displayDate) ?: "Check date"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.status == "REVIEW") Text(m.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = LedgerSpace.touch).ledgerClickable { expanded = !expanded }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (expanded) "Hide original message" else "View original message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)); AppIcon("down", Modifier.size(12.dp), MaterialTheme.colorScheme.primary) }
                AnimatedVisibility(expanded, enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None else androidx.compose.animation.expandVertically(animationSpec = fluidSpring()) + androidx.compose.animation.fadeIn(animationSpec = fluidTween()), exit = if (reducedMotion) androidx.compose.animation.ExitTransition.None else androidx.compose.animation.shrinkVertically(animationSpec = fluidSpring()) + androidx.compose.animation.fadeOut(animationSpec = fluidTween())) { Text("${m.sender}\n\n${m.body}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (m.status == "REVIEW") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { review(m) }, modifier = Modifier.weight(1f)) { Text("Edit & save") }; TextButton(onClick = { dismiss(m) }) { Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            }
        }
    }
    }
}

@Composable fun ImportPreview(candidates: List<Candidate>, known: Set<String>, accounts: List<Account>, dismiss: () -> Unit, import: (List<Candidate>) -> Unit) {
    var selected by remember { mutableStateOf(candidates.indices.filter { candidates[it].key !in known }.toSet()) }
    LedgerSheet("Preview alerts", dismiss, "Import (${selected.size})", { import(candidates.filterIndexed { index, _ -> index in selected }) }, scrollContent = false, actionEnabled = selected.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { IconTile("sms"); Column { Text("${candidates.size} bank alerts found", style = MaterialTheme.typography.titleMedium); Text("Choose what belongs in your ledger.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Text("Existing and dismissed messages are skipped. Unconfirmed senders and incomplete details go to review.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FluidList(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (candidates.isEmpty()) item { Empty("Try another date range or check your bank's alert settings.", "No supported alerts found", "search") }
            itemsIndexed(candidates, key = { index, _ -> index }) { index, c ->
                CardBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(index in selected, enabled = c.key !in known, onCheckedChange = { if (it) selected = selected + index else selected = selected - index })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(c.parsed.amount?.let(::money) ?: "Check amount", style = MaterialTheme.typography.titleMedium); Text(c.parsed.payee.ifBlank { c.parsed.bank }, style = MaterialTheme.typography.bodyMedium, maxLines = 2); Text("${c.parsed.bank} · ${c.parsed.date?.let(::displayDate) ?: "Check date"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text("Sender: ${c.sender}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.key in known) StatusPill("Already imported or dismissed", MaterialTheme.colorScheme.onSurfaceVariant)
                    val account = accounts.singleOrNull { it.bank == c.parsed.bank && it.suffix == c.parsed.suffix }
                    if (account == null || c.parsed.date == null || c.parsed.date < account.openingDate) Text("Needs account mapping or an earlier opening date", style = MaterialTheme.typography.bodySmall, color = rust)
                    Text(c.body.take(350), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun SettingsDialog(capture: Boolean, permission: Boolean, senders: List<TrustedSender>, rules: List<MerchantRule>, export: () -> Unit, restore: () -> Unit, untrust: (TrustedSender) -> Unit, removeRule: (MerchantRule) -> Unit, appSettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val preferences = remember { context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE) }
    var appearance by remember { mutableStateOf(preferences.getString("mode", "SYSTEM") ?: "SYSTEM") }
    var dynamic by remember { mutableStateOf(preferences.getBoolean("dynamic", true)) }
    var contrast by remember { mutableStateOf(preferences.getBoolean("contrast", false)) }
    var reduceMotion by remember { mutableStateOf(preferences.getBoolean("reduceMotion", false)) }
    FluidList(contentPadding = PaddingValues(LedgerSpace.margin, 16.dp, LedgerSpace.margin, 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { item {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) { IconTile("shield", MaterialTheme.colorScheme.tertiary, 50); Column { Text("A little more private.", style = MaterialTheme.typography.titleMedium); Text("Your money stays with you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text("Your finances are processed and stored on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GroupedSection("Appearance", footer = "System animation settings are also respected.") {
            Pick("Theme", appearance, listOf("SYSTEM", "LIGHT", "DARK"), { when (it) { "LIGHT" -> "Light"; "DARK" -> "Dark"; else -> "Follow system" } }, grouped = true) {
                appearance = it
                preferences.edit().putString("mode", it).apply()
            }
            GroupedDivider()
            GroupedRow("Dynamic color", trailing = { PocketSwitch(dynamic, "Dynamic color") { dynamic = it; preferences.edit().putBoolean("dynamic", it).apply() } })
            GroupedDivider()
            GroupedRow("High contrast", trailing = { PocketSwitch(contrast, "High contrast") { contrast = it; preferences.edit().putBoolean("contrast", it).apply() } })
            GroupedDivider()
            GroupedRow("Reduce motion", trailing = { PocketSwitch(reduceMotion, "Reduce motion") { reduceMotion = it; preferences.edit().putBoolean("reduceMotion", it).apply() } })
        }
        GroupedSection("Permissions & capture") {
            GroupedRow("SMS access", subtitle = if (permission) "Permission granted" else "Permission not granted", leading = { IconTile("sms", size = 30) }, chevron = true, onClick = appSettings)
            GroupedDivider(inset = 58.dp)
            GroupedRow("Automatic capture", trailing = { StatusPill(if (capture && permission) "Active" else "Paused", if (capture && permission) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant) })
        }
        GroupedSection("Home screen", footer = "Keep this month\u2019s totals close at hand.") {
            GroupedRow("Add monthly summary", subtitle = "Income, spending, and balance", leading = { IconTile("home", size = 30) }, chevron = true, onClick = {
                if (!LedgerWidgets.pin(context, false)) android.widget.Toast.makeText(context, "Long-press your home screen, choose Widgets, then Ledger.", android.widget.Toast.LENGTH_LONG).show()
            })
            GroupedDivider(inset = 58.dp)
            GroupedRow("Add budget widget", subtitle = "Monthly limits and remaining amount", leading = { IconTile("budgets", size = 30) }, chevron = true, onClick = {
                if (!LedgerWidgets.pin(context, true)) android.widget.Toast.makeText(context, "Long-press your home screen, choose Widgets, then Ledger.", android.widget.Toast.LENGTH_LONG).show()
            })
        }
        GroupedSection("Backups") {
            GroupedRow("Save encrypted backup", subtitle = "Choose a password and a safe location", leading = { IconTile("lock", size = 30) }, chevron = true, onClick = export)
            GroupedDivider(inset = 58.dp)
            GroupedRow("Restore a backup", subtitle = "Replace data from a saved backup", leading = { IconTile("backup", size = 30) }, chevron = true, onClick = restore)
        }
        GroupedSection("Trusted senders", footer = if (senders.isEmpty()) "Confirm your bank's sender IDs after previewing SMS history." else null) {
            senders.forEachIndexed { index, sender ->
                if (index > 0) GroupedDivider()
                GroupedRow(sender.sender, subtitle = sender.bank, trailing = { TextButton(onClick = { untrust(sender) }) { Text("Remove", color = rust, style = MaterialTheme.typography.bodyMedium) } })
            }
        }
        GroupedSection("Remembered categories", footer = if (rules.isEmpty()) "Remember a payee's category when editing a transaction to use it for future alerts." else null) {
            rules.forEachIndexed { index, rule ->
                if (index > 0) GroupedDivider()
                GroupedRow(rule.merchant, subtitle = rule.category, trailing = { TextButton(onClick = { removeRule(rule) }) { Text("Remove", color = rust, style = MaterialTheme.typography.bodyMedium) } })
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Ledger", style = MaterialTheme.typography.titleSmall); Text("Version $version · Built for your everyday", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
        }
    }
}

@Composable fun SmsPermissionSheet(dismiss: () -> Unit, proceed: () -> Unit) {
    LedgerSheet("Bank SMS", dismiss, "Continue", proceed, closeLabel = "Later") {
        IconTile("shield", MaterialTheme.colorScheme.tertiary, 58)
        Text("Your bank alerts.\nA little more useful.", style = MaterialTheme.typography.headlineMedium)
        Text("Ledger reads your SMS inbox to identify SBM Bank India and Axis Bank account transactions. Processing happens on this phone.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CardBlock {
            Text("You're in control", style = MaterialTheme.typography.titleMedium)
            Text("Only matching financial messages are retained. OTPs, credit card alerts, and unrelated messages are excluded. Preview history before saving, confirm your bank senders, and turn capture off at any time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Continue to choose SMS permissions in Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
