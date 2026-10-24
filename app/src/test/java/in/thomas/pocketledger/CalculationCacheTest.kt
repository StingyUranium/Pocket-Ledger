package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test

class CalculationCacheTest {
    @Test fun evictsLeastRecentlyUsedCategoryResult() {
        val cache = CalculationCache(2)
        cache.store("food", listOf("snapshot-1"), 100L)
        cache.store("rent", listOf("snapshot-1"), 200L)
        assertEquals(100L, cache.find<Long>("food", listOf("snapshot-1")))
        cache.store("travel", listOf("snapshot-2"), 300L)
        assertNull(cache.find<Long>("rent", listOf("snapshot-1")))
        assertEquals(100L, cache.find<Long>("food", listOf("snapshot-1")))
        assertEquals(300L, cache.find<Long>("travel", listOf("snapshot-2")))
    }

    @Test fun changedMonthOrSnapshotNeverReturnsStaleResult() {
        val cache = CalculationCache()
        cache.store("overview", listOf("snapshot-1", "October"), 100L)
        assertNull(cache.find<Long>("overview", listOf("snapshot-1", "November")))
        assertNull(cache.find<Long>("overview", listOf("snapshot-2", "October")))
        cache.store("overview", listOf("snapshot-2", "October"), 200L)
        assertEquals(200L, cache.find<Long>("overview", listOf("snapshot-2", "October")))
    }
}
