package com.novabox.tv.ui

import com.novabox.tv.data.EpgEntry
import com.novabox.tv.data.Season
import com.novabox.tv.data.VodItem
import com.novabox.tv.data.VodKind
import com.novabox.tv.data.WatchEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchAndGuideTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L - Math.floorMod(1_800_000_000_000L, HALF_HOUR)
    private fun e(title: String, startMin: Long, endMin: Long) = EpgEntry(title, "", t0 + startMin * min, t0 + endMin * min)

    @Test fun guideClipsAndFillsGaps() {
        val cells = guideCells(listOf(e("A", -20, 30), e("B", 30, 60), e("C", 90, 200)), t0, t0 + 120 * min)
        assertEquals(listOf("A", "B", null, "C"), cells.map { it.entry?.title })
        assertEquals(t0, cells[0].start)                    // clipped to the window start
        assertEquals(t0 + 60 * min, cells[2].start)         // the 30-minute gap is a "no information" cell
        assertEquals(t0 + 120 * min, cells.last().end)      // clipped to the window end
    }

    @Test fun guideAbsorbsSmallGapsAndOverlaps() {
        val cells = guideCells(listOf(e("A", 0, 28), e("B", 30, 70), e("C", 60, 120)), t0, t0 + 120 * min)
        assertEquals(listOf("A", "B", "C"), cells.map { it.entry?.title })
        assertEquals(t0 + 28 * min, cells[1].start)         // 2-minute gap absorbed by B
        assertEquals(t0 + 70 * min, cells[2].start)         // C starts after B's end, no overlap
        assertEquals(1, guideCells(emptyList(), t0, t0 + 120 * min).size)
        // Guide ends 3 minutes early: the last programme is stretched to the edge.
        assertEquals(t0 + 120 * min, guideCells(listOf(e("A", 0, 117)), t0, t0 + 120 * min).single().end)
    }

    @Test fun guideMovesAtTheEdgesOnly() {
        val now = t0 + 10 * min
        val edge = GuideCell(e("Long", 90, 200), t0 + 90 * min, t0 + GUIDE_SPAN)
        val (start, focus) = guideShift(edge, t0, now, forward = true)!!
        assertEquals(t0 + 200 * min, focus)                // focus the programme after "Long"
        assertTrue(start + GUIDE_SPAN >= focus + HALF_HOUR)
        assertNull(guideShift(GuideCell(null, t0, t0 + 30 * min), t0, now, forward = true))
        assertNull(guideShift(GuideCell(null, t0, t0 + 30 * min), t0, now, forward = false)) // can't go into the past
        val later = guideShift(GuideCell(e("X", 60, 90), t0 + 60 * min, t0 + 90 * min), t0 + 60 * min, now, forward = false)!!
        assertEquals(t0 + 30 * min, later.first)
    }

    private val show = VodItem("7", VodKind.SERIES, "Show", "", "", "", "", "", "", "", "", "", emptyList(), "")
    private val s1 = Season("s1", "Season 1", "c1", listOf(1, 2))
    private val s2 = Season("s2", "Season 2", "c2", listOf(1, 2, 3))
    private fun ep(season: Season, n: Int, pos: Long, watched: Boolean, at: Long) =
        WatchEntry(WatchEntry.episodeKey(show, season, n), "Show", "${season.name} · Episode $n", "", season.cmd, n, false, pos, 60 * min, at, watched, show)

    @Test fun seriesOffersResumeThenNextEpisode() {
        val resume = nextEpisode(listOf(ep(s1, 1, 60 * min, true, 1), ep(s1, 2, 10 * min, false, 2)), show, listOf(s1, s2))!!
        assertEquals("Resume S1 · E2", resume.second)
        assertEquals(2, resume.first.episode)
        val next = nextEpisode(listOf(ep(s1, 2, 60 * min, true, 3)), show, listOf(s1, s2))!!
        assertEquals("Next: S2 · E1", next.second)     // last of season 1 → first of season 2
        assertEquals("c2", next.first.cmd)
        assertNull(nextEpisode(listOf(ep(s2, 3, 60 * min, true, 4)), show, listOf(s1, s2))) // finished
    }

    @Test fun continueWatchingKeepsOneCardPerSeries() {
        val list = continueFor(listOf(ep(s1, 1, 10 * min, false, 1), ep(s1, 2, 20 * min, false, 5), ep(s2, 1, 5 * min, true, 9)), VodKind.SERIES)
        assertEquals(1, list.size)
        assertEquals(2, list[0].episode)
        assertTrue(continueFor(list, VodKind.MOVIE).isEmpty())
    }

    @Test fun inProgressNeedsAStartAndNoEnd() {
        assertFalse(ep(s1, 1, 10_000, false, 1).inProgress)         // under 30 s
        assertTrue(ep(s1, 1, 30 * min, false, 1).inProgress)
        assertFalse(ep(s1, 1, 57 * min, false, 1).inProgress)       // past 92%
        assertEquals("30 min left", timeLeft(ep(s1, 1, 30 * min, false, 1)))
        val back = WatchEntry.fromJson(ep(s1, 1, 30 * min, false, 1).toJson())
        assertEquals(ep(s1, 1, 30 * min, false, 1), back)
    }

    @Test fun episodeLengthPrefersPortalThenPlayedThenShow() {
        val played = WatchEntry("e:1:2:3", "Show", "", "", "", 3, false, 10 * min, 44 * min + 40_000, 0, false, null)
        assertEquals("42 min", episodeLength(42, played, "60 min"))
        assertEquals("45 min", episodeLength(null, played, "60 min"))
        assertEquals("~60 min", episodeLength(null, null, "60 min"))
        assertNull(episodeLength(null, null, ""))
        assertEquals("1 h 5 min", formatMinutes(65))
        assertEquals("2 h", formatMinutes(120))
    }
}
