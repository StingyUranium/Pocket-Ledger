package `in`.thomas.pocketledger

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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

/**
 * Material owns drag consumption, fling settling and interruption. The header is an overlay: only
 * its own visible height changes during scrolling, so the list below keeps a constant viewport and
 * never re-clamps its scroll position at the end. Pages reserve [LocalLedgerHeaderInset] instead.
 * The status-bar inset stays outside the clipped region so content never enters its safe area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollingLedgerHeader(
    state: TopAppBarState,
    pinned: Boolean,
    modifier: Modifier = Modifier,
    onInset: (Dp) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val reportInset by rememberUpdatedState(onInset)
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Layout(
            modifier = Modifier.fillMaxWidth().clipToBounds(),
            content = {
                Column(Modifier.fillMaxWidth().onSizeChanged { size ->
                    val fraction = state.collapsedFraction
                    state.heightOffsetLimit = -size.height.toFloat()
                    state.heightOffset = state.heightOffsetLimit * fraction
                    reportInset(with(density) { (size.height + statusTop).toDp() })
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
