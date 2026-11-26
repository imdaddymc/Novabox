package com.novabox.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** What a release name like "The.Long.Road.2024.1080p.WEB-DL.mkv" describes. */
data class ReleaseName(val title: String, val year: Int?, val season: Int?, val episode: Int?) {
    val isSeries: Boolean get() = season != null
}

/** The film or show a release matched: proper title, year and poster. */
data class TitleInfo(val title: String, val year: String, val series: Boolean, val poster: String, val cast: String)

object ReleaseNames {
    private val sxe = Regex("""(?i)^s(\d{1,2})(?:e(\d{1,3}))?$""")
    private val nxn = Regex("""(?i)^(\d{1,2})x(\d{1,3})$""")
    private val yearRx = Regex("""^(19[2-9]\d|20[0-4]\d)$""")
    /** Words that mark the end of the title in a release name. */
    private val stop = setOf(
        "480p", "576p", "720p", "1080p", "1080i", "2160p", "4k", "uhd", "hdr", "hdr10", "dv", "web", "webrip", "web-dl", "webdl",
        "bluray", "blu-ray", "brrip", "bdrip", "dvdrip", "hdtv", "hdrip", "remux", "x264", "x265", "h264", "h265", "hevc",
        "aac", "ac3", "dts", "proper", "repack", "extended", "unrated", "complete", "multi", "imax",
    )

    fun parse(raw: String): ReleaseName {
        var name = raw.substringAfterLast('/')
        if (RealDebrid.isVideo(name)) name = name.substringBeforeLast('.')
        name = name.replace(Regex("""\[[^\]]*]"""), " ").replace(Regex("""\(([^)]*)\)""")) { m ->
            // keep a year in brackets, drop other bracketed tags
            if (yearRx.matches(m.groupValues[1].trim())) " ${m.groupValues[1].trim()} " else " "
        }
        val words = name.split(Regex("""[\s._]+""")).filter { it.isNotBlank() }
        val title = mutableListOf<String>()
        var year: Int? = null
        var season: Int? = null
        var episode: Int? = null
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val lw = w.lowercase()
            val s = sxe.matchEntire(w) ?: nxn.matchEntire(w)
            if (s != null) { season = s.groupValues[1].toInt(); episode = s.groupValues[2].toIntOrNull(); break }
            if (lw == "season" && words.getOrNull(i + 1)?.toIntOrNull() != null) { season = words[i + 1].toInt(); break }
            // A year ends the title, unless the title would be empty ("2012.2009.1080p")
            if (yearRx.matches(w) && title.isNotEmpty()) { year = w.toInt(); i++; continue }
            if (year != null || lw in stop || lw.startsWith("ddp") || lw.startsWith("dd5")) break
            title += w.trimEnd('-')
            i++
        }
        return ReleaseName(title.joinToString(" ").trim().ifBlank { words.firstOrNull().orEmpty() }, year, season, episode)
    }
}

/**
 * Posters for Real-Debrid items, from IMDb's public title suggestions (the search-as-you-type
 * list on imdb.com). No account or key is needed. Answers are kept on the TV for 30 days.
 */
object Posters {
    private const val BASE = "https://v3.sg.media-imdb.com/suggestion"

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val prefs by lazy { AppGraph.appContext.getSharedPreferences("posters", Context.MODE_PRIVATE) }
    private val memory = HashMap<String, TitleInfo?>()
    private val lock = Mutex()

    private fun cacheKey(r: ReleaseName) = "${r.title.lowercase()}|${r.year ?: ""}|${r.isSeries}"

