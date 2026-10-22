package `in`.thomas.pocketledger

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MonthlyWidgetProvider : LedgerWidgetProvider()
class BudgetWidgetProvider : LedgerWidgetProvider()

open class LedgerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) refresh(context)
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
        val textColor = context.getColor(R.color.widget_text)
        val negativeColor = context.getColor(R.color.widget_expense)
        manager.getAppWidgetIds(ComponentName(context, MonthlyWidgetProvider::class.java)).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_monthly)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_income, money(summary.income))
            views.setTextViewText(R.id.widget_spent, money(summary.spent))
            views.setTextViewText(R.id.widget_balance, money(summary.balance))
            views.setTextColor(R.id.widget_balance, if (summary.balance < 0) negativeColor else textColor)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, "overview"))
            views.setOnClickPendingIntent(R.id.widget_income_tile, open(context, "income"))
            views.setOnClickPendingIntent(R.id.widget_spent_tile, open(context, "expense"))
            views.setOnClickPendingIntent(R.id.widget_balance_tile, open(context, "overview"))
            manager.updateAppWidget(id, views)
        }
        manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java)).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_budget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_remaining, if (summary.budgetLimit > 0) money(summary.budgetRemaining) else context.getString(R.string.widget_no_budget))
            views.setTextColor(R.id.widget_remaining, if (summary.budgetRemaining < 0) negativeColor else textColor)
            views.setTextViewText(R.id.widget_budget_meta, context.getString(R.string.widget_budget_usage, money(summary.budgetSpent), money(summary.budgetLimit)))
            val progress = if (summary.budgetLimit > 0) (summary.budgetSpent.toDouble() / summary.budgetLimit * 1000).coerceIn(0.0, 1000.0).toInt() else 0
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, "budget"))
            manager.updateAppWidget(id, views)
        }
    }

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
