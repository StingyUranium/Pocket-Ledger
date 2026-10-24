package `in`.thomas.pocketledger

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: Repository
    private lateinit var prefs: SharedPreferences
    private val sms = "INR 5000.00 debited\nA/c no. XX0424\n14-10-26, 11:03:22\nUPI/P2M/628728128503/TEST MERCHANT\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank"
    private fun candidate(text: String = sms, sender: String = "VM-AXISBK") = Candidate(sender, text, dayMillis("2026-10-22"), SmsParser.parse(text)!!)
    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val tag = UUID.randomUUID().toString()
        val context = object : ContextWrapper(app) { override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("test-$tag-$name", mode) }
        prefs = context.getSharedPreferences("capture", Context.MODE_PRIVATE)
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build(); repo = Repository(context, db)
        repo.initialize()
        db.dao().allAccounts().forEach { db.dao().account(it.copy(openingDate = dayMillis("2026-01-01"), opening = 1000000)) }
    }
    @After fun teardown() { db.close(); prefs.edit().clear().commit() }
    @Test fun unknownSenderNeedsReviewThenTrustSavesOnce() = runBlocking {
        repo.ingest(listOf(candidate())); assertTrue(db.dao().allEntries().isEmpty()); assertEquals("REVIEW", db.dao().allMessages().single().status)
        repo.trust("VM-AXISBK", "AXIS"); assertEquals(1, db.dao().allEntries().size)
        repo.ingest(listOf(candidate())); assertEquals(1, db.dao().allEntries().size)
    }
    @Test fun deleteAndDismissSurviveReimport() = runBlocking {
        repo.trust("VM-AXISBK", "AXIS"); repo.ingest(listOf(candidate()))
        repo.delete(db.dao().allEntries().single()); repo.ingest(listOf(candidate()))
        assertEquals(1, db.dao().allEntries().size); assertTrue(db.dao().allEntries().single().deleted); assertEquals("DISMISSED", db.dao().allMessages().single().status)
    }
    @Test fun conflictingReferenceCannotAutoSave() = runBlocking {
        repo.trust("VM-AXISBK", "AXIS"); repo.ingest(listOf(candidate())); repo.ingest(listOf(candidate(sms.replace("5000.00", "6000.00"))))
        repo.processReview(); assertEquals(1, db.dao().allEntries().size); assertEquals(1, db.dao().allMessages().count { it.status == "REVIEW" })
    }
    @Test fun undoDismissRestoresReviewWithoutOverwritingSavedStatus() = runBlocking {
        repo.ingest(listOf(candidate()))
        val original = db.dao().allMessages().single()
        repo.dismiss(original)
        repo.undoDismiss(original)
        assertEquals("REVIEW", db.dao().message(original.key)?.status)
        repo.dismiss(original)
        db.dao().message(original.key)!!.let { db.dao().message(it.copy(status = "SAVED")) }
        repo.undoDismiss(original)
        assertEquals("SAVED", db.dao().message(original.key)?.status)
    }
    @Test fun unmatchedAccountDoesNotAutoSave() = runBlocking {
        repo.trust("VM-AXISBK", "AXIS"); repo.ingest(listOf(candidate(sms.replace("0424", "9999"))))
        assertTrue(db.dao().allEntries().isEmpty()); assertEquals("REVIEW", db.dao().allMessages().single().status)
    }
    @Test fun oldMessagesRespectOpeningDate() = runBlocking {
        db.dao().allAccounts().filter { it.bank == "AXIS" }.forEach { db.dao().account(it.copy(openingDate = dayMillis("2026-10-20"))) }
        repo.trust("VM-AXISBK", "AXIS"); repo.ingest(listOf(candidate())); repo.processReview(); assertTrue(db.dao().allEntries().isEmpty())
    }
    @Test fun disabledCaptureSkipsLiveIngest() = runBlocking {
        repo.trust("VM-AXISBK", "AXIS"); repo.ingest(listOf(candidate()), requireCapture = true)
        assertTrue(db.dao().allMessages().isEmpty())
    }
    @Test fun transferLegsMatchAndStayOutOfReports() = runBlocking {
        val credit = "Your account XX6317 is credited with INR 5000.00 on 14-10-2026. Info:UPI/P2P/628728128503/TEST/.The Curr bal is 10000.00. SBM BANK (INDIA)."
        repo.trust("VM-AXISBK", "AXIS"); repo.trust("VM-SBMBNK", "SBM")
        repo.ingest(listOf(candidate(), candidate(credit, "VM-SBMBNK")))
        val live = db.dao().allEntries().filter { !it.deleted }; assertEquals(1, live.size); assertEquals("TRANSFER", live.single().type)
        assertEquals("axis", live.single().accountId); assertEquals("sbm", live.single().destinationId)
        val all = db.dao().allEntries(); val accounts = db.dao().allAccounts()
        assertEquals(3_000_000L, accounts.sumOf { balance(it, all) })
    }
    @Test fun rememberedMerchantRuleAppliesToFutureEntry() = runBlocking {
        repo.trust("VM-AXISBK", "AXIS"); repo.ingest(listOf(candidate()))
        val entry = db.dao().allEntries().single(); repo.save(entry.copy(category = "Food"), remember = true)
        repo.ingest(listOf(candidate(sms.replace("628728128503", "628728128504"))))
        assertTrue(db.dao().allEntries().all { it.category == "Food" })
    }
    @Test fun rememberedCategoriesAreIndependentForIncomeAndSpending() = runBlocking {
        val credit = candidate(sms.replace("debited", "credited").replace("628728128503", "628728128505"))
        repo.trust("VM-AXISBK", "AXIS")
        repo.ingest(listOf(candidate()))
        val expense = db.dao().allEntries().single()
        repo.save(expense.copy(category = "Groceries"), true)
        repo.ingest(listOf(credit))
        val income = db.dao().allEntries().single { it.type == "INCOME" }
        assertEquals("Uncategorized", income.category)
        repo.save(income.copy(category = "Salary"), true)
        repo.ingest(listOf(candidate(sms.replace("628728128503", "628728128506")),
            candidate(credit.body.replace("628728128505", "628728128507"))))
        assertTrue(db.dao().allEntries().filter { it.type == "EXPENSE" }.all { it.category == "Groceries" })
        assertTrue(db.dao().allEntries().filter { it.type == "INCOME" }.all { it.category == "Salary" })
        val password = "test-password".toCharArray()
        val backup = Backup.export(db, password)
        db.dao().clearRules()
        Backup.restore(repo, backup, password)
        assertEquals(setOf("INCOME", "EXPENSE"), db.dao().allRules().map { it.type }.toSet())
        db.dao().removeRule(expense.payee.uppercase(), "INCOME")
        assertEquals("EXPENSE", db.dao().allRules().single().type)
    }

    @Test fun oldBackupRestoresUntypedRulesWithoutChangingEntries() = runBlocking {
        val e = Entry("manual-income", "axis", type = "INCOME", amount = 100, date = dayMillis("2026-10-20"), payee = "Employer", category = "Salary")
        repo.save(e, true)
        val password = "test-password".toCharArray()
        val root = org.json.JSONObject(String(BackupCrypto.decrypt(Backup.export(db, password), password), Charsets.UTF_8))
        root.put("version", 1)
        root.getJSONArray("rules").getJSONObject(0).remove("type")
        Backup.restore(repo, BackupCrypto.encrypt(root.toString().toByteArray(), password), password)
        assertEquals(e, db.dao().allEntries().single())
        assertEquals("INCOME", db.dao().allRules().single().type)
    }
    @Test fun backupRestoresAllDataAndTombstonesAtomically() = runBlocking {
        repo.trust("VM-AXISBK", "AXIS"); repo.ingest(listOf(candidate()))
        val entry = db.dao().allEntries().single(); repo.save(entry.copy(category = "Food"), true); repo.delete(entry)
        db.dao().budget(Budget("Food", 10000))
        val password = "test-password".toCharArray(); val bytes = Backup.export(db, password)
        db.dao().clearEntries(); db.dao().clearMessages(); db.dao().clearBudgets(); db.dao().clearRules(); db.dao().clearSenders()
        prefs.edit().putBoolean("enabled", true).commit()
        Backup.restore(repo, bytes, password)
        assertEquals(3, db.dao().allAccounts().size); assertTrue(db.dao().allEntries().single().deleted)
        assertEquals("DISMISSED", db.dao().allMessages().single().status); assertEquals(1, db.dao().allBudgets().size); assertEquals(1, db.dao().allRules().size); assertEquals(1, db.dao().allSenders().size)
        assertFalse(prefs.getBoolean("enabled", true)); repo.ingest(listOf(candidate())); assertEquals(1, db.dao().allEntries().size)
    }
    @Test fun wrongBackupPasswordLeavesDatabaseIntact() = runBlocking {
        val bytes = Backup.export(db, "test-password".toCharArray())
        try { Backup.restore(repo, bytes, "wrong-password".toCharArray()); fail("Expected password error") } catch (_: IllegalStateException) { }
        assertEquals(3, db.dao().allAccounts().size)
    }

    private fun neftCandidate(): Candidate = candidate(
        "INR 123.45 credited to Axis Bank A/c XX0424 on 20-10-26 at 12:30:45 IST. Ref: NEFT/FBPT123456789012/SAMPLE REMITTER.View bal: https://example.invalid"
    )

    private suspend fun legacyRecord(c: Candidate, status: String): SmsRecord {
        val p = c.parsed
        val oldKey = SmsParser.bodyKey(c.sender, c.body, c.received)
        return SmsRecord(oldKey, c.sender, c.body, c.received, p.bank, p.suffix, p.amount, p.direction, p.date, p.dateOnly, "", null, p.balance, false, status).also { db.dao().message(it) }
    }

    @Test fun parserUpgradeRefreshesReviewWithoutCreatingSecondMessage() = runBlocking {
        val c = neftCandidate()
        val old = legacyRecord(c, "REVIEW")
        repo.trust(c.sender, "AXIS")
        repo.ingest(listOf(c))
        assertEquals(1, db.dao().allMessages().size)
        assertEquals(old.key, db.dao().allMessages().single().key)
        assertEquals("NEFT:FBPT123456789012", db.dao().allMessages().single().reference)
        assertEquals(1, db.dao().allEntries().size)
        repo.ingest(listOf(c.copy(received = c.received + 1000)))
        assertEquals(1, db.dao().allMessages().size)
        assertEquals(1, db.dao().allEntries().size)
    }

    @Test fun parserUpgradePreservesDismissedLegacyMessageAndResentReference() = runBlocking {
        val c = neftCandidate()
        legacyRecord(c, "DISMISSED")
        repo.trust(c.sender, "AXIS")
        repo.ingest(listOf(c))
        repo.ingest(listOf(c.copy(received = c.received + 1000)))
        assertEquals(1, db.dao().allMessages().size)
        assertEquals("DISMISSED", db.dao().allMessages().single().status)
        assertTrue(db.dao().allEntries().isEmpty())
    }

    @Test fun parserUpgradePreservesManualCorrectionToLegacyEntry() = runBlocking {
        val c = neftCandidate()
        val old = legacyRecord(c, "REVIEW")
        val manual = Entry("legacy-manual", "axis", type = "INCOME", amount = 777L, date = c.parsed.date!!, payee = "Corrected payee", category = "Other", smsKey = old.key)
        repo.save(manual)
        repo.trust(c.sender, "AXIS")
        repo.ingest(listOf(c))
        repo.ingest(listOf(c.copy(received = c.received + 1000)))
        assertEquals(manual, db.dao().allEntries().single())
        assertEquals(1, db.dao().allMessages().size)
    }

    @Test fun upgradedLegacyReferenceStillDetectsConflictingAmount() = runBlocking {
        val c = neftCandidate()
        legacyRecord(c, "REVIEW")
        repo.trust(c.sender, "AXIS")
        repo.ingest(listOf(c))
        repo.ingest(listOf(candidate(c.body.replace("123.45", "456.78"))))
        repo.processReview()
        assertEquals(1, db.dao().allEntries().size)
        assertEquals(1, db.dao().allMessages().count { it.status == "REVIEW" && it.key.contains(":conflict:") })
    }

    @Test fun recurringAchReferenceOnDifferentDatesCreatesSeparateEntries() = runBlocking {
        fun ach(day: String): Candidate = candidate(
            "Your account XX6317 is credited with INR 123.45 on $day-10-2026. Info:ACH_CR/SAMPLE FUND/TPZECS123456.The Curr bal is 9999.00. SBM BANK (INDIA).", "VM-SBMBNK"
        )
        repo.trust("VM-SBMBNK", "SBM")
        repo.ingest(listOf(ach("19"), ach("20")))
        assertEquals(2, db.dao().allEntries().size)
    }

    @Test fun upgradeMatchesLegacyBodyWhenProviderReceiptTimeDiffers() = runBlocking {
        val c = neftCandidate()
        legacyRecord(c, "DISMISSED")
        repo.trust(c.sender, "AXIS")
        repo.ingest(listOf(c.copy(received = c.received + 5000)))
        assertEquals(1, db.dao().allMessages().size)
        assertEquals("DISMISSED", db.dao().allMessages().single().status)
        assertTrue(db.dao().allEntries().isEmpty())
    }

    @Test fun storedReviewRefreshUpdatesFieldsWithoutPostingOrReadingInbox() = runBlocking {
        val c = neftCandidate()
        val old = legacyRecord(c, "REVIEW")
        repo.trust(c.sender, "AXIS")
        repo.refreshStoredReviews()
        val refreshed = db.dao().allMessages().single()
        assertEquals(old.key, refreshed.key)
        assertEquals("NEFT:FBPT123456789012", refreshed.reference)
        assertEquals("REVIEW", refreshed.status)
        assertTrue(db.dao().allEntries().isEmpty())
    }
}
