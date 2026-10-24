package `in`.thomas.pocketledger

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the manifest-registered system broadcasts (date, time, time zone, day rollover alarm). One pass refreshes
 * both widget kinds, so [BudgetWidgetProvider] must not register those actions.
 */
class MonthlyWidgetProvider : LedgerWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == LedgerWidgets.SELECT_TAB) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                context.getSharedPreferences(LedgerWidgets.TABS_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("income_$id", intent.getBooleanExtra("income", false)).apply()
                LedgerWidgets.launch(context, setOf(id), cached = true, pending = goAsync())
            }
            return
        }
        super.onReceive(context, intent)
        if (intent.action in LedgerWidgets.SYSTEM_ACTIONS) LedgerWidgets.launch(context, null, pending = goAsync())
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(LedgerWidgets.TABS_PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove("income_$it") }
        editor.apply()
        (context.applicationContext as? LedgerApp)?.updateWidgetPresence()
    }

    override fun onDisabled(context: Context) {
        if (!LedgerWidgets.hasWidgets(context)) LedgerWidgets.cancelRollover(context)
        (context.applicationContext as? LedgerApp)?.updateWidgetPresence()
    }
}

class BudgetWidgetProvider : LedgerWidgetProvider()

open class LedgerWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        (context.applicationContext as? LedgerApp)?.updateWidgetPresence()
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        (context.applicationContext as? LedgerApp)?.updateWidgetPresence()
        LedgerWidgets.launch(context, ids.toSet(), pending = goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        LedgerWidgets.launch(context, setOf(id), pending = goAsync())
}

