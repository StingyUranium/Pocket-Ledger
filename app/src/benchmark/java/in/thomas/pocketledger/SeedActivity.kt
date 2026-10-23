package `in`.thomas.pocketledger

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.room.withTransaction
import kotlinx.coroutines.*
import java.time.LocalDate

/** Only packaged in the benchmark variant, and refuses the personal package. */
class SeedActivity : Activity() {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "in.thomas.pocketledger.validation")
        val label = TextView(this).apply { text = "Preparing synthetic fixture" }
        setContentView(label)
        work.launch {
            withContext(Dispatchers.IO) {
                val db = (application as LedgerApp).db
                val prefs = getSharedPreferences("benchmark_fixture", MODE_PRIVATE)
                if (!prefs.getBoolean("seeded_v2", false)) {
                    val start = LocalDate.now(india).minusDays(729)
                    db.withTransaction {
                        db.dao().clearEntries()
                        db.dao().clearMessages()
                        db.dao().clearBudgets()
                        db.dao().account(Account("benchmark-cash", "Synthetic cash", "CASH", "", openingDate = dayMillis(start.toString())))
                        repeat(10_000) { index ->
                            db.dao().entry(Entry(
                                id = "benchmark-$index", accountId = "benchmark-cash",
                                type = if (index % 10 == 0) "INCOME" else "EXPENSE",
                                amount = 1_000L + index * 17L,
                                date = start.plusDays((index % 730).toLong()).atStartOfDay(india).toInstant().toEpochMilli(),
                                payee = "Synthetic merchant ${index % 120}", category = defaultCategories[index % defaultCategories.size]
                            ))
                        }
                        db.dao().budget(Budget("Food", 100_000))
                    }
                    prefs.edit().putBoolean("seeded_v2", true).commit()
                }
            }
            label.text = "Benchmark fixture ready"
        }
    }
    override fun onDestroy() { work.cancel(); super.onDestroy() }
}
