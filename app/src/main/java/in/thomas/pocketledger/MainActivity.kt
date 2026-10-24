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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.collect
import androidx.compose.animation.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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

// NumberFormat is expensive to create and not thread-safe; money() runs per row and on widget threads.
private val rupeeFormat = ThreadLocal.withInitial { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
fun money(v: Long): String = rupeeFormat.get().format(BigDecimal.valueOf(v, 2))
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
    data class Page(val destination: Destination) : LedgerRoute
    data class Transactions(val type: String, val category: String) : LedgerRoute
}

private fun LedgerRoute.depth(): Int = when (this) {
    LedgerRoute.Tabs -> 0
    is LedgerRoute.Page -> if (destination in listOf(Destination.Accounts, Destination.IncomePlan)) 2 else 1
    is LedgerRoute.Transactions -> 2
    LedgerRoute.Settings -> 3
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
    var tab by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Destination, Any>(save = { it.name }, restore = { Destination.restore(it.toString()) })) { mutableStateOf(Destination.Overview) }; var month by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })) { mutableStateOf(YearMonth.now(india)) }
    var parentTab by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Destination, String>(save = { it.name }, restore = { Destination.restore(it) })) { mutableStateOf(Destination.Overview) }
    val openManagement: () -> Unit = { if (tab in Destination.primary) parentTab = tab; tab = Destination.Management }
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
    var from by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(LocalDate.now(india).withDayOfMonth(1).toString()) }
    var to by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(LocalDate.now(india).toString()) }
    var passwordMode by remember { mutableStateOf<String?>(null) }; var secret by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var trustCandidate by remember { mutableStateOf<TrustedSender?>(null) }
    var categoryEntry by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = EntryDraftSaver) { mutableStateOf<Entry?>(null) }
    var budgetAdd by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
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
            tab = if (destination == "budget") Destination.Budgets else Destination.Overview
            if (destination == "income") openTransactions("INCOME", "ALL")
            if (destination == "expense") openTransactions("EXPENSE", "ALL")
        }
    }
    LaunchedEffect(launchRequest, snapshot) {
        val id = launchRequest?.getStringExtra(CategoryNotifications.EXTRA_ENTRY_ID)
        if (id != null && snapshot != null) {
            settings = false
            details = null
            tab = Destination.Activity
            val target = entries.singleOrNull { it.id == id }
            if (target != null) {
                editor = target
                CategoryNotifications.cancel(repo.context, id)
                launchRequest.removeExtra(CategoryNotifications.EXTRA_ENTRY_ID)
            } else {
                // A committed entry can precede its Room snapshot. Keep the request until that
                // snapshot arrives; discard only IDs that are actually missing or deleted.
                val current = withContext(Dispatchers.IO) { dao.entry(id) }
                if (current == null || current.deleted) {
                    CategoryNotifications.cancel(repo.context, id)
                    launchRequest.removeExtra(CategoryNotifications.EXTRA_ENTRY_ID)
                }
            }
        }
    }
    val tabRoute: LedgerRoute = if (tab in Destination.primary) LedgerRoute.Tabs else LedgerRoute.Page(tab)
    val route: LedgerRoute = if (settings) LedgerRoute.Settings else details ?: tabRoute
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
    val backTarget: LedgerRoute = if (settings) details ?: tabRoute else if (details != null) tabRoute else if (tab in listOf(Destination.Accounts, Destination.IncomePlan)) LedgerRoute.Page(Destination.Management) else LedgerRoute.Tabs
    PredictiveBackHandler(enabled = route != LedgerRoute.Tabs) { events ->
        predictiveBackInProgress = true
        try {
            events.collect { event ->
                if (!reducedMotion) seekState.seekTo(event.progress, backTarget)
            }
            if (settings) settings = false else if (details != null) details = null else tab = (backTarget as? LedgerRoute.Page)?.destination ?: parentTab
        } finally {
            // On cancellation the route stays unchanged, so the settling effect restores it.
            predictiveBackInProgress = false
        }
    }
    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val calculationCache = remember { CalculationCache() }
    val lastHeaderInset = remember { floatArrayOf(0f, 0f) }

    // Keep the shared card data ready while detail pages are open, including during back previews.
    val overviewReport by backgroundCalculation(entries, budgets, month) { calculateOverviewReport(entries, budgets, month) }
    val pending = remember(messages) { messages.count { it.status == "REVIEW" } }
    val expandedEditor = LocalLedgerWindowWidth.current >= 840.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f
    val editorContent by rememberUpdatedState<@Composable (Entry) -> Unit> { current -> EntryEditor(current, accounts, messages.find { it.key == current.smsKey }, { editor = null }, { entry, remember -> work { repo.save(entry, remember); withContext(Dispatchers.Main) { editor = null } } }, if (allEntries.any { it.id == current.id }) { { deleteCandidate = current } } else null, knownEntries = entries) }
    val presentEntry = remember { movableContentOf<Entry> { current -> key(current.id) { editorContent(current) } } }
    CompositionLocalProvider(LocalCalculationCache provides calculationCache, LocalLedgerBusy provides busy,LocalLedgerOperationError provides operationError, LocalLedgerClearError provides { operationError = null }) {
    SharedTransitionLayout {
    CompositionLocalProvider(LocalLedgerSharedScope provides this) {
    BoxWithConstraints(Modifier.fillMaxSize().motionSurface(active = navigation.isRunning)) {
    val wide = maxWidth >= 600.dp
    Row(Modifier.fillMaxSize()) {
    navigation.AnimatedContent(
        transitionSpec = {
            val forward = targetState.depth() > initialState.depth()
            val metricNavigation = tab == Destination.Overview && (
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
                // Push: the new page slides in from the trailing edge while the parent drifts back a quarter width.
                slideInHorizontally(ledgerNavigationTween()) { it } togetherWith slideOutHorizontally(ledgerNavigationTween()) { -it / 4 }
            } else {
                slideInHorizontally(ledgerNavigationTween()) { -it / 4 } togetherWith slideOutHorizontally(ledgerNavigationTween()) { it }
            }
            // Keep the detail page above its parent while returning or seeking back.
            transform.apply { targetContentZIndex = if (forward) 1f else 0f }.using(null)
        },
        modifier = Modifier.weight(1f).fillMaxHeight()
    ) { shownRoute ->
    val shownDestination = (shownRoute as? LedgerRoute.Page)?.destination ?: if (tab in Destination.primary) tab else parentTab
    CompositionLocalProvider(LocalLedgerVisibilityScope provides this) {
    pageState.SaveableStateProvider(shownRoute.toString()) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (wide && shownRoute == LedgerRoute.Tabs) PocketNavigationRail(shownDestination, pending, { tab = it }, openManagement)
    val headerState = rememberTopAppBarState()
    val headerPinned = rememberHeaderPinned()
    val headerCanScroll by rememberUpdatedState(!headerPinned && !navigation.isRunning && editor == null && accountEditor == null)
    val headerScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = headerState,
        canScroll = { headerCanScroll },
        snapAnimationSpec = LedgerMotion.gentle()
    )
    LaunchedEffect(shownDestination, headerPinned) {
        // Reveal actions on destination changes without altering that destination's list position.
        headerState.heightOffset = 0f
        headerState.contentOffset = 0f
    }
    // The header overlays the page; pages reserve its full height so scrolling never resizes a list.
    var headerInset by remember { mutableStateOf(lastHeaderInset[0].dp) }
    var bottomInset by remember { mutableStateOf(lastHeaderInset[1].dp) }
    val hazeState = remember { HazeState() }
    val showTabBar = !wide && shownRoute == LedgerRoute.Tabs
    val density = LocalDensity.current
    val headerTitle = when (val current = shownRoute) {
        LedgerRoute.Settings -> "Settings"
        LedgerRoute.Tabs -> shownDestination.title
        is LedgerRoute.Page -> current.destination.title
        is LedgerRoute.Transactions -> if (current.category != "ALL") current.category else if (current.type == "INCOME") "Income" else "Spent"
    }
    Scaffold(
        modifier = Modifier.weight(1f).nestedScroll(headerScroll.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.statusBars).let { if (showTabBar) it.exclude(WindowInsets.navigationBars) else it },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack, Modifier.padding(bottom = if (showTabBar) bottomInset else 0.dp)) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            ScrollingLedgerHeader(headerState, pinned = headerPinned, modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).zIndex(1f),
                onInset = { inset -> if (inset != headerInset) { headerInset = inset; lastHeaderInset[0] = inset.value } },
                haze = hazeState,
                bar = { collapsed ->
                    PocketNavigationBar(
                        title = headerTitle, collapsed = collapsed, addLabel = if (shownDestination == Destination.Budgets) "Add budget" else "Add transaction",
                        back = if (shownRoute != LedgerRoute.Tabs) ({ if (settings) settings = false else if (details != null) details = null else tab = if (shownDestination == Destination.Accounts || shownDestination == Destination.IncomePlan) Destination.Management else parentTab }) else null,
                        backLabel = if (shownRoute is LedgerRoute.Page && shownDestination in listOf(Destination.Accounts, Destination.IncomePlan)) "Management" else if (shownRoute is LedgerRoute.Page) parentTab.title else shownDestination.title,
                        settings = if (shownRoute == LedgerRoute.Tabs && !wide) (openManagement) else null,
                        add = if (shownRoute == LedgerRoute.Tabs && shownDestination == Destination.Budgets) ({ budgetAdd++ }) else if (shownRoute != LedgerRoute.Settings && (shownDestination in listOf(Destination.Overview, Destination.Activity) || shownRoute is LedgerRoute.Transactions)) ({
                            accounts.firstOrNull()?.let {
                                editor = Entry(UUID.randomUUID().toString(), it.id, type = (shownRoute as? LedgerRoute.Transactions)?.type ?: "EXPENSE", category = (shownRoute as? LedgerRoute.Transactions)?.category?.takeUnless { category -> category == "ALL" } ?: "Uncategorized", amount = 0, date = System.currentTimeMillis())
                            }
                        }) else null
                    )
                }) {
            PocketLargeTitle(headerTitle)
            if (shownRoute is LedgerRoute.Transactions || (shownDestination in listOf(Destination.Overview, Destination.Activity, Destination.Budgets, Destination.Insights, Destination.IncomePlan) && shownRoute != LedgerRoute.Settings)) {
                MonthPicker(month) { month = it }
            }
            }
            if (snapshot != null) readState.error?.let { error ->
                Surface(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).zIndex(1f).padding(bottom = if (showTabBar) bottomInset else 0.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { readAttempt++ }) { Text("Retry") }
                    }
                }
            }
            if (busy && shownRoute == LedgerRoute.Tabs && snapshot != null) {
                Text("Updating ledger...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).zIndex(1f).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + (if (showTabBar) bottomInset else 0.dp)))
            }
            if (showTabBar) PocketTabBar(shownDestination, pending,
                Modifier.align(androidx.compose.ui.Alignment.BottomCenter).zIndex(1f).onSizeChanged { size ->
                    val inset = with(density) { size.height.toDp() }
                    if (inset != bottomInset) { bottomInset = inset; lastHeaderInset[1] = inset.value }
                }, hazeState) { tab = it }
            CompositionLocalProvider(LocalLedgerHeaderInset provides headerInset, LocalLedgerBottomInset provides (if (showTabBar) bottomInset else 0.dp)) {
            Column(Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (shownRoute == LedgerRoute.Settings) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SettingsDialog(capture, permission, senders, rules, { passwordMode = "export" }, { chooseRestore.launch(arrayOf("*/*")) }, { work { dao.untrust(it.sender, it.bank) } }, { work { dao.removeRule(it.merchant, it.type) } }, { repo.context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${repo.context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                }
            } else if (shownRoute is LedgerRoute.Transactions) {
                Transactions(entries, accounts, month, shownRoute.type, shownRoute.category, delete = { deleteCandidate = it },
                    categorize = { categoryEntry = it }, leading = if (shownRoute.category == "ALL") ({ MetricDetailHeader(shownRoute.type, month, overviewReport?.summary) }) else null) { editor = it }
            } else if (snapshot == null) {
                if (readState.error == null) LedgerSkeleton() else ReadErrorState(readState.error!!) { readAttempt++ }
            } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                val page = shownDestination
                val entrance = rememberDestinationEntrance(page)
                pageState.SaveableStateProvider("tab-$page") {
                Box(Modifier.widthIn(max = 840.dp).fillMaxSize().graphicsLayer { alpha = entrance.value }) {
                when (page) {
                    Destination.IncomePlan -> FluidList(contentPadding = PaddingValues(LedgerSpace.margin)) { item { IncomeAllocationPlan(entries, month) } }
                    Destination.Management -> MoreScreen(pending, { tab = it }, { settings = true })
                    Destination.Overview -> Overview(accounts, entries, messages, month, budgets, overviewReport, { tab = Destination.Accounts }, { tab = Destination.Inbox }, openTransactions, insights = { parentTab = Destination.Overview; tab = Destination.Insights }) { editor = it }
                    Destination.Activity -> Transactions(entries, accounts, month, delete = { deleteCandidate = it }, categorize = { categoryEntry = it }) { editor = it }
                    Destination.Accounts -> AccountsScreen(accounts, entries, messages, { accountEditor = it }, { accountEditor = Account(UUID.randomUUID().toString(), "", "CASH", "", openingDate = dayMillis(LocalDate.now(india).withDayOfMonth(1).toString())) })
                    Destination.Budgets -> BudgetScreen(budgets, entries, month, addRequest = budgetAdd, remove = { category, done -> work { repo.removeBudget(category); withContext(Dispatchers.Main) { done() } } }) { budget, originalCategory, saved -> work { repo.saveBudget(budget, originalCategory); withContext(Dispatchers.Main) { saved() } } }
                    Destination.Insights -> GraphsScreen(entries, month, { month = it }, openTransactions)
                    Destination.Inbox -> SmsScreen(messages, entries, accounts, senders, permission, capture, from, to, { from = it }, { to = it },
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
                        } }, { transferCandidate = it }, refreshing = busy, refresh = { work { repo.refreshStoredReviews(); repo.reconcile() } }, viewActivity = { tab = Destination.Activity })
                }
            }
        }
    }
    } } } } } } } } }
    if (expandedEditor && editor != null) {
        VerticalDivider(Modifier.fillMaxHeight())
        Box(Modifier.width(400.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
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
    categoryEntry?.let { current -> CategoryReviewSheet(current, entries, { categoryEntry = null }) { category, remember -> work { repo.updateCategory(current.id, category, remember); withContext(Dispatchers.Main) { categoryEntry = null } } } }
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

