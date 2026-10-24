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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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

/** Swipe a transaction row to ask for deletion; the confirmation dialog still decides, so the row always springs back. */
@Composable fun EntrySwipe(entry: Entry, shape: androidx.compose.ui.graphics.Shape, requestDelete: (Entry) -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            requestDelete(entry)
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Delete transaction") { requestDelete(entry); true })
        },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.error).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Delete", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.bodyLarge)
            }
        }
    ) { content() }
}
