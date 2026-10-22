package `in`.thomas.pocketledger

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*
import java.util.Locale
import java.util.UUID

fun money(v: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(BigDecimal.valueOf(v, 2))
fun amountText(v: Long) = BigDecimal.valueOf(v, 2).toPlainString()
fun dayText(v: Long) = Instant.ofEpochMilli(v).atZone(india).toLocalDate().toString()
fun dayMillis(v: String) = LocalDate.parse(v.trim()).atStartOfDay(india).toInstant().toEpochMilli()
fun inMonth(e: Entry, m: YearMonth) = YearMonth.from(Instant.ofEpochMilli(e.date).atZone(india)) == m

class MainActivity : ComponentActivity() {
    private var launchRequest by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchRequest = intent
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            PocketTheme {
                Ledger((application as LedgerApp).repo, launchRequest)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent
    }
}

private sealed interface LedgerRoute {
    data object Tabs : LedgerRoute
    data object Settings : LedgerRoute
    data class Transactions(val type: String, val category: String) : LedgerRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Ledger(repo: Repository, launchRequest: Intent? = null) {
    val dao = repo.dao
    val accounts by remember { dao.accounts() }.collectAsStateWithLifecycle(emptyList())
    val allEntries by remember { dao.entries() }.collectAsStateWithLifecycle(emptyList())
    val messages by remember { dao.messages() }.collectAsStateWithLifecycle(emptyList())
    val budgets by remember { dao.budgets() }.collectAsStateWithLifecycle(emptyList())
    val senders by remember { dao.senders() }.collectAsStateWithLifecycle(emptyList())
    val rules by remember { dao.rules() }.collectAsStateWithLifecycle(emptyList())
    val entries = allEntries.filter { !it.deleted }
    val scope = rememberCoroutineScope(); val snack = remember { SnackbarHostState() }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }; var month by remember { mutableStateOf(YearMonth.now(india)) }
    var editor by remember { mutableStateOf<Entry?>(null) }; var accountEditor by remember { mutableStateOf<Account?>(null) }
    var settings by remember { mutableStateOf(false) }; var disclosure by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<LedgerRoute.Transactions?>(null) }
    val openTransactions: (String, String) -> Unit = { type, category -> details = LedgerRoute.Transactions(type, category) }
    var capture by remember { mutableStateOf(repo.prefs.getBoolean("enabled", false)) }
    var permission by remember { mutableStateOf(repo.hasSmsPermission()) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<Candidate>?>(null) }
    var from by remember { mutableStateOf(LocalDate.now(india).withDayOfMonth(1).toString()) }
    var to by remember { mutableStateOf(LocalDate.now(india).toString()) }
    var passwordMode by remember { mutableStateOf<String?>(null) }; var secret by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var trustCandidate by remember { mutableStateOf<TrustedSender?>(null) }
    var deleteCandidate by remember { mutableStateOf<Entry?>(null) }
    var transferCandidate by remember { mutableStateOf<Pair<Entry, Entry>?>(null) }
    fun work(block: suspend () -> Unit) { scope.launch { busy = true; try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { if (e is CancellationException) throw e; snack.showSnackbar(e.message ?: "Could not complete this action") } finally { busy = false } } }
    val requestSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission = repo.hasSmsPermission() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = secret; secret = null
        if (uri != null && pass != null) work {
            try {
                val bytes = Backup.export(repo.db, pass)
                repo.context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Cannot write this file")
                withContext(Dispatchers.Main) { snack.showSnackbar("Encrypted backup saved") }
            } finally { pass.fill('\u0000') }
        } else pass?.fill('\u0000')
    }
    val chooseRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { restoreUri = uri; passwordMode = "restore" } }
    LaunchedEffect(Unit) { work { repo.initialize(); repo.refreshStoredReviews(); repo.reconcile() } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { permission = repo.hasSmsPermission(); capture = repo.prefs.getBoolean("enabled", false); work { repo.reconcile() } } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(launchRequest) {
        launchRequest?.getStringExtra("ledger_route")?.let { destination ->
            settings = false
            details = null
            month = YearMonth.now(india)
            tab = if (destination == "budget") 3 else 0
            if (destination == "income") openTransactions("INCOME", "ALL")
            if (destination == "expense") openTransactions("EXPENSE", "ALL")
        }
    }
    val route: LedgerRoute = if (settings) LedgerRoute.Settings else details ?: LedgerRoute.Tabs
    BackHandler(route != LedgerRoute.Tabs) { if (settings) settings = false else details = null }
    val density = LocalDensity.current
    val collapseRange = with(density) { 56.dp.toPx() }
    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val titleOffsets = remember { mutableStateMapOf<String, Float>() }
    val routeKey = if (route == LedgerRoute.Tabs) "tab-$tab" else route.toString()
    val collapseOffset = titleOffsets[routeKey] ?: 0f
    val titleScroll = remember(routeKey, collapseRange) { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            titleOffsets[routeKey] = ((titleOffsets[routeKey] ?: 0f) - consumed.y).coerceIn(0f, collapseRange)
            return Offset.Zero
        }
    } }
    val titles = listOf("Overview", "Activity", "Accounts", "Budgets", "Inbox", "Graphs")
    Scaffold(
        topBar = {
            PocketHeader(
                title = when (val current = route) {
                    LedgerRoute.Settings -> "Settings"
                    LedgerRoute.Tabs -> titles[tab]
                    is LedgerRoute.Transactions -> if (current.category != "ALL") current.category else if (current.type == "INCOME") "Income" else "Spent"
                },
                collapse = collapseOffset / collapseRange,
                back = if (route != LedgerRoute.Tabs) ({ if (settings) settings = false else details = null }) else null,
                settings = { settings = true },
                add = if (!settings && (tab <= 1 || details != null)) ({
                    accounts.firstOrNull()?.let {
                        editor = Entry(UUID.randomUUID().toString(), it.id, type = details?.type ?: "EXPENSE", category = details?.category?.takeUnless { category -> category == "ALL" } ?: "Uncategorized", amount = 0, date = System.currentTimeMillis())
                    }
                }) else null
            )
        },
        bottomBar = { if (route == LedgerRoute.Tabs) PocketTabBar(tab, messages.count { it.status == "REVIEW" }) { tab = it } },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().nestedScroll(titleScroll)) {
            AnimatedContent(targetState = route, transitionSpec = {
                if (targetState == LedgerRoute.Settings || initialState == LedgerRoute.Tabs) {
                    (slideInHorizontally(fluidSpring()) { it } + fadeIn(fluidTween())) togetherWith
                        slideOutHorizontally(fluidSpring()) { -it / 3 }
                } else {
                    slideInHorizontally(fluidSpring()) { -it / 3 } togetherWith
                        (slideOutHorizontally(fluidSpring()) { it } + fadeOut(fluidTween()))
                }
            }, label = "navigation stack", modifier = Modifier.fillMaxSize()) { shownRoute ->
            pageState.SaveableStateProvider(shownRoute.toString()) {
            if (shownRoute == LedgerRoute.Settings) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SettingsDialog(capture, permission, senders, rules, { settings = false }, { passwordMode = "export" }, { chooseRestore.launch(arrayOf("*/*")) }, { work { dao.untrust(it.sender, it.bank) } }, { work { dao.removeRule(it.merchant) } }, { repo.context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${repo.context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                }
            } else if (shownRoute is LedgerRoute.Transactions) {
                Column(Modifier.fillMaxSize()) {
                    MonthPicker(month) { month = it }
                    Transactions(entries, accounts, month, shownRoute.type, shownRoute.category) { editor = it }
                }
            } else Column(Modifier.fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (tab in listOf(0, 1, 3, 5)) MonthPicker(month) { month = it }
            Box(Modifier.weight(1f)) {
                val page = tab
                pageState.SaveableStateProvider("tab-$page") {
                when (page) {
                    0 -> Overview(accounts, entries, messages, month, budgets, { tab = 2 }, { tab = 4 }, openTransactions) { editor = it }
                    1 -> Transactions(entries, accounts, month) { editor = it }
                    2 -> AccountsScreen(accounts, entries, messages, { accountEditor = it }, { accountEditor = Account(UUID.randomUUID().toString(), "", "CASH", "", openingDate = dayMillis(LocalDate.now(india).withDayOfMonth(1).toString())) })
                    3 -> BudgetScreen(budgets, entries, month) { work { dao.budget(it) } }
                    5 -> GraphsScreen(entries, month, { month = it }, openTransactions)
                    4 -> SmsScreen(messages, entries, accounts, senders, permission, capture, from, to, { from = it }, { to = it },
                        { disclosure = true }, { enabled ->
                            if (enabled && !permission) disclosure = true else {
                                capture = enabled; repo.prefs.edit().putBoolean("enabled", enabled).putLong("from", System.currentTimeMillis()).apply()
                            }
                        }, { work { val f = dayMillis(from); val t = dayMillis(to) + 86_400_000L; require(f < t) { "Start date must precede end date" }; val list = repo.scan(f, t); withContext(Dispatchers.Main) { preview = list } } },
                        { trustCandidate = it }, { record ->
                            val a = accounts.firstOrNull { it.bank == record.bank && it.suffix == record.suffix } ?: accounts.firstOrNull()
                            a?.let { editor = Entry(UUID.randomUUID().toString(), it.id, type = record.direction ?: "EXPENSE", amount = record.amount ?: 0, date = record.date ?: record.received, dateOnly = record.dateOnly, payee = record.payee, smsKey = record.key) }
                        }, { work { repo.dismiss(it) } }, { transferCandidate = it })
                }
            }
        }
    }
    } } } }
    FluidPresentation(editor) { current -> EntryEditor(current, accounts, messages.find { it.key == current.smsKey }, { editor = null }, { entry, remember -> work { repo.save(entry, remember); withContext(Dispatchers.Main) { editor = null } } }, if (allEntries.any { it.id == current.id }) { { deleteCandidate = current } } else null) }
    FluidPresentation(accountEditor) { a -> AccountEditor(a, { accountEditor = null }) { edited -> work {
        require(edited.name.isNotBlank()) { "Name is required" }; require(edited.bank == "CASH" || edited.suffix.matches(Regex("\\d{4}"))) { "Enter the account's last four digits" }
        require(accounts.none { it.id != edited.id && it.bank == edited.bank && it.suffix == edited.suffix && edited.bank != "CASH" }) { "This account is already mapped" }
        require(entries.none { (it.accountId == edited.id || it.destinationId == edited.id) && it.date < edited.openingDate }) { "Opening date cannot be after an existing transaction" }
        dao.account(edited); withContext(Dispatchers.Main) { accountEditor = null }
    } } }
    deleteCandidate?.let { e -> Confirm("Delete transaction?", "This entry will leave your balances and reports. Its source SMS will stay dismissed during future scans.", { deleteCandidate = null }) { work { repo.delete(e); withContext(Dispatchers.Main) { deleteCandidate = null; editor = null } } } }
    transferCandidate?.let { pair -> Confirm("Link as a transfer?", "Combine these two entries into one transfer between your accounts. It will be excluded from income and spending.", { transferCandidate = null }) { work { repo.linkTransfer(pair.first, pair.second); withContext(Dispatchers.Main) { transferCandidate = null } } } }
    trustCandidate?.let { t -> Confirm("Trust ${t.sender}?", "Confirm this is your ${t.bank} transaction sender. Complete messages for mapped accounts will be saved, including reviewed historical messages. Sender IDs alone cannot prove authenticity.", { trustCandidate = null }) { work { repo.trust(t.sender, t.bank); withContext(Dispatchers.Main) { trustCandidate = null } } } }
    if (disclosure) SmsPermissionSheet(
        dismiss = { disclosure = false },
        proceed = { disclosure = false; requestSms.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }
    )
    FluidPresentation(preview) { candidates ->
        val knownKeys = messages.flatMap { listOf(it.key, SmsParser.key(it.parsed(), it.sender, it.body, it.received)) }.toSet()
        ImportPreview(candidates, knownKeys, accounts, { preview = null }) { selected -> work { repo.ingest(selected, forceReview = true); repo.processReview(); withContext(Dispatchers.Main) { preview = null; snack.showSnackbar("Imported ${selected.size} selected messages. Check the review inbox.") } } }
    }

    FluidPresentation(passwordMode) { mode -> PasswordDialog(mode, { passwordMode = null; restoreUri = null }) { pass ->
        passwordMode = null
        if (mode == "export") { secret = pass; export.launch("Ledger-${LocalDate.now(india)}.plbackup") }
        else {
            val uri = restoreUri; restoreUri = null
            if (uri != null) work { try {
                val bytes = repo.context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 33_554_432) { "Backup is too large" }; output.write(buffer, 0, n) }
                    output.toByteArray()
                } ?: error("Cannot read this file")
                Backup.restore(repo, bytes, pass)
                withContext(Dispatchers.Main) { capture = false; settings = false; snack.showSnackbar("Backup restored. Review accounts before re-enabling SMS capture.") }
            } finally { pass.fill('\u0000') } }
        }
    } }
}

