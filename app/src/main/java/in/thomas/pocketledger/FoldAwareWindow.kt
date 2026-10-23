package `in`.thomas.pocketledger

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.*
import kotlin.math.roundToInt

val LocalLedgerWindowWidth = staticCompositionLocalOf { 0.dp }

/** Keep the active ledger within a usable pane when a fold separates the window. */
@Composable fun FoldAwareWindow(content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    val info: kotlinx.coroutines.flow.Flow<WindowLayoutInfo?> = remember(activity) {
        if (activity == null) kotlinx.coroutines.flow.flowOf(null)
        else WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }
    val layout by info.collectAsStateWithLifecycle(initialValue = null)
    val hinge = layout?.displayFeatures.orEmpty().filterIsInstance<FoldingFeature>().firstOrNull {
        it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL
    }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        var left = 0
        var top = 0
        var right = widthPx
        var bottom = heightPx
        hinge?.let {
            val bounds = it.bounds
            if (it.orientation == FoldingFeature.Orientation.VERTICAL) {
                val start = (bounds.left - origin.x).roundToInt().coerceIn(0, widthPx)
                val end = (bounds.right - origin.x).roundToInt().coerceIn(start, widthPx)
                if (start > 0 && end < widthPx) {
                    if (start >= widthPx - end) right = start else left = end
                }
            } else {
                val start = (bounds.top - origin.y).roundToInt().coerceIn(0, heightPx)
                val end = (bounds.bottom - origin.y).roundToInt().coerceIn(start, heightPx)
                if (start > 0 && end < heightPx) {
                    // Tabletop: use the lower pane for reachable form and navigation controls.
                    if (heightPx - end >= start) top = end else bottom = start
                }
            }
        }
        val safeWidth = with(density) { (right - left).toDp() }
        val safeHeight = with(density) { (bottom - top).toDp() }
        Box(Modifier.offset { IntOffset(left, top) }.size(safeWidth, safeHeight)) {
            CompositionLocalProvider(LocalLedgerWindowWidth provides safeWidth, content = content)
        }
    }
}
