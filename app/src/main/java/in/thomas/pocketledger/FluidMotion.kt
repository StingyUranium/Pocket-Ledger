package `in`.thomas.pocketledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

val FluidEasing = CubicBezierEasing(.25f, 1f, .5f, 1f)
const val LedgerNavigationDurationMillis = 280
fun <T> ledgerNavigationTween(): TweenSpec<T> = tween(LedgerNavigationDurationMillis, easing = FluidEasing)
fun <T> fluidSpring(): SpringSpec<T> = LedgerMotion.gentle()
fun <T> fluidTween(): TweenSpec<T> = tween(LedgerMotion.fast, easing = LinearEasing)
val LocalReducedMotion = staticCompositionLocalOf { false }

/** One vote per window. Input observation never consumes gesture events. */
fun Modifier.motionSurface(active: Boolean = false): Modifier = composed {
    val pulses = remember { Channel<Unit>(Channel.CONFLATED) }
    var high by remember { mutableStateOf(false) }
    val activeNow by rememberUpdatedState(active)
    LaunchedEffect(active) { if (active || high) pulses.trySend(Unit) }
    LaunchedEffect(pulses) {
        pulses.receiveAsFlow().collectLatest {
            high = true
            if (activeNow) awaitCancellation() else delay(1500)
            high = false
        }
    }
    val scroll = remember { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed != Offset.Zero || available != Offset.Zero) pulses.trySend(Unit)
            return Offset.Zero
        }
    } }
    this.preferredFrameRate(if (high) FrameRateCategory.High else FrameRateCategory.Normal)
        .nestedScroll(scroll)
        .pointerInput(pulses) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.pressed || it.previousPressed }) pulses.trySend(Unit)
                }
            }
        }
}

/**
 * Short fade when a root destination replaces another, hiding the hard cut. The alpha is created in
 * the same composition as the new page, so it never paints one fully opaque frame first. The initial
 * composition, route changes (which have their own transition) and reduced motion are not animated.
 */
@Composable fun rememberDestinationEntrance(key: Any): Animatable<Float, AnimationVector1D> {
    val reduced = LocalReducedMotion.current
    val composed = remember { booleanArrayOf(false) }
    val alpha = remember(key) { Animatable(if (reduced || !composed[0]) 1f else 0f) }
    SideEffect { composed[0] = true }
    LaunchedEffect(alpha) { if (alpha.value < 1f) alpha.animateTo(1f, tween(LedgerMotion.standard, easing = FluidEasing)) }
    return alpha
}

@Composable fun FluidList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit
) {
    val inset = LocalLedgerHeaderInset.current
    val direction = LocalLayoutDirection.current
    val padding = if (inset == 0.dp) contentPadding else PaddingValues(
        start = contentPadding.calculateStartPadding(direction), top = contentPadding.calculateTopPadding() + inset,
        end = contentPadding.calculateEndPadding(direction), bottom = contentPadding.calculateBottomPadding()
    )
    LazyColumn(modifier, contentPadding = padding,
        verticalArrangement = verticalArrangement, content = content)
}

@Composable fun FluidRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyListScope.() -> Unit
) {
    LazyRow(modifier, contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement, content = content)
}

private val LocalSheetExitRequested = staticCompositionLocalOf { false }
private val LocalSheetExited = staticCompositionLocalOf<() -> Unit> { {} }

@Composable fun <T : Any> FluidPresentation(value: T?, content: @Composable (T) -> Unit) {
    var retained by remember { mutableStateOf(value) }
    val shown = value ?: retained
    SideEffect { if (value != null) retained = value }
    if (shown != null) CompositionLocalProvider(
        LocalSheetExitRequested provides (value == null),
        LocalSheetExited provides { retained = null }
    ) { content(shown) }
}

/** Material owns dragging, nested scrolling, accessibility and predictive back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FluidModalSheet(dismiss: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    if (LocalReducedMotion.current) {
        val exitRequested = LocalSheetExitRequested.current
        val exited = LocalSheetExited.current
        LaunchedEffect(exitRequested) { if (exitRequested) exited() }
        if (!exitRequested) androidx.compose.ui.window.Dialog(
            onDismissRequest = dismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().navigationBarsPadding(),
                    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.background) { content(dismiss) }
            }
        }
        return
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val exitRequested = LocalSheetExitRequested.current
    val exited = LocalSheetExited.current
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch { state.hide(); dismiss() }
        }
    }
    LaunchedEffect(exitRequested) {
        if (exitRequested) { state.hide(); exited() }
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        modifier = Modifier.motionSurface(active = state.isAnimationRunning),
        sheetState = state,
        sheetMaxWidth = 640.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        content(close)
    }
}
