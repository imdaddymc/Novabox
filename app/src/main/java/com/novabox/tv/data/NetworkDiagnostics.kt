package com.novabox.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

enum class CheckState { PENDING, RUNNING, OK, WARN, FAIL, SKIPPED }

data class CheckResult(val title: String, val state: CheckState = CheckState.PENDING, val detail: String = "")

/**
 * Step-by-step connection check: local network, internet, internet speed,
 * portal DNS, portal server, portal login and a live stream test.
 */
class NetworkDiagnostics(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val titles = listOf(
        "Network connection",
        "Internet access",
        "Internet speed",
        "Portal address (DNS)",
        "Portal server",
        "Portal login",
        "Live stream test",
    )

    suspend fun run(profile: Profile?, sampleChannel: Channel?, update: (Int, CheckResult) -> Unit) = withContext(Dispatchers.IO) {
        fun set(i: Int, s: CheckState, d: String) = update(i, CheckResult(titles[i], s, d))

        // 1. Local network
        set(0, CheckState.RUNNING, "Checking…")
        val local = localNetwork()
        set(0, local.first, local.second)
        if (local.first == CheckState.FAIL) {
            for (i in 1 until titles.size) set(i, CheckState.SKIPPED, "Skipped: no network connection")
            return@withContext
        }

        // 2. Internet reachability
        set(1, CheckState.RUNNING, "Contacting Google…")
        val internet = timed {
            http.newCall(Request.Builder().url("http://connectivitycheck.gstatic.com/generate_204").build()).execute().use { it.code }
        }
        set(
            1,
            if (internet.value == 204) CheckState.OK else CheckState.FAIL,
            when {
                internet.error != null -> "Not reachable (${internet.error})"
                internet.value == 204 -> "Online · ${internet.ms} ms response"
                else -> "Unexpected reply (HTTP ${internet.value}). A captive portal or filter may be in the way"
            },
        )

        // 3. Download speed
        if (internet.value == 204) {
            set(2, CheckState.RUNNING, "Measuring download speed…")
            val speed = download("https://speed.cloudflare.com/__down?bytes=25000000", seconds = 8)
            set(2, speed.first, speed.second)
        } else {
            set(2, CheckState.SKIPPED, "Skipped: no internet access")
        }

        // 4–6. Portal
        val url = profile?.portalUrl?.trim().orEmpty()
        val portalUrl = (if (url.startsWith("http")) url else "http://$url").toHttpUrlOrNull()
        if (profile == null || portalUrl == null) {
            for (i in 3 until titles.size) set(i, CheckState.SKIPPED, "No portal profile set up")
            return@withContext
        }
        set(3, CheckState.RUNNING, "Looking up ${portalUrl.host}…")
        val dns = timed { InetAddress.getAllByName(portalUrl.host).map { it.hostAddress }.distinct() }
        if (dns.error != null) {
            set(3, CheckState.FAIL, "Can't find ${portalUrl.host} (${dns.error})")
            for (i in 4 until titles.size) set(i, CheckState.SKIPPED, "Skipped: portal address not found")
            return@withContext
        }
        val isIp = portalUrl.host.all { it.isDigit() || it == '.' || it == ':' }
        set(3, CheckState.OK, if (isIp) "${portalUrl.host} is an IP address, no lookup needed" else "${portalUrl.host} → ${dns.value!!.take(2).joinToString(", ")} · ${dns.ms} ms")

        set(4, CheckState.RUNNING, "Connecting to ${portalUrl.host}:${portalUrl.port}…")
        val server = timed {
            http.newCall(Request.Builder().url(portalUrl).header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG250 stbapp").build())
                .execute().use { it.code }
        }
        set(
            4,
            when {
                server.error != null -> CheckState.FAIL
                server.value!! >= 500 -> CheckState.WARN
                server.ms > 1500 -> CheckState.WARN
                else -> CheckState.OK
            },
            if (server.error != null) "No response (${server.error})"
            else "Responding · ${server.ms} ms" + if (server.ms > 1500) " (slow)" else "",
        )
        if (server.error != null) {
            for (i in 5 until titles.size) set(i, CheckState.SKIPPED, "Skipped: portal not responding")
            return@withContext
        }

        set(5, CheckState.RUNNING, "Logging in with MAC ${profile.mac}…")
        val client = StalkerClient(profile)
        val login = timed { client.login() }
        set(
            5,
            if (login.error == null) CheckState.OK else CheckState.FAIL,
            if (login.error == null) "Logged in · ${login.ms} ms"
            else "Login refused: ${login.error.lineSequence().first()}",
        )
        if (login.error != null) {
            set(6, CheckState.SKIPPED, "Skipped: not logged in")
            return@withContext
        }

        val channel = sampleChannel ?: runCatching { client.allChannels().firstOrNull() }.getOrNull()
        if (channel == null) {
            set(6, CheckState.SKIPPED, "No channels to test")
            return@withContext
        }
        set(6, CheckState.RUNNING, "Opening ${channel.name}…")
        val link = timed { client.liveLink(channel) }
        if (link.error != null || link.value.isNullOrBlank()) {
            set(6, CheckState.FAIL, "Couldn't get a stream link for ${channel.name}")
            return@withContext
        }
        val stream = download(link.value, seconds = 6, headers = client.playbackHeaders(), isStream = true)
        set(6, stream.first, "${channel.name}: ${stream.second}")
    }

    private fun localNetwork(): Pair<CheckState, String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return CheckState.FAIL to "Not connected. Check Wi-Fi or the network cable"
        val caps = cm.getNetworkCapabilities(net) ?: return CheckState.FAIL to "Not connected"
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
        val ip = cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        val signal = if (Build.VERSION.SDK_INT >= 29 && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.signalStrength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
        ) {
            val dbm = caps.signalStrength
            " · signal ${if (dbm >= -60) "strong" else if (dbm >= -70) "good" else if (dbm >= -80) "weak" else "very weak"} ($dbm dBm)"
        } else ""
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val detail = "$type${ip?.let { " · IP $it" } ?: ""}$signal"
        return if (validated) CheckState.OK to detail else CheckState.WARN to "$detail · no internet reported"
    }

    private class Timed<T>(val value: T?, val ms: Long, val error: String?)

    private inline fun <T> timed(block: () -> T): Timed<T> {
        val start = System.nanoTime()
        return try {
            val v = block()
            Timed(v, (System.nanoTime() - start) / 1_000_000, null)
        } catch (e: Exception) {
            Timed(null, (System.nanoTime() - start) / 1_000_000, friendly(e))
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "name lookup failed"
        is java.net.SocketTimeoutException -> "timed out"
        is java.net.ConnectException -> "connection refused"
        is javax.net.ssl.SSLException -> "secure connection failed"
        is PortalException -> e.message?.lineSequence()?.firstOrNull() ?: "portal error"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    /** Downloads for up to [seconds] and reports the average speed. */
    private fun download(url: String, seconds: Int, headers: Map<String, String> = emptyMap(), isStream: Boolean = false): Pair<CheckState, String> {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val start = System.nanoTime()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return CheckState.FAIL to "Server refused (HTTP ${resp.code})"
                val firstByteMs = (System.nanoTime() - start) / 1_000_000
                val src = resp.body!!.byteStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                val t0 = System.nanoTime()
                val limit = seconds * 1_000_000_000L
                while (System.nanoTime() - t0 < limit) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                }
                val secs = ((System.nanoTime() - t0) / 1e9).coerceAtLeast(0.2)
                val mbps = total * 8 / secs / 1_000_000
                val speed = if (mbps >= 10) "%.0f Mbps".format(mbps) else "%.1f Mbps".format(mbps)
                if (isStream) {
                    when {
                        total == 0L -> CheckState.FAIL to "No video data received"
                        mbps < 0.5 -> CheckState.WARN to "Started in $firstByteMs ms but receiving only $speed. Expect buffering"
                        else -> CheckState.OK to "Started in $firstByteMs ms · receiving $speed"
                    }
                } else {
                    when {
                        mbps < 5 -> CheckState.FAIL to "$speed. Too slow for HD; SD may buffer"
                        mbps < 15 -> CheckState.WARN to "$speed. Fine for SD and HD, not enough for 4K"
                        mbps < 25 -> CheckState.OK to "$speed. Good for HD, borderline for 4K"
                        else -> CheckState.OK to "$speed. Good for HD and 4K"
                    }
                }
            }
        } catch (e: Exception) {
            CheckState.FAIL to "Failed (${friendly(e)})"
        }
    }
}
