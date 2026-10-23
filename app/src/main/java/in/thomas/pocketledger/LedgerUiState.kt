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

/** Cancel obsolete reports and never show the previous month's values as current. */
@Composable
fun <T> backgroundCalculation(vararg inputs: Any?, calculation: () -> T): State<T?> {
    val dependencies = inputs.toList()
    val result = remember(dependencies) { mutableStateOf<T?>(null) }
    LaunchedEffect(dependencies) { result.value = withContext(Dispatchers.Default) { calculation() } }
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