object LedgerWidgets {
    const val SELECT_TAB = "in.thomas.pocketledger.widget.SELECT_TAB"
    const val ROLLOVER = "in.thomas.pocketledger.widget.ROLLOVER"
    internal const val TABS_PREFS = "widget_tabs"
    internal val SYSTEM_ACTIONS = setOf(
        Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, ROLLOVER
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchingConfiguration = AtomicBoolean(false)
    @Volatile private var renderedFontScale = 0f
    @Volatile private var renderedNight = -1
    @Volatile private var lastSummary: Pair<YearMonth, MonthlySummary>? = null

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(MonthlyWidgetProvider::class.java, BudgetWidgetProvider::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }

    /** Runs a refresh on the shared scope and finishes the broadcast when done. [ids] null means all widgets. */
    internal fun launch(context: Context, ids: Set<Int>?, cached: Boolean = false, pending: BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (cached) refreshFromCache(app, ids) else refresh(app, ids)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("LedgerWidgets", "Could not update widgets", error)
            } finally {
                pending?.finish()
            }
        }
    }

    suspend fun refresh(context: Context, ids: Set<Int>? = null) {
        if (!hasWidgets(context)) return
        try {
            val db = (context.applicationContext as LedgerApp).db
            val data = db.withTransaction { db.dao().allEntries() to db.dao().allBudgets() }
            render(context, data.first, data.second, ids)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("LedgerWidgets", "Could not load widget data", error)
            renderFallback(context, ids)
        }
    }

    /** Tab switches only change the selection, so reuse the last summary when it is still this month. */
    private suspend fun refreshFromCache(context: Context, ids: Set<Int>?) {
        val month = YearMonth.now(india)
        val last = lastSummary
        if (last != null && last.first == month) renderSummary(context, last.second, month, ids, stale = false)
        else refresh(context, ids)
    }

    fun render(context: Context, entries: List<Entry>, budgets: List<Budget>, ids: Set<Int>? = null) {
        val month = YearMonth.now(india)
        val summary = monthlySummary(entries, budgets, month)
        lastSummary = month to summary
        renderSummary(context, summary, month, ids, stale = false)
    }

    private fun renderFallback(context: Context, ids: Set<Int>?) {
        val month = YearMonth.now(india)
        val last = lastSummary
        if (last != null && last.first == month) renderSummary(context, last.second, month, ids, stale = true)
        else renderUnavailable(context, month, ids)
    }

    private fun idsOf(context: Context, provider: Class<*>, only: Set<Int>?): List<Int> =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider)).filter { only == null || it in only }

    private fun renderSummary(context: Context, summary: MonthlySummary, month: YearMonth, ids: Set<Int>?, stale: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val title = WidgetLogic.monthTitle(month)
        val prefs = context.getSharedPreferences(TABS_PREFS, Context.MODE_PRIVATE)
        renderedFontScale = context.resources.configuration.fontScale
        renderedNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        idsOf(context, MonthlyWidgetProvider::class.java, ids).forEach { id ->
            val income = prefs.getBoolean("income_$id", false)
            manager.updateAppWidget(id, responsiveViews(context, manager.getAppWidgetOptions(id), FALLBACK_HEIGHT) { height ->
                monthlyViews(context, id, summary, title, income, height, stale)
            })
        }
        idsOf(context, BudgetWidgetProvider::class.java, ids).forEach { id ->
            manager.updateAppWidget(id, responsiveViews(context, manager.getAppWidgetOptions(id), FALLBACK_HEIGHT) { height ->
                budgetViews(context, summary, title, height, stale)
            })
        }
        scheduleRollover(context)
        watchConfiguration(context)
    }

    private fun renderUnavailable(context: Context, month: YearMonth, ids: Set<Int>?) {
        val manager = AppWidgetManager.getInstance(context)
        val title = WidgetLogic.monthTitle(month)
        idsOf(context, MonthlyWidgetProvider::class.java, ids).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_monthly)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_total, context.getString(R.string.widget_unavailable_amount))
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_unavailable))
            views.setViewVisibility(R.id.widget_status, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, "expense"))
            manager.updateAppWidget(id, views)
        }
        idsOf(context, BudgetWidgetProvider::class.java, ids).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_budget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_remaining, context.getString(R.string.widget_unavailable_amount))
            views.setTextViewText(R.id.widget_budget_meta, context.getString(R.string.widget_unavailable))
            views.setViewVisibility(R.id.widget_budget_meta, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, "budget"))
            manager.updateAppWidget(id, views)
        }
        scheduleRollover(context)
    }

    /** Launcher-provided sizes are in dp, excluding host padding. Keep old hosts resize-aware too. */
    private fun responsiveViews(context: Context, options: Bundle, fallbackHeight: Int, create: (Int) -> RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                val buckets = WidgetLogic.buckets(sizes.map { it.width to it.height }, context.resources.configuration.fontScale)
                return RemoteViews(buckets.associate { SizeF(it.widthDp, it.heightDp) to create(it.heightDp.toInt()) })
            }
        }
        val portrait = create(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, fallbackHeight))
        val landscape = create(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallbackHeight))
        // A host can omit options entirely during its first update.
        return if (options.isEmpty) create(fallbackHeight) else RemoteViews(landscape, portrait)
    }

    private fun showContext(context: Context, height: Int): Boolean =
        WidgetLogic.showContext(height, context.resources.configuration.fontScale)

    internal fun monthlyViews(context: Context, id: Int, summary: MonthlySummary, title: String, income: Boolean, height: Int, stale: Boolean = false): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_monthly)
        val amount = money(if (income) summary.income else summary.spent)
        val label = context.getString(if (income) R.string.widget_income_label else R.string.widget_expenses_label)
        views.setTextViewText(R.id.widget_title, title)
        views.setViewVisibility(R.id.widget_title, if (showContext(context, height)) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_total, amount)
        views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_stale))
        views.setViewVisibility(R.id.widget_status, if (stale) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_expense_tab, context.getString(
            if (context.resources.configuration.fontScale > 1.3f) R.string.widget_spent_label else R.string.widget_expenses_label))
        views.setContentDescription(R.id.widget_total, context.getString(R.string.widget_total_accessibility, title, label, amount))
        listOf(R.id.widget_income_tab to true, R.id.widget_expense_tab to false).forEach { (viewId, tabIncome) ->
            val selected = income == tabIncome
            // RemoteViews cannot call setSelected, so colour and background are chosen here; a uiMode change re-renders.
            views.setTextColor(viewId, context.getColor(if (selected) R.color.widget_accent else R.color.widget_secondary))
            views.setInt(viewId, "setBackgroundResource", if (selected) R.drawable.widget_selected else 0)
            val tabLabel = context.getString(if (tabIncome) R.string.widget_income_label else R.string.widget_expenses_label)
            views.setContentDescription(viewId, context.getString(R.string.widget_tab_accessibility, tabLabel,
                context.getString(if (selected) R.string.widget_selected_label else R.string.widget_not_selected_label)))
            views.setOnClickPendingIntent(viewId, selectTab(context, id, tabIncome))
        }
        views.setOnClickPendingIntent(R.id.widget_root, open(context, if (income) "income" else "expense"))
        return views
    }

    internal fun budgetViews(context: Context, summary: MonthlySummary, title: String, height: Int, stale: Boolean = false): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_budget)
        val hasBudget = summary.budgetLimit > 0
        val remaining = if (hasBudget) money(summary.budgetRemaining) else context.getString(R.string.widget_no_budget)
        val usage = if (stale) context.getString(R.string.widget_stale)
            else if (hasBudget) context.getString(R.string.widget_budget_usage, money(summary.budgetSpent), money(summary.budgetLimit))
            else context.getString(R.string.widget_budget_preview)
        val expanded = showContext(context, height)
        views.setTextViewText(R.id.widget_title, title)
        views.setViewVisibility(R.id.widget_title, if (expanded) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_remaining, remaining)
        val overspent = WidgetLogic.overspent(summary.budgetRemaining, summary.budgetLimit)
        views.setTextColor(R.id.widget_remaining, context.getColor(if (overspent) R.color.widget_expense else R.color.widget_text))
        // Two bars with fixed tints (resolved by the host, so night-aware); only one is visible.
        views.setViewVisibility(R.id.widget_progress, if (overspent) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_progress_over, if (overspent) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_budget_meta, usage)
        views.setViewVisibility(R.id.widget_budget_meta, if (expanded || stale) View.VISIBLE else View.GONE)
        val progress = WidgetLogic.progress(summary.budgetSpent, summary.budgetLimit)
        views.setProgressBar(R.id.widget_progress, WidgetLogic.PROGRESS_MAX, progress, false)
        views.setProgressBar(R.id.widget_progress_over, WidgetLogic.PROGRESS_MAX, progress, false)
        views.setContentDescription(R.id.widget_root, context.getString(R.string.widget_budget_accessibility, title, remaining, usage))
        views.setOnClickPendingIntent(R.id.widget_root, open(context, "budget"))
        return views
    }

    /** Fires just after the next Asia/Kolkata midnight, which also covers month boundaries. Inexact; needs no permission. */
    private fun scheduleRollover(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = WidgetLogic.nextBoundary(Instant.now(), india)
        runCatching { alarms.set(AlarmManager.RTC, at.toEpochMilli(), rolloverIntent(context)) }
    }

    internal fun cancelRollover(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(rolloverIntent(context))
    }

    private fun rolloverIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, MonthlyWidgetProvider::class.java).setAction(ROLLOVER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Font scale drives layout choices (title, tab label); text colours are resolved at render time, so night mode re-renders too. */
    private fun watchConfiguration(context: Context) {
        if (!watchingConfiguration.compareAndSet(false, true)) return
        val app = context.applicationContext
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (newConfig.fontScale != renderedFontScale || night != renderedNight) launch(app, null)
            }
            override fun onLowMemory() = Unit
        })
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

    private const val FALLBACK_HEIGHT = 110
}
