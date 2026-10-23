package `in`.thomas.pocketledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
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
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable fun Field(value: String, change: (String) -> Unit, label: String, numeric: Boolean = false, imeAction: ImeAction = ImeAction.Next) {
    val focus = LocalFocusManager.current
    TextField(value, change, label = { Text(label, style = MaterialTheme.typography.bodySmall) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, shape = MaterialTheme.shapes.medium, colors = fieldColors(), textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { focus.clearFocus() }))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(value: String, change: (String) -> Unit, label: String, grouped: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    if (grouped) GroupedItem(onClick = { open = true }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(runCatching { displayDate(dayMillis(value)) }.getOrDefault(value), style = MaterialTheme.typography.bodyLarge)
        }
        AppIcon("calendar", Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
    } else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().ledgerClickable(role = Role.Button) { open = true }) {
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
    LedgerSheet(label, dismiss, closeLabel = "Cancel") {
        Column(Modifier.fillMaxWidth().padding(horizontal = LedgerSpace.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FluidList(Modifier.heightIn(max = 440.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                if (options.isEmpty()) item { Text("No options available", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(options.distinct(), key = { it }) { option ->
                    Column(Modifier.fillMaxWidth().ledgerClickable { change(option); dismiss() }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = LedgerSpace.margin, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(display(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (option == value) AppIcon("check", Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
                        }
                        if (option != options.lastOrNull()) HorizontalDivider(Modifier.padding(start = LedgerSpace.margin), thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable fun Pick(label: String, value: String, options: List<String>, display: (String) -> String = { it.lowercase().replaceFirstChar(Char::titlecase) }, grouped: Boolean = false, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (grouped) GroupedItem(onClick = { open = true }) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(display(value).ifBlank { "Choose" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        AppIcon("down", Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    } else Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().ledgerClickable { open = true }) {
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
    Row(Modifier.heightIn(min = LedgerSpace.touch).clip(MaterialTheme.shapes.medium).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface).ledgerClickable { open = true }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(display(value), style = MaterialTheme.typography.bodyMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        AppIcon("down", Modifier.size(11.dp), if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) ChoiceSheet(label, value, options, display, { open = false }, change)
}

/** Compact centered alert: title and message, hairline, then side-by-side text buttons. */
@Composable fun Confirm(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    val destructive = title.startsWith("Delete")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss) {
        Surface(Modifier.widthIn(max = 300.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
                Column(Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.height(IntrinsicSize.Min)) {
                    AlertButton("Cancel", MaterialTheme.colorScheme.primary, FontWeight.SemiBold, dismiss, Modifier.weight(1f))
                    VerticalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
                    AlertButton(if (destructive) "Delete" else "Confirm", if (destructive) rust else MaterialTheme.colorScheme.primary, FontWeight.Normal, {
                        if (destructive) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        confirm()
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable private fun AlertButton(label: String, color: Color, weight: FontWeight, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(modifier.heightIn(min = LedgerSpace.touch).premiumPressEffect(interaction, pressedScale = .97f)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = weight)
    }
}

@Composable fun EntryEditor(initial: Entry, accounts: List<Account>, sms: SmsRecord?, dismiss: () -> Unit, save: (Entry, Boolean) -> Unit, delete: (() -> Unit)?) {
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var account by rememberSaveable { mutableStateOf(initial.accountId) }
    var target by rememberSaveable { mutableStateOf(initial.destinationId ?: accounts.firstOrNull { it.id != account }?.id.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(if (initial.amount > 0) amountText(initial.amount) else "") }
    var date by rememberSaveable { mutableStateOf(dayText(initial.date)) }
    var time by rememberSaveable { mutableStateOf(if (initial.dateOnly) "" else Instant.ofEpochMilli(initial.date).atZone(india).toLocalTime().withNano(0).toString()) }
    var payee by rememberSaveable { mutableStateOf(initial.payee) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var rememberMerchant by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var customCategory by rememberSaveable { mutableStateOf(initial.category !in defaultCategories) }
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
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = fieldColors().copy(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        GroupedSection("Details") {
            Pick(if (type == "TRANSFER") "From account" else "Account", account, accounts.map { it.id }, { value -> accounts.find { it.id == value }?.name.orEmpty() }, grouped = true) {
                account = it
                if (target == it) target = accounts.firstOrNull { a -> a.id != it }?.id.orEmpty()
            }
            GroupedDivider()
            if (type == "TRANSFER") {
                Pick("To account", target, accounts.filter { it.id != account }.map { it.id }, { value -> accounts.find { it.id == value }?.name.orEmpty() }, grouped = true) { target = it }
                GroupedDivider()
            }
            GroupedField("Payee or description", payee, { payee = it })
            GroupedDivider()
            if (type != "TRANSFER") {
                Pick("Category", category, (defaultCategories + category + "Add category\u2026").distinct(), { it }, grouped = true) {
                    if (it == "Add category\u2026") customCategory = true else { category = it; customCategory = false }
                }
                GroupedDivider()
                if (customCategory) { GroupedField("Custom category", category, { category = it }); GroupedDivider() }
            }
            DateField(date, { date = it }, "Date", grouped = true)
            GroupedDivider()
            GroupedField("Time \u00b7 HH:MM:SS, optional (IST)", time, { time = it })
            GroupedDivider()
            GroupedField("Note, optional", note, { note = it }, imeAction = ImeAction.Done)
        }
        if (type != "TRANSFER") GroupedSection(footer = "Use this category for future alerts from this payee.") {
            GroupedRow("Remember category", trailing = { PocketSwitch(rememberMerchant, "Remember category") { rememberMerchant = it } })
        }
        sms?.let {
            GroupedSection("Original bank message") {
                GroupedItem { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(it.sender, style = MaterialTheme.typography.titleSmall); Text(it.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
        delete?.let {
            GroupedSection {
                GroupedItem(onClick = it) { Text("Delete transaction", color = rust, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable fun AccountEditor(a: Account, dismiss: () -> Unit, save: (Account) -> Unit) {
    var name by rememberSaveable { mutableStateOf(a.name) }
    var bank by rememberSaveable { mutableStateOf(a.bank) }
    var suffix by rememberSaveable { mutableStateOf(a.suffix) }
    var opening by rememberSaveable { mutableStateOf(amountText(a.opening)) }
    var date by rememberSaveable { mutableStateOf(dayText(a.openingDate)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    LedgerSheet("Account details", dismiss, "Save", {
        try {
            require(name.isNotBlank()) { "Enter an account name" }
            require(bank == "CASH" || suffix.matches(Regex("\\d{4}"))) { "Enter four account digits" }
            save(a.copy(name = name.trim(), bank = bank, suffix = if (bank == "CASH") "" else suffix, opening = paise(opening), openingDate = dayMillis(date)))
        } catch (e: Exception) { error = e.message }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { BankMark(bank); Column { Text(name.ifBlank { "A new account" }, style = MaterialTheme.typography.titleLarge); Text("A home for your everyday money", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        GroupedSection("Account") {
            GroupedField("Account name", name, { name = it })
            GroupedDivider()
            Pick("Bank", bank, listOf("SBM", "AXIS", "CASH"), { when (it) { "SBM" -> "SBM Bank India"; "AXIS" -> "Axis Bank"; else -> "Cash" } }, grouped = true) { bank = it }
            if (bank != "CASH") { GroupedDivider(); GroupedField("Last four account digits", suffix, { suffix = it }) }
        }
        GroupedSection("Starting point", footer = "Use the balance just before this date. Choose an earlier opening date if you plan to import older transactions.") {
            GroupedField("Opening balance (INR)", opening, { opening = it }, numeric = true)
            GroupedDivider()
            DateField(date, { date = it }, "Opening date", grouped = true)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable fun BudgetEditor(b: Budget, dismiss: () -> Unit, save: (Budget) -> Unit) {
    var category by rememberSaveable { mutableStateOf(b.category) }
    var amount by rememberSaveable { mutableStateOf(if (b.amount > 0) amountText(b.amount) else "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    LedgerSheet("Category budget", dismiss, "Save", {
        try {
            val p = paise(amount)
            require(p >= 0 && category.isNotBlank()) { "Enter a category and a non-negative limit" }
            save(b.copy(category = category.trim(), amount = p))
        } catch (e: Exception) { error = e.message }
    }) {
        IconTile("budgets", MaterialTheme.colorScheme.tertiary, 56)
        Text("A plan that carries forward.", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a category and set its limit for every month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GroupedSection(footer = "Set the limit to 0 to remove this budget. Transfers are excluded from spending.") {
            Pick("Category", category, (defaultCategories + category).distinct(), { it }, grouped = true) { category = it }
            GroupedDivider()
            GroupedField("Category name", category, { category = it })
            GroupedDivider()
            GroupedField("Monthly limit (INR)", amount, { amount = it }, numeric = true)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable fun PasswordDialog(mode: String, dismiss: () -> Unit, confirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    var error by remember { mutableStateOf<String?>(null) }
    LedgerSheet(if (mode == "export") "Save a backup" else "Restore a backup", dismiss, if (mode == "export") "Save" else "Restore", {
        if (password.length < 8) error = "Use at least 8 characters"
        else if (mode == "export" && password != repeat) error = "Passwords do not match"
        else { val chars = password.toCharArray(); password = ""; repeat = ""; confirm(chars) }
    }) {
        IconTile("lock", size = 56)
        Text(if (mode == "export") "Your money.\nYour peace of mind." else "Pick up where you left off.", style = MaterialTheme.typography.headlineMedium)
        Text(if (mode == "export") "Protect your backup with a password of at least 8 characters. Keep it somewhere safe; it cannot be recovered." else "Restoring replaces your current accounts, transactions, budgets, and import history. Automatic SMS capture will be switched off.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GroupedSection {
            GroupedField("Password", password, { password = it; error = null },
                imeAction = if (mode == "export") ImeAction.Next else ImeAction.Done, keyboardType = KeyboardType.Password,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), isError = error != null,
                trailing = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } })
            if (mode == "export") {
                GroupedDivider()
                GroupedField("Repeat password", repeat, { repeat = it; error = null }, imeAction = ImeAction.Done, keyboardType = KeyboardType.Password,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), isError = error != null)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
