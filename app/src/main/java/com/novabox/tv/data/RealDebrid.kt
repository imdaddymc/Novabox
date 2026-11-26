package com.novabox.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One Real-Debrid account login, kept on the TV only. */
class DebridStore(context: Context) {
    private val prefs = context.getSharedPreferences("debrid", Context.MODE_PRIVATE)
    var clientId: String? get() = prefs.getString("clientId", null); set(v) { prefs.edit().putString("clientId", v).apply() }
    var clientSecret: String? get() = prefs.getString("clientSecret", null); set(v) { prefs.edit().putString("clientSecret", v).apply() }
    var accessToken: String? get() = prefs.getString("accessToken", null); set(v) { prefs.edit().putString("accessToken", v).apply() }
    var refreshToken: String? get() = prefs.getString("refreshToken", null); set(v) { prefs.edit().putString("refreshToken", v).apply() }
    var expiresAt: Long get() = prefs.getLong("expiresAt", 0); set(v) { prefs.edit().putLong("expiresAt", v).apply() }
    val linked: Boolean get() = refreshToken != null && clientId != null && clientSecret != null
    fun clear() = prefs.edit().clear().apply()
}

/**
 * Real-Debrid: the user's own torrents and downloads, played through RD's unrestricted links.
 * Login uses RD's device-code flow for open-source apps, so no password is typed on the TV.
 * NovaBox doesn't search torrent sites; it only shows what is already in the user's account.
 */
object RealDebrid {
    private const val BASE = "https://api.real-debrid.com"
    /** Real-Debrid's published client ID for open-source apps (device-code flow). */
    private const val OPEN_SOURCE_CLIENT_ID = "X245A4XAIBGVM"

    data class DeviceCode(val userCode: String, val deviceCode: String, val verificationUrl: String, val intervalSec: Int, val expiresAt: Long)
    data class User(val username: String, val premium: Boolean, val expiration: String)
    data class Torrent(val id: String, val name: String, val bytes: Long, val status: String, val progress: Int, val added: String)
    data class Download(val id: String, val name: String, val bytes: Long, val url: String, val streamable: Boolean, val generated: String)
    data class RdFile(val name: String, val bytes: Long, val link: String)

    private val store by lazy { DebridStore(AppGraph.appContext) }
    val linked: Boolean get() = store.linked

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun call(req: Request): String = http.newCall(req).execute().use { r ->
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw RdException(r.code, rdError(body) ?: "HTTP ${r.code}")
        body
    }

    private fun rdError(body: String): String? = runCatching { JSONObject(body).optString("error").ifBlank { null } }.getOrNull()

    class RdException(val code: Int, message: String) : IOException(message)

    // ------------------------------------------------------------------ login

