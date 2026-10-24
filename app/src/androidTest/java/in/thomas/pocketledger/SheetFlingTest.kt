package `in`.thomas.pocketledger

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SheetFlingTest {
    @get:Rule val ui = createComposeRule()

    @Before fun showSheet() {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "in.thomas.pocketledger.validation")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            wakeUp()
            executeShellCommand("wm dismiss-keyguard")
        }
        ui.setContent {
            PocketTheme {
                val visible = remember { mutableStateOf(true) }
                if (visible.value) LedgerSheet("Fling test", { visible.value = false }) {
                    repeat(18) { Text("Field $it", Modifier.height(64.dp)) }
                }
            }
        }
        ui.waitForIdle()
    }

    @Test fun fastDownwardFlingReturnsToFormTopWithoutMovingSheet() {
        val top = ui.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot.top
        repeat(3) {
            ui.onNodeWithText("Field 12").performScrollTo()
            ui.onNodeWithText("Field 0").assertIsNotDisplayed()
            ui.onNode(hasScrollAction()).performTouchInput {
                swipeWithVelocity(center.copy(y = height * .25f), center.copy(y = height * .65f), 9000f)
            }
            ui.onNodeWithText("Field 0").assertIsDisplayed()
            assertEquals(top, ui.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
    }

    @Test fun downwardSwipeAtFormTopStillDismissesSheet() {
        ui.onNode(hasScrollAction()).performTouchInput {
            swipeDown(startY = height * .15f, endY = height * .8f, durationMillis = 150)
        }
        ui.onNodeWithText("Fling test").assertDoesNotExist()
    }

    @Test fun sheetCannotTakeDownwardDragWhileFormIsScrolled() {
        ui.onNodeWithText("Field 12").performScrollTo()
        val top = ui.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot.top
        ui.onNodeWithText("Fling test").performTouchInput {
            swipe(center, center + Offset(0f, 500f), durationMillis = 100)
        }
        ui.onNodeWithText("Fling test").assertIsDisplayed()
        assertEquals(top, ui.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
