package `in`.thomas.pocketledger

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpendingReviewUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun categorizeAndRemoveRecurringLimitWithoutChangingTransaction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "in.thomas.pocketledger.validation")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply { wakeUp(); executeShellCommand("wm dismiss-keyguard") }
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        val repo = Repository(context, db)
        val entry = Entry("review", "cash", type = "EXPENSE", amount = 12345, date = System.currentTimeMillis(), payee = "Synthetic merchant", note = "Keep this correction")
        try {
            db.dao().account(Account("cash", "Synthetic cash", "CASH", ""))
            db.dao().entry(entry)
            db.dao().budget(Budget("Travel", 10000))
            ui.setContent { PocketTheme { FoldAwareWindow { Ledger(repo) } } }
            ui.waitUntil(15000) { ui.onAllNodesWithText("Spent").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNode(hasText("Activity") and isSelectable()).performClick()
            ui.onNodeWithText("Needs category", substring = false).performClick()
            ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Choose category"))
            ui.onNodeWithText("Choose category").performClick()
            ui.onNodeWithContentDescription("Category name").performTextReplacement("Travel")
            ui.onNodeWithContentDescription("Remember for this payee").performClick()
            ui.onNodeWithText("Save").performClick()
            ui.waitUntil(10000) { runBlocking { db.dao().entry(entry.id)?.category == "Travel" } }
            assertEquals(entry.copy(category = "Travel"), db.dao().entry(entry.id))
            assertTrue(db.dao().allRules().any { it.merchant == "SYNTHETIC MERCHANT" && it.category == "Travel" })
            ui.onNode(hasText("Budgets") and isSelectable()).performClick()
            ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Travel", substring = false))
            ui.onNodeWithText("Travel", substring = false).performClick()
            ui.onNodeWithText("Remove budget", substring = false).performClick()
            ui.onNodeWithText("Remove", substring = false).performClick()
            ui.waitUntil(10000) { runBlocking { db.dao().allBudgets().isEmpty() } }
            assertEquals(entry.copy(category = "Travel"), db.dao().entry(entry.id))
        } finally { db.close() }
    }
}
