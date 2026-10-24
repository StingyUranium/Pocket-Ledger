package `in`.thomas.pocketledger

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Keep navigation controls available to exploration users and when motion is disabled. */
@Composable
internal fun rememberHeaderPinned(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var exploring by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return LocalReducedMotion.current || exploring
}

/** Space the page content reserves under the overlaid header: status bar plus full header height. */
val LocalLedgerHeaderInset = compositionLocalOf { 0.dp }

/** Space reserved at the bottom for the overlaid tab bar. */
val LocalLedgerBottomInset = compositionLocalOf { 0.dp }

/** Translucent, blurred chrome shared by the header and the tab bar. */
@Composable internal fun rememberChromeStyle(): HazeBlurStyle {
    val background = MaterialTheme.colorScheme.background
    return remember(background) {
        HazeBlurStyle {
            blurRadius(20.dp)
            noiseFactor(0f)
            backgroundColor(background)
            colorEffects(listOf(HazeColorEffect.tint(background.copy(alpha = .72f))))
            fallbackColorEffect(HazeColorEffect.tint(background.copy(alpha = .94f)))
        }
    }
}

/**
 * Large-title header. A compact bar stays pinned (back, inline title, actions) while the large
 * title and any controls below it collapse under Material's exit-until-collapsed behavior, which
 * owns drag consumption, fling settling and interruption. The header is an overlay: its own
 * visible height changes during scrolling, so the list keeps a constant viewport and never
 * re-clamps its scroll position at the end. Pages reserve [LocalLedgerHeaderInset] instead.
 * Only alpha and the hairline are read at draw time; the bar itself is never remeasured.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollingLedgerHeader(
    state: TopAppBarState,
    pinned: Boolean,
    modifier: Modifier = Modifier,
    onInset: (Dp) -> Unit,
    haze: HazeState? = null,
    bar: @Composable (collapsed: () -> Float) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val reportInset by rememberUpdatedState(onInset)
    var barPx by remember { mutableIntStateOf(0) }
    var fullPx by remember { mutableIntStateOf(0) }
    val background = MaterialTheme.colorScheme.background.copy(alpha = .94f)
    val chrome = rememberChromeStyle()
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val collapsed: () -> Float = { if (pinned) 0f else state.collapsedFraction }
    fun report() = reportInset(with(density) { (statusTop + barPx + fullPx).toDp() })
    Column(
        modifier.fillMaxWidth().then(if (haze != null) Modifier.hazeBlur(HazeInput.Sources(haze), chrome) else Modifier.background(background)).drawWithContent {
            drawContent()
            // The hairline appears only once content scrolls beneath the compact bar.
            val alpha = ((collapsed() - .9f) * 10f).coerceIn(0f, 1f)
            if (alpha > 0f) drawLine(hairline.copy(alpha = alpha), Offset(0f, size.height), Offset(size.width, size.height), 1f)
        }.statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().onSizeChanged { size -> barPx = size.height; report() }) { bar(collapsed) }
        Layout(
            modifier = Modifier.fillMaxWidth().clipToBounds(),
            content = {
                Column(Modifier.fillMaxWidth().onSizeChanged { size ->
                    val fraction = state.collapsedFraction
                    state.heightOffsetLimit = -size.height.toFloat()
                    state.heightOffset = state.heightOffsetLimit * fraction
                    fullPx = size.height; report()
                }, content = content)
            }
        ) { measurables, constraints ->
            val header = measurables.single().measure(constraints.copy(minHeight = 0))
            val offset = if (pinned) 0 else state.heightOffset.roundToInt().coerceIn(-header.height, 0)
            layout(header.width, (header.height + offset).coerceAtLeast(0)) {
                header.placeRelative(0, offset)
            }
        }
    }
}
