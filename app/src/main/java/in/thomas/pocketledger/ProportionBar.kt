package `in`.thomas.pocketledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** A data bar: continuous track, exact proportional bounds, and no minimum-size progress dot. */
@Composable internal fun ProportionBar(progress: Float, color: Color, trackColor: Color, modifier: Modifier = Modifier) {
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    Canvas(modifier.clip(CircleShape).progressSemantics(fraction)) {
        drawRect(trackColor)
        if (fraction > 0f) drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
    }
}
