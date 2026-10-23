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
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.collect
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
    private var frameTracker: LedgerFrameTracker? = null
    private var launchRequest by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        launchRequest = intent
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            PocketTheme {
                FoldAwareWindow { Ledger((application as LedgerApp).repo, launchRequest) }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    if (frameTracker == null) frameTracker = LedgerFrameTracker(window)
                    frameTracker?.resume()
                }
            }
        }
    }
    override fun onPause() { frameTracker?.pause(); super.onPause() }
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
    var readAttempt by remember { mutableIntStateOf(0) }
    val readState by remember(dao, readAttempt) { ledgerSnapshots(dao) }.collectAsStateWithLifecycle(LedgerReadState())
    androidx.activity.compose.ReportDrawnWhen { readState.data != null }
    val snapshot = readState.data
    val accounts = snapshot?.accounts.orEmpty()
    val allEntries = snapshot?.allEntries.orEmpty()
    val entries = snapshot?.entries.orEmpty()
    val messages = snapshot?.messages.orEmpty()
    val budgets = snapshot?.budgets.orEmpty()
    val senders = snapshot?.senders.orEmpty()
    val rules = snapshot?.rules.orEmpty()
    val scope = rememberCoroutineScope(); val snack = remember { SnackbarHostState() }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }; var month by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })) { mutableStateOf(YearMonth.now(india)) }
    var editor by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = EntryDraftSaver) { mutableStateOf<Entry?>(null) }; var accountEditor by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = AccountDraftSaver) { mutableStateOf<Account?>(null) }
    var settings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }; var disclosure by remember { mutableStateOf(false) }
    var details by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<LedgerRoute.Transactions?, ArrayList<String>>(
            save = { it?.let { route -> arrayListOf(route.type, route.category) } ?: arrayListOf() },
            restore = { if (it.isEmpty()) null else LedgerRoute.Transactions(it[0], it[1]) }
        )
    ) { mutableStateOf<LedgerRoute.Transactions?>(null) }
    val openTransactions: (String, String) -> Unit = { type, category -> details = LedgerRoute.Transactions(type, category) }
    var capture by remember { mutableStateOf(repo.prefs.getBoolean("enabled", false)) }
    var permission by remember { mutableStateOf(repo.hasSmsPermission()) }
    var activeWork by remember { mutableIntStateOf(0) }
    val busy = activeWork > 0
    var operationError by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<Candidate>?>(null) }
    var from by remember { mutableStateOf(LocalDate.now(india).withDayOfMonth(1).toString()) }
    var to by remember { mutableStateOf(LocalDate.now(india).toString()) }
    var passwordMode by remember { mutableStateOf<String?>(null) }; var secret by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var trustCandidate by remember { mutableStateOf<TrustedSender?>(null) }
    var deleteCandidate by remember { mutableStateOf<Entry?>(null) }
    var transferCandidate by remember { mutableStateOf<Pair<Entry, Entry>?>(null) }
    fun work(block: suspend () -> Unit) { scope.launch { activeWork++; operationError = null; try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { if (e is CancellationException) throw e; operationError = e.message ?: "Could not complete this action"; snack.showSnackbar(operationError!!) } finally { activeWork-- } } }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(capture) {
        if (capture && android.os.Build.VERSION.SDK_INT >= 33 &&
            repo.context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !repo.prefs.getBoolean("notification_permission_requested", false)) {
            repo.prefs.edit().putBoolean("notification_permission_requested", true).apply()
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
    LaunchedEffect(Unit) {
        // Let the usable shell render before starting reconciliation.
        withFrameNanos { }
        work { repo.initialize(); repo.refreshStoredReviews(); repo.reconcile() }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { permission = repo.hasSmsPermission(); capture = repo.prefs.getBoolean("enabled", false); if (firstResume) firstResume = false else work { repo.reconcile() } } }
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
    LaunchedEffect(launchRequest, snapshot != null) {
        val id = launchRequest?.getStringExtra(CategoryNotifications.EXTRA_ENTRY_ID)
        if (id != null && snapshot != null) {
            settings = false
            details = null
            tab = 1
            entries.singleOrNull { it.id == id }?.let { editor = it }
            CategoryNotifications.cancel(repo.context, id)
            launchRequest.removeExtra(CategoryNotifications.EXTRA_ENTRY_ID)
        }
    }
    val route: LedgerRoute = if (settings) LedgerRoute.Settings else details ?: LedgerRoute.Tabs
    val reducedMotion = LocalReducedMotion.current
    val seekState = remember { SeekableTransitionState<LedgerRoute>(route) }
    val navigation = rememberTransition(seekState, label = "ledger navigation")
    var predictiveBackInProgress by remember { mutableStateOf(false) }
    // One owner settles both ordinary navigation and completed/cancelled back gestures.
    // A gesture only seeks; it must not race another coroutine finishing the same transition.
    LaunchedEffect(route, predictiveBackInProgress, reducedMotion) {
        if (!predictiveBackInProgress) {
            if (!reducedMotion) seekState.animateTo(route, tween(LedgerNavigationDurationMillis, easing = LinearEasing))
            seekState.snapTo(route)
        }
    }
    val backTarget: LedgerRoute = if (settings) details ?: LedgerRoute.Tabs else LedgerRoute.Tabs
    PredictiveBackHandler(enabled = route != LedgerRoute.Tabs) { events ->
        predictiveBackInProgress = true
        try {
            events.collect { event ->
                if (!reducedMotion) seekState.seekTo(event.progress, backTarget)
            }
            if (settings) settings = false else details = null
        } finally {
            // On cancellation the route stays unchanged, so the settling effect restores it.
            predictiveBackInProgress = false
        }
    }
    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val titles = destinationLabels
    // Keep the shared card data ready while detail pages are open, including during back previews.
    val overviewReport by backgroundCalculation(entries, budgets, month) { calculateOverviewReport(entries, budgets, month) }
    val pending = remember(messages) { messages.count { it.status == "REVIEW" } }
    val expandedEditor = LocalLedgerWindowWidth.current >= 840.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f
    val editorContent by rememberUpdatedState<@Composable (Entry) -> Unit> { current -> EntryEditor(current, accounts, messages.find { it.key == current.smsKey }, { editor = null }, { entry, remember -> work { repo.save(entry, remember); withContext(Dispatchers.Main) { editor = null } } }, if (allEntries.any { it.id == current.id }) { { deleteCandidate = current } } else null) }
    val presentEntry = remember { movableContentOf<Entry> { current -> key(current.id) { editorContent(current) } } }
    CompositionLocalProvider(LocalLedgerBusy provides busy, LocalLedgerOperationError provides operationError, LocalLedgerClearError provides { operationError = null }) {
    SharedTransitionLayout {
    CompositionLocalProvider(LocalLedgerSharedScope provides this) {
    BoxWithConstraints(Modifier.fillMaxSize().motionSurface(active = navigation.isRunning)) {
    val wide = maxWidth >= 600.dp
    Row(Modifier.fillMaxSize()) {
    navigation.AnimatedContent(
        transitionSpec = {
            val forward = targetState == LedgerRoute.Settings || initialState == LedgerRoute.Tabs
            val metricNavigation = tab == 0 && (
                (initialState == LedgerRoute.Tabs && (targetState as? LedgerRoute.Transactions)?.category == "ALL") ||
                    (targetState == LedgerRoute.Tabs && (initialState as? LedgerRoute.Transactions)?.category == "ALL")
                )
            val transform = if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (metricNavigation) {
                // Only the shared Income/Spent card travels. Overview remains stationary
                // underneath the detail page, which reveals itself around the moving card.
                if (forward) fadeIn(ledgerNavigationTween()) togetherWith ExitTransition.None
                else EnterTransition.None togetherWith fadeOut(ledgerNavigationTween())
            } else if (forward) {
                slideInVertically(ledgerNavigationTween()) { it } togetherWith
                    slideOutVertically(ledgerNavigationTween()) { -it / 3 }
            } else {
                slideInVertically(ledgerNavigationTween()) { -it / 3 } togetherWith
                    slideOutVertically(ledgerNavigationTween()) { it }
            }
            // Keep the detail page above its parent while returning or seeking back.
            transform.apply { targetContentZIndex = if (forward) 1f else 0f }.using(null)
        },
        modifier = Modifier.weight(1f).fillMaxHeight()
    ) { shownRoute ->
    CompositionLocalProvider(LocalLedgerVisibilityScope provides this) {
    pageState.SaveableStateProvider(shownRoute.toString()) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (wide && shownRoute == LedgerRoute.Tabs) PocketNavigationRail(tab, pending, { tab = it }, { settings = true })
    Scaffold(
        modifier = Modifier.weight(1f),
        topBar = {
            PocketHeader(
                title = when (val current = shownRoute) {
                    LedgerRoute.Settings -> "Settings"
                    LedgerRoute.Tabs -> titles[tab]
                    is LedgerRoute.Transactions -> if (current.category != "ALL") current.category else if (current.type == "INCOME") "Income" else "Spent"
                },
                back = if (shownRoute != LedgerRoute.Tabs || (!wide && tab in listOf(2, 4, 5))) ({ if (settings) settings = false else if (details != null) details = null else tab = 6 }) else null,
                settings = { settings = true },
                add = if (shownRoute != LedgerRoute.Settings && (tab <= 1 || shownRoute is LedgerRoute.Transactions)) ({
                    accounts.firstOrNull()?.let {
                        editor = Entry(UUID.randomUUID().toString(), it.id, type = (shownRoute as? LedgerRoute.Transactions)?.type ?: "EXPENSE", category = (shownRoute as? LedgerRoute.Transactions)?.category?.takeUnless { category -> category == "ALL" } ?: "Uncategorized", amount = 0, date = System.currentTimeMillis())
                    }
                }) else null
            )
        },
        bottomBar = { if (!wide && shownRoute == LedgerRoute.Tabs) PocketTabBar(tab, pending) { tab = it } },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            readState.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { readAttempt++ }) { Text("Retry") }
                    }
                }
            }
            if (shownRoute == LedgerRoute.Settings) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SettingsDialog(capture, permission, senders, rules, { settings = false }, { passwordMode = "export" }, { chooseRestore.launch(arrayOf("*/*")) }, { work { dao.untrust(it.sender, it.bank) } }, { work { dao.removeRule(it.merchant) } }, { repo.context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${repo.context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                }
            } else if (shownRoute is LedgerRoute.Transactions) {
                Column(Modifier.fillMaxSize()) {
                    if (shownRoute.category == "ALL") MetricDetailHeader(shownRoute.type, month, overviewReport?.summary)
                    MonthPicker(month) { month = it }
                    Transactions(entries, accounts, month, shownRoute.type, shownRoute.category) { editor = it }
                }
            } else if (snapshot == null) {
                if (readState.error == null) LedgerSkeleton()
            } else Column(Modifier.fillMaxSize()) {
            if (busy) Text("Updating ledger...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            if (tab in listOf(0, 1, 3, 5)) MonthPicker(month) { month = it }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                val page = tab
                pageState.SaveableStateProvider("tab-$page") {
                Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                when (page) {
                    6 -> MoreScreen(pending, { tab = it }, { settings = true })
                    0 -> Overview(accounts, entries, messages, month, budgets, overviewReport, { tab = 2 }, { tab = 4 }, openTransactions) { editor = it }
                    1 -> Transactions(entries, accounts, month) { editor = it }
                    2 -> AccountsScreen(accounts, entries, messages, { accountEditor = it }, { accountEditor = Account(UUID.randomUUID().toString(), "", "CASH", "", openingDate = dayMillis(LocalDate.now(india).withDayOfMonth(1).toString())) })
                    3 -> BudgetScreen(budgets, entries, month) { budget, saved -> work { dao.budget(budget); withContext(Dispatchers.Main) { saved() } } }
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
                        }, { message -> scope.launch {
                            try {
                                withContext(Dispatchers.IO) { repo.dismiss(message) }
                                val undo = snack.showSnackbar("Alert dismissed", actionLabel = "Undo", duration = SnackbarDuration.Long)
                                if (undo == SnackbarResult.ActionPerformed) withContext(Dispatchers.IO) { repo.undoDismiss(message) }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                snack.showSnackbar(e.message ?: "Could not dismiss alert")
                            }
                        } }, { transferCandidate = it }, refreshing = busy, refresh = { work { repo.refreshStoredReviews(); repo.reconcile() } })
                }
            }
        }
    }
    } } } } } } }
    if (expandedEditor && editor != null) {
        VerticalDivider(Modifier.fillMaxHeight())
        Box(Modifier.width(400.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            CompositionLocalProvider(LocalInlineSheet provides true) { editor?.let { presentEntry(it) } }
        }
    }
    } } } }
    if (!expandedEditor) FluidPresentation(editor) { presentEntry(it) }
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
}

