package `in`.thomas.pocketledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*

/** User-facing validation failure; its message is safe to show as is. */
private class InvalidInput(message: String) : Exception(message)
private fun need(ok: Boolean, message: String) { if (!ok) throw InvalidInput(message) }

private val amountPattern = Regex("^\\d{1,12}(\\.\\d{1,2})?$")
private val signedAmountPattern = Regex("^-?\\d{1,12}(\\.\\d{1,2})?$")
private const val AmountHint = "Enter an amount like 250 or 250.50"

/** Returns paise for plain rupee input (up to 12 digits, at most two decimals), or null when it is malformed. */
internal fun parseAmountInput(text: String, allowNegative: Boolean = false): Long? {
    val cleaned = text.trim().removePrefix("\u20b9").trim().replace(",", "")
    if (!(if (allowNegative) signedAmountPattern else amountPattern).matches(cleaned)) return null
    return paise(cleaned)
}

private fun parseDay(text: String, message: String): LocalDate = try { LocalDate.parse(text.trim()) } catch (_: Exception) { throw InvalidInput(message) }
private fun validationMessage(e: Exception, fallback: String) = if (e is InvalidInput) e.message ?: fallback else fallback

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
                if (options.isEmpty()) item(key = "empty", contentType = "empty") { Text("No options available", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(options.distinct(), key = { it }, contentType = { "option" }) { option ->
                    Column(Modifier.fillMaxWidth().semantics { selected = option == value }
                        .ledgerClickable(role = Role.RadioButton) { change(option); dismiss() }) {
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

/** Category-only presentation; other choice sheets retain their existing controls. */
@Composable private fun CategoryChoiceSheet(value: String, options: List<String>, display: (String) -> String,
    dismiss: () -> Unit, addCategory: (() -> Unit)?, change: (String) -> Unit) {
    val choices = options.filter { it.isNotBlank() }.distinct()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = choices.indexOf(value).coerceAtLeast(0))
    LaunchedEffect(choices, value) {
        listState.scrollToItem(choices.indexOf(value).coerceAtLeast(0))
    }
    LedgerSheet("Category", dismiss, closeLabel = "Close", scrollContent = false, showAction = false,
        contentPadding = LedgerSpace.margin, contentScroll = listState) {
        // One viewport owns scrolling; no fixed-height list nested inside a scrolling form.
        GroupedSection(modifier = Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (choices.isEmpty()) item(key = "empty", contentType = "empty") {
                    GroupedItem { Text("No categories available", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(choices, key = { it }, contentType = { "category" }) { option ->
                    Column {
                        GroupedItem(modifier = Modifier.semantics { selected = option == value }
                            .ledgerClickable(role = Role.RadioButton) { change(option); dismiss() }) {
                            Box(Modifier.size(32.dp).clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                AppIcon(categoryIconName(option), Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                            }
                            Text(display(option), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (option == value) AppIcon("check", Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (option != choices.last()) GroupedDivider(inset = LedgerSpace.margin + 32.dp + LedgerSpace.medium)
                    }
                }
            }
        }
        addCategory?.let { add ->
            GroupedSection {
                GroupedRow("Add category\u2026", titleColor = MaterialTheme.colorScheme.primary,
                    leading = { Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        AppIcon("plus", Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                    } }, onClick = { dismiss(); add() })
            }
        }
    }
}

@Composable fun Pick(label: String, value: String, options: List<String>, display: (String) -> String = { it.lowercase().replaceFirstChar(Char::titlecase) }, categoryPresentation: Boolean = false, addCategory: (() -> Unit)? = null, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    GroupedItem(onClick = { open = true }) {
        if (largeText) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LedgerSpace.xs)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(display(value).ifBlank { "Choose" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        } else {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(display(value).ifBlank { "Choose" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        AppIcon("down", Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        if (categoryPresentation) CategoryChoiceSheet(value, options, display, { open = false }, addCategory, change)
        else ChoiceSheet(label, value, options, display, { open = false }, change)
    }
}

@Composable fun ChoiceChip(label: String, value: String, options: List<String>, display: (String) -> String, categoryPresentation: Boolean = false, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = value != "ALL"
    Row(Modifier.heightIn(min = LedgerSpace.touch).clip(MaterialTheme.shapes.medium).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface).ledgerClickable { open = true }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(display(value), style = MaterialTheme.typography.bodyMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        AppIcon("down", Modifier.size(11.dp), if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        if (categoryPresentation) CategoryChoiceSheet(value, options, display, { open = false }, null, change)
        else ChoiceSheet(label, value, options, display, { open = false }, change)
    }
}

/**
 * Compact centered alert: title and message, hairline, then side-by-side text buttons. A [destructive]
 * confirmation is red and gives haptic feedback; the message scrolls when large text would push the buttons off screen.
 */
@Composable fun Confirm(
    title: String, text: String, dismiss: () -> Unit, destructive: Boolean = false,
    confirmLabel: String = if (destructive) "Delete" else "Confirm", confirm: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss) {
        Surface(Modifier.widthIn(max = 300.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = LedgerSpace.margin),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(LedgerSpace.xs)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.height(IntrinsicSize.Min)) {
                    AlertButton("Cancel", MaterialTheme.colorScheme.primary, FontWeight.SemiBold, dismiss, Modifier.weight(1f))
                    VerticalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
                    AlertButton(confirmLabel, if (destructive) danger else MaterialTheme.colorScheme.primary, FontWeight.Normal, {
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

@Composable fun EntryEditor(initial: Entry, accounts: List<Account>, sms: SmsRecord?, dismiss: () -> Unit, save: (Entry, Boolean) -> Unit, delete: (() -> Unit)?, knownEntries: List<Entry> = emptyList()) {
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var account by rememberSaveable { mutableStateOf(initial.accountId) }
    var target by rememberSaveable { mutableStateOf(initial.destinationId ?: accounts.firstOrNull { it.id != account }?.id.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(if (initial.amount > 0) amountText(initial.amount) else "") }
    var date by rememberSaveable { mutableStateOf(dayText(initial.date)) }
    var time by rememberSaveable { mutableStateOf(if (initial.dateOnly) "" else Instant.ofEpochMilli(initial.date).atZone(india).toLocalTime().withNano(0).toString()) }
    var payee by rememberSaveable { mutableStateOf(initial.payee) }
    var showSource by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val expenseCategory = rememberSaveable { mutableStateOf(if (initial.type == "EXPENSE") initial.category else "Uncategorized") }
    val incomeCategory = rememberSaveable { mutableStateOf(if (initial.type == "INCOME") initial.category else "Uncategorized") }
    var category by if (type == "INCOME") incomeCategory else expenseCategory
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var rememberMerchant by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val expenseCustom = rememberSaveable { mutableStateOf(initial.type == "EXPENSE" && initial.category !in expenseCategories) }
    val incomeCustom = rememberSaveable { mutableStateOf(initial.type == "INCOME" && initial.category !in incomeCategories) }
    var customCategory by if (type == "INCOME") incomeCustom else expenseCustom
    val savedCategories by backgroundCalculation(type, knownEntries, cacheKey = "editor-categories-$type") { categoryOptions(type, knownEntries) }
    val categories = savedCategories ?: categoriesForType(type)
    fun submit() {
        val entry = try {
            val p = parseAmountInput(amount) ?: throw InvalidInput(AmountHint)
            need(p > 0, "Enter an amount above zero")
            val d = parseDay(date, "Choose a valid date")
            val clock = if (time.isBlank()) null else try { LocalTime.parse(time.trim()) } catch (_: Exception) { throw InvalidInput("Enter the time as HH:MM or HH:MM:SS, or leave it blank") }
            val millis = if (clock == null) d.atStartOfDay(india).toInstant().toEpochMilli() else d.atTime(clock).atZone(india).toInstant().toEpochMilli()
            need(type != "TRANSFER" || target.isNotBlank() && target != account, "Choose a different destination account")
            val source = accounts.firstOrNull { it.id == account } ?: throw InvalidInput("Choose an account")
            need(millis >= source.openingDate, "Date is before account opening date. Adjust the account first.")
            if (type == "TRANSFER") {
                val destination = accounts.firstOrNull { it.id == target } ?: throw InvalidInput("Choose a destination account")
                need(millis >= destination.openingDate, "Date is before destination account opening date")
            }
            need(category.isNotBlank() || type == "TRANSFER", "Enter a category")
            initial.copy(accountId = account, destinationId = if (type == "TRANSFER") target else null, type = type, amount = p, date = millis, dateOnly = clock == null, payee = payee.trim(), category = if (type == "TRANSFER") "Transfer" else category.trim(), note = note)
        } catch (e: Exception) { error = validationMessage(e, "Check the amount, date and time"); return }
        save(entry, rememberMerchant)
    }
    LedgerSheet(if (delete == null) "New transaction" else "Transaction", dismiss, "Save", { submit() }) {
        Segments(listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer"), type) { type = it }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // The amount keeps a prominent size at normal scale but steps down at large font scales so it still fits.
            val amountStyle = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge
            Text("AMOUNT IN INR", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextField(amount, { amount = it; error = null }, placeholder = { Text("0.00", style = amountStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)) }, prefix = { Text("\u20b9 ", style = amountStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }, singleLine = true,
                textStyle = amountStyle.copy(fontWeight = FontWeight.Medium), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = fieldColors().copy(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        GroupedSection("Details") {
            Pick(if (type == "TRANSFER") "From account" else "Account", account, accounts.map { it.id }, { value -> accounts.find { it.id == value }?.name.orEmpty() }) {
                account = it
                if (target == it) target = accounts.firstOrNull { a -> a.id != it }?.id.orEmpty()
            }
            GroupedDivider()
            if (type == "TRANSFER") {
                Pick("To account", target, accounts.filter { it.id != account }.map { it.id }, { value -> accounts.find { it.id == value }?.name.orEmpty() }) { target = it }
                GroupedDivider()
            }
            GroupedField("Payee or description", payee, { payee = it })
            GroupedDivider()
            if (type != "TRANSFER") {
                Pick("Category", category, (categories + category).filter { it.isNotBlank() }.distinct(), { it },
                    categoryPresentation = true, addCategory = { customCategory = true }) {
                    category = it; customCategory = false
                }
                GroupedDivider()
                if (customCategory) { GroupedField("Custom category", category, { category = it }); GroupedDivider() }
            }
            DateField(date, { date = it }, "Date", grouped = true)
            GroupedDivider()
            GroupedRow("Details", subtitle = "Optional time and note", onClick = { showDetails = !showDetails })
            if (showDetails) {
            GroupedField("Time \u00b7 HH:MM:SS, optional (IST)", time, { time = it })
            GroupedDivider()
            GroupedField("Note, optional", note, { note = it }, imeAction = ImeAction.Done)
            }
        }
        if (type != "TRANSFER") GroupedSection(footer = "Use this category for future ${if (type == "INCOME") "income" else "spending"} alerts from this payee.") {
            GroupedRow("Remember category", trailing = { PocketSwitch(rememberMerchant, "Remember category") { rememberMerchant = it } })
        }
        sms?.takeUnless { it.bodyPurged() }?.let {
            GroupedSection("Original bank message") {
                GroupedRow(if (showSource) "Hide original message" else "View original message", onClick = { showSource = !showSource })
                if (showSource) GroupedItem { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(it.sender, style = MaterialTheme.typography.titleSmall); Text(it.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
        delete?.let {
            GroupedSection {
                GroupedItem(onClick = it) { Text("Delete transaction", color = danger, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
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
        val edited = try {
            need(name.isNotBlank(), "Enter an account name")
            need(bank == "CASH" || suffix.matches(Regex("\\d{4}")), "Enter four account digits")
            val openingPaise = parseAmountInput(opening, allowNegative = true) ?: throw InvalidInput(AmountHint)
            val openingDate = parseDay(date, "Choose a valid opening date")
            a.copy(name = name.trim(), bank = bank, suffix = if (bank == "CASH") "" else suffix, opening = openingPaise, openingDate = openingDate.atStartOfDay(india).toInstant().toEpochMilli())
        } catch (e: Exception) { error = validationMessage(e, "Check the details and try again"); null }
        if (edited != null) save(edited)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { BankMark(bank); Column { Text(name.ifBlank { "A new account" }, style = MaterialTheme.typography.titleLarge); Text("A home for your everyday money", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        GroupedSection("Account") {
            GroupedField("Account name", name, { name = it })
            GroupedDivider()
            Pick("Bank", bank, listOf("SBM", "AXIS", "CASH"), { when (it) { "SBM" -> "SBM Bank India"; "AXIS" -> "Axis Bank"; else -> "Cash" } }) { bank = it }
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

@Composable fun BudgetEditor(b: Budget, dismiss: () -> Unit, knownEntries: List<Entry> = emptyList(), existing: List<Budget> = emptyList(), originalCategory: String? = null, remove: (() -> Unit)? = null, save: (Budget) -> Unit) {
    var category by rememberSaveable { mutableStateOf(b.category) }
    var amount by rememberSaveable { mutableStateOf(if (b.amount > 0) amountText(b.amount) else "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // Pending (new, old) limits waiting for the user to confirm that an existing category limit is replaced.
    var replacing by remember { mutableStateOf<Pair<Budget, Budget>?>(null) }
    val savedCategories by backgroundCalculation(knownEntries, cacheKey = "budget-category-options") { categoryOptions("EXPENSE", knownEntries) }
    // Another category that already has a limit would be overwritten by this save.
    val clash = existing.firstOrNull { it.amount > 0 && it.category == category.trim() && it.category != originalCategory }
    LedgerSheet("Category budget", dismiss, "Save", {
        val budget = try {
            val p = parseAmountInput(amount) ?: throw InvalidInput(AmountHint)
            need(p > 0 && category.isNotBlank(), "Enter a category and a positive limit")
            b.copy(category = category.trim(), amount = p)
        } catch (e: Exception) { error = validationMessage(e, "Enter a category and a positive limit"); null }
        if (budget != null) {
            val old = existing.firstOrNull { it.amount > 0 && it.category == budget.category && it.category != originalCategory }
            if (old != null) replacing = budget to old else save(budget)
        }
    }) {
        IconTile("budgets", MaterialTheme.colorScheme.tertiary, 56)
        Text("A plan that carries forward.", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a category and set its limit for every month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GroupedSection(footer = "Transfers are excluded from spending. Removing a limit preserves transactions.") {
            Pick("Category", category, ((savedCategories ?: expenseCategories) + category).distinct(), { it }, categoryPresentation = true) { category = it }
            GroupedDivider()
            GroupedField("Category name", category, { category = it })
            GroupedDivider()
            GroupedField("Monthly limit (INR)", amount, { amount = it }, numeric = true)
        }
        clash?.let { Text("${it.category} already has a limit of ${money(it.amount)}. Saving replaces it.", style = MaterialTheme.typography.bodyMedium, color = warning) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        remove?.let { action -> GroupedSection { GroupedItem(onClick = action) { Text("Remove budget", color = danger, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } } }
    }
    replacing?.let { (new, old) ->
        Confirm("Replace existing limit?", "${old.category} already has a limit of ${money(old.amount)} a month. Saving replaces it with ${money(new.amount)}.",
            { replacing = null }, confirmLabel = "Replace") { replacing = null; save(new) }
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

@Composable fun CategoryReviewSheet(entry: Entry, entries: List<Entry>, dismiss: () -> Unit, save: (String, Boolean) -> Unit) {
    var category by rememberSaveable(entry.id) { mutableStateOf(entry.category) }
    var rememberPayee by rememberSaveable(entry.id) { mutableStateOf(false) }
    val options by backgroundCalculation(entries, entry.type, cacheKey = "review-category-options") { categoryOptions(entry.type, entries) }
    LedgerSheet("Choose category", dismiss, "Save", { save(category, rememberPayee) }) {
        Text(entry.payee.ifBlank { "Transaction" }, style = MaterialTheme.typography.titleMedium)
        Text(money(entry.amount), style = MaterialTheme.typography.headlineMedium)
        GroupedSection {
            Pick("Category", category, (options ?: categoriesForType(entry.type)) + category, { it }, categoryPresentation = true) { category = it }
            GroupedField("Category name", category, { category = it })
            GroupedRow("Remember for this payee", trailing = { PocketSwitch(rememberPayee, "Remember for this payee") { rememberPayee = it } })
        }
    }
}
