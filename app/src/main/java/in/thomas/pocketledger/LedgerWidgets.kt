package `in`.thomas.pocketledger

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.util.Log
import android.widget.RemoteViews
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MonthlyWidgetProvider : LedgerWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == LedgerWidgets.SELECT_TAB) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                context.getSharedPreferences("widget_tabs", Context.MODE_PRIVATE).edit()
                    .putBoolean("income_$id", intent.getBooleanExtra("income", false)).apply()
            }
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences("widget_tabs", Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove("income_$it") }
        editor.apply()
    }
}
class BudgetWidgetProvider : LedgerWidgetProvider()

open class LedgerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, LedgerWidgets.SELECT_TAB)) refresh(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { LedgerWidgets.refresh(context.applicationContext) }
            catch (error: Exception) { Log.e("LedgerWidgets", "Could not update widgets", error) }
            finally { pending.finish() }
        }
    }
}

object LedgerWidgets {
    const val SELECT_TAB = "in.thomas.pocketledger.widget.SELECT_TAB"
    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(MonthlyWidgetProvider::class.java, BudgetWidgetProvider::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }

    suspend fun refresh(context: Context) {
        if (!hasWidgets(context)) return
        val db = (context.applicationContext as LedgerApp).db
        val data = db.withTransaction { db.dao().allEntries() to db.dao().allBudgets() }
        render(context, data.first, data.second)
    }

    fun render(context: Context, entries: List<Entry>, budgets: List<Budget>) {
        val month = YearMonth.now(india)
        val summary = monthlySummary(entries, budgets, month)
        val manager = AppWidgetManager.getInstance(context)
        val title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        manager.getAppWidgetIds(ComponentName(context, MonthlyWidgetProvider::class.java)).forEach { id ->
            val income = context.getSharedPreferences("widget_tabs", Context.MODE_PRIVATE).getBoolean("income_$id", false)
            manager.updateAppWidget(id, responsiveViews(manager.getAppWidgetOptions(id), 112) { height ->
                monthlyViews(context, id, summary, title, income, height)
            })
        }
        manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java)).forEach { id ->
            manager.updateAppWidget(id, responsiveViews(manager.getAppWidgetOptions(id), 110) { height ->
                budgetViews(context, summary, title, height)
            })
        }
    }

    /** Launcher-provided sizes are in dp, excluding host padding. Keep old hosts resize-aware too. */
    private fun responsiveViews(options: Bundle, fallbackHeight: Int, create: (Int) -> RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty() && sizes.size <= 16) {
                return RemoteViews(sizes.associateWith { create(it.height.toInt()) })
            }
        }
        val portrait = create(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, fallbackHeight))
        val landscape = create(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallbackHeight))
        // A host can omit options entirely during its first update.
        return if (options.isEmpty) create(fallbackHeight) else RemoteViews(landscape, portrait)
    }

    private fun showContext(context: Context, height: Int): Boolean =
        height >= 144 * context.resources.configuration.fontScale.coerceAtLeast(1f)

    internal fun monthlyViews(context: Context, id: Int, summary: MonthlySummary, title: String, income: Boolean, height: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_monthly)
        val amount = money(if (income) summary.income else summary.spent)
        val label = context.getString(if (income) R.string.widget_income_label else R.string.widget_expenses_label)
        views.setTextViewText(R.id.widget_title, title)
        views.setViewVisibility(R.id.widget_title, if (showContext(context, height)) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_total, amount)
        views.setTextViewText(R.id.widget_expense_tab, context.getString(
            if (context.resources.configuration.fontScale > 1.3f) R.string.widget_spent_label else R.string.widget_expenses_label))
        views.setContentDescription(R.id.widget_total, context.getString(R.string.widget_total_accessibility, title, label, amount))
        listOf(R.id.widget_income_tab to true, R.id.widget_expense_tab to false).forEach { (viewId, tabIncome) ->
            val selected = income == tabIncome
            views.setTextColor(viewId, context.getColor(if (selected) R.color.widget_accent else R.color.widget_secondary))
            views.setInt(viewId, "setBackgroundResource", if (selected) R.drawable.widget_selected else android.R.color.transparent)
            val tabLabel = context.getString(if (tabIncome) R.string.widget_income_label else R.string.widget_expenses_label)
            views.setContentDescription(viewId, context.getString(R.string.widget_tab_accessibility, tabLabel,
                context.getString(if (selected) R.string.widget_selected_label else R.string.widget_not_selected_label)))
            views.setOnClickPendingIntent(viewId, selectTab(context, id, tabIncome))
        }
        val destination = open(context, if (income) "income" else "expense")
        views.setOnClickPendingIntent(R.id.widget_root, destination)
        views.setOnClickPendingIntent(R.id.widget_total, destination)
        return views
    }

    internal fun budgetViews(context: Context, summary: MonthlySummary, title: String, height: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_budget)
        val hasBudget = summary.budgetLimit > 0
        val remaining = if (hasBudget) money(summary.budgetRemaining) else context.getString(R.string.widget_no_budget)
        val usage = if (hasBudget) context.getString(R.string.widget_budget_usage, money(summary.budgetSpent), money(summary.budgetLimit))
            else context.getString(R.string.widget_budget_preview)
        val expanded = showContext(context, height)
        views.setTextViewText(R.id.widget_title, title)
        views.setViewVisibility(R.id.widget_title, if (expanded) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_remaining, remaining)
        views.setTextColor(R.id.widget_remaining, context.getColor(if (summary.budgetRemaining < 0) R.color.widget_expense else R.color.widget_text))
        views.setTextViewText(R.id.widget_budget_meta, usage)
        views.setViewVisibility(R.id.widget_budget_meta, if (expanded) View.VISIBLE else View.GONE)
        val progress = if (hasBudget) (summary.budgetSpent.toDouble() / summary.budgetLimit * 1000).coerceIn(0.0, 1000.0).toInt() else 0
        views.setProgressBar(R.id.widget_progress, 1000, progress, false)
        views.setContentDescription(R.id.widget_root, context.getString(R.string.widget_budget_accessibility, title, remaining, usage))
        views.setOnClickPendingIntent(R.id.widget_root, open(context, "budget"))
        return views
    }

    private fun selectTab(context: Context, id: Int, income: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context, id * 2 + if (income) 1 else 0,
        Intent(context, MonthlyWidgetProvider::class.java).setAction(SELECT_TAB)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("income", income),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun open(context: Context, destination: String): PendingIntent = PendingIntent.getActivity(
        context, destination.hashCode(),
        Intent(context, MainActivity::class.java).putExtra("ledger_route", destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun pin(context: Context, budget: Boolean): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = if (budget) BudgetWidgetProvider::class.java else MonthlyWidgetProvider::class.java
        return manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }
}
