package `in`.thomas.pocketledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun SmsScreen(messages: List<SmsRecord>, entries: List<Entry>, accounts: List<Account>, senders: List<TrustedSender>, permission: Boolean, capture: Boolean, from: String, to: String, setFrom: (String) -> Unit, setTo: (String) -> Unit, request: () -> Unit, toggle: (Boolean) -> Unit, scan: () -> Unit, trust: (TrustedSender) -> Unit, review: (SmsRecord) -> Unit, dismiss: (SmsRecord) -> Unit, link: (Pair<Entry, Entry>) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { CardBlock {
            SectionTitle("Your bank alerts, organized")
            Text("SBM Bank India + Axis Bank · account transactions only. All processing stays on this phone.")
            if (!permission) Button(onClick = request) { Text("Enable SMS access") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Capture new bank alerts", modifier = Modifier.weight(1f).padding(top = 12.dp)); Switch(capture && permission, toggle) }
            if (capture && !permission) Text("SMS access is missing. Grant permission or check Settings.", color = rust)
            Text("Historical import", style = MaterialTheme.typography.titleSmall)
            Field(from, setFrom, "From (YYYY-MM-DD)"); Field(to, setTo, "Through (YYYY-MM-DD)")
            OutlinedButton(onClick = scan, enabled = permission, modifier = Modifier.fillMaxWidth()) { Text("Preview bank SMS") }
            Text("Adjust opening dates and balances in Accounts before importing older messages.", style = MaterialTheme.typography.bodySmall)
        } }
        val candidates = messages.map { TrustedSender(normalizedSender(it.sender), it.bank) }.distinct().filter { it !in senders }
        if (candidates.isNotEmpty()) item { SectionTitle("Confirm your bank senders") }
        items(candidates) { t -> CardBlock {
            Text("${t.bank} · ${t.sender}")
            messages.firstOrNull { normalizedSender(it.sender) == t.sender && it.bank == t.bank }?.let { Text(it.body.take(260), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { trust(t) }) { Text("Review and trust sender") }
        } }
        val possible = entries.filter { it.type == "EXPENSE" }.flatMap { out -> entries.filter { it.type == "INCOME" && it.accountId != out.accountId && it.amount == out.amount && kotlin.math.abs(it.date - out.date) <= 86_400_000L }.map { out to it } }.take(10)
        if (possible.isNotEmpty()) item { SectionTitle("Possible account transfers") }
        items(possible) { (out, inc) -> CardBlock {
            Text("${money(out.amount)} · ${dayText(out.date)}")
            Text("${accounts.find { it.id == out.accountId }?.name} → ${accounts.find { it.id == inc.accountId }?.name}")
            Text("${out.payee} / ${inc.payee}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { link(out to inc) }) { Text("Link as transfer") }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { SectionTitle(if (showAll) "Financial SMS history" else "Needs review"); TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Review only" else "Show all") } } }
        val list = messages.filter { showAll || it.status == "REVIEW" }
        if (list.isEmpty()) item { Empty("Nothing needs review. Preview your bank SMS to get started.") }
        items(list, key = { it.key }) { m -> CardBlock {
            SectionTitle("${m.bank} · ${m.suffix ?: "Unmapped account"}")
            Text("${m.direction ?: "Unknown type"} · ${m.amount?.let(::money) ?: "Check amount"} · ${m.date?.let(::dayText) ?: "Check date"}")
            Text(m.payee.ifBlank { m.sender })
            Text(m.body, style = MaterialTheme.typography.bodySmall)
            Text(if (m.status == "REVIEW") m.reason else m.status.lowercase().replaceFirstChar(Char::titlecase), color = if (m.status == "REVIEW") rust else pine)
            if (m.status == "REVIEW") Row { TextButton(onClick = { review(m) }) { Text("Edit and save") }; TextButton(onClick = { dismiss(m) }) { Text("Dismiss") } }
        } }
    }
}

@Composable fun ImportPreview(candidates: List<Candidate>, known: Set<String>, accounts: List<Account>, dismiss: () -> Unit, import: (List<Candidate>) -> Unit) {
    var selected by remember { mutableStateOf(candidates.filter { it.key !in known }.map { it.key }.toSet()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Preview · ${candidates.size} bank messages") }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Existing and dismissed messages are skipped. Unconfirmed senders or incomplete matches go to review. SMS dates determine the transaction date.") }
            if (candidates.isEmpty()) item { Text("No supported account alerts found in this range.") }
            items(candidates, key = { it.key }) { c ->
                Row {
                    Checkbox(checked = c.key in selected, enabled = c.key !in known, onCheckedChange = { if (it) selected = selected + c.key else selected = selected - c.key })
                    Column(Modifier.weight(1f)) {
                        Text("${c.parsed.bank} · ${c.parsed.amount?.let(::money) ?: "Check amount"}")
                        Text("${c.parsed.payee} · ${c.parsed.date?.let(::dayText) ?: "Unknown date"}", style = MaterialTheme.typography.bodySmall)
                        Text("Sender: ${c.sender}", style = MaterialTheme.typography.bodySmall)
                        if (c.key in known) Text("Already imported or dismissed", color = pine)
                        val a = accounts.singleOrNull { it.bank == c.parsed.bank && it.suffix == c.parsed.suffix }
                        if (a == null || c.parsed.date == null || c.parsed.date < a.openingDate) Text("Needs account mapping or earlier opening date", color = rust)
                        Text(c.body.take(350), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = selected.isNotEmpty(), onClick = { import(candidates.filter { it.key in selected }) }) { Text("Import ${selected.size}") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable fun SettingsDialog(capture: Boolean, permission: Boolean, senders: List<TrustedSender>, rules: List<MerchantRule>, dismiss: () -> Unit, export: () -> Unit, restore: () -> Unit, untrust: (TrustedSender) -> Unit, removeRule: (MerchantRule) -> Unit, appSettings: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Settings & backup") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Private by design"); Text("Offline · INR · Asia/Kolkata\nNo account, cloud service, analytics, or internet permission.")
            Text("SMS access: ${if (permission) "granted" else "not granted"}\nNew alert capture: ${if (capture) "on" else "off"}")
            TextButton(onClick = appSettings) { Text("Android app permissions") }
            HorizontalDivider(); SectionTitle("Encrypted backups")
            Text("Backups include financial SMS, balances, rules, and import history. Choose a safe location and keep your password.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = export, modifier = Modifier.fillMaxWidth()) { Text("Save encrypted backup") }
            OutlinedButton(onClick = restore, modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
            HorizontalDivider(); SectionTitle("Trusted SMS senders")
            if (senders.isEmpty()) Text("Confirm sender IDs from your SMS inbox after previewing history.")
            senders.forEach { sender -> Row { Text("${sender.bank} · ${sender.sender}", modifier = Modifier.weight(1f).padding(top = 12.dp)); TextButton(onClick = { untrust(sender) }) { Text("Remove") } } }
            SectionTitle("Remembered categories")
            if (rules.isEmpty()) Text("Choose ‘Remember this payee’s category’ while editing a transaction.")
            rules.forEach { rule -> Row { Text("${rule.merchant} → ${rule.category}", modifier = Modifier.weight(1f).padding(top = 12.dp)); TextButton(onClick = { removeRule(rule) }) { Text("Remove") } } }
            Text("Pocket Ledger 1.1 · Android 17", style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}
