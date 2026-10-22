package `in`.thomas.pocketledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*

@Composable fun fieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable fun Field(value: String, change: (String) -> Unit, label: String, numeric: Boolean = false) {
    TextField(value, change, label = { Text(label, style = MaterialTheme.typography.bodySmall) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(value: String, change: (String) -> Unit, label: String) {
    var open by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open = true }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(runCatching { displayDate(dayMillis(value)) }.getOrDefault(value), style = MaterialTheme.typography.bodyLarge)
            }
            AppIcon("calendar", Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis()))
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
            open = false
        }) { Text("Done") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ChoiceSheet(label: String, value: String, options: List<String>, display: (String) -> String, dismiss: () -> Unit, change: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(label)
            LazyColumn(Modifier.heightIn(max = 440.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                if (options.isEmpty()) item { Text("No options available", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(options.distinct(), key = { it }) { option ->
                    Column(Modifier.fillMaxWidth().clickable { change(option); dismiss() }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 17.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(display(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (option == value) AppIcon("check", Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
                        }
                        if (option != options.lastOrNull()) HorizontalDivider(Modifier.padding(start = 17.dp), thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable fun Pick(label: String, value: String, options: List<String>, display: (String) -> String = { it.lowercase().replaceFirstChar(Char::titlecase) }, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { open = true }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(display(value).ifBlank { "Choose" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
            AppIcon("down", Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) ChoiceSheet(label, value, options, display, { open = false }, change)
}

@Composable fun ChoiceChip(label: String, value: String, options: List<String>, display: (String) -> String, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = value != "ALL"
    Row(Modifier.clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface).clickable { open = true }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(display(value), style = MaterialTheme.typography.bodyMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        AppIcon("down", Modifier.size(11.dp), if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) ChoiceSheet(label, value, options, display, { open = false }, change)
}

@Composable fun Confirm(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, shape = RoundedCornerShape(26.dp), containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) }, text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirm", color = if (title.startsWith("Delete")) rust else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel", fontWeight = FontWeight.Normal) } })
}

@Composable fun EntryEditor(initial: Entry, accounts: List<Account>, sms: SmsRecord?, dismiss: () -> Unit, save: (Entry, Boolean) -> Unit, delete: (() -> Unit)?) {
    var type by remember { mutableStateOf(initial.type) }
    var account by remember { mutableStateOf(initial.accountId) }
    var target by remember { mutableStateOf(initial.destinationId ?: accounts.firstOrNull { it.id != account }?.id.orEmpty()) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) amountText(initial.amount) else "") }
    var date by remember { mutableStateOf(dayText(initial.date)) }
    var time by remember { mutableStateOf(if (initial.dateOnly) "" else Instant.ofEpochMilli(initial.date).atZone(india).toLocalTime().withNano(0).toString()) }
    var payee by remember { mutableStateOf(initial.payee) }
    var category by remember { mutableStateOf(initial.category) }
    var note by remember { mutableStateOf(initial.note) }
    var rememberMerchant by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var customCategory by remember { mutableStateOf(initial.category !in defaultCategories) }
    fun submit() {
        try {
            val p = paise(amount)
            require(p > 0) { "Enter an amount above zero" }
            val d = LocalDate.parse(date)
            val millis = if (time.isBlank()) d.atStartOfDay(india).toInstant().toEpochMilli() else d.atTime(LocalTime.parse(time)).atZone(india).toInstant().toEpochMilli()
            require(type != "TRANSFER" || target.isNotBlank() && target != account) { "Choose a different destination account" }
            require(millis >= accounts.single { it.id == account }.openingDate) { "Date is before account opening date. Adjust the account first." }
            if (type == "TRANSFER") require(millis >= accounts.single { it.id == target }.openingDate) { "Date is before destination account opening date" }
            require(category.isNotBlank() || type == "TRANSFER") { "Enter a category" }
            save(initial.copy(accountId = account, destinationId = if (type == "TRANSFER") target else null, type = type, amount = p, date = millis, dateOnly = time.isBlank(), payee = payee.trim(), category = if (type == "TRANSFER") "Transfer" else category.trim(), note = note), rememberMerchant)
        } catch (e: Exception) { error = e.message ?: "Check the amount and date" }
    }
    LedgerSheet(if (delete == null) "New transaction" else "Transaction", dismiss, "Save", { submit() }) {
        Segments(listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer"), type) { type = it }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("AMOUNT IN INR", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextField(amount, { amount = it; error = null }, placeholder = { Text("0.00", fontSize = 40.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)) }, prefix = { Text("₹ ", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        SectionLabel("Details")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pick(if (type == "TRANSFER") "From account" else "Account", account, accounts.map { it.id }, { value -> accounts.find { it.id == value }?.name.orEmpty() }) {
                account = it
                if (target == it) target = accounts.firstOrNull { a -> a.id != it }?.id.orEmpty()
            }
            if (type == "TRANSFER") Pick("To account", target, accounts.filter { it.id != account }.map { it.id }, { value -> accounts.find { it.id == value }?.name.orEmpty() }) { target = it }
            Field(payee, { payee = it }, "Payee or description")
            if (type != "TRANSFER") {
                Pick("Category", category, (defaultCategories + category + "Add category…").distinct(), { it }) {
                    if (it == "Add category…") customCategory = true else { category = it; customCategory = false }
                }
                if (customCategory) Field(category, { category = it }, "Custom category")
            }
            DateField(date, { date = it }, "Date")
            Field(time, { time = it }, "Time · HH:MM:SS, optional (IST)")
            Field(note, { note = it }, "Note, optional")
        }
        if (type != "TRANSFER") CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Text("Remember category", style = MaterialTheme.typography.bodyLarge); Text("Use this category for future alerts from this payee.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                PocketSwitch(rememberMerchant, "Remember category") { rememberMerchant = it }
            }
        }
        sms?.let { SectionLabel("Original bank message"); CardBlock { Text(it.sender, style = MaterialTheme.typography.titleSmall); Text(it.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        delete?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Delete transaction", color = rust) } }
    }
}

@Composable fun AccountEditor(a: Account, dismiss: () -> Unit, save: (Account) -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var bank by remember { mutableStateOf(a.bank) }
    var suffix by remember { mutableStateOf(a.suffix) }
    var opening by remember { mutableStateOf(amountText(a.opening)) }
    var date by remember { mutableStateOf(dayText(a.openingDate)) }
    var error by remember { mutableStateOf<String?>(null) }
    LedgerSheet("Account details", dismiss, "Save", {
        try {
            require(name.isNotBlank()) { "Enter an account name" }
            require(bank == "CASH" || suffix.matches(Regex("\\d{4}"))) { "Enter four account digits" }
            save(a.copy(name = name.trim(), bank = bank, suffix = if (bank == "CASH") "" else suffix, opening = paise(opening), openingDate = dayMillis(date)))
        } catch (e: Exception) { error = e.message }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { BankMark(bank); Column { Text(name.ifBlank { "A new account" }, style = MaterialTheme.typography.titleLarge); Text("A home for your everyday money", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        SectionLabel("Account")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it }, "Account name")
            Pick("Bank", bank, listOf("SBM", "AXIS", "CASH"), { when (it) { "SBM" -> "SBM Bank India"; "AXIS" -> "Axis Bank"; else -> "Cash" } }) { bank = it }
            if (bank != "CASH") Field(suffix, { suffix = it }, "Last four account digits")
        }
        SectionLabel("Starting point")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Field(opening, { opening = it }, "Opening balance (INR)", true); DateField(date, { date = it }, "Opening date") }
        Text("Use the balance just before this date. Choose an earlier opening date if you plan to import older transactions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable fun BudgetEditor(b: Budget, dismiss: () -> Unit, save: (Budget) -> Unit) {
    var category by remember { mutableStateOf(b.category) }
    var amount by remember { mutableStateOf(if (b.amount > 0) amountText(b.amount) else "") }
    var error by remember { mutableStateOf<String?>(null) }
    LedgerSheet("Category budget", dismiss, "Save", {
        try {
            val p = paise(amount)
            require(p >= 0 && category.isNotBlank()) { "Enter a category and a non-negative limit" }
            save(b.copy(category = category.trim(), amount = p))
        } catch (e: Exception) { error = e.message }
    }) {
        IconTile("budgets", MaterialTheme.colorScheme.tertiary, 56)
        Text("A little plan for ${YearMonth.parse(b.month).format(java.time.format.DateTimeFormatter.ofPattern("MMMM"))}.", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a category and give it a monthly limit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pick("Category", category, (defaultCategories + category).distinct(), { it }) { category = it }
            Field(category, { category = it }, "Category name")
            Field(amount, { amount = it }, "Monthly limit (INR)", true)
        }
        Text("Set the limit to 0 to remove this budget. Transfers are excluded from spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable fun PasswordDialog(mode: String, dismiss: () -> Unit, confirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LedgerSheet(if (mode == "export") "Save a backup" else "Restore a backup", dismiss, if (mode == "export") "Save" else "Restore", {
        if (password.length < 8) error = "Use at least 8 characters"
        else if (mode == "export" && password != repeat) error = "Passwords do not match"
        else { val chars = password.toCharArray(); password = ""; repeat = ""; confirm(chars) }
    }) {
        IconTile("lock", size = 56)
        Text(if (mode == "export") "Your money.\nYour peace of mind." else "Pick up where you left off.", style = MaterialTheme.typography.headlineMedium)
        Text(if (mode == "export") "Protect your backup with a password of at least 8 characters. Keep it somewhere safe; it cannot be recovered." else "Restoring replaces your current accounts, transactions, budgets, and import history. Automatic SMS capture will be switched off.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (mode == "export") TextField(repeat, { repeat = it }, label = { Text("Repeat password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
