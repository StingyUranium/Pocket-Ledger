package `in`.thomas.pocketledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Before
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
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Monthly balance").fetchSemanticsNodes().isNotEmpty() }
        InstrumentationRegistry.getArguments().getString("ledger.expectedHz")?.toFloat()?.let { expected ->
            ui.waitUntil(10_000) { kotlin.math.abs(ui.activity.window.decorView.display.refreshRate - expected) < 1f }
        }
    }

    @Test fun compactNavigationExposesAccountsInboxAndGraphs() {
        ready()
        ui.onNodeWithText("More").performClick()
        ui.onNodeWithText("Accounts").performClick()
        ui.onNodeWithText("Add account").assertExists()
        ui.onNodeWithContentDescription("Back").performClick()
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
        ui.onAllNodes(hasSetTextAction())[0].performTextInput("123.45")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("New transaction").assertExists()
        ui.onNodeWithText("123.45").assertExists()
        ui.onNodeWithText("Cancel").performClick()
    }
}