    suspend fun lookup(r: ReleaseName): TitleInfo? = withContext(Dispatchers.IO) {
        if (r.title.isBlank()) return@withContext null
        val ck = cacheKey(r)
        synchronized(memory) { if (memory.containsKey(ck)) return@withContext memory[ck] }
        lock.withLock {
            synchronized(memory) { if (memory.containsKey(ck)) return@withLock memory[ck] }
            val stored = prefs.getString("c:$ck", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (stored != null && System.currentTimeMillis() - stored.optLong("at") < 30L * 24 * 3600_000) {
                val info = stored.optJSONObject("info")?.let { fromJson(it) }
                synchronized(memory) { memory[ck] = info }
                return@withLock info
            }
            val info = fetch(r) // network errors propagate and aren't cached
            synchronized(memory) { memory[ck] = info }
            prefs.edit().putString("c:$ck", JSONObject().put("at", System.currentTimeMillis()).apply { info?.let { put("info", toJson(it)) } }.toString()).apply()
            info
        }
    }

    private fun fetch(r: ReleaseName): TitleInfo? {
        val q = r.title.lowercase().replace(Regex("""[^\p{L}\p{N} ]"""), "").trim()
        if (q.isBlank()) return null
        val first = q.first().let { if (it.isLetterOrDigit() && it.code < 128) it else 'x' }
        val url = "$BASE/$first/${URLEncoder.encode(q, "UTF-8").replace("+", "%20")}.json"
        val body = http.newCall(Request.Builder().url(url).header("User-Agent", "NovaBox (Android TV)").build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string().orEmpty()
        }
        return best(JSONObject(body), r)
    }

    /** Picks the suggestion that is a film or series as wanted, preferring the same year. */
    internal fun best(o: JSONObject, r: ReleaseName): TitleInfo? {
        val d = o.optJSONArray("d") ?: return null
        val candidates = (0 until d.length()).map { d.getJSONObject(it) }.filter { it.optString("id").startsWith("tt") }
        fun isSeries(x: JSONObject) = x.optString("qid").startsWith("tv") && x.optString("qid") != "tvMovie"
        val sameKind = candidates.filter { isSeries(it) == r.isSeries }
        val pool = sameKind.ifEmpty { candidates }
        val pick = r.year?.let { y -> pool.firstOrNull { kotlin.math.abs(it.optInt("y") - y) <= 1 } } ?: pool.firstOrNull() ?: return null
        val image = pick.optJSONObject("i")?.optString("imageUrl").orEmpty()
        return TitleInfo(
            title = pick.optString("l"),
            year = pick.optInt("y").takeIf { it > 0 }?.toString().orEmpty(),
            series = isSeries(pick),
            // Ask IMDb's image server for a small copy: posters are shown at ~120 px.
            poster = if (image.contains("._V1_")) image.replace(Regex("""\._V1_.*\.jpg$"""), "._V1_UX300_.jpg") else image,
            cast = pick.optString("s"),
        )
    }

    private fun toJson(t: TitleInfo) = JSONObject().put("title", t.title).put("year", t.year).put("series", t.series).put("poster", t.poster).put("cast", t.cast)
    private fun fromJson(o: JSONObject) = TitleInfo(o.optString("title"), o.optString("year"), o.optBoolean("series"), o.optString("poster"), o.optString("cast"))
}

/** One card on the Debrid shelf: a single item, or every season/episode of one show. */
data class ShelfGroup<T>(val key: String, val showTitle: String?, val items: List<T>) {
    val isShow: Boolean get() = showTitle != null
}

object ShelfGroups {
    /**
     * Puts series releases with the same show title on one card, in the order the show first
     * appears; films and anything without a season stay as their own cards. Items in a show are
     * sorted by season, then episode.
     */
    fun <T> group(items: List<T>, id: (T) -> String, name: (T) -> String): List<ShelfGroup<T>> {
        val shows = LinkedHashMap<String, MutableList<Pair<T, ReleaseName>>>()
        val order = mutableListOf<Any>() // String show key, or a single item
        for (item in items) {
            val r = ReleaseNames.parse(name(item))
            if (r.isSeries && r.title.isNotBlank()) {
                val k = "show:" + r.title.lowercase()
                shows.getOrPut(k) { order += k; mutableListOf() } += item to r
            } else order += ShelfGroup(id(item), null, listOf(item))
        }
        return order.map { o ->
            if (o is String) {
                val list = shows.getValue(o).sortedWith(compareBy({ it.second.season ?: 0 }, { it.second.episode ?: 0 }))
                ShelfGroup(o, list.first().second.title, list.map { it.first })
            } else {
                @Suppress("UNCHECKED_CAST")
                o as ShelfGroup<T>
            }
        }
    }
}
