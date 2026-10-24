package `in`.thomas.pocketledger

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import java.time.YearMonth

val LocalLedgerSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalLedgerVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

fun Modifier.metricBounds(key: String): Modifier = composed {
    val shared = LocalLedgerSharedScope.current
    val visibility = LocalLedgerVisibilityScope.current
    if (shared == null || visibility == null || LocalReducedMotion.current) this
    else with(shared) {
        this@composed.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> ledgerNavigationTween() },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(androidx.compose.ui.layout.ContentScale.FillWidth)
        )
    }
}

@Composable fun MetricDetailHeader(type: String, month: YearMonth, summary: MonthlySummary?) {
    Surface(Modifier.padding(horizontal = LedgerSpace.margin, vertical = LedgerSpace.small).metricBounds("$type:$month").fillMaxWidth(),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(LedgerSpace.margin), verticalArrangement = Arrangement.spacedBy(LedgerSpace.small)) {
            Text(if (type == "INCOME") "Income this month" else "Spent this month", style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(money(if (type == "INCOME") it.income else it.spent), style = MaterialTheme.typography.headlineMedium) }
        }
    }
}
