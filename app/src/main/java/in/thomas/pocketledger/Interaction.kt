package `in`.thomas.pocketledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
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
    val large = 32.dp
    val touch = 48.dp
}

object LedgerMotion {
    const val fast = 120
    const val standard = 200
    fun <T> responsive(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 400f)
}

/** For custom rows and surfaces; Material buttons keep their built-in interaction handling. */
fun Modifier.ledgerClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    premiumPressEffect(interaction, enabled, pressedScale = .993f)
        .clickable(interactionSource = interaction, indication = LocalIndication.current,
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
    val scale by animateFloatAsState(
        if (pressed && enabled && !reduced) pressedScale else 1f,
        if (reduced) snap() else LedgerMotion.responsive(), label = "press scale"
    )
    val opacity by animateFloatAsState(
        if (pressed && enabled) .88f else 1f,
        if (reduced) snap() else tween(LedgerMotion.fast), label = "press tone"
    )
    graphicsLayer { scaleX = scale; scaleY = scale; alpha = opacity }
}
