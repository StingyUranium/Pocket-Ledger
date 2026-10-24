package `in`.thomas.pocketledger

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure widget helpers kept free of Android types so JVM tests can cover them. */
internal object WidgetLogic {
    /** Decimal formatting stays in paise until conversion; never round through a Double. */
    fun amount(paise: Long): String = java.text.DecimalFormat("#,##0.00",
        java.text.DecimalFormatSymbols(Locale.US)).format(java.math.BigDecimal.valueOf(paise, 2))

    data class Comparison(val delta: Long, val favorable: Boolean?, val text: String)

    fun comparison(current: Long, previous: Long, income: Boolean): Comparison {
        val delta = current - previous
        val arrow = when { delta > 0 -> "\u25B2 "; delta < 0 -> "\u25BC "; else -> "" }
        return Comparison(delta, if (delta == 0L) null else (delta > 0) == income,
            arrow + amount(kotlin.math.abs(delta)))
    }

    const val PROGRESS_MAX = 1000
    const val CONTEXT_HEIGHT_DP = 144
    private const val BOUNDARY_SLACK_SECONDS = 5L

    /** Title and extra copy only appear when the widget is tall enough, scaled for large fonts. */
    fun showContext(heightDp: Int, fontScale: Float): Boolean =
        heightDp >= CONTEXT_HEIGHT_DP * fontScale.coerceAtLeast(1f)

    fun monthTitle(month: YearMonth, locale: Locale = Locale.ENGLISH): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    /** Progress in thousandths, clamped to 0..1000; 0 when no limit is set. */
    fun progress(spent: Long, limit: Long): Int =
        if (limit <= 0) 0 else (spent.toDouble() / limit * PROGRESS_MAX).coerceIn(0.0, PROGRESS_MAX.toDouble()).toInt()

    fun overspent(remaining: Long, limit: Long): Boolean = limit > 0 && remaining < 0

    /** The first moment of the next day in [zone], plus a few seconds so the new day is unambiguous. */
    fun nextBoundary(now: Instant, zone: ZoneId): Instant =
        ZonedDateTime.ofInstant(now, zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            .plusSeconds(BOUNDARY_SLACK_SECONDS)

    data class SizeBucket(val widthDp: Float, val heightDp: Float, val expanded: Boolean)

    /**
     * Collapses launcher-reported sizes into at most two layouts (compact and expanded). Each bucket is keyed
     * by the smallest size that falls inside it, so the host picks it for every size in that range.
     */
    fun buckets(sizes: List<Pair<Float, Float>>, fontScale: Float): List<SizeBucket> =
        sizes.groupBy { showContext(it.second.toInt(), fontScale) }.map { (expanded, group) ->
            SizeBucket(group.minOf { it.first }, group.minOf { it.second }, expanded)
        }.sortedBy { it.expanded }
}
