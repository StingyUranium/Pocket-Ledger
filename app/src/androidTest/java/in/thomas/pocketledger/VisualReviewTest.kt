package `in`.thomas.pocketledger

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** Opt-in physical-device screenshots; synthetic data stays in the validation installation. */
class VisualReviewTest {
    @get:Rule val ui = createComposeRule()

    @Test fun reviewPopulatedScreensInBothThemesAndLargeText() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ledger.visualReview") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "in.thomas.pocketledger.validation")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        val repo = Repository(context, db)
        val month = YearMonth.now(india)
        val date = month.atDay(10).atStartOfDay(india).toInstant().toEpochMilli()
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val output = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val scale = context.resources.configuration.fontScale
        try {
            db.dao().account(Account("cash", "Household and everyday expenses", "CASH", "", 123456789))
            db.dao().account(Account("bank", "Synthetic bank", "SBM", "0000", 100000))
            listOf("Groceries" to 600000L, "Transport" to 300000L,
                "Home maintenance and repairs" to 99999L, "Fees & charges" to 1L).forEachIndexed { i, (category, amount) ->
                db.dao().entry(Entry("expense-$i", "cash", type = "EXPENSE", amount = amount, date = date - i * 86400000L,
                    payee = if (i == 2) "Synthetic merchant with a long description" else "Synthetic merchant $i", category = category))
            }
            db.dao().entry(Entry("income", "cash", type = "INCOME", amount = 2500000, date = date, payee = "Synthetic employer", category = "Salary"))
            db.dao().entry(Entry("transfer", "cash", destinationId = "bank", type = "TRANSFER", amount = 10000, date = date, category = "Transfer"))
            db.dao().budget(Budget("Groceries", 400000)); db.dao().budget(Budget("Transport", 500000))
            db.dao().budget(Budget("Utilities", 100000))
            ui.setContent {
                PocketTheme { FoldAwareWindow { Ledger(repo) } }
            }
            ui.waitUntil(10000) { ui.onAllNodesWithText("Spent").fetchSemanticsNodes().isNotEmpty() }
            fun capture(name: String) {
                ui.waitForIdle()
                // Allow the physical display to present the frame after Compose becomes idle.
                Thread.sleep(350)
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            for (mode in listOf("DARK", "LIGHT")) {
                ui.runOnIdle {
                    prefs.edit().putString("mode", mode).putBoolean("dynamic", false).apply()
                }
                val prefix = "${mode.lowercase()}-${scale.toInt()}x"
                ui.onNode(hasText("Overview") and isSelectable()).performClick()
                ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(0)
                capture("$prefix-overview")
                ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Groceries", substring = false))
                capture("$prefix-breakdown")
                ui.onNode(hasText("Activity") and isSelectable()).performClick()
                capture("$prefix-activity")
                ui.onNodeWithContentDescription("Add transaction").performClick()
                capture("$prefix-editor")
                ui.onNodeWithText("Cancel").performClick()
                ui.onNode(hasText("Budgets") and isSelectable()).performClick()
                capture("$prefix-budgets")
                ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Utilities"))
                capture("$prefix-budget-rows")
                ui.onNodeWithText("Manage").performClick()
                capture("$prefix-more")
                ui.onNodeWithText("Accounts", substring = false).performClick()
                capture("$prefix-accounts")
                ui.onNodeWithContentDescription("Back to", substring = true).performClick()
                ui.onNodeWithContentDescription("Back to", substring = true).performClick()
                ui.onNode(hasText("Overview") and isSelectable()).performClick()
                ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Insights", substring = false))
                ui.onNodeWithText("Insights").performClick()
                ui.waitUntil(10000) { ui.onAllNodesWithText("Where it goes").fetchSemanticsNodes().isNotEmpty() }
                capture("$prefix-graphs")
                ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Home maintenance and repairs"))
                capture("$prefix-graph-categories")
                ui.onNodeWithContentDescription("Back to", substring = true).performClick()
                ui.onNodeWithText("Inbox").performClick()
                capture("$prefix-inbox")
                ui.onNode(hasText("Overview") and isSelectable()).performClick()
                ui.onNodeWithText("Manage").performClick()
                ui.onNodeWithText("Settings and backups").performClick()
                capture("$prefix-settings")
                ui.onNodeWithContentDescription("Back to", substring = true).performClick()
                ui.onNodeWithContentDescription("Back to", substring = true).performClick()
                ui.onNode(hasText("Overview") and isSelectable()).performClick()
            }
        } finally { db.close() }
    }
}
