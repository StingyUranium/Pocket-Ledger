package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test

class DestinationTest {
    @Test fun legacyAndNamedDestinationsRestoreWithoutDependingOnEnumOrder() {
        assertEquals(Destination.Budgets, Destination.restore("3"))
        assertEquals(Destination.Inbox, Destination.restore("4"))
        assertEquals(Destination.Management, Destination.restore("6"))
        assertEquals(Destination.Overview, Destination.restore("unknown"))
        Destination.entries.forEach { assertEquals(it, Destination.restore(it.name)) }
        assertEquals(listOf(Destination.Overview, Destination.Activity, Destination.Budgets, Destination.Inbox), Destination.primary)
    }
}
