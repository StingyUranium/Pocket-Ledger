package `in`.thomas.pocketledger

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

data class LedgerSnapshot(
    val accounts: List<Account>, val allEntries: List<Entry>, val messages: List<SmsRecord>,
    val budgets: List<Budget>, val senders: List<TrustedSender>, val rules: List<MerchantRule>
) {
    val entries = allEntries.filterNot { it.deleted }
}
data class LedgerReadState(val data: LedgerSnapshot? = null, val error: String? = null)
val LocalLedgerBusy = staticCompositionLocalOf { false }
val LocalLedgerOperationError = staticCompositionLocalOf<String?> { null }
val LocalLedgerClearError = staticCompositionLocalOf<() -> Unit> { {} }

fun ledgerSnapshots(dao: LedgerDao): Flow<LedgerReadState> {
    val records = combine(dao.accounts(), dao.entries(), dao.messages()) { a, e, m -> Triple(a, e, m) }
    val config = combine(dao.budgets(), dao.senders(), dao.rules()) { b, s, r -> Triple(b, s, r) }
    return combine(records, config) { a, b -> LedgerSnapshot(a.first, a.second, a.third, b.first, b.second, b.third) }
        .map<LedgerSnapshot, Result<LedgerSnapshot>> { Result.success(it) }
        .catch { emit(Result.failure(it)) }
        .runningFold(LedgerReadState()) { previous, result ->
            result.fold({ LedgerReadState(data = it) }, { previous.copy(error = "Could not read your ledger. Retry to reload it.") })
        }.flowOn(Dispatchers.Default)
}

/** Latest result per call site, so revisiting a root tab with unchanged inputs paints immediately. */
class CalculationCache {
    private class Stored(val inputs: List<Any?>, val value: Any)
    private val stored = HashMap<String, Stored>()
    @Suppress("UNCHECKED_CAST")
    fun <T> find(key: String, inputs: List<Any?>): T? = stored[key]?.takeIf { it.inputs == inputs }?.value as T?
    fun store(key: String, inputs: List<Any?>, value: Any) { stored[key] = Stored(inputs, value) }
}
val LocalCalculationCache = staticCompositionLocalOf<CalculationCache?> { null }

/**
 * Cancel obsolete reports and never show the previous month's values as current. A [cacheKey]
 * reuses the last result only when every input is equal, which avoids a skeleton flash on tab switches.
 */
@Composable
fun <T> backgroundCalculation(vararg inputs: Any?, cacheKey: String? = null, calculation: () -> T): State<T?> {
    val dependencies = inputs.toList()
    val cache = LocalCalculationCache.current
    val result = remember(dependencies) { mutableStateOf<T?>(cacheKey?.let { cache?.find<T>(it, dependencies) }) }
    LaunchedEffect(dependencies) {
        if (result.value == null) {
            val value = withContext(Dispatchers.Default) { calculation() }
            result.value = value
            if (cacheKey != null && value != null) cache?.store(cacheKey, dependencies, value)
        }
    }
    return result
}

@Composable fun LedgerSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Loading your ledger", style = MaterialTheme.typography.bodyLarge)
        repeat(4) { index ->
            Surface(Modifier.fillMaxWidth().height(if (index == 0) 144.dp else 72.dp),
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
        }
    }
}
