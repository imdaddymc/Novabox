package com.novabox.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PortalException(msg: String) : Exception(msg)

/**
 * Client for Stalker / Ministra middleware portals (the protocol MAG set-top boxes use).
 * Logs in with the MAC address, then exposes live TV, VOD, series, EPG and stream links.
 */
class StalkerClient(val profile: Profile) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mac = profile.mac.trim().uppercase()
    private val model = profile.stbModel.ifBlank { "MAG250" }
    val serial = profile.serial.ifBlank { defaultSerial(mac) }
    private val deviceId = profile.deviceId.ifBlank { defaultDeviceId(mac) }
    private val deviceId2 = profile.deviceId2.ifBlank { deviceId }
    private val signature = profile.signature.ifBlank { sha256(serial + mac).uppercase() }

    val userAgent: String = profile.userAgent.ifBlank { Profile.defaultUserAgent(model) }

    private var endpoint: String = ""
    private var referer: String = ""
    @Volatile private var token: String = ""
    private val loginMutex = Mutex()
    var loggedIn = false
        private set

    /** Headers the stream servers usually expect too. */
    fun playbackHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("X-User-Agent", "Model: $model; Link: Ethernet")
        put("Cookie", cookie())
        if (referer.isNotEmpty()) put("Referer", referer)
    }

    // The MAC goes in plain (colons are allowed in cookies): PHP portals read it either way, but some
    // panels match the raw Cookie header and can't find an encoded MAC.
    private fun cookie() = "mac=$mac; stb_lang=${profile.language}; timezone=${enc(profile.timezone)}"

    // ---------------------------------------------------------------- login

    private fun normalize(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        u = u.removeSuffix("index.html")
        if (!u.endsWith("/")) u += "/"
        return u
    }

    /** Works out the possible load.php / portal.php endpoints for what the user typed. */
    private fun candidateEndpoints(url: String): List<String> {
        val u = normalize(url)
        val host = u.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}:${it.port}/" } ?: u
        val out = LinkedHashSet<String>()
        if (u.endsWith(".php/") ) out += u.removeSuffix("/")
        if (u.contains("/stalker_portal/")) {
            out += u.substringBefore("/stalker_portal/") + "/stalker_portal/server/load.php"
        }
        if (u.contains("/c/")) {
            val base = u.substringBefore("/c/") + "/"
            out += base + "portal.php"
            out += base + "server/load.php"
        }
        out += u + "portal.php"
        out += u + "server/load.php"
        out += host + "portal.php"
        out += host + "stalker_portal/server/load.php"
        out += host + "server/load.php"
        return out.toList()
    }

    suspend fun login() = withContext(Dispatchers.IO) { loginMutex.withLock {
        val errors = mutableListOf<String>()
        for (ep in candidateEndpoints(profile.portalUrl)) {
            try {
                endpoint = ep
                referer = if (ep.contains("/stalker_portal/")) ep.substringBefore("server/load.php") + "c/"
                else normalize(profile.portalUrl).let { if (it.contains("/c/")) it else ep.substringBeforeLast("/") + "/c/" }
                token = ""
                val hs = rawCall(mapOf("type" to "stb", "action" to "handshake", "token" to "", "prehash" to "0"))
                val js = hs.optJSONObject("js") ?: throw PortalException("no handshake")
                token = js.optString("token")
                if (token.isEmpty()) throw PortalException("empty token")
                getProfile()
                loggedIn = true
                return@withLock
            } catch (e: PortalSays) {
                loggedIn = false
                throw PortalException(e.message ?: "The portal refused this device")
            } catch (e: Exception) {
                errors += "${ep}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        loggedIn = false
        throw PortalException("Could not log in to the portal. Check the URL, and that your provider has registered this MAC address: $mac\n" + errors.take(3).joinToString("\n"))
    } }

    private fun getProfile() {
        val metrics = JSONObject().apply {
            put("mac", mac); put("sn", serial); put("model", model); put("type", "STB"); put("uid", deviceId); put("random", "")
        }.toString()
        val r = rawCall(
            mapOf(
                "type" to "stb", "action" to "get_profile", "hd" to "1",
                "ver" to profile.firmware.ifBlank { Profile.DEFAULT_FIRMWARE },
                "num_banks" to "2", "sn" to serial, "stb_type" to model, "client_type" to "STB",
                "image_version" to profile.imageVersion.ifBlank { Profile.DEFAULT_IMAGE_VERSION }, "video_out" to "hdmi", "device_id" to deviceId, "device_id2" to deviceId2,
                "signature" to signature, "auth_second_step" to "1", "hw_version" to profile.hwVersion.ifBlank { Profile.DEFAULT_HW_VERSION },
                "not_valid_token" to "0", "metrics" to metrics, "hw_version_2" to sha1(mac),
                "timestamp" to (System.currentTimeMillis() / 1000).toString(), "api_signature" to "262", "prehash" to "",
            )
        )
        val js = r.optJSONObject("js")
        // Some portals ask for a login + password on top of the MAC.
        if (profile.login.isNotBlank() && (js == null || js.optInt("status", 0) == 2 || js.optString("id").isEmpty())) {
            val a = rawCall(
                mapOf(
                    "type" to "stb", "action" to "do_auth", "login" to profile.login, "password" to profile.password,
                    "device_id" to deviceId, "device_id2" to deviceId2,
                )
            )
            if (a.opt("js") == false) throw PortalException("Login or password rejected")
        }
        if (js != null && js.has("blocked") && js.optString("blocked") == "1") throw PortalSays("This MAC address ($mac) is blocked by the portal")
        // Portals answer an unknown MAC with a message and no account id; show it instead of carrying on.
        val msg = js?.let { it.optString("msg").ifBlank { it.optString("block_msg") } }?.takeIf { it.isNotBlank() && it != "null" }
        if (js != null && msg != null && js.optString("id").let { it.isBlank() || it == "null" } && profile.login.isBlank()) {
            throw PortalSays("The portal says: $msg\nMAC sent: $mac")
        }
    }

    // ---------------------------------------------------------------- transport

    private fun rawCall(params: Map<String, String>): JSONObject {
        val url = endpoint.toHttpUrlOrNull()?.newBuilder()?.apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("JsHttpRequest", "1-xml")
        }?.build() ?: throw PortalException("Invalid portal URL")
        val req = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("X-User-Agent", "Model: $model; Link: Ethernet")
            .header("Cookie", cookie())
            .header("Referer", referer)
            .header("Accept", "*/*")
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401 || body.contains("Authorization failed", ignoreCase = true)) throw AuthExpired()
            if (!resp.isSuccessful) throw PortalException("HTTP ${resp.code}")
            val start = body.indexOf('{')
            if (start < 0) throw PortalException("Unexpected response from portal")
            return JSONObject(body.substring(start))
        }
    }

    private class AuthExpired : Exception("Authorization failed")
    /** The portal itself answered and refused this device: no point trying other endpoints. */
    private class PortalSays(msg: String) : Exception(msg)

    /** Calls the portal, logging in again once if the session expired. */
    private suspend fun call(params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        if (!loggedIn) login()
        try {
            rawCall(params)
        } catch (e: AuthExpired) {
            loggedIn = false
            login()
            rawCall(params)
        }
    }

    // ---------------------------------------------------------------- account

    suspend fun accountInfo(): AccountInfo {
        val js = call(mapOf("type" to "account_info", "action" to "get_main_info")).optJSONObject("js")
        val fields = mutableListOf<Pair<String, String>>()
        fields += "MAC" to mac
        js?.keys()?.forEach { k ->
            val v = js.optString(k)
            if (v.isNotBlank() && v != "null") fields += k.replace('_', ' ').replaceFirstChar { it.uppercase() } to v
        }
        return AccountInfo(fields)
    }

    suspend fun keepAlive() {
        runCatching {
            call(mapOf("type" to "watchdog", "action" to "get_events", "event_active_id" to "0", "init" to "0", "cur_play_type" to "1"))
        }
    }

    // ---------------------------------------------------------------- live TV

    suspend fun liveGenres(): List<Genre> {
        val js = call(mapOf("type" to "itv", "action" to "get_genres")).optJSONArray("js") ?: JSONArray()
        return (0 until js.length()).mapNotNull { i ->
            js.optJSONObject(i)?.let { Genre(it.optString("id"), it.optString("title").ifBlank { "Unnamed" }) }
        }.filter { it.id != "*" && it.id.isNotBlank() }.distinctBy { it.id }
    }

    suspend fun allChannels(): List<Channel> {
        val list = runCatching {
            val js = call(mapOf("type" to "itv", "action" to "get_all_channels")).optJSONObject("js")
            parseChannels(js?.optJSONArray("data"))
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) return list
        // Fallback: page through the ordered list.
        val out = mutableListOf<Channel>()
        var p = 1
        while (p < 200) {
            val page = runCatching {
                call(
                    mapOf("type" to "itv", "action" to "get_ordered_list", "genre" to "*", "force_ch_link_check" to "", "fav" to "0", "sortby" to "number", "hd" to "0", "p" to "$p")
                )
            }
            // Keep the pages already loaded if a later one fails.
            val js = (if (out.isEmpty()) page.getOrThrow() else page.getOrNull())?.optJSONObject("js") ?: break
            val items = parseChannels(js.optJSONArray("data")).map { if (it.id.matches(Regex("n\\d+"))) it.copy(id = "p$p-${it.id}") else it }
            out += items
            val total = js.optInt("total_items")
            if (items.isEmpty() || out.size >= total) break
            p++
        }
        return out.distinctBy { it.id }
    }

    private fun parseChannels(a: JSONArray?): List<Channel> {
        a ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Channel(
                // Lists are keyed by id, and a repeated id would crash them: some portals send blanks or repeats.
                id = o.optString("id").ifBlank { "n$i" },
                name = o.optString("name").trim(),
                number = o.optString("number").toIntOrNull() ?: (i + 1),
                cmd = o.optString("cmd"),
                logo = absolute(o.optString("logo")),
                genreId = o.optString("tv_genre_id"),
                useTmpLink = o.optString("use_http_tmp_link") == "1",
            )
        }.distinctBy { it.id }.sortedBy { it.number }
    }

    suspend fun shortEpg(channelId: String, size: Int = 10): List<EpgEntry> {
        val r = call(mapOf("type" to "itv", "action" to "get_short_epg", "ch_id" to channelId, "size" to "$size"))
        val a = r.optJSONArray("js") ?: r.optJSONObject("js")?.optJSONArray("data") ?: JSONArray()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            EpgEntry(
                title = o.optString("name"),
                description = o.optString("descr"),
                start = o.optLong("start_timestamp") * 1000,
                end = o.optLong("stop_timestamp") * 1000,
            )
        }
    }

    suspend fun liveLink(c: Channel): String {
        val link = runCatching {
            call(
                mapOf("type" to "itv", "action" to "create_link", "cmd" to c.cmd, "series" to "", "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0", "force_ch_link_check" to "0")
            ).optJSONObject("js")?.optString("cmd").orEmpty()
        }.getOrDefault("")
        return cleanCmd(link.ifBlank { c.cmd })
    }

    // ---------------------------------------------------------------- VOD + series

    private fun type(kind: VodKind) = if (kind == VodKind.MOVIE) "vod" else "series"

    suspend fun vodCategories(kind: VodKind): List<Genre> {
        val own = categoriesOf(type(kind))
        // Some portals file TV shows under Movies (VOD): those categories move to the Series tab.
        val vod = if (kind == VodKind.SERIES) runCatching { categoriesOf("vod") }.getOrDefault(emptyList()) else own
        return when (kind) {
            VodKind.MOVIE -> own.filterNot { isShowCategory(it.title) }
            VodKind.SERIES -> own + vod.filter { isShowCategory(it.title) }.map { it.copy(id = VOD_PREFIX + it.id) }
        }.distinctBy { it.id }
    }

    private suspend fun categoriesOf(type: String): List<Genre> {
        val js = call(mapOf("type" to type, "action" to "get_categories")).optJSONArray("js") ?: JSONArray()
        return (0 until js.length()).mapNotNull { i ->
            js.optJSONObject(i)?.let { Genre(it.optString("id"), it.optString("title").ifBlank { "Unnamed" }) }
        }.filter { it.id.isNotBlank() }.distinctBy { it.id }
    }

    /**
     * One page of a category. Films and shows are kept apart: shows a portal lists under Movies are
     * left out of the Movies tab (unless [keepShows]) and appear in the Series tab through their category.
     */
    suspend fun vodPage(kind: VodKind, categoryId: String, page: Int, search: String = "", keepShows: Boolean = false): Page<VodItem> {
        val fromVod = categoryId.startsWith(VOD_PREFIX)
        val cat = categoryId.removePrefix(VOD_PREFIX)
        val params = mutableMapOf(
            "type" to if (fromVod) "vod" else type(kind), "action" to "get_ordered_list", "category" to cat.ifBlank { "*" },
            "genre" to "*", "sortby" to "added", "hd" to "0", "fav" to "0", "p" to "$page",
        )
        if (search.isNotBlank()) params["search"] = search
        val js = call(params).optJSONObject("js") ?: return Page(emptyList(), 0, page, 0)
        val a = js.optJSONArray("data") ?: JSONArray()
        var items = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { parseVod(it, if (fromVod) VodKind.MOVIE else kind, categoryId) } }
        if (fromVod) items = items.map { it.copy(kind = VodKind.SERIES, source = VodKind.MOVIE) }
        val pageSize = js.optInt("max_page_items", items.size)
        val fetched = items.size
        if (kind == VodKind.MOVIE && !keepShows) items = items.filter { it.kind == VodKind.MOVIE }
        return Page(items, js.optInt("total_items"), page, pageSize, fetched)
    }

    private fun parseVod(o: JSONObject, kind: VodKind, cat: String): VodItem {
        val eps = o.optJSONArray("series")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
        val realKind = if (kind == VodKind.SERIES || eps.isNotEmpty() || o.optString("is_series") == "1") VodKind.SERIES else VodKind.MOVIE
        return VodItem(
            id = o.optString("id"),
            kind = realKind,
            name = o.optString("name").trim(),
            cmd = o.optString("cmd"),
            poster = absolute(o.optString("screenshot_uri").ifBlank { o.optString("cover_big").ifBlank { o.optString("pic") } }),
            description = o.optString("description").clean(),
            year = o.optString("year").clean(),
            rating = (o.optString("rating_imdb").ifBlank { o.optString("rating_kinopoisk") }).clean(),
            genres = o.optString("genres_str").clean(),
            director = o.optString("director").clean(),
            actors = o.optString("actors").clean(),
            duration = o.optString("time").clean().let { if (it.isNotEmpty() && it != "0") "$it min" else "" },
            episodes = eps,
            categoryId = o.optString("category_id", cat),
            source = kind,
        )
    }

    /**
     * Seasons of a show. Newer portals nest them under the item; shows listed under Movies are asked
     * for through the VOD section, and seasons without an episode list get their episodes fetched.
     */
    suspend fun seasons(item: VodItem): List<Season> {
        if (item.episodes.isNotEmpty()) return listOf(Season(item.id, "Episodes", item.cmd, item.episodes))
        val types = if (item.source == VodKind.MOVIE) listOf("vod", "series") else listOf("series", "vod")
        for (t in types) {
            val found = runCatching { seasonsFrom(t, item) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private suspend fun seasonsFrom(type: String, item: VodItem): List<Season> {
        val js = call(
            mapOf(
                "type" to type, "action" to "get_ordered_list", "movie_id" to item.id, "season_id" to "0",
                "episode_id" to "0", "category" to item.categoryId.removePrefix(VOD_PREFIX).ifBlank { "*" }, "sortby" to "added", "p" to "1",
            )
        ).optJSONObject("js") ?: return emptyList()
        val a = js.optJSONArray("data") ?: return emptyList()
        val rows = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        // A portal that ignores movie_id sends its whole catalogue back; only keep rows that look like seasons.
        val looksLikeSeasons = rows.isNotEmpty() && rows.none { it.optString("id") == item.id } && rows.all { r ->
            r.optJSONArray("series")?.length()?.let { it > 0 } == true || r.optBoolean("is_season") || r.optString("is_season") == "1" ||
                r.optString("name").contains("season", ignoreCase = true) || r.optString("id").contains(':')
        }
        if (!looksLikeSeasons) return emptyList()
        return rows.mapIndexedNotNull { i, o ->
            val sid = o.optString("id")
            val name = o.optString("name").ifBlank { "Season ${i + 1}" }
            val listed = o.optJSONArray("series")?.let { s -> (0 until s.length()).map { s.optInt(it) } }.orEmpty().filter { it > 0 }
            if (listed.isNotEmpty()) return@mapIndexedNotNull Season(sid, name, o.optString("cmd"), listed)
            val eps = runCatching { episodesOf(type, item, sid) }.getOrDefault(emptyList())
            if (eps.isEmpty()) null
            else Season(sid, name, o.optString("cmd"), eps.map { it.first }, eps.associate { it.first to it.second })
        }
    }

    /** Episodes listed one by one under a season: (episode number, play command). */
    private suspend fun episodesOf(type: String, item: VodItem, seasonId: String): List<Pair<Int, String>> {
        val js = call(
            mapOf(
                "type" to type, "action" to "get_ordered_list", "movie_id" to item.id, "season_id" to seasonId,
                "episode_id" to "0", "category" to item.categoryId.removePrefix(VOD_PREFIX).ifBlank { "*" }, "sortby" to "added", "p" to "1",
            )
        ).optJSONObject("js") ?: return emptyList()
        val a = js.optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val cmd = o.optString("cmd").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            val n = o.optString("series_number").toIntOrNull()
                ?: Regex("""\d+""").findAll(o.optString("name")).lastOrNull()?.value?.toIntOrNull() ?: (i + 1)
            n to cmd
        }.distinctBy { it.first }.sortedBy { it.first }
    }

    suspend fun vodLink(cmd: String, episode: Int? = null): String {
        val js = call(
            mapOf(
                "type" to "vod", "action" to "create_link", "cmd" to cmd, "series" to (episode?.toString() ?: ""),
                "forced_storage" to "", "disable_ad" to "0", "download" to "0", "force_ch_link_check" to "0",
            )
        ).optJSONObject("js")
        val link = js?.optString("cmd").orEmpty()
        if (link.isBlank()) throw PortalException("The portal did not return a stream link")
        return cleanCmd(link)
    }

    // ---------------------------------------------------------------- helpers

    private fun absolute(path: String): String {
        if (path.isBlank() || path == "null") return ""
        if (path.startsWith("http")) return path
        val base = endpoint.toHttpUrlOrNull() ?: return path
        return "${base.scheme}://${base.host}:${base.port}" + (if (path.startsWith("/")) path else "/stalker_portal/misc/logos/320/$path")
    }

    private fun String.clean() = if (this == "null" || this == "N/A") "" else this.trim()

    companion object {
        /** Marks a Series-tab category that really lives in the portal's VOD section. */
        const val VOD_PREFIX = "vod:"

        private val SHOW_WORDS = Regex("""\b(tv ?shows?|web ?series|series|serials?|dramas|seasons?|shows|novelas?|dizi)\b""", RegexOption.IGNORE_CASE)
        fun isShowCategory(title: String) = SHOW_WORDS.containsMatchIn(title)

        fun cleanCmd(cmd: String): String {
            var c = cmd.trim()
            listOf("ffmpeg ", "ffrt ", "ffrt2 ", "ffrt3 ", "auto ").forEach { if (c.startsWith(it)) c = c.removePrefix(it) }
            return c.trim()
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun hash(alg: String, s: String) =
            MessageDigest.getInstance(alg).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        fun md5(s: String) = hash("MD5", s)
        fun sha1(s: String) = hash("SHA-1", s)
        fun sha256(s: String) = hash("SHA-256", s)
        /** What a blank serial number or device ID becomes for this MAC (shown in the profile editor too). */
        fun defaultSerial(mac: String) = md5(mac.trim().uppercase()).uppercase().take(13)
        fun defaultDeviceId(mac: String) = sha256(mac.trim().uppercase()).uppercase()
    }
}
