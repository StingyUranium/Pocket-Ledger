package `in`.thomas.pocketledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/**
 * Bar chart of spending per day. The selected day is saved with the instance state, can be set by
 * tapping or dragging, and mirrors in right-to-left layouts. The selection is only read while
 * drawing and by the readout below, so changing it does not recompose the chart itself.
 */
@Composable fun DailySpendingChart(month: YearMonth, daily: Map<Int, Long>) {
    val days = month.lengthOfMonth()
    val selected = rememberSaveable(month.toString()) { mutableIntStateOf(0) }
    var showData by rememberSaveable { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val labels = remember(month, daily) { (1..days).associateWith { "Day $it: ${money(daily[it] ?: 0)}" } }
    val maximum = remember(daily) { daily.values.maxOrNull()?.coerceAtLeast(1) ?: 1L }
    // Day 1 sits at the leading edge: left in LTR, right in RTL.
    fun slot(day: Int) = if (rtl) days - day else day - 1
    fun dayAt(x: Float, width: Float): Int {
        val fraction = (x / width).coerceIn(0f, 1f)
        return ((if (rtl) 1f - fraction else fraction) * days).toInt().plus(1).coerceIn(1, days)
    }
    Canvas(Modifier.fillMaxWidth().height(140.dp)
        .semantics { contentDescription = "Daily spending for $month. Tap or swipe horizontally to inspect a day. Use Show daily amounts for the data list." }
        .pointerInput(days, rtl) {
            detectTapGestures { offset -> selected.intValue = dayAt(offset.x, size.width.toFloat()) }
        }
        .pointerInput(days, rtl) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> selected.intValue = dayAt(offset.x, size.width.toFloat()) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    selected.intValue = dayAt(change.position.x, size.width.toFloat())
                })
        }
        .drawWithContent {
            drawContent()
            val day = selected.intValue
            if (day > 0) {
                val x = (slot(day) + .5f) * size.width / days
                drawLine(primary, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }) {
        val step = size.width / days
        drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        for (day in 1..days) {
            val height = ((daily[day] ?: 0).toDouble() / maximum * (size.height - 4.dp.toPx())).toFloat()
            if (height > 0) drawRoundRect(primary, Offset(slot(day) * step + step * .15f, size.height - height),
                Size(step * .7f, height), CornerRadius(2.dp.toPx()))
        }
    }
    SelectedDayReadout(selected, labels)
    TextButton(onClick = { showData = !showData }) { Text(if (showData) "Hide daily amounts" else "Show daily amounts") }
    if (showData) labels.values.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

/** Reads the selection in its own scope so only this text recomposes while dragging. */
@Composable private fun SelectedDayReadout(selected: IntState, labels: Map<Int, String>) {
    Text(labels[selected.intValue] ?: "Tap or drag across the chart to inspect a day", style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
