package com.novabox.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where the user stopped in each film or episode, and which episodes are watched. Kept on the TV,
 * per profile, like favourites. Real-Debrid items use the "debrid" profile key so they survive a
 * portal switch.
 */
data class WatchEntry(
    /** "m:<film id>", "e:<series id>:<season id>:<episode>" or "d:<release name>". */
    val key: String,
    val title: String,
    val subtitle: String,
    val poster: String,
    /** How to play it again: the portal cmd (or RD link) and episode number. */
    val cmd: String,
    val episode: Int?,
    val direct: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val watched: Boolean,
    /** The film or series, for opening its page; null for Real-Debrid. */
    val item: VodItem?,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    /** Worth offering in "Continue watching": started, not finished. */
    val inProgress: Boolean get() = !watched && positionMs >= MIN_RESUME_MS && (durationMs <= 0 || positionMs < durationMs * DONE_AT)
    val isSeries: Boolean get() = key.startsWith("e:") || (item?.kind == VodKind.SERIES)

    fun toJson() = JSONObject().apply {
        put("key", key); put("title", title); put("subtitle", subtitle); put("poster", poster); put("cmd", cmd)
        episode?.let { put("episode", it) }
        put("direct", direct); put("pos", positionMs); put("dur", durationMs); put("at", updatedAt); put("watched", watched)
        item?.let { put("item", it.toJson()) }
    }

    companion object {
        const val MIN_RESUME_MS = 30_000L
        /** Past 92% counts as watched: end credits. */
        const val DONE_AT = 0.92

        fun fromJson(o: JSONObject) = WatchEntry(
            o.optString("key"), o.optString("title"), o.optString("subtitle"), o.optString("poster"), o.optString("cmd"),
            if (o.has("episode")) o.optInt("episode") else null, o.optBoolean("direct"),
            o.optLong("pos"), o.optLong("dur"), o.optLong("at"), o.optBoolean("watched"),
            o.optJSONObject("item")?.let { VodItem.fromJson(it) },
        )

        fun filmKey(item: VodItem) = "m:${item.id}"
        fun episodeKey(item: VodItem, season: Season, episode: Int) = "e:${item.id}:${season.id}:$episode"
        fun debridKey(releaseName: String) = "d:${releaseName.lowercase()}"
    }
}

class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("watch", Context.MODE_PRIVATE)
    private var profileId = ""
    private val _entries = MutableStateFlow<Map<String, WatchEntry>>(emptyMap())
    val entries: StateFlow<Map<String, WatchEntry>> = _entries

    fun switchProfile(id: String) {
        profileId = id
        _entries.value = read(id) + read(DEBRID)
    }

    private fun read(pid: String): Map<String, WatchEntry> = runCatching {
        val a = JSONArray(prefs.getString("w_$pid", "[]"))
        (0 until a.length()).map { WatchEntry.fromJson(a.getJSONObject(it)) }.associateBy { it.key }
    }.getOrDefault(emptyMap())

    private fun persist() {
        val all = _entries.value.values
        fun write(pid: String, list: List<WatchEntry>) {
            // Keep the newest 300 per profile so the store stays small.
            val keep = list.sortedByDescending { it.updatedAt }.take(300)
            prefs.edit().putString("w_$pid", JSONArray(keep.map { it.toJson() }).toString()).apply()
        }
        write(DEBRID, all.filter { it.key.startsWith("d:") })
        if (profileId.isNotEmpty()) write(profileId, all.filterNot { it.key.startsWith("d:") })
    }

    fun get(key: String): WatchEntry? = _entries.value[key]

    /** Saves where playback is. Marks it watched once it is nearly over. */
    fun save(e: WatchEntry) {
        val done = e.watched || (e.durationMs > 0 && e.positionMs >= e.durationMs * WatchEntry.DONE_AT)
        _entries.value = _entries.value + (e.key to e.copy(watched = done, updatedAt = System.currentTimeMillis()))
        persist()
    }

    fun setWatched(key: String, watched: Boolean) {
        val e = _entries.value[key] ?: return
        _entries.value = _entries.value + (key to e.copy(watched = watched, positionMs = if (watched) e.positionMs else 0, updatedAt = System.currentTimeMillis()))
        persist()
    }

    fun remove(key: String) {
        _entries.value = _entries.value - key
        persist()
    }

    /** Started but not finished, newest first. */
    fun continueWatching(): List<WatchEntry> = _entries.value.values.filter { it.inProgress }.sortedByDescending { it.updatedAt }

    companion object { private const val DEBRID = "debrid" }
}

/** The last live channels watched, newest first, per profile. */
class RecentChannelsStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent", Context.MODE_PRIVATE)
    private var profileId = ""
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    fun switchProfile(id: String) {
        profileId = id
        _channels.value = runCatching {
            val a = JSONArray(prefs.getString("r_$id", "[]"))
            (0 until a.length()).map { Channel.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun add(c: Channel) {
        val list = (listOf(c) + _channels.value.filterNot { it.id == c.id }).take(MAX)
        _channels.value = list
        if (profileId.isNotEmpty()) prefs.edit().putString("r_$profileId", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    companion object { const val MAX = 12 }
}
