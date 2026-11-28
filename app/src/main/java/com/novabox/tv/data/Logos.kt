package com.novabox.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Channel logos for portals that send none (or broken ones): the channel's name is looked up in a
 * small list of public logos (from the iptv-org database, about 0.5 MB, refreshed monthly from the
 * NovaBox repository; see tools/build_logos.py). Only logos the TV then shows are downloaded, and
 * the image cache keeps them. When a name could belong to channels in several countries, the
 * country most of the same category's channels come from decides; when nothing fits, the
 * channel keeps its initials rather than risk a wrong logo.
 */
object Logos {
    private const val URL = "https://raw.githubusercontent.com/imdaddymc/Novabox/main/release/logos.tsv.gz"
    private const val MAX_AGE_MS = 30L * 24 * 3600 * 1000

    private val _byName = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Channel name (as the portal writes it) to logo URL. */
    val byName: StateFlow<Map<String, String>> = _byName

    private fun file() = File(AppGraph.appContext.filesDir, "logos.tsv.gz")

    /** Finds logos for [channels]. Safe to call again whenever the list changes. */
    suspend fun prepare(channels: List<Channel>, genres: List<Genre>) = withContext(Dispatchers.IO) {
        runCatching {
            val f = file()
            if (!f.isFile || System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) download(f)
            if (!f.isFile) return@runCatching
            val wanted = channels.associateBy({ it.name }, { normalize(it.name) })
            val keys = wanted.values.toHashSet()
            val index = HashMap<String, MutableList<Pair<String, String>>>()
            GZIPInputStream(f.inputStream()).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val a = line.indexOf('\t'); if (a < 0) return@forEach
                    val k = line.substring(0, a)
                    if (k !in keys) return@forEach
                    val b = line.indexOf('\t', a + 1); if (b < 0) return@forEach
                    index.getOrPut(k) { mutableListOf() } += line.substring(a + 1, b) to line.substring(b + 1)
                }
            }
            _byName.value = resolve(channels, genres, index)
        }
    }

    /** Picks one logo per channel; see the class comment. Visible for tests. */
    internal fun resolve(channels: List<Channel>, genres: List<Genre>, index: Map<String, List<Pair<String, String>>>): Map<String, String> {
        val out = HashMap<String, String>()
        // Countries of the channels whose name fits only one, counted per category.
        val votes = HashMap<String, HashMap<String, Int>>()
        val hard = mutableListOf<Channel>()
        for (c in channels) {
            val cands = index[normalize(c.name)] ?: continue
            val hint = countryHint(c.name)
            val pick = when {
                cands.size == 1 -> cands[0]
                hint != null -> cands.firstOrNull { it.first == hint }
                else -> null
            }
            if (pick != null) {
                out[c.name] = pick.second
                votes.getOrPut(c.genreId) { HashMap() }.merge(pick.first, 1, Int::plus)
            } else if (cands.size > 1) hard += c
        }
        for (c in hard) {
            val cands = index[normalize(c.name)] ?: continue
            val v = votes[c.genreId] ?: continue
            cands.filter { (v[it.first] ?: 0) > 0 }.maxByOrNull { v[it.first] ?: 0 }?.let { out[c.name] = it.second }
        }
        return out
    }

    private fun download(f: File) {
        val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        http.newCall(Request.Builder().url(URL).build()).execute().use { r ->
            if (!r.isSuccessful) { if (f.isFile) f.setLastModified(System.currentTimeMillis()); return }
            val tmp = File(f.path + ".tmp")
            r.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return
            tmp.renameTo(f)
        }
    }

    /** "PK| ", "UK: " and "IN - " in front of a name say which country it is from. */
    internal fun countryHint(name: String): String? =
        Regex("^\\s*([A-Za-z]{2})\\s*[|:\\-]").find(name)?.groupValues?.get(1)?.uppercase()?.let { if (it == "UK") "GB" else it }

    /** The same normalization as tools/build_logos.py. */
    internal fun normalize(name: String): String {
        var s = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("[^\\p{ASCII}]"), "").uppercase()
        s = s.replace(Regex("^\\s*[A-Z]{2,4}\\s*[|:\\-]\\s*"), "")
        s = s.replace(Regex("[\\[(].*?[\\])]"), " ")
        s = s.replace(Regex("\\b(F?HD|UHD|SD|4K|8K|HEVC|H265|1080P?|720P?|HQ|LQ|RAW|BACKUP|VIP|LIVE)\\b"), " ")
        s = s.replace("&", "AND").replace("+", "PLUS")
        return s.replace(Regex("[^A-Z0-9]"), "")
    }
}
