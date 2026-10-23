package `in`.thomas.pocketledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp

@Composable fun MessageSwipe(message: SmsRecord, dismiss: (SmsRecord) -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            dismiss(message)
            // A failed write must leave a usable row; repository observation removes it on success.
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = message.status == "REVIEW",
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer).padding(24.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    ) { content() }
}
