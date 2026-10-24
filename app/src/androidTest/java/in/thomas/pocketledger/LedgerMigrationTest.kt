package `in`.thomas.pocketledger

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Migration chain 1 -> 2 -> 3 -> 4 on synthetic fixtures. Run only with the validation application ID
 * (-Pledger.testApplicationId=in.thomas.pocketledger.validation); never against the personal package.
 */
class LedgerMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LedgerDatabase::class.java)

    private val body = "INR 100.00 debited A/c XX0424 UPI/P2M/111111111111/SYNTHETIC SHOP"
    private val reviewBody = "INR 7.00 debited A/c XX0424 UPI/P2M/222222222222/PENDING SHOP"

    private fun requireValidationPackage() {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "in.thomas.pocketledger.validation") {
            "Migration tests must run against the validation application ID"
        }
    }

    private fun seedVersion1(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO Account VALUES ('cash', 'Cash', 'CASH', '', 0, 0)")
        // Two live entries share one SMS key: the categorized row is the manual correction and must keep it.
        db.execSQL("INSERT INTO Entry VALUES ('e-auto', 'cash', NULL, 'EXPENSE', 10000, 1000, 0, 'SYNTHETIC SHOP', 'Uncategorized', '', 'k1', 0)")
        db.execSQL("INSERT INTO Entry VALUES ('e-fixed', 'cash', NULL, 'EXPENSE', 10000, 1000, 0, 'SYNTHETIC SHOP', 'Groceries', '', 'k1', 0)")
        db.execSQL("INSERT INTO Entry VALUES ('e-manual-1', 'cash', NULL, 'INCOME', 5000, 2000, 0, 'EMPLOYER', 'Salary', '', NULL, 0)")
        db.execSQL("INSERT INTO Entry VALUES ('e-manual-2', 'cash', NULL, 'EXPENSE', 300, 3000, 0, 'SHOP', 'Food', '', NULL, 0)")
        val columns = "(`key`, sender, body, received, bank, suffix, amount, direction, date, dateOnly, payee, reference, balance, complete, status, reason)"
        db.execSQL("INSERT INTO SmsRecord $columns VALUES ('k1', 'VM-AXISBK', '$body', 1000, 'AXIS', '0424', 10000, 'EXPENSE', 1000, 0, 'SYNTHETIC SHOP', '111111111111', NULL, 1, 'SAVED', '')")
        db.execSQL("INSERT INTO SmsRecord $columns VALUES ('k2', 'VM-AXISBK', 'dismissed text', 1100, 'AXIS', NULL, NULL, NULL, NULL, 0, '', NULL, NULL, 0, 'DISMISSED', 'Incomplete')")
        db.execSQL("INSERT INTO SmsRecord $columns VALUES ('k3', 'VM-AXISBK', '$reviewBody', 1200, 'AXIS', '0424', 700, 'EXPENSE', 1200, 0, 'PENDING SHOP', '222222222222', NULL, 1, 'REVIEW', 'Confirm this sender ID')")
        db.execSQL("INSERT INTO TrustedSender VALUES ('VM-AXISBK', 'AXIS')")
        db.execSQL("INSERT INTO Budget VALUES ('2026-01', 'Food', 1000), ('2026-02', 'Food', 2000), ('2026-02', 'Rent', 9000)")
        db.execSQL("INSERT INTO MerchantRule VALUES ('EMPLOYER', 'Salary'), ('SHOP', 'Food')")
    }

    private fun assertVersion4(db: SupportSQLiteDatabase) {
        // No money row is lost; the duplicate is detached and soft-deleted instead.
        assertEquals(4, db.query("SELECT COUNT(*) FROM Entry").use { it.moveToFirst(); it.getInt(0) })
        db.query("SELECT id, smsKey, deleted FROM Entry WHERE id IN ('e-auto', 'e-fixed') ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("e-auto", c.getString(0)); assertTrue(c.isNull(1)); assertEquals(1, c.getInt(2))
            assertTrue(c.moveToNext())
            assertEquals("e-fixed", c.getString(0)); assertEquals("k1", c.getString(1)); assertEquals(0, c.getInt(2))
        }
        val live = db.query("SELECT SUM(amount) FROM Entry WHERE deleted = 0 AND type = 'EXPENSE'").use { it.moveToFirst(); it.getLong(0) }
        assertEquals(10300L, live)
        val indexes = db.query("PRAGMA index_list(`Entry`)").use { c -> buildMap { while (c.moveToNext()) put(c.getString(1), c.getInt(2) == 1) } }
        assertEquals(true, indexes["index_Entry_smsKey"])
        assertEquals(false, indexes["index_Entry_date"])
        assertEquals(false, indexes["index_Entry_accountId"])
        // Resolved messages keep identity and parsed fields but not their text.
        db.query("SELECT `key`, body, reference, status FROM SmsRecord ORDER BY `key`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("k1", c.getString(0)); assertEquals(purgedBody("VM-AXISBK", body), c.getString(1)); assertEquals("111111111111", c.getString(2)); assertEquals("SAVED", c.getString(3))
            assertTrue(c.moveToNext())
            assertEquals("k2", c.getString(0)); assertEquals(purgedBody("VM-AXISBK", "dismissed text"), c.getString(1)); assertEquals("DISMISSED", c.getString(3))
            assertTrue(c.moveToNext())
            assertEquals("k3", c.getString(0)); assertEquals(reviewBody, c.getString(1)); assertEquals("REVIEW", c.getString(3))
        }
        // The new unique index rejects a second entry for the same SMS but still allows many NULL keys.
        db.execSQL("INSERT INTO Entry VALUES ('e-null-1', 'cash', NULL, 'EXPENSE', 1, 4000, 0, '', 'Food', '', NULL, 0)")
        db.execSQL("INSERT INTO Entry VALUES ('e-null-2', 'cash', NULL, 'EXPENSE', 1, 4001, 0, '', 'Food', '', NULL, 0)")
        try {
            db.execSQL("INSERT INTO Entry VALUES ('e-dup', 'cash', NULL, 'EXPENSE', 1, 4002, 0, '', 'Food', '', 'k1', 0)")
            fail("Expected the unique smsKey index to reject a duplicate")
        } catch (_: SQLiteConstraintException) { }
    }

    @Test fun migratingStraightFromVersionOneToFourPreservesMoneyAndIdentities() {
        requireValidationPackage()
        val name = "migration-chain-test"
        helper.createDatabase(name, 1).use { seedVersion1(it) }
        helper.runMigrationsAndValidate(name, 4, true, *LedgerMigrations).use { db ->
            assertEquals(2000L, db.query("SELECT amount FROM Budget WHERE category = 'Food'").use { it.moveToFirst(); it.getLong(0) })
            assertEquals(2, db.query("SELECT COUNT(*) FROM Budget").use { it.moveToFirst(); it.getInt(0) })
            assertEquals("INCOME", db.query("SELECT type FROM MerchantRule WHERE merchant = 'EMPLOYER'").use { it.moveToFirst(); it.getString(0) })
            assertVersion4(db)
        }
    }

    @Test fun migratingOneVersionAtATimeValidatesEverySchema() {
        requireValidationPackage()
        val name = "migration-stepwise-test"
        helper.createDatabase(name, 1).use { seedVersion1(it) }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).close()
        helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).use { assertVersion4(it) }
    }

    @Test fun versionThreeWithoutDuplicatesOnlyGainsIndicesAndPurgedBodies() {
        requireValidationPackage()
        val name = "migration-v3-test"
        helper.createDatabase(name, 3).use { db ->
            db.execSQL("INSERT INTO Account VALUES ('cash', 'Cash', 'CASH', '', 0, 0)")
            db.execSQL("INSERT INTO Entry VALUES ('e1', 'cash', NULL, 'EXPENSE', 100, 10, 0, '', 'Food', '', NULL, 0)")
            db.execSQL("INSERT INTO Entry VALUES ('e2', 'cash', NULL, 'EXPENSE', 200, 20, 0, '', 'Food', '', NULL, 0)")
        }
        helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).use { db ->
            assertEquals(300L, db.query("SELECT SUM(amount) FROM Entry WHERE deleted = 0").use { it.moveToFirst(); it.getLong(0) })
        }
    }
}
