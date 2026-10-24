package `in`.thomas.pocketledger

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises real RemoteViews inflation/actions; all fixtures and preferences are validation-only. */
class WidgetRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun renderWidgetsAcrossSizesThemesAndFontScales() {
        check(context.packageName == "in.thomas.pocketledger.validation")
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        val output = File(context.getExternalFilesDir(null), "widget-review").apply { mkdirs() }
        val fixtures = listOf(
            "normal" to MonthlySummary(2500000, 1234567, 1500000, 1234567),
            "empty" to MonthlySummary(0, 0, 0, 0),
            "over" to MonthlySummary(100000000000, 99999999999, 10000, 99999999999)
        )
        for (night in listOf(false, true)) for (scale in listOf(1f, 2f)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                fontScale = scale
            }
            val themed = context.createConfigurationContext(configuration)
            for ((name, summary) in fixtures) for (income in listOf(false, true)) {
                for ((width, height) in listOf(160 to 112, 240 to 144, 320 to 220)) {
                    val views = LedgerWidgets.monthlyViews(themed, 900001, summary, "October 2026", income, height)
                    capture(themed, views, width, height, File(output, "monthly-$night-$scale-$name-$income-$width.png")) { root ->
                        val total = root.findViewById<TextView>(R.id.widget_total)
                        assertEquals(money(if (income) summary.income else summary.spent), total.text.toString())
                        assertTrue(total.contentDescription.contains(total.text))
                        assertTouchHeight(themed, total)
                        assertTouchHeight(themed, root.findViewById(R.id.widget_income_tab))
                        assertTouchHeight(themed, root.findViewById(R.id.widget_expense_tab))
                        for (tabId in listOf(R.id.widget_income_tab, R.id.widget_expense_tab)) {
                            val tab = root.findViewById<TextView>(tabId)
                            assertTrue("Full tab label must fit at $width dp / $scale: ${tab.text} (${tab.paint.measureText(tab.text.toString())}px in ${tab.width}px)", tab.paint.measureText(tab.text.toString()) <= tab.width - tab.totalPaddingLeft - tab.totalPaddingRight)
                        }
                        assertEquals(if (height >= 144 * scale) View.VISIBLE else View.GONE,
                            root.findViewById<View>(R.id.widget_title).visibility)
                    }
                }
            }
            for ((name, summary) in fixtures) for ((width, height) in listOf(220 to 110, 280 to 144, 320 to 220)) {
                val views = LedgerWidgets.budgetViews(themed, summary, "October 2026", height)
                capture(themed, views, width, height, File(output, "budget-$night-$scale-$name-$width.png")) { root ->
                    val amount = root.findViewById<TextView>(R.id.widget_remaining)
                    val label = root.findViewById<TextView>(R.id.widget_budget_label)
                    assertTrue("Label line must fit vertically", label.layout.getLineBottom(0) <= label.height - label.totalPaddingTop - label.totalPaddingBottom)
                    assertEquals(if (summary.budgetLimit > 0) money(summary.budgetRemaining) else themed.getString(R.string.widget_no_budget), amount.text.toString())
                    assertEquals(themed.getColor(if (summary.budgetRemaining < 0) R.color.widget_expense else R.color.widget_text), amount.currentTextColor)
                    assertTrue(root.findViewById<View>(R.id.widget_root).contentDescription.contains(amount.text))
                }
            }
        }
    }

    @Test fun incomeSelectionIsIndependentAndPersistsAcrossRendering() {
        check(context.packageName == "in.thomas.pocketledger.validation")
        val host = AppWidgetHost(context, 82741)
        val first = host.allocateAppWidgetId()
        val second = host.allocateAppWidgetId()
        val prefs = context.getSharedPreferences("widget_tabs", Context.MODE_PRIVATE)
        try {
            val summary = MonthlySummary(20000, 10000, 0, 0)
            instrumentation.runOnMainSync {
                val root = LedgerWidgets.monthlyViews(context, first, summary, "October 2026", false, 112).apply(context, null)
                assertTrue(root.findViewById<View>(R.id.widget_income_tab).performClick())
            }
            val deadline = System.currentTimeMillis() + 5000
            while (!prefs.getBoolean("income_$first", false) && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertTrue(prefs.getBoolean("income_$first", false))
            assertFalse(prefs.getBoolean("income_$second", false))
            instrumentation.runOnMainSync {
                val root = LedgerWidgets.monthlyViews(context, first, summary, "October 2026", prefs.getBoolean("income_$first", false), 112).apply(context, null)
                assertEquals(money(summary.income), root.findViewById<TextView>(R.id.widget_total).text.toString())
                assertTrue(root.findViewById<View>(R.id.widget_expense_tab).performClick())
            }
            val resetDeadline = System.currentTimeMillis() + 5000
            while (prefs.getBoolean("income_$first", false) && System.currentTimeMillis() < resetDeadline) Thread.sleep(50)
            assertFalse(prefs.getBoolean("income_$first", false))
        } finally {
            host.deleteAppWidgetId(first)
            host.deleteAppWidgetId(second)
            prefs.edit().remove("income_$first").remove("income_$second").apply()
        }
    }

    @Test fun amountAndBudgetTapsOpenMatchingDestinations() {
        check(context.packageName == "in.thomas.pocketledger.validation")
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        val summary = MonthlySummary(20000, 10000, 30000, 10000)
        for (route in listOf("income", "expense", "budget")) {
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync {
                    val views = if (route == "budget") LedgerWidgets.budgetViews(context, summary, "October 2026", 110)
                        else LedgerWidgets.monthlyViews(context, 900001, summary, "October 2026", route == "income", 112)
                    val root = views.apply(context, null)
                    val target = root.findViewById<View>(if (route == "budget") R.id.widget_root else R.id.widget_total)
                    assertTrue(target.performClick())
                }
                val activity = monitor.waitForActivityWithTimeout(10000)
                assertNotNull("Widget must open $route", activity)
                assertEquals(route, activity.intent.getStringExtra("ledger_route"))
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    private fun assertTouchHeight(context: Context, view: View) {
        assertTrue("Touch target was ${view.height}px", view.height >= (48 * context.resources.displayMetrics.density).toInt())
    }

    private fun capture(context: Context, views: android.widget.RemoteViews, width: Int, height: Int, file: File, verify: (View) -> Unit) {
        instrumentation.runOnMainSync {
            val density = context.resources.displayMetrics.density
            val w = (width * density).toInt()
            val h = (height * density).toInt()
            val host = AppWidgetHostView(context)
            host.setPadding(0, 0, 0, 0)
            host.updateAppWidget(views)
            host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, w, h)
            val root = host.findViewById<View>(R.id.widget_root)
            assertNotNull("RemoteViews must inflate successfully", root)
            verify(root)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(105, 120, 145))
            host.draw(canvas)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
