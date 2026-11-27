package com.novabox.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Free TV's monthly clean-up. The channel list itself comes fresh from iptv-org (new channels show
 * up on the next refresh); this checks every stream on the TV and hides the ones that don't answer,
 * then checks again a month later so channels that come back reappear.
 */
class FreeTvCheck(context: Context) {
    private val prefs = context.getSharedPreferences("freecheck", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true).build()

    /** Stream URLs that failed the last check. */
    fun dead(): Set<String> = runCatching {
        val o = JSONObject(prefs.getString("dead", "{}") ?: "{}")
        o.keys().asSequence().toSet()
    }.getOrDefault(emptySet())

    fun lastCheck(country: String): Long = prefs.getLong("sweep_$country", 0)

    /** A month since this country's streams were last checked. */
    fun isDue(country: String, now: Long = System.currentTimeMillis()) = now - lastCheck(country) >= MONTH

    /**
     * Tries every stream, a few at a time, and records which don't answer. A stream gets a second
     * try a few seconds later before it counts as dead, so a brief hiccup doesn't hide it.
     */
    suspend fun check(country: String, channels: List<Channel>, onProgress: (done: Int, total: Int) -> Unit): Set<String> = withContext(Dispatchers.IO) {
        val urls = channels.map { it.cmd }.distinct()
        val limit = Semaphore(8)
        var done = 0
        val failed = coroutineScope {
            urls.map { u ->
                async {
                    val ok = limit.withPermit { answers(u) || run { delay(3000); answers(u) } }
                    synchronized(this@FreeTvCheck) { done++; onProgress(done, urls.size) }
                    if (ok) null else u
                }
            }.awaitAll().filterNotNull().toSet()
        }
        // Keep other countries' results; replace this country's.
        val all = JSONObject(prefs.getString("dead", "{}") ?: "{}")
        urls.forEach { all.remove(it) }
        val now = System.currentTimeMillis()
        failed.forEach { all.put(it, now) }
        prefs.edit().putString("dead", all.toString()).putLong("sweep_$country", now).apply()
        failed
    }

    /** The stream's server answers with something playable-looking (not an error page). */
    private fun answers(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", FreeTv.USER_AGENT)
            .header("Range", "bytes=0-4095").build()
        http.newCall(req).execute().use { r -> r.isSuccessful && (r.body?.contentLength() ?: -1L) != 0L }
    }.getOrDefault(false)

    companion object { const val MONTH = 30L * 24 * 3600_000 }
}
