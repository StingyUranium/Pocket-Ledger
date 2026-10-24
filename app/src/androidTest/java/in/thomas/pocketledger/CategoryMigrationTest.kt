package `in`.thomas.pocketledger

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CategoryMigrationTest {
    @Test fun versionTwoKeepsEntriesAndScopesLegacyRulesToTheirCorrectionType() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "in.thomas.pocketledger.validation")
        val name = "category-migration-${UUID.randomUUID()}.db"
        val income = Entry("income", "cash", type = "INCOME", amount = 12000, date = 100, payee = "Employer", category = "Consulting")
        val expense = Entry("expense", "cash", type = "EXPENSE", amount = 500, date = 200, payee = "Shop", category = "Food")
        var database: LedgerDatabase? = null
        try {
            database = Room.databaseBuilder(context, LedgerDatabase::class.java, name).build()
            database.dao().account(Account("cash", "Cash", "CASH", ""))
            database.dao().entry(income); database.dao().entry(expense)
            database.close(); database = null
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                old.execSQL("DROP TABLE MerchantRule")
                old.execSQL("CREATE TABLE MerchantRule (merchant TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL)")
                old.execSQL("INSERT INTO MerchantRule VALUES ('EMPLOYER', 'Consulting'), ('SHOP', 'Food'), ('LEGACY', 'Salary')")
                old.execSQL("UPDATE room_master_table SET identity_hash = 'a3f3f91c693cdd966c2d1ce178f3f4ab' WHERE id = 42")
                old.version = 2
            }
            database = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_2_3).build()
            assertEquals(setOf(income, expense), database.dao().allEntries().toSet())
            val rules = database.dao().allRules().associateBy { it.merchant }
            assertEquals("INCOME", rules["EMPLOYER"]?.type)
            assertEquals("EXPENSE", rules["SHOP"]?.type)
            assertEquals("INCOME", rules["LEGACY"]?.type)
            database.dao().rule(MerchantRule("EMPLOYER", "Gifts", "EXPENSE"))
            assertEquals(2, database.dao().allRules().count { it.merchant == "EMPLOYER" })
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }
}
