package `in`.thomas.pocketledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable fun DailySpendingChart(month: YearMonth, daily: Map<Int, Long>) {
    var selected by remember(month) { mutableIntStateOf(0) }
    var showData by rememberSaveable { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val labels = remember(month, daily) { (1..month.lengthOfMonth()).associateWith { "Day $it: ${money(daily[it] ?: 0)}" } }
    val maximum = remember(daily) { daily.values.maxOrNull()?.coerceAtLeast(1) ?: 1L }
    Canvas(Modifier.fillMaxWidth().height(140.dp)
        .semantics { contentDescription = "Daily spending for $month. Swipe horizontally to inspect. Use Show daily amounts for the data list." }
        .pointerInput(month, daily) {
            detectHorizontalDragGestures(onHorizontalDrag = { change, _ ->
                change.consume()
                selected = (change.position.x / size.width * month.lengthOfMonth()).toInt().plus(1).coerceIn(1, month.lengthOfMonth())
            })
        }
        .drawWithContent {
            drawContent()
            if (selected > 0) {
                val x = (selected - .5f) * size.width / month.lengthOfMonth()
                drawLine(primary, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }) {
        val step = size.width / month.lengthOfMonth()
        drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        (1..month.lengthOfMonth()).forEach { day ->
            val height = ((daily[day] ?: 0).toDouble() / maximum * (size.height - 4.dp.toPx())).toFloat()
            if (height > 0) drawRoundRect(primary, Offset((day - 1) * step + step * .15f, size.height - height),
                Size(step * .7f, height), CornerRadius(2.dp.toPx()))
        }
    }
    Text(labels[selected] ?: "Drag across the chart to inspect a day", style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { showData = !showData }) { Text(if (showData) "Hide daily amounts" else "Show daily amounts") }
    if (showData) labels.values.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}
