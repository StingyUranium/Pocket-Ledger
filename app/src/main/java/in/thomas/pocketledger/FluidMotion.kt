package `in`.thomas.pocketledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

// Compose uses a damping ratio, not UIKit's damping coefficient. A ratio of 1
// is critically damped; the requested stiffness is expressed in Compose units.
val FluidEasing = CubicBezierEasing(.25f, 1f, .5f, 1f)
fun <T> fluidSpring(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 300f)
fun <T> fluidTween(): TweenSpec<T> = tween(320, easing = FluidEasing)

// Child scrollables retain Compose's momentum fling. Only unconsumed boundary
// movement is resisted, so ordinary scrolling never competes with the bounce.
fun Modifier.elasticScroll(orientation: Orientation = Orientation.Vertical): Modifier = composed {
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 96.dp.toPx() }
    var displacement by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val connection = remember(orientation, limit) { object : NestedScrollConnection {
        fun axis(offset: Offset) = if (orientation == Orientation.Vertical) offset.y else offset.x
        fun offset(value: Float) = if (orientation == Orientation.Vertical) Offset(0f, value) else Offset(value, 0f)
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            settling?.cancel()
            val delta = axis(available)
            if (displacement * delta >= 0f) return Offset.Zero
            val consumed = if (abs(delta) > abs(displacement)) -displacement else delta
            displacement += consumed
            return offset(consumed)
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val delta = axis(available)
            if (delta == 0f) return Offset.Zero
            if (source == NestedScrollSource.UserInput) {
                displacement = (displacement + delta * .45f / (1f + abs(displacement) / limit)).coerceIn(-limit, limit)
                return offset(delta)
            }
            // A fling that hits a boundary also transfers a little energy into
            // the elastic edge before onPostFling brings it back to rest.
            displacement = (displacement + delta * .15f).coerceIn(-limit, limit)
            return Offset.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settling?.cancel()
            settling = scope.launch {
                animate(displacement, 0f, animationSpec = fluidSpring()) { value, _ -> displacement = value }
            }
            return Velocity.Zero
        }
    } }
    this.nestedScroll(connection).graphicsLayer {
        if (orientation == Orientation.Vertical) translationY = displacement else translationX = displacement
    }
}

@Composable fun FluidList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(modifier.elasticScroll(), contentPadding = contentPadding,
        verticalArrangement = verticalArrangement, overscrollEffect = null, content = content)
}

@Composable fun FluidRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyListScope.() -> Unit
) {
    LazyRow(modifier.elasticScroll(Orientation.Horizontal), contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement, overscrollEffect = null, content = content)
}

private val LocalSheetExitRequested = staticCompositionLocalOf { false }
private val LocalSheetExited = staticCompositionLocalOf<() -> Unit> { {} }

// Retain the outgoing content until its exit spring finishes, including when
// an asynchronous save clears the caller's selected editor or preview.
@Composable fun <T : Any> FluidPresentation(value: T?, content: @Composable (T) -> Unit) {
    var retained by remember { mutableStateOf(value) }
    val shown = value ?: retained
    SideEffect { if (value != null) retained = value }
    if (shown != null) CompositionLocalProvider(
        LocalSheetExitRequested provides (value == null),
        LocalSheetExited provides { retained = null }
    ) { content(shown) }
}

// A single modal presentation owns entry, drag-to-dismiss, scrim, and exit
// animation; callers clear their overlay state only after its dismissal settles.
@Composable fun FluidModalSheet(dismiss: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }
    var height by remember { mutableFloatStateOf(1f) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch { position.animateTo(1f, fluidSpring()); dismiss() }
        }
    }
    val exitRequested = LocalSheetExitRequested.current
    val exited = LocalSheetExited.current
    LaunchedEffect(exitRequested) {
        if (exitRequested) { position.animateTo(1f, fluidSpring()); exited() }
        else position.animateTo(0f, fluidSpring())
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .38f * (1f - position.value.coerceIn(0f, 1f))))
                .clickable(onClick = close))
            androidx.compose.material3.Surface(
                modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding()
                    .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer { translationY = position.value * height },
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(28.dp)
                        .semantics { contentDescription = "Dismiss sheet"; onClick { close(); true } }
                        .draggable(rememberDraggableState { delta ->
                            scope.launch { position.snapTo((position.value + delta / height).coerceIn(0f, 1f)) }
                        }, Orientation.Vertical, onDragStopped = { velocity ->
                            if (position.value > .18f || velocity > 1200f) close()
                            else position.animateTo(0f, fluidSpring())
                        }), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(34.dp, 5.dp).background(
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f),
                            androidx.compose.foundation.shape.CircleShape))
                    }
                    content(close)
                }
            }
        }
    }
}
