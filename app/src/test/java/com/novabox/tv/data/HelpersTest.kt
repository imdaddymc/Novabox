package com.novabox.tv.data

import com.novabox.tv.player.resolutionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpersTest {
    @Test fun macIsNormalisedFromAnyTyping() {
        assertEquals("00:1A:79:12:34:5F", Profile.normalizeMac("001a7912345f"))
        assertEquals("00:1A:79:12:34:5F", Profile.normalizeMac("00-1a-79-12-34-5f"))
        assertEquals("00:1A:79:12:34:5F", Profile.normalizeMac(" 00 1A 79 12 34 5F "))
        assertTrue(Profile.isValidMac(Profile.normalizeMac("001A7912345F")))
        assertEquals("00:1A:79", Profile.normalizeMac("00:1a:79")) // too short: left for validation to reject
    }

    @Test fun resolutionLabels() {
        assertEquals("4K", resolutionLabel(3840, 2160))
        assertEquals("1080p", resolutionLabel(1920, 1080))
        assertEquals("1080p", resolutionLabel(1920, 800))
        assertEquals("720p", resolutionLabel(1280, 720))
        assertEquals("576p", resolutionLabel(720, 576))
        assertEquals("240p", resolutionLabel(426, 240))
    }

    @Test fun freeTvListParsesAndKeepsIdsUnique() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="http://x/l.png" group-title="News;General",CBC News (720p) [Geo-blocked]
            https://example.com/a.m3u8
            #EXTINF:-1 group-title="News",CBC News copy
            https://example.com/a.m3u8
            #EXTINF:-1 http-referrer="https://other.site/",Restream
            https://example.com/b.m3u8
            #EXTINF:-1 group-title="Music",Tunes
            #EXTVLCOPT:http-user-agent=Foo
            https://example.com/c.m3u8
        """.trimIndent()
        val list = com.novabox.tv.data.FreeTv.parse(m3u)
        assertEquals(2, list.size)
        assertEquals("CBC News", list[0].name)
        assertEquals("News", list[0].genreId)
        assertEquals("720p · Geo-blocked", list[0].note)
        assertEquals(2, list.map { it.id }.toSet().size)
    }

    @Test fun streamProblemsReadPlainly() {
        assertEquals("the stream is offline", com.novabox.tv.player.streamProblem("ERROR_CODE_IO_BAD_HTTP_STATUS"))
        assertEquals("it couldn't be reached", com.novabox.tv.player.streamProblem("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
    }

    @Test fun debridTorrentFilesKeepOnlySelectedVideos() {
        val info = org.json.JSONObject("""
            {"files":[
              {"id":1,"path":"/Show.S01E01.mkv","bytes":1000,"selected":1},
              {"id":2,"path":"/sample/readme.txt","bytes":5,"selected":1},
              {"id":3,"path":"/Show.S01E02.mkv","bytes":2000,"selected":0},
              {"id":4,"path":"/Show.S01E03.mp4","bytes":3000,"selected":1}],
             "links":["https://rd/l1","https://rd/l2","https://rd/l4"]}
        """)
        val files = com.novabox.tv.data.RealDebrid.parseTorrentFiles(info)
        assertEquals(listOf("Show.S01E01.mkv", "Show.S01E03.mp4"), files.map { it.name })
        assertEquals(listOf("https://rd/l1", "https://rd/l4"), files.map { it.link })
    }

    @Test fun debridSearchMatchesAllWords() {
        val rd = com.novabox.tv.data.RealDebrid
        assertTrue(rd.matches("The.Long.Road.2024.1080p.WEB-DL.mkv", "long road 1080p"))
        assertTrue(rd.matches("Anything", ""))
        assertEquals(false, rd.matches("The.Long.Road.2024", "short road"))
    }

    @Test fun debridNamesReadNicely() {
        assertEquals("The Long Road 2024 1080p WEB-DL", com.novabox.tv.data.RealDebrid.pretty("The.Long.Road.2024.1080p.WEB-DL.mkv"))
        assertEquals("Harbour Lights S01 1080p WEB", com.novabox.tv.data.RealDebrid.pretty("Harbour.Lights.S01.1080p.WEB"))
    }

    @Test fun releaseNamesGiveTitleYearAndSeason() {
        val p = com.novabox.tv.data.ReleaseNames::parse
        assertEquals(com.novabox.tv.data.ReleaseName("The Long Road", 2024, null, null), p("The.Long.Road.2024.1080p.WEB-DL.mkv"))
        assertEquals(com.novabox.tv.data.ReleaseName("Harbour Lights", null, 1, null), p("Harbour.Lights.S01.1080p.WEB"))
        assertEquals(com.novabox.tv.data.ReleaseName("Harbour Lights", null, 1, 3), p("Harbour.Lights.S01E03.1080p.WEB.mkv"))
        assertEquals(com.novabox.tv.data.ReleaseName("Quiet Valley", 2022, null, null), p("Quiet Valley (2022) [720p] BluRay.mp4"))
        assertEquals(com.novabox.tv.data.ReleaseName("2012", 2009, null, null), p("2012.2009.1080p.BluRay.x264.mkv"))
        assertEquals(com.novabox.tv.data.ReleaseName("Paper Kites", null, 2, null), p("Paper_Kites_Season_2_Complete"))
    }

    @Test fun postersPickTheRightKindAndYear() {
        val o = org.json.JSONObject("""
            {"d":[
              {"i":{"imageUrl":"https://m.media-amazon.com/images/M/AAA._V1_.jpg"},"id":"tt1","l":"The Long Road","q":"TV series","qid":"tvSeries","y":2010},
              {"i":{"imageUrl":"https://m.media-amazon.com/images/M/BBB._V1_.jpg"},"id":"tt2","l":"The Long Road","q":"feature","qid":"movie","y":2016},
              {"i":{"imageUrl":"https://m.media-amazon.com/images/M/CCC._V1_.jpg"},"id":"tt3","l":"The Long Road","q":"feature","qid":"movie","y":2024,"s":"A. Actor, B. Actor"},
              {"id":"/name/nm1","l":"Someone"}]}
        """)
        val film = com.novabox.tv.data.Posters.best(o, com.novabox.tv.data.ReleaseName("The Long Road", 2024, null, null))!!
        assertEquals("2024", film.year)
        assertEquals("https://m.media-amazon.com/images/M/CCC._V1_UX300_.jpg", film.poster)
        val show = com.novabox.tv.data.Posters.best(o, com.novabox.tv.data.ReleaseName("The Long Road", null, 1, null))!!
        assertTrue(show.series)
        assertEquals("2010", show.year)
    }

    @Test fun seasonsOfOneShowShareACard() {
        val names = listOf("Harbour.Lights.S02.1080p.WEB", "The.Long.Road.2024.1080p.mkv", "harbour lights S01 720p", "Paper.Kites.S01E02.mkv", "Paper.Kites.S01E01.mkv")
        val groups = com.novabox.tv.data.ShelfGroups.group(names, { it }, { it })
        assertEquals(3, groups.size)
        assertEquals(listOf("harbour lights S01 720p", "Harbour.Lights.S02.1080p.WEB"), groups[0].items)
        assertEquals(false, groups[1].isShow)
        assertEquals(listOf("Paper.Kites.S01E01.mkv", "Paper.Kites.S01E02.mkv"), groups[2].items)
    }
}
