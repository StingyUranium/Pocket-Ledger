package `in`.thomas.pocketledger

import java.text.NumberFormat
import java.util.Locale

/** Keep tiny non-zero categories distinct from zero without implying a minimum bar length. */
internal fun spendingShareText(amount: Long, total: Long, locale: Locale = Locale.getDefault()): String {
    val share = if (total > 0) (amount.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
    val format = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    return if (share > 0 && share < .001) "<${format.format(.001)}" else format.format(share)
}
