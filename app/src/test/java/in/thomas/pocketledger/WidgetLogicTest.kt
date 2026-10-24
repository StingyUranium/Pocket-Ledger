package `in`.thomas.pocketledger

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Locale

class WidgetLogicTest {
    @Test fun amountsKeepPaiseAndGroupingWithoutCurrency() {
        assertEquals("23,627.27", WidgetLogic.amount(2362727))
        assertEquals("0.01", WidgetLogic.amount(1))
        assertEquals("0.00", WidgetLogic.amount(0))
        assertEquals("-1,234.56", WidgetLogic.amount(-123456))
    }

    @Test fun expenseComparisonUsesDirectionAndAbsoluteDifference() {
        val increase = WidgetLogic.comparison(2362727, 1802703, false)
        assertEquals(560024L, increase.delta)
        assertEquals("▲ 5,600.24", increase.text)
        assertEquals(false, increase.favorable)
        val decrease = WidgetLogic.comparison(1802703, 2362727, false)
        assertEquals(-560024L, decrease.delta)
        assertEquals("▼ 5,600.24", decrease.text)
        assertEquals(true, decrease.favorable)
    }

    @Test fun incomeComparisonReversesFavorableDirection() {
        assertEquals(true, WidgetLogic.comparison(20000, 10000, true).favorable)
        assertEquals(false, WidgetLogic.comparison(10000, 20000, true).favorable)
    }

    @Test fun unchangedAndMissingPreviousTotalsHaveDefinedComparisons() {
        val unchanged = WidgetLogic.comparison(10000, 10000, false)
        assertEquals(0L, unchanged.delta)
        assertEquals("0.00", unchanged.text)
        assertNull(unchanged.favorable)
        assertEquals("▲ 100.00", WidgetLogic.comparison(10000, 0, false).text)
        assertEquals("0.00", WidgetLogic.comparison(0, 0, true).text)
    }

    @Test fun contextAppearsOnlyWhenTallEnoughForFontScale() {
        assertFalse(WidgetLogic.showContext(143, 1f))
        assertTrue(WidgetLogic.showContext(144, 1f))
        assertFalse(WidgetLogic.showContext(200, 2f))
        assertTrue(WidgetLogic.showContext(288, 2f))
        assertTrue(WidgetLogic.showContext(144, 0.85f))
    }

    @Test fun monthTitleIsLongMonthAndYear() {
        assertEquals("October 2026", WidgetLogic.monthTitle(YearMonth.of(2026, 10), Locale.ENGLISH))
    }

    @Test fun progressClampsAndHandlesNoLimit() {
        assertEquals(0, WidgetLogic.progress(500, 0))
        assertEquals(500, WidgetLogic.progress(50, 100))
        assertEquals(1000, WidgetLogic.progress(250, 100))
        assertEquals(0, WidgetLogic.progress(-10, 100))
    }

    @Test fun overspentRequiresLimitAndNegativeRemaining() {
        assertTrue(WidgetLogic.overspent(-1, 100))
        assertFalse(WidgetLogic.overspent(0, 100))
        assertFalse(WidgetLogic.overspent(-1, 0))
    }

    @Test fun boundaryIsNextKolkataMidnightNotUtc() {
        val now = ZonedDateTime.of(2026, 10, 31, 23, 30, 0, 0, india).toInstant()
        val next = ZonedDateTime.ofInstant(WidgetLogic.nextBoundary(now, india), india)
        assertEquals(2026, next.year); assertEquals(11, next.monthValue); assertEquals(1, next.dayOfMonth)
        assertEquals(0, next.hour); assertEquals(0, next.minute)
        assertTrue(WidgetLogic.nextBoundary(now, india).isAfter(now))
        // 19:00 UTC on 30 Oct is already 00:30 IST on 31 Oct.
        val late = Instant.parse("2026-10-30T19:00:00Z")
        assertEquals(31, ZonedDateTime.ofInstant(late, india).dayOfMonth)
        val lateNext = ZonedDateTime.ofInstant(WidgetLogic.nextBoundary(late, india), india)
        assertEquals(1, lateNext.dayOfMonth)
        assertEquals(11, lateNext.monthValue)
    }

    @Test fun sixteenSizesCollapseToTwoBuckets() {
        val sizes = (1..16).map { (100f + it * 10) to (80f + it * 10) }
        val buckets = WidgetLogic.buckets(sizes, 1f)
        assertEquals(2, buckets.size)
        assertFalse(buckets[0].expanded)
        assertTrue(buckets[1].expanded)
        assertEquals(110f, buckets[0].widthDp, 0f)
        assertTrue(buckets[1].heightDp >= 144f)
    }

    @Test fun uniformSizesYieldOneBucket() {
        assertEquals(1, WidgetLogic.buckets(listOf(200f to 110f, 300f to 110f), 1f).size)
    }
}
