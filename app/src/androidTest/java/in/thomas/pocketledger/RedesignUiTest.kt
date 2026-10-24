package `in`.thomas.pocketledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.BackEventCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.Assert.assertEquals
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import android.view.WindowManager
import org.junit.runner.RunWith

/** Run only with -Pledger.testApplicationId=in.thomas.pocketledger.validation. */
@RunWith(AndroidJUnit4::class)
class RedesignUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Before fun wakeDevice() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            wakeUp()
            executeShellCommand("wm dismiss-keyguard")
        }
        ui.runOnUiThread { ui.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    private fun ready() {
        check(ui.activity.packageName == "in.thomas.pocketledger.validation")
        ui.waitForIdle()
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Monthly balance").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        InstrumentationRegistry.getArguments().getString("ledger.expectedHz")?.toFloat()?.let { expected ->
            ui.waitUntil(10_000) { kotlin.math.abs(ui.activity.window.decorView.display.refreshRate - expected) < 1f }
        }
    }

    @Test fun compactNavigationExposesAccountsInboxAndGraphs() {
        ready()
        ui.onNodeWithText("More").performClick()
        ui.onNodeWithText("Accounts").performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Add account"))
        ui.onNodeWithText("Add account").assertExists()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Accounts").performClick()
        ui.onNode(hasText("More") and !hasContentDescription("Back")).performClick()
        ui.onNodeWithText("Inbox").performClick()
        ui.onNodeWithText("Automatic capture").assertExists()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Graphs").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Where it goes").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun metricNavigationAndEditorDismissalReturnToOverview() {
        ready()
        ui.onNodeWithText("Spent").performClick()
        ui.onNodeWithText("Spent this month").assertExists()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithContentDescription("Add transaction").performClick()
        ui.onNodeWithText("New transaction").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Monthly balance").assertExists()
    }

    @Test fun metricPagesSettleAfterRepeatedOpenAndBack() {
        ready()
        repeat(3) {
            ui.onNodeWithText("Spent").performClick()
            ui.onNodeWithText("Spent this month").assertIsDisplayed()
            ui.onNodeWithText("Monthly balance").assertDoesNotExist()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.onNodeWithText("Monthly balance").assertIsDisplayed()
            ui.onNodeWithText("Spent this month").assertDoesNotExist()
            ui.onNodeWithText("Income").performClick()
            ui.onNodeWithText("Income this month").assertIsDisplayed()
            ui.onNodeWithText("Spent this month").assertDoesNotExist()
            ui.onNodeWithText("Monthly balance").assertDoesNotExist()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.onNodeWithText("Monthly balance").assertIsDisplayed()
            ui.onNodeWithText("Income this month").assertDoesNotExist()
        }
    }

    @Test fun predictiveBackCancellationAndCompletionKeepMetricAndHeaderTogether() {
        ready()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Updating ledger...").fetchSemanticsNodes().isEmpty() }
        val overviewTop = ui.onNodeWithText("Monthly balance").fetchSemanticsNode().boundsInRoot.top
        ui.onNodeWithText("Spent").performClick()
        ui.onNodeWithText("Spent this month").assertIsDisplayed()
        val dispatcher = ui.activity.onBackPressedDispatcher
        fun previewBack() {
            ui.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT)) }
            ui.waitForIdle()
            ui.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 400f, .6f, BackEventCompat.EDGE_LEFT)) }
            ui.waitForIdle()
            assertEquals("Overview stays in place behind the moving metric card", overviewTop,
                ui.onNodeWithText("Monthly balance").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        previewBack()
        ui.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        ui.waitForIdle()
        ui.onNodeWithText("Spent").assertIsDisplayed()
        ui.onNodeWithText("Spent this month").assertIsDisplayed()
        ui.onNodeWithText("Monthly balance").assertDoesNotExist()
        previewBack()
        ui.runOnUiThread { dispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.onNodeWithText("Monthly balance").assertIsDisplayed()
        ui.onNodeWithText("Spent this month").assertDoesNotExist()
        ui.onNodeWithContentDescription("Back").assertDoesNotExist()
        ui.onNodeWithText("Income").performClick()
        ui.onNodeWithText("Income this month").assertIsDisplayed()
        ui.onNodeWithText("Spent this month").assertDoesNotExist()
    }

    @Test fun appearanceControlsAndRecurringBudgetsAreAvailable() {
        ready()
        ui.onNodeWithText("Budgets").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Category budgets").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Category budgets").assertExists()
        ui.onNodeWithContentDescription("Settings").performClick()
        ui.onNodeWithText("High contrast").performScrollTo().assertExists()
        ui.onNodeWithText("Reduce motion").performScrollTo().assertExists()
    }

    @Test fun transactionDraftSurvivesActivityRecreation() {
        ready()
        ui.onNodeWithContentDescription("Add transaction").performClick()
        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(hasSetTextAction())[0].performTextInput("123.45")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("New transaction").assertExists()
        ui.onNodeWithText("123.45").assertExists()
        ui.onNodeWithText("Cancel").performClick()
    }
    @Test fun budgetSegmentsSettleAndRetainSelectionAcrossTabs() {
        ready()
        ui.onNodeWithText("Budgets").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Category budgets").fetchSemanticsNodes().isNotEmpty() }
        repeat(3) {
            ui.onNode(hasText("50 / 30 / 20") and hasClickAction()).performClick()
            ui.onAllNodesWithText("50 / 30 / 20").filter(isSelected()).assertCountEquals(1)
            ui.onNodeWithText("Category budgets").performClick().assertIsSelected()
        }
        ui.onNodeWithText("Overview").performClick()
        ui.onNodeWithText("Budgets").performClick()
        ui.onNodeWithText("Category budgets").assertIsSelected()
        ui.onNodeWithText("Set a budget").assertIsDisplayed()
    }

    @Test fun searchCanBeClearedWithoutLosingTheActivityDestination() {
        ready()
        ui.onNodeWithText("Activity").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("no-matching-payee")
        ui.onNodeWithContentDescription("Clear search").performClick()
        ui.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        ui.onNode(hasSetTextAction()).assertTextContains("")
        ui.onNode(hasText("Activity") and isSelected()).assertIsSelected()
    }
    @Test fun overviewLargeTitleAndMonthCollapseWhilePinnedControlsRemain() {
        ready()
        ui.onNodeWithContentDescription("Settings").assertIsDisplayed()
        ui.onNodeWithContentDescription("Previous month").assertIsDisplayed()
        ui.onNode(hasScrollAction()).performTouchInput { swipeUp(startY = height * .75f, endY = height * .25f) }
        ui.onNodeWithContentDescription("Previous month").assertIsNotDisplayed()
        ui.onNodeWithContentDescription("Settings").assertIsDisplayed()
        ui.onNodeWithContentDescription("Add transaction").assertIsDisplayed()
        ui.onNode(hasText("Overview") and isSelected()).assertIsDisplayed()
        ui.onNode(hasScrollAction()).performTouchInput { swipeDown(startY = height * .25f, endY = height * .75f) }
        ui.onNode(hasScrollAction()).performTouchInput { swipeDown(startY = height * .25f, endY = height * .75f) }
        ui.onNodeWithContentDescription("Previous month").assertIsDisplayed()
        ui.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test fun settingsKeepsBackPinnedWhileTitleCollapsesAndBackStillReturns() {
        ready()
        ui.onNodeWithContentDescription("Settings").performClick()
        ui.onNodeWithContentDescription("Back").assertIsDisplayed()
        ui.onNode(hasScrollAction()).performTouchInput { swipeUp(startY = height * .75f, endY = height * .25f) }
        ui.onNodeWithContentDescription("Back").assertIsDisplayed()
        ui.onNode(hasScrollAction()).performTouchInput { swipeDown(startY = height * .25f, endY = height * .75f) }
        ui.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("Settings").assertIsDisplayed()
        ui.onNodeWithText("Monthly balance").assertIsDisplayed()
    }
}
