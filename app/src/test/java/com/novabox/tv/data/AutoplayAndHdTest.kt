package com.novabox.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayAndHdTest {
    @Test fun nextEpisodeInSeasonThenNextSeason() {
        val files = listOf("Show.Name.S01E01.1080p.mkv", "Show.Name.S01E02.1080p.mkv", "Other.Show.S01E03.mkv", "Show.Name.S02E01.720p.mkv")
        assertEquals("Show.Name.S01E02.1080p.mkv", DebridNext.pick("Show.Name.S01E01.1080p.mkv", files) { it })
        assertEquals("Show.Name.S02E01.720p.mkv", DebridNext.pick("Show.Name.S01E02.1080p.mkv", files) { it }) // end of season 1
        assertNull(DebridNext.pick("Show.Name.S02E01.720p.mkv", files) { it })                              // nothing after
        assertNull(DebridNext.pick("Other.Show.S01E02.mkv", listOf("Show.Name.S01E03.mkv")) { it })         // other show
        assertFalse(DebridNext.isEpisode("Some.Film.2019.1080p.mkv"))
    }

    @Test fun seasonPacksCanHoldTheNextEpisode() {
        val torrents = listOf("Show.Name.S01.1080p.WEB", "Show.Name.S02.1080p", "Show.Name.S01E05.720p", "Show.Name.S03.1080p", "Film.2020.1080p")
        assertEquals(listOf("Show.Name.S01.1080p.WEB", "Show.Name.S02.1080p", "Show.Name.S01E05.720p"),
            DebridNext.holders("Show.Name.S01E04.1080p.mkv", torrents) { it })
    }

    private fun ch(n: Int, name: String, note: String) = Channel("free:$n", name, n, "http://x/$n", "", "News", note = note)

    @Test fun freeTvPutsTheSharpestFeedFirst() {
        val list = FreeTv.bestFirst(listOf(ch(1, "CBC News", "720p"), ch(2, "Global", "1080p"), ch(3, "CBC News", "1080p · Not 24/7"), ch(4, "CTV", "")))
        assertEquals(listOf("CBC News" to 1080, "CBC News" to 720, "Global" to 1080, "CTV" to 0), list.map { it.name to FreeTv.height(it) })
        assertEquals(listOf(1, 2, 3, 4), list.map { it.number })
        assertTrue(FreeTv.height(ch(5, "X", "2160p")) >= FreeTv.HD_MIN)
    }
}
