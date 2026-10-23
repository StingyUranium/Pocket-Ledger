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

/**
 * Material owns drag consumption, fling settling and interruption. Only the wrapper's visible
 * height changes during scrolling; the header child is measured with unchanged constraints.
 * The status-bar inset stays outside the clipped region so content never enters its safe area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollingLedgerHeader(
    state: TopAppBarState,
    pinned: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Layout(
            modifier = Modifier.fillMaxWidth().clipToBounds(),
            content = {
                Column(Modifier.fillMaxWidth().onSizeChanged { size ->
                    val fraction = state.collapsedFraction
                    state.heightOffsetLimit = -size.height.toFloat()
                    state.heightOffset = state.heightOffsetLimit * fraction
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
