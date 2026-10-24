package `in`.thomas.pocketledger

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class CategoryPickerUiTest {
    @get:Rule val ui = createComposeRule()

    private fun ready(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "in.thomas.pocketledger.validation")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            wakeUp(); executeShellCommand("wm dismiss-keyguard")
        }
        return context
    }

    private fun showScrollablePicker() {
        ready()
        ui.setContent {
            PocketTheme {
                var category by remember { mutableStateOf("Entertainment") }
                Pick("Category", category, expenseCategories, { it },
                    categoryPresentation = true, addCategory = {}) { category = it }
            }
        }
        ui.onNodeWithText("Category", substring = false).performClick()
        ui.onNode(hasText("Entertainment") and isSelected()).assertIsDisplayed()
    }

    /** Sample during motion as well as after settling: the original jump could spring back. */
    private fun assertChromeDuringMotion(gesture: () -> Unit) {
        val close = ui.onNodeWithText("Close", substring = false)
        val add = ui.onNodeWithText("Add category\u2026", substring = false)
        val closeBounds = close.fetchSemanticsNode().boundsInRoot
        val addBounds = add.fetchSemanticsNode().boundsInRoot
        fun checkBounds() {
            val actualClose = close.fetchSemanticsNode().boundsInRoot
            val actualAdd = add.fetchSemanticsNode().boundsInRoot
            assertEquals("Category header moved", closeBounds.top, actualClose.top, 1f)
            assertEquals("Category footer moved", addBounds.top, actualAdd.top, 1f)
            assertEquals("Category viewport resized", addBounds.bottom, actualAdd.bottom, 1f)
        }
        ui.mainClock.autoAdvance = false
        try {
            gesture()
            repeat(40) {
                ui.mainClock.advanceTimeByFrame()
                checkBounds()
            }
        } finally {
            ui.mainClock.autoAdvance = true
        }
        ui.waitForIdle()
        checkBounds()
    }

    @Test fun categoryBottomFlingsAndReversalsKeepHeaderAndAddFixed() {
        showScrollablePicker()
        val list = ui.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Other", substring = false))
        repeat(3) {
            assertChromeDuringMotion {
                list.performTouchInput {
                    swipeWithVelocity(center.copy(y = height * .75f), center.copy(y = height * .25f), 7000f)
                }
            }
            assertChromeDuringMotion {
                list.performTouchInput {
                    down(center.copy(y = height * .55f))
                    moveTo(center.copy(y = height * .3f), delayMillis = 80)
                    moveTo(center.copy(y = height * .65f), delayMillis = 80)
                    moveTo(center.copy(y = height * .35f), delayMillis = 80)
                    up()
                }
            }
        }
        ui.onNodeWithText("Close", substring = false).performClick()
        ui.onNodeWithText("Close", substring = false).assertDoesNotExist()
    }

    @Test fun categoryFlingKeepsSheetFixedUntilListSettlesAtTop() {
        showScrollablePicker()
        val list = ui.onNode(hasScrollAction())
        list.assert(SemanticsMatcher("Category list is scrolled") { node ->
            node.config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value() > 0f
        })
        // Use the non-actionable title so the gesture cannot activate Close.
        assertChromeDuringMotion {
            ui.onNode(hasText("Category", substring = false) and hasAnyAncestor(isDialog())).performTouchInput {
                swipeDown(startY = centerY, endY = centerY + 300f, durationMillis = 150)
            }
        }
        // ScrollToNode may stop at index zero once Groceries is visible. Start below the top.
        list.performScrollToIndex(expenseCategories.indexOf("Groceries"))
        list.assert(SemanticsMatcher("Fling starts below the list top") { node ->
            node.config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value() > 0f
        })
        assertChromeDuringMotion {
            list.performTouchInput {
                swipeWithVelocity(center.copy(y = height * .2f), center.copy(y = height * .8f), 9000f)
            }
        }
        ui.onNodeWithText("Uncategorized", substring = false).assertIsDisplayed()
        // Once settled at the top, the native sheet dismissal gesture remains available.
        list.performTouchInput {
            swipeDown(startY = height * .15f, endY = height * .8f, durationMillis = 150)
        }
        ui.onNodeWithText("Close", substring = false).assertDoesNotExist()
    }

    @Test fun captureCategoryIconsInBothThemes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ledger.visualReview") == "true")
        val context = ready()
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldMode = prefs.getString("mode", null)
        val oldDynamic = prefs.all["dynamic"] as? Boolean
        val output = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val scale = context.resources.configuration.fontScale
        var income by mutableStateOf(false)
        ui.setContent {
            PocketTheme {
                key(income) {
                    var category by remember { mutableStateOf("Uncategorized") }
                    Pick("Category", category,
                        (if (income) incomeCategories else expenseCategories) + "Synthetic custom category", { it },
                        categoryPresentation = true, addCategory = {}) { category = it }
                }
            }
        }
        try {
            for (mode in listOf("DARK", "LIGHT")) {
                ui.runOnIdle { prefs.edit().putString("mode", mode).putBoolean("dynamic", false).apply() }
                for (isIncome in listOf(false, true)) {
                    ui.runOnIdle { income = isIncome }
                    ui.onNodeWithText("Category", substring = false).performClick()
                    val options = (if (isIncome) incomeCategories else expenseCategories) + "Synthetic custom category"
                    for ((position, category) in listOf("top" to options.first(), "middle" to options[options.size / 2], "end" to options.last())) {
                        ui.onNode(hasScrollAction()).performScrollToNode(hasText(category, substring = false))
                        ui.waitForIdle()
                        Thread.sleep(350)
                        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        val name = "category-${mode.lowercase()}-${scale}x-${if (isIncome) "income" else "expense"}-$position.png"
                        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    ui.onNodeWithText("Close", substring = false).performClick()
                }
            }
        } finally {
            ui.runOnIdle {
                prefs.edit().apply {
                    if (oldMode == null) remove("mode") else putString("mode", oldMode)
                    if (oldDynamic == null) remove("dynamic") else putBoolean("dynamic", oldDynamic)
                }.apply()
            }
        }
    }

    @Test fun selectedCustomCategoryCloseAndAddPreserveDraftUntilSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "in.thomas.pocketledger.validation")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            wakeUp(); executeShellCommand("wm dismiss-keyguard")
        }
        var category by mutableStateOf("Synthetic custom category")
        var additions = 0
        ui.setContent {
            PocketTheme {
                Pick("Category", category, expenseCategories + "Synthetic custom category", { it },
                    categoryPresentation = true, addCategory = { additions++ }) { category = it }
            }
        }
        ui.onNodeWithText("Category", substring = false).performClick()
        ui.onNode(hasText("Synthetic custom category") and isSelected()).assertIsDisplayed()
        ui.onNodeWithText("Done", substring = false).assertDoesNotExist()
        ui.onNodeWithText("Close", substring = false).performClick()
        ui.runOnIdle { assertEquals("Synthetic custom category", category) }

        ui.onNodeWithText("Category", substring = false).performClick()
        ui.onNodeWithText("Add category\u2026", substring = false).assertIsDisplayed().performClick()
        ui.runOnIdle {
            assertEquals(1, additions)
            assertEquals("Synthetic custom category", category)
        }
        ui.onNodeWithText("Close", substring = false).assertDoesNotExist()

        ui.onNodeWithText("Category", substring = false).performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Groceries", substring = false))
        ui.onNodeWithText("Groceries", substring = false).performClick()
        ui.runOnIdle { assertEquals("Groceries", category) }
        ui.onNodeWithText("Close", substring = false).assertDoesNotExist()
        ui.onNodeWithText("Category", substring = false).performClick()
        ui.onNode(hasText("Groceries") and isSelected()).assertIsDisplayed()
    }
}