    suspend fun startLogin(): DeviceCode = withContext(Dispatchers.IO) {
        val url = "$BASE/oauth/v2/device/code".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", OPEN_SOURCE_CLIENT_ID)
            .addQueryParameter("new_credentials", "yes").build()
        val o = JSONObject(call(Request.Builder().url(url).build()))
        DeviceCode(
            userCode = o.getString("user_code"),
            deviceCode = o.getString("device_code"),
            verificationUrl = o.optString("verification_url", "https://real-debrid.com/device"),
            intervalSec = o.optInt("interval", 5).coerceIn(2, 30),
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 600) * 1000,
        )
    }

    /** True once the user has entered the code and NovaBox holds a token. */
    suspend fun pollLogin(code: DeviceCode): Boolean = withContext(Dispatchers.IO) {
        val url = "$BASE/oauth/v2/device/credentials".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", OPEN_SOURCE_CLIENT_ID)
            .addQueryParameter("code", code.deviceCode).build()
        val creds = try {
            JSONObject(call(Request.Builder().url(url).build()))
        } catch (e: RdException) {
            if (e.code in 400..403) return@withContext false // not approved yet
            throw e
        }
        val id = creds.optString("client_id").ifBlank { return@withContext false }
        val secret = creds.optString("client_secret").ifBlank { return@withContext false }
        store.clientId = id
        store.clientSecret = secret
        token(code.deviceCode)
        true
    }

    private fun token(code: String) {
        val form = FormBody.Builder()
            .add("client_id", store.clientId ?: throw IOException("Not linked"))
            .add("client_secret", store.clientSecret ?: throw IOException("Not linked"))
            .add("code", code)
            .add("grant_type", "http://oauth.net/grant_type/device/1.0")
            .build()
        val o = JSONObject(call(Request.Builder().url("$BASE/oauth/v2/token").post(form).build()))
        store.accessToken = o.getString("access_token")
        store.refreshToken = o.getString("refresh_token")
        store.expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000
    }

    fun unlink() = store.clear()

    // ------------------------------------------------------------------ API

    private fun authorised(build: (String) -> Request): String {
        if (!store.linked) throw IOException("Real-Debrid isn't linked")
        if (store.accessToken == null || System.currentTimeMillis() > store.expiresAt - 60_000) token(store.refreshToken!!)
        return try {
            call(build(store.accessToken!!))
        } catch (e: RdException) {
            if (e.code != 401) throw e
            token(store.refreshToken!!)
            call(build(store.accessToken!!))
        }
    }

    private fun get(path: String) = authorised { t -> Request.Builder().url("$BASE/rest/1.0$path").header("Authorization", "Bearer $t").build() }

    suspend fun user(): User = withContext(Dispatchers.IO) {
        val o = JSONObject(get("/user"))
        User(o.optString("username"), o.optString("type") == "premium", o.optString("expiration"))
    }

    /** Up to 500 torrents, newest first. */
    suspend fun torrents(): List<Torrent> = withContext(Dispatchers.IO) {
        pages("/torrents") { o ->
            Torrent(o.optString("id"), o.optString("filename"), o.optLong("bytes"), o.optString("status"), o.optInt("progress"), o.optString("added"))
        }
    }

    /** Up to 500 entries of the download history, newest first. */
    suspend fun downloads(): List<Download> = withContext(Dispatchers.IO) {
        pages("/downloads") { o ->
            Download(o.optString("id"), o.optString("filename"), o.optLong("filesize"), o.optString("download"), o.optInt("streamable") == 1, o.optString("generated"))
        }
    }

    private fun <T> pages(path: String, map: (JSONObject) -> T): List<T> {
        val out = mutableListOf<T>()
        for (page in 1..5) {
            val text = try { get("$path?limit=100&page=$page") } catch (e: RdException) { if (e.code == 404 || e.code == 204) break else throw e }
            if (text.isBlank()) break
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) out += map(arr.getJSONObject(i))
            if (arr.length() < 100) break
        }
        return out
    }

    /** The torrent's video files, each with the RD link to unrestrict. */
    suspend fun torrentFiles(id: String): List<RdFile> = withContext(Dispatchers.IO) {
        parseTorrentFiles(JSONObject(get("/torrents/info/$id")))
    }

    /** Turns an RD link into a direct stream URL. */
    suspend fun unrestrict(link: String): String = withContext(Dispatchers.IO) {
        val o = JSONObject(authorised { t ->
            Request.Builder().url("$BASE/rest/1.0/unrestrict/link").header("Authorization", "Bearer $t")
                .post(FormBody.Builder().add("link", link).build()).build()
        })
        o.getString("download")
    }

    private val videoExt = setOf("mkv", "mp4", "m4v", "avi", "mov", "ts", "m2ts", "webm", "wmv", "mpg", "mpeg", "flv")
    fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase() in videoExt

    /** RD lists one link per selected file, in file order. */
    internal fun parseTorrentFiles(o: JSONObject): List<RdFile> {
        val links = o.optJSONArray("links") ?: JSONArray()
        val files = o.optJSONArray("files") ?: JSONArray()
        val out = mutableListOf<RdFile>()
        var li = 0
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            if (f.optInt("selected") != 1) continue
            val link = links.optString(li++, "")
            val name = f.optString("path").substringAfterLast('/')
            if (link.isNotBlank() && isVideo(name)) out += RdFile(name, f.optLong("bytes"), link)
        }
        return out
    }

    /** Words in [query] must all appear in [name], ignoring case, dots and dashes. */
    fun matches(name: String, query: String): Boolean {
        val n = name.lowercase().replace(Regex("[._\\-\\[\\]()]"), " ")
        return query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.all { it in n }
    }

    /** "The.Long.Road.2024.mkv" -> "The Long Road 2024 .mkv"-free display name: dots and underscores become spaces. */
    fun pretty(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val base = if (ext in videoExt) name.substringBeforeLast('.') else name
        return base.replace(Regex("[._]+"), " ").trim()
    }

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} MB"
        else -> "${bytes / 1024} KB"
    }
}
