package `in`.thomas.pocketledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

val FluidEasing = CubicBezierEasing(.25f, 1f, .5f, 1f)
const val LedgerNavigationDurationMillis = 280
fun <T> ledgerNavigationTween(): TweenSpec<T> = tween(LedgerNavigationDurationMillis, easing = FluidEasing)
fun <T> fluidSpring(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 400f)
fun <T> fluidTween(): TweenSpec<T> = tween(120, easing = LinearEasing)
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

// Source compatibility for forms; the standard scrollable owns stretch and fling.
@Suppress("UNUSED_PARAMETER")
fun Modifier.elasticScroll(orientation: Orientation = Orientation.Vertical): Modifier = this

@Composable fun FluidList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(modifier, contentPadding = contentPadding,
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
                    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) { content(dismiss) }
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
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        content(close)
    }
}
