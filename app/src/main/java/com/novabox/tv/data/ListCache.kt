package com.novabox.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The channel list and categories saved on the TV for each profile, so NovaBox opens straight to
 * them and refreshes from the portal in the background. Stored gzipped: about 0.1 MB for a portal
 * with 5,700 channels, and never more than [MAX_BYTES] (a bigger list just isn't saved).
 */
object ListCache {
    const val MAX_BYTES = 5L * 1024 * 1024

    data class Lists(
        val genres: List<Genre>,
        val channels: List<Channel>,
        val movieCategories: List<Genre> = emptyList(),
        val seriesCategories: List<Genre> = emptyList(),
        val savedAt: Long = System.currentTimeMillis(),
        /** The portal and MAC the lists came from; a changed profile doesn't reuse them. */
        val owner: String = "",
    )

    fun ownerOf(p: Profile) = "${p.portalUrl.trim()}|${p.mac.trim().uppercase()}"

    private fun dir() = File(AppGraph.appContext.filesDir, "lists").apply { mkdirs() }
    private fun file(profileId: String) = File(dir(), "${profileId.filter { it.isLetterOrDigit() || it == '-' }}.json.gz")

    fun load(p: Profile): Lists? = runCatching { file(p.id).takeIf { it.isFile }?.readBytes()?.let(::decode) }.getOrNull()
        ?.takeIf { it.owner == ownerOf(p) && it.channels.isNotEmpty() }

    /** Saves [lists]; false when it would be bigger than [MAX_BYTES]. */
    fun save(profileId: String, lists: Lists): Boolean = runCatching {
        val bytes = encode(lists)
        if (bytes.size > MAX_BYTES) { file(profileId).delete(); return false }
        val tmp = File(dir(), "${file(profileId).name}.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file(profileId))
    }.getOrDefault(false)

    fun delete(profileId: String) { runCatching { file(profileId).delete() } }

    /** Space used by all saved lists, in bytes. */
    fun totalBytes(): Long = runCatching { dir().listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)

    fun encode(l: Lists): ByteArray {
        fun genres(g: List<Genre>) = JSONArray().apply { g.forEach { put(JSONObject().put("id", it.id).put("title", it.title)) } }
        val o = JSONObject()
            .put("v", 1).put("savedAt", l.savedAt).put("owner", l.owner)
            .put("genres", genres(l.genres))
            .put("movies", genres(l.movieCategories))
            .put("series", genres(l.seriesCategories))
            .put("channels", JSONArray().apply { l.channels.forEach { put(it.toJson()) } })
        return ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(o.toString().toByteArray()) } }.toByteArray()
    }

    fun decode(bytes: ByteArray): Lists {
        val o = JSONObject(GZIPInputStream(ByteArrayInputStream(bytes)).use { String(it.readBytes()) })
        fun genres(a: JSONArray?) = (0 until (a?.length() ?: 0)).mapNotNull { i -> a!!.optJSONObject(i)?.let { Genre(it.optString("id"), it.optString("title")) } }
        val ch = o.optJSONArray("channels")
        return Lists(
            genres(o.optJSONArray("genres")),
            (0 until (ch?.length() ?: 0)).mapNotNull { i -> ch!!.optJSONObject(i)?.let(Channel::fromJson) },
            genres(o.optJSONArray("movies")), genres(o.optJSONArray("series")),
            o.optLong("savedAt"), o.optString("owner"),
        )
    }
}
