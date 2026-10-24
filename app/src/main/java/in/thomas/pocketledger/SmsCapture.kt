package `in`.thomas.pocketledger
import android.content.*
import android.provider.Telephony
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val repo = (context.applicationContext as LedgerApp).repo
        if (!repo.prefs.getBoolean("enabled", false)) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = parts.joinToString("") { it.messageBody.orEmpty() }; val parsed = SmsParser.parseSelected(body) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { repo.ingest(listOf(Candidate(parts.firstOrNull()?.originatingAddress.orEmpty(), body, parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis(), parsed)), requireCapture = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Log.w("LedgerSms", "Live capture failed; reconciliation will retry") }
            finally {
                try {
                    WorkManager.getInstance(context).enqueueUniqueWork("sms-recovery", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SmsWorker>().build())
                } catch (_: Exception) {
                    Log.w("LedgerSms", "Immediate recovery scheduling failed; periodic reconciliation remains available")
                } finally { pending.finish() }
            }
        }
    }
}
class SmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as LedgerApp).repo.reconcile()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        // Permission denial is not a transient provider failure.
        Result.success()
    } catch (_: Exception) {
        Log.w("LedgerSms", "Reconciliation failed; retry requested")
        Result.retry()
    }
}
