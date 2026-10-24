package `in`.thomas.pocketledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Shared layout rhythm. Touch targets remain independent of visible icon size. */
object LedgerSpace {
    val xs = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val content = 16.dp
    val section = 20.dp
    val generous = 24.dp
    val touch = 48.dp
    /** Standard horizontal screen margin. */
    val margin = 16.dp
}

object LedgerMotion {
    const val fast = 120
    const val standard = 200
    fun <T> responsive(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 400f)
}

/**
 * For custom rows and surfaces; Material buttons keep their built-in interaction handling.
 * TODO: migrate the composed {} modifiers in Interaction.kt, FluidMotion.kt and MetricTransition.kt to
 * Modifier.Node implementations. composed {} allocates per-instance state during composition and
 * cannot be skipped; the migration needs its own focused device validation, so it is not done here.
 */
fun Modifier.ledgerClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    premiumPressEffect(interaction, enabled, pressedScale = .993f)
        .clickable(interactionSource = interaction, indication = null,
            enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

/** Observe the control's own interactions: scrolling/cancellation and semantics stay native. */
fun Modifier.premiumPressEffect(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = .985f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    // One animation per control: lists create this for every row, and the value is only read at draw time.
    val press by animateFloatAsState(
        if (pressed && enabled) 1f else 0f,
        if (reduced) snap() else LedgerMotion.responsive(), label = "press"
    )
    graphicsLayer {
        val scale = if (reduced) 1f else 1f - (1f - pressedScale) * press
        scaleX = scale; scaleY = scale; alpha = 1f - .12f * press
    }
}
