package `in`.thomas.pocketledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
    val transfers by backgroundCalculation(entries) {
        val credits = entries.filter { it.type == "INCOME" }.groupBy { it.amount }
        entries.asSequence().filter { it.type == "EXPENSE" }.flatMap { out ->
            credits[out.amount].orEmpty().asSequence().filter { it.accountId != out.accountId && kotlin.math.abs(it.date - out.date) <= 86_400_000L }.map { out to it }
        }.take(10).toList()
    }
    val messageList = remember(messages, showAll) { messages.filter { showAll || it.status == "REVIEW" } }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
    FluidList(contentPadding = PaddingValues(22.dp, 12.dp, 22.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("shield", MaterialTheme.colorScheme.tertiary, 48)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Less typing. More living.", style = MaterialTheme.typography.titleMedium); Text("SBM Bank India & Axis Bank", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("Turn bank alerts into everyday clarity. Processed privately on your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!permission) Button(onClick = request, modifier = Modifier.fillMaxWidth().height(48.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) { Text("Enable SMS access") }
            HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Automatic capture", style = MaterialTheme.typography.bodyLarge); Text(if (capture && permission) "Ready for your next bank alert" else "Capture new bank transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                PocketSwitch(capture && permission, "Automatic capture", toggle)
            }
            if (capture && !permission) Text("Grant SMS access in Settings to resume capture.", style = MaterialTheme.typography.bodySmall, color = rust)
        } }
        item { SectionLabel("Bring in your history") }
        item { CardBlock {
            Text("Start with a date range", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { DateField(from, setFrom, "From") }
                Box(Modifier.weight(1f)) { DateField(to, setTo, "Through") }
            }
            Button(onClick = scan, enabled = permission, modifier = Modifier.fillMaxWidth().height(48.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) { AppIcon("search", Modifier.size(17.dp), if (permission) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); Text("Preview bank SMS") }
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
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (expanded) "Hide original message" else "View original message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)); AppIcon("down", Modifier.size(12.dp), MaterialTheme.colorScheme.primary) }
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

@Composable private fun SettingsRow(icon: String, title: String, subtitle: String? = null, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, tint, 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.bodyLarge); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        AppIcon("right", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
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
    FluidList(contentPadding = PaddingValues(22.dp, 16.dp, 22.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { item {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) { IconTile("shield", MaterialTheme.colorScheme.tertiary, 50); Column { Text("A little more private.", style = MaterialTheme.typography.titleMedium); Text("Your money stays with you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text("Your finances are processed and stored on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Appearance")
        Pick("Theme", appearance, listOf("SYSTEM", "LIGHT", "DARK"), { when (it) { "LIGHT" -> "Light"; "DARK" -> "Dark"; else -> "Follow system" } }) {
            appearance = it
            preferences.edit().putString("mode", it).apply()
        }
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dynamic color", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                PocketSwitch(dynamic, "Dynamic color") { dynamic = it; preferences.edit().putBoolean("dynamic", it).apply() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("High contrast", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                PocketSwitch(contrast, "High contrast") { contrast = it; preferences.edit().putBoolean("contrast", it).apply() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reduce motion", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                PocketSwitch(reduceMotion, "Reduce motion") { reduceMotion = it; preferences.edit().putBoolean("reduceMotion", it).apply() }
            }
            Text("System animation settings are also respected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Permissions & capture")
        CardBlock {
            SettingsRow("sms", "SMS access", if (permission) "Permission granted" else "Permission not granted", onClick = appSettings)
            HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Automatic capture", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); StatusPill(if (capture && permission) "Active" else "Paused", if (capture && permission) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SectionLabel("Home screen")
        CardBlock {
            Text("Home screen widgets", style = MaterialTheme.typography.titleMedium)
            Text("Keep this month’s totals close at hand.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsRow("home", "Add monthly summary", "Income, spending, and balance", onClick = {
                if (!LedgerWidgets.pin(context, false)) android.widget.Toast.makeText(context, "Long-press your home screen, choose Widgets, then Ledger.", android.widget.Toast.LENGTH_LONG).show()
            })
            SettingsRow("budgets", "Add budget widget", "Monthly limits and remaining amount", onClick = {
                if (!LedgerWidgets.pin(context, true)) android.widget.Toast.makeText(context, "Long-press your home screen, choose Widgets, then Ledger.", android.widget.Toast.LENGTH_LONG).show()
            })
        }
        SectionLabel("Backups")
        CardBlock {
            SettingsRow("lock", "Save encrypted backup", "Choose a password and a safe location", onClick = export)
            HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow("backup", "Restore a backup", "Replace data from a saved backup", onClick = restore)
        }
        SectionLabel("Trusted senders")
        CardBlock {
            if (senders.isEmpty()) Text("Confirm your bank's sender IDs after previewing SMS history.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            senders.forEach { sender -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(sender.sender, style = MaterialTheme.typography.bodyLarge); Text(sender.bank, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { untrust(sender) }) { Text("Remove", color = rust, style = MaterialTheme.typography.bodyMedium) } } }
        }
        SectionLabel("Remembered categories")
        CardBlock {
            if (rules.isEmpty()) Text("Remember a payee's category when editing a transaction to use it for future alerts.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rules.forEach { rule -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(rule.merchant, style = MaterialTheme.typography.bodyMedium); Text(rule.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { removeRule(rule) }) { Text("Remove", color = rust, style = MaterialTheme.typography.bodyMedium) } } }
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
