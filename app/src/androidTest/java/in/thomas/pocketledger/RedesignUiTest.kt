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
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Spent").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        InstrumentationRegistry.getArguments().getString("ledger.expectedHz")?.toFloat()?.let { expected ->
            ui.waitUntil(10_000) { kotlin.math.abs(ui.activity.window.decorView.display.refreshRate - expected) < 1f }
        }
    }

    @Test fun compactNavigationExposesReviewAndManagement() {
        ready()
        ui.onNodeWithText("Inbox").performClick()
        ui.onNodeWithText("Bank messages").assertExists()
        ui.onNodeWithText("Manage").performClick()
        ui.onNodeWithText("Accounts").performClick()
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Add account"))
        ui.onNodeWithText("Add account").assertExists()
        ui.onNodeWithContentDescription("Back to", substring = true).performClick()
        ui.onNodeWithText("Income plan").performClick()
        ui.onNodeWithText("50 / 30 / 20").assertExists()
        ui.onNodeWithContentDescription("Back to", substring = true).performClick()
        ui.onNodeWithContentDescription("Back to", substring = true).performClick()
        ui.onNode(hasText("Overview") and isSelectable()).performClick()
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Insights"))
        ui.onNodeWithText("Insights").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Where it goes").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun metricNavigationAndEditorDismissalReturnToOverview() {
        ready()
        ui.onNodeWithText("Spent").performClick()
        ui.onNodeWithText("Spent this month").assertExists()
        ui.onNodeWithContentDescription("Back to", substring = true).performClick()
        ui.onNodeWithContentDescription("Add transaction").performClick()
        ui.onNodeWithText("New transaction").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Spent").assertExists()
    }

    @Test fun metricPagesSettleAfterRepeatedOpenAndBack() {
        ready()
        repeat(3) {
            ui.onNodeWithText("Spent").performClick()
            ui.onNodeWithText("Spent this month").assertIsDisplayed()
            ui.onNodeWithText("Income remaining").assertDoesNotExist()
            ui.onNodeWithContentDescription("Back to", substring = true).performClick()
            ui.onNodeWithText("Spent").assertIsDisplayed()
            ui.onNodeWithText("Spent this month").assertDoesNotExist()
            ui.onNodeWithText("Income").performClick()
            ui.onNodeWithText("Income this month").assertIsDisplayed()
            ui.onNodeWithText("Spent this month").assertDoesNotExist()
            ui.onNodeWithText("Income remaining").assertDoesNotExist()
            ui.onNodeWithContentDescription("Back to", substring = true).performClick()
            ui.onNodeWithText("Spent").assertIsDisplayed()
            ui.onNodeWithText("Income this month").assertDoesNotExist()
        }
    }

    @Test fun predictiveBackCancellationAndCompletionKeepMetricAndHeaderTogether() {
        ready()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Updating ledger...").fetchSemanticsNodes().isEmpty() }
        val overviewTop = ui.onNodeWithText("Income remaining").fetchSemanticsNode().boundsInRoot.top
        ui.onNodeWithText("Spent").performClick()
        ui.onNodeWithText("Spent this month").assertIsDisplayed()
        val dispatcher = ui.activity.onBackPressedDispatcher
        fun previewBack() {
            ui.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT)) }
            ui.waitForIdle()
            ui.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 400f, .6f, BackEventCompat.EDGE_LEFT)) }
            ui.waitForIdle()
            assertEquals("Overview stays in place behind the moving metric card", overviewTop,
                ui.onNodeWithText("Income remaining").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        previewBack()
        ui.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        ui.waitForIdle()
        ui.onNodeWithText("Spent").assertIsDisplayed()
        ui.onNodeWithText("Spent this month").assertIsDisplayed()
        ui.onNodeWithText("Income remaining").assertDoesNotExist()
        previewBack()
        ui.runOnUiThread { dispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.onNodeWithText("Spent").assertIsDisplayed()
        ui.onNodeWithText("Spent this month").assertDoesNotExist()
        ui.onNodeWithContentDescription("Back to", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Income").performClick()
        ui.onNodeWithText("Income this month").assertIsDisplayed()
        ui.onNodeWithText("Spent this month").assertDoesNotExist()
    }

    @Test fun appearanceControlsAndRecurringBudgetsAreAvailable() {
        ready()
        ui.onNodeWithText("Budgets").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Budget remaining").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Budget remaining").assertExists()
        ui.onNodeWithText("Manage").performClick()
        ui.onNodeWithText("Settings and backups").performClick()
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
    @Test fun transactionTypeHasSeparateCategoriesAndRemembersSelections() {
        ready()
        ui.onNodeWithContentDescription("Add transaction").performClick()
        ui.onNode(hasText("Income", substring = false) and isSelectable()).performClick()
        ui.onNodeWithText("Category", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Salary").assertIsDisplayed().performClick()
        ui.onNode(hasText("Expense", substring = false) and isSelectable()).performScrollTo().performClick()
        ui.onNodeWithText("Category", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Food").assertIsDisplayed()
        ui.onNodeWithText("Salary").assertDoesNotExist()
        ui.onNodeWithText("Groceries").performClick()
        ui.onNode(hasText("Income", substring = false) and isSelectable()).performScrollTo().performClick()
        ui.onNodeWithText("Salary").performScrollTo().assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Salary").performScrollTo().assertIsDisplayed()
        ui.onNode(hasText("Expense", substring = false) and isSelectable()).performScrollTo().performClick()
        ui.onNodeWithText("Groceries").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Cancel").performClick()
    }
    @Test fun incomePlanIsSeparateFromRecurringBudgets() {
        ready()
        ui.onNodeWithText("Budgets").performClick()
        ui.onNodeWithContentDescription("Add budget").performClick()
        ui.onNodeWithText("Category budget").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithText("Manage").performClick()
        ui.onNodeWithText("Income plan").performClick()
        ui.onNodeWithText("50 / 30 / 20").assertExists()
        ui.onNodeWithText("Category budget").assertDoesNotExist()
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
        ui.onNodeWithText("Manage").assertIsDisplayed()
        ui.onNodeWithContentDescription("Previous month").assertIsDisplayed()
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp(startY = height * .75f, endY = height * .25f) }
        ui.onNodeWithContentDescription("Previous month").assertIsNotDisplayed()
        ui.onNodeWithText("Manage").assertIsDisplayed()
        ui.onNodeWithContentDescription("Add transaction").assertIsDisplayed()
        ui.onNode(hasText("Overview") and isSelected()).assertIsDisplayed()
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeDown(startY = height * .25f, endY = height * .75f) }
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeDown(startY = height * .25f, endY = height * .75f) }
        ui.onNodeWithContentDescription("Previous month").assertIsDisplayed()
        ui.onNodeWithText("Manage").assertIsDisplayed()
    }

    @Test fun settingsKeepsBackPinnedWhileTitleCollapsesAndBackStillReturns() {
        ready()
        ui.onNodeWithText("Manage").performClick()
        ui.onNodeWithText("Settings and backups").performClick()
        ui.onNodeWithContentDescription("Back to", substring = true).assertIsDisplayed()
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp(startY = height * .75f, endY = height * .25f) }
        ui.onNodeWithContentDescription("Back to", substring = true).assertIsDisplayed()
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeDown(startY = height * .25f, endY = height * .75f) }
        ui.onNodeWithContentDescription("Back to", substring = true).assertIsDisplayed().performClick()
        ui.onNodeWithText("Settings and backups").assertExists()
    }

    @Test fun widgetAndCategoryNotificationRoutesRemainSupported() {
        ready()
        fun launch(intent: android.content.Intent) {
            ui.runOnUiThread {
                // Keep ActivityScenario's launch identity while delivering the external route extras.
                val delivered = android.content.Intent(ui.activity.intent).apply {
                    removeExtra("ledger_route")
                    removeExtra(CategoryNotifications.EXTRA_ENTRY_ID)
                    intent.extras?.let { putExtras(it) }
                }
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(ui.activity, delivered)
            }
            ui.waitForIdle()
        }
        launch(android.content.Intent(ui.activity, MainActivity::class.java).putExtra("ledger_route", "budget"))
        ui.waitUntil(10000) { ui.onAllNodesWithText("Budget remaining").fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasText("Budgets") and isSelected()).assertIsSelected()
        launch(android.content.Intent(ui.activity, MainActivity::class.java).putExtra("ledger_route", "expense"))
        ui.onNodeWithText("Spent this month").assertExists()
        ui.onNodeWithContentDescription("Back to", substring = true).performClick()
        launch(android.content.Intent(ui.activity, MainActivity::class.java).putExtra("ledger_route", "income"))
        ui.onNodeWithText("Income this month").assertExists()
        ui.onNodeWithContentDescription("Back to", substring = true).performClick()
        val repo = (ui.activity.application as LedgerApp).repo
        val entry = kotlinx.coroutines.runBlocking {
            val account = repo.dao.allAccounts().first()
            Entry(java.util.UUID.randomUUID().toString(), account.id, type = "EXPENSE", amount = 12345,
                date = System.currentTimeMillis(), payee = "Synthetic notification review").also { repo.save(it) }
        }
        try {
            launch(android.content.Intent(ui.activity, MainActivity::class.java).putExtra(CategoryNotifications.EXTRA_ENTRY_ID, entry.id))
            ui.waitUntil(10000) { ui.onAllNodesWithText("Transaction", substring = false).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription("Payee or description").assertTextContains("Synthetic notification review")
            ui.onNodeWithText("Cancel").performClick()
            ui.onNode(hasText("Activity") and isSelected()).assertIsSelected()
        } finally { kotlinx.coroutines.runBlocking { repo.delete(entry) } }
    }
}
