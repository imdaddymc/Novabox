package com.novabox.tv.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A fake Stalker portal that behaves like a typical /c/ + portal.php deployment. */
class StalkerClientTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var expireOnce = false
    private var messy = false

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val url = request.requestUrl!!
                if (url.encodedPath != "/portal.php") return MockResponse().setResponseCode(404)
                val type = url.queryParameter("type"); val action = url.queryParameter("action")
                if (action != "handshake" && request.getHeader("Authorization") != "Bearer TOKEN123") {
                    return MockResponse().setBody("Authorization failed.")
                }
                if (expireOnce && action == "get_genres") { expireOnce = false; return MockResponse().setBody("Authorization failed.") }
                if (messy && action == "get_genres") return MockResponse().setResponseCode(500).setBody("<html>error</html>")
                if (messy && action == "get_all_channels") return MockResponse().setBody("""{"js":{"total_items":3,"data":[
                    {"id":"7","name":"A","number":"1","cmd":"a"},{"id":"7","name":"A again","number":"2","cmd":"a2"},
                    {"id":"","name":"B","number":"3","cmd":"b"},{"id":"","name":"C","number":"4","cmd":"c"}]}}""")
                val body = when ("$type/$action") {
                    "stb/handshake" -> """{"js":{"token":"TOKEN123","random":"abc"}}"""
                    "stb/get_profile" -> """{"js":{"id":"1","name":"test","status":0}}"""
                    "itv/get_genres" -> """{"js":[{"id":"*","title":"All"},{"id":"10","title":"News"},{"id":"11","title":"Sport"}]}"""
                    "itv/get_all_channels" -> """{"js":{"total_items":2,"data":[
                        {"id":"5","name":"Sport One","number":"2","cmd":"ffrt http://localhost/ch/5","logo":"/logos/5.png","tv_genre_id":"11","use_http_tmp_link":"1"},
                        {"id":"4","name":"News 24","number":"1","cmd":"ffmpeg http://stream/news.m3u8","logo":"","tv_genre_id":"10","use_http_tmp_link":"0"}]}}"""
                    "itv/create_link" -> """{"js":{"id":"5","cmd":"ffmpeg http://cdn.example/live/5.ts?token=xyz"}}"""
                    "itv/get_short_epg" -> """{"js":[{"name":"Match of the day","descr":"Football","start_timestamp":"1700000000","stop_timestamp":"1700003600"}]}"""
                    "vod/get_categories" -> """{"js":[{"id":"*","title":"All"},{"id":"3","title":"Action"}]}"""
                    "vod/get_ordered_list" -> """{"js":{"total_items":30,"max_page_items":14,"data":[{"id":"77","name":"Big Film","cmd":"eyJ0eXBlIjoibW92aWUifQ==","screenshot_uri":"http://img/77.jpg","year":"2023","rating_imdb":"7.1","description":"A film","time":"120"}]}}"""
                    "series/get_ordered_list" -> if (url.queryParameter("movie_id") != null)
                        """{"js":{"total_items":2,"data":[{"id":"9:1","name":"Season 1","cmd":"c1","series":[1,2,3]},{"id":"9:2","name":"Season 2","cmd":"c2","series":[1,2]}]}}"""
                        else """{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"9","name":"A Show","cmd":"s","series":[]}]}}"""
                    "vod/create_link" -> """{"js":{"cmd":"ffmpeg http://cdn.example/vod/${url.queryParameter("series")}.mp4"}}"""
                    "account_info/get_main_info" -> """{"js":{"mac":"00:1A:79:12:34:56","phone":"Premium","end_date":"2027-01-01"}}"""
                    else -> """{"js":[]}"""
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun client() = StalkerClient(Profile(portalUrl = server.url("/c/").toString(), mac = "00:1a:79:12:34:56"))

    @Test fun fullFlow() = runTest {
        val c = client()
        c.login()
        assertTrue(c.loggedIn)
        val hs = requests.first { it.requestUrl!!.queryParameter("action") == "handshake" }
        assertTrue(hs.getHeader("Cookie")!!.contains("mac=00%3A1A%3A79%3A12%3A34%3A56"))
        assertTrue(hs.getHeader("User-Agent")!!.contains("MAG250"))

        val genres = c.liveGenres()
        assertEquals(listOf("10", "11"), genres.map { it.id })

        val ch = c.allChannels()
        assertEquals(listOf("News 24", "Sport One"), ch.map { it.name })
        assertTrue(ch[1].logo.endsWith("/logos/5.png") && ch[1].logo.startsWith("http"))

        assertEquals("http://cdn.example/live/5.ts?token=xyz", c.liveLink(ch[1]))
        assertEquals("Match of the day", c.shortEpg("5").single().title)

        val page = c.vodPage(VodKind.MOVIE, "*", 1)
        assertEquals("Big Film", page.items.single().name)
        assertEquals("120 min", page.items.single().duration)
        assertTrue(page.hasMore)

        val show = c.vodPage(VodKind.SERIES, "*", 1).items.single()
        val seasons = c.seasons(show)
        assertEquals(2, seasons.size)
        assertEquals("http://cdn.example/vod/2.mp4", c.vodLink(seasons[0].cmd, 2))

        val acc = c.accountInfo()
        assertTrue(acc.fields.any { it.second == "2027-01-01" })
    }

    @Test fun reLoginsWhenTokenExpires() = runTest {
        val c = client()
        c.login()
        expireOnce = true
        assertEquals(2, c.liveGenres().size)
        assertEquals(2, requests.count { it.requestUrl!!.queryParameter("action") == "handshake" })
    }

    @Test fun cleansCommands() {
        assertEquals("http://a/b", StalkerClient.cleanCmd("ffmpeg http://a/b"))
        assertEquals("http://a/b", StalkerClient.cleanCmd("ffrt http://a/b "))
    }

    /** Real portals send repeated or blank ids; lists keyed by id crash on those, so they must come out unique. */
    @Test fun messyPortalGivesUniqueIds() = runTest {
        messy = true
        val c = client()
        c.login()
        val ch = c.allChannels()
        assertEquals(3, ch.size)
        assertEquals(ch.size, ch.map { it.id }.toSet().size)
        assertTrue(runCatching { c.liveGenres() }.isFailure)
    }
}
