package com.novabox.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved portal login, like a "profile" in classic STB emulators. */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val portalUrl: String = "",
    val mac: String = DEFAULT_MAC,
    val stbModel: String = "MAG250",
    val serial: String = "",
    val deviceId: String = "",
    val deviceId2: String = "",
    val signature: String = "",
    val login: String = "",
    val password: String = "",
    val timezone: String = java.util.TimeZone.getDefault().id,
    val language: String = "en",
    val userAgent: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("portalUrl", portalUrl); put("mac", mac)
        put("stbModel", stbModel); put("serial", serial); put("deviceId", deviceId)
        put("deviceId2", deviceId2); put("signature", signature); put("login", login)
        put("password", password); put("timezone", timezone); put("language", language)
        put("userAgent", userAgent)
    }

    companion object {
        const val DEFAULT_MAC = "00:1A:79:00:00:00"
        val MODELS = listOf("MAG250", "MAG254", "MAG256", "MAG322", "MAG324", "MAG349", "MAG351", "MAG424", "MAG520", "AuraHD")

        fun fromJson(o: JSONObject) = Profile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            portalUrl = o.optString("portalUrl"),
            mac = o.optString("mac", DEFAULT_MAC),
            stbModel = o.optString("stbModel", "MAG250"),
            serial = o.optString("serial"),
            deviceId = o.optString("deviceId"),
            deviceId2 = o.optString("deviceId2"),
            signature = o.optString("signature"),
            login = o.optString("login"),
            password = o.optString("password"),
            timezone = o.optString("timezone", java.util.TimeZone.getDefault().id),
            language = o.optString("language", "en"),
            userAgent = o.optString("userAgent"),
        )

        fun randomMac(): String {
            val r = java.util.Random()
            return "00:1A:79:" + (0 until 3).joinToString(":") { "%02X".format(r.nextInt(256)) }
        }

        /** Turns "001a79123456", "00-1A-79-12-34-56" or "00 1a 79 12 34 56" into "00:1A:79:12:34:56". */
        fun normalizeMac(input: String): String {
            val hex = input.filter { it.isLetterOrDigit() }.uppercase()
            return if (hex.length == 12 && hex.all { it in "0123456789ABCDEF" }) hex.chunked(2).joinToString(":") else input.trim().uppercase()
        }

        fun isValidMac(mac: String) = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(mac.trim())
    }
}

data class Genre(val id: String, val title: String)

data class Channel(
    val id: String,
    val name: String,
    val number: Int,
    val cmd: String,
    val logo: String,
    val genreId: String,
    val useTmpLink: Boolean = false,
    /** Free TV only: quality and tags such as "720p · Not 24/7". */
    val note: String = "",
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("number", number); put("cmd", cmd)
        put("logo", logo); put("genreId", genreId); put("useTmpLink", useTmpLink)
    }

    companion object {
        fun fromJson(o: JSONObject) = Channel(
            o.optString("id"), o.optString("name"), o.optInt("number"), o.optString("cmd"),
            o.optString("logo"), o.optString("genreId"), o.optBoolean("useTmpLink"),
        )
    }
}

enum class VodKind { MOVIE, SERIES }

data class VodItem(
    val id: String,
    val kind: VodKind,
    val name: String,
    val cmd: String,
    val poster: String,
    val description: String,
    val year: String,
    val rating: String,
    val genres: String,
    val director: String,
    val actors: String,
    val duration: String,
    /** Old-style portals list episode numbers directly on the series item. */
    val episodes: List<Int>,
    val categoryId: String,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("kind", kind.name); put("name", name); put("cmd", cmd); put("poster", poster)
        put("description", description); put("year", year); put("rating", rating); put("genres", genres)
        put("director", director); put("actors", actors); put("duration", duration)
        put("episodes", JSONArray(episodes)); put("categoryId", categoryId)
    }

    companion object {
        fun fromJson(o: JSONObject) = VodItem(
            o.optString("id"), runCatching { VodKind.valueOf(o.optString("kind")) }.getOrDefault(VodKind.MOVIE),
            o.optString("name"), o.optString("cmd"), o.optString("poster"), o.optString("description"),
            o.optString("year"), o.optString("rating"), o.optString("genres"), o.optString("director"),
            o.optString("actors"), o.optString("duration"),
            o.optJSONArray("episodes")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
            o.optString("categoryId"),
        )
    }
}

data class Season(val id: String, val name: String, val cmd: String, val episodes: List<Int>)

data class EpgEntry(val title: String, val description: String, val start: Long, val end: Long)

data class Page<T>(val items: List<T>, val totalItems: Int, val page: Int, val pageSize: Int) {
    val hasMore get() = pageSize > 0 && page * pageSize < totalItems
}

data class AccountInfo(val fields: List<Pair<String, String>>)
