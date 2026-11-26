package com.novabox.tv.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.novabox.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A newer NovaBox published in the GitHub repo's release/version.json. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/**
 * In-app updates. Reads release/version.json from the NovaBox GitHub repo, downloads the APK
 * and installs it over this app with Android's package installer. Because the new APK is signed
 * with the same key, Android keeps all app data: profiles, favourites and settings.
 */
object Updater {
    private const val REPO_RAW = "https://raw.githubusercontent.com/imdaddymc/Novabox/main/release/"
    const val VERSION_URL = REPO_RAW + "version.json"
    private const val DEFAULT_APK = REPO_RAW + "NovaBox.apk"

    /** Set by [UpdateInstallReceiver] when Android reports that the install failed. */
    val installError = MutableStateFlow<String?>(null)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Returns the newer version, or null if this one is up to date. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$VERSION_URL?t=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("the update server replied HTTP ${r.code}")
            val o = JSONObject(r.body?.string().orEmpty())
            val info = UpdateInfo(
                versionCode = o.getInt("versionCode"),
                versionName = o.optString("versionName", "?"),
                apkUrl = o.optString("apk").ifBlank { DEFAULT_APK },
                notes = o.optString("notes"),
            )
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        }
    }

    /** Downloads the APK into the app's cache, reporting progress from 0 to 1 (or -1 if unknown). */
    suspend fun download(context: Context, info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "NovaBox-${info.versionCode}.apk")
        val req = Request.Builder().url(info.apkUrl + (if ('?' in info.apkUrl) "&" else "?") + "v=${info.versionCode}").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("download failed (HTTP ${r.code})")
            val body = r.body ?: throw IOException("download failed (empty reply)")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 128 * 1024) {
                            lastReport = done
                            progress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
        }
        // Make sure it really is a newer NovaBox before handing it to the installer.
        @Suppress("DEPRECATION")
        val pkg = context.packageManager.getPackageArchiveInfo(out.path, 0)
            ?: throw IOException("the downloaded file isn't a valid app")
        if (pkg.packageName != context.packageName) throw IOException("the downloaded file isn't NovaBox")
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toInt() else pkg.versionCode
        if (code <= BuildConfig.VERSION_CODE) throw IOException("the downloaded file isn't newer than this version")
        progress(1f)
        out
    }

    /** Android 8+ asks once per app before it may install other apps. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure {
            // Some TVs have no per-app screen; fall back to the general security settings.
            runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Hands the APK to Android's installer. Android shows its own "Update this app?" prompt. */
    fun install(context: Context, apk: File) {
        installError.value = null
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, Intent(context, UpdateInstallReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's result: shows Android's confirm screen, or reports a failure. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android closes the old version; the user reopens NovaBox.
            else -> {
                val msg = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Update cancelled"
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "This update is signed differently, so Android won't install it over the current app"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage space for the update"
                    else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update couldn't be installed"
                }
                Updater.installError.value = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
