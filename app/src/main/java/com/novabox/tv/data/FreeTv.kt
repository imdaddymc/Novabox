package com.novabox.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Free TV: channels that broadcasters stream publicly, from the iptv-org community list
 * (github.com/iptv-org/iptv). No portal, profile or login is involved.
 */
object FreeTv {
    data class Country(val code: String, val name: String)

    val countries = listOf(
        Country("ca", "Canada"), Country("us", "United States"), Country("uk", "United Kingdom"),
        Country("ie", "Ireland"), Country("au", "Australia"), Country("nz", "New Zealand"),
        Country("in", "India"), Country("pk", "Pakistan"), Country("bd", "Bangladesh"),
        Country("ae", "United Arab Emirates"), Country("sa", "Saudi Arabia"), Country("qa", "Qatar"),
        Country("eg", "Egypt"), Country("tr", "Turkey"), Country("fr", "France"),
        Country("de", "Germany"), Country("it", "Italy"), Country("es", "Spain"),
        Country("pt", "Portugal"), Country("nl", "Netherlands"), Country("mx", "Mexico"),
        Country("br", "Brazil"), Country("ph", "Philippines"), Country("ng", "Nigeria"),
        Country("za", "South Africa"),
    )

    fun country(code: String) = countries.firstOrNull { it.code == code } ?: countries.first()

    /** The TV's own country if we have a list for it, otherwise Canada. */
    fun defaultCountry(): String {
        val c = java.util.Locale.getDefault().country.lowercase().let { if (it == "gb") "uk" else it }
        return countries.firstOrNull { it.code == c }?.code ?: "ca"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = HashMap<String, Pair<Long, List<Channel>>>()

    suspend fun channels(countryCode: String, forceRefresh: Boolean = false): List<Channel> = withContext(Dispatchers.IO) {
        val hit = cache[countryCode]
        if (!forceRefresh && hit != null && System.currentTimeMillis() - hit.first < 6 * 3600_000L) return@withContext hit.second
        val urls = listOf(
            "https://iptv-org.github.io/iptv/countries/$countryCode.m3u",
            "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/countries/$countryCode.m3u",
        )
        var lastError: Exception? = null
        for (u in urls) {
            try {
                val text = http.newCall(Request.Builder().url(u).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    r.body?.string().orEmpty()
                }
                val list = parse(text)
                if (list.isEmpty()) throw IOException("the channel list was empty")
                cache[countryCode] = System.currentTimeMillis() to list
                return@withContext list
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException(friendlyError(lastError))
    }

    /** Plain-language reason for a failed list download; Free TV needs the internet, not a portal. */
    private fun friendlyError(e: Exception?): String {
        val m = (e?.message ?: "").lowercase()
        val why = when {
            e is java.net.UnknownHostException || "unable to resolve" in m -> "the TV doesn't seem to be online"
            e is java.net.SocketTimeoutException || "timeout" in m -> "the connection timed out"
            e is javax.net.ssl.SSLException || "cert" in m || "trust anchor" in m ->
                "a secure connection couldn't be made (check the TV's date and time)"
            m.startsWith("http ") -> "the list server replied ${e?.message}"
            "empty" in m -> "the list came back empty"
            else -> "the list server couldn't be reached"
        }
        return "Couldn't load the free channel list: $why. Free TV needs an internet connection."
    }

    /**
     * Parses an extended M3U playlist. Entries that need a faked referrer or user agent are left
     * out: they are usually re-streams of other services rather than a broadcaster's own free feed.
     */
    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var info: String? = null
        var needsSpoofing = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = line
                    needsSpoofing = line.contains("http-referrer=") || line.contains("http-user-agent=")
                }
                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> needsSpoofing = true
                line.startsWith("#") -> Unit
                info != null -> {
                    if (!needsSpoofing && (line.startsWith("http://") || line.startsWith("https://"))) {
                        out += toChannel(info, line, out.size + 1)
                    }
                    info = null
                    needsSpoofing = false
                }
            }
        }
        return bestFirst(out)
    }

    /** The stream's height from its label ("1080p" → 1080), or 0 when the list doesn't say. */
    fun height(c: Channel): Int = Regex("""\b(\d{3,4})p\b""").find(c.note)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /**
     * Free streams are checked by iptv-org with ordinary browser and player requests; some servers
     * refuse an unknown app name, so playback and the channel check look like a regular browser.
     */
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** "HD · 1080p and up" in Free TV. */
    const val HD_MIN = 1080

    /**
     * When a channel is listed more than once (720p and 1080p feeds), its sharpest feed moves to
     * where the channel first appears, so it is the one picked first. The others stay as backups.
     */
    internal fun bestFirst(list: List<Channel>): List<Channel> {
        val byName = list.groupBy { it.name.lowercase() }
        val seen = HashSet<String>()
        val out = ArrayList<Channel>(list.size)
        for (c in list) {
            val k = c.name.lowercase()
            if (!seen.add(k)) continue
            out += byName.getValue(k).sortedByDescending { height(it) } // stable: equal heights keep list order
        }
        return out.mapIndexed { i, c -> c.copy(number = i + 1) }
    }

    private val attr = Regex("""([a-zA-Z-]+)="([^"]*)"""")

    private fun toChannel(info: String, url: String, number: Int): Channel {
        val attrs = attr.findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
        val afterAttrs = info.substring((info.lastIndexOf('"').takeIf { it >= 0 } ?: 0))
        var title = afterAttrs.substringAfter(',', "").trim().ifBlank { attrs["tvg-name"].orEmpty() }
        val tags = Regex("""\[([^\]]+)]""").findAll(title).map { it.groupValues[1] }.toList()
        title = title.replace(Regex("""\s*\[[^\]]+]"""), "").trim()
        val quality = Regex("""\((\d{3,4}p)\)""").find(title)?.groupValues?.get(1).orEmpty()
        title = title.replace(Regex("""\s*\(\d{3,4}p\)"""), "").trim()
        val group = attrs["group-title"].orEmpty().split(';').firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifBlank { "Other" }
        val note = buildList { if (quality.isNotEmpty()) add(quality); addAll(tags) }.joinToString(" · ")
        return Channel(
            // The number keeps ids unique: some lists carry the same stream URL twice.
            id = "free:$number:" + url.hashCode().toUInt().toString(16),
            name = title.ifBlank { "Channel $number" },
            number = number,
            cmd = url,
            logo = attrs["tvg-logo"].orEmpty(),
            genreId = group,
            note = note,
        )
    }
}
