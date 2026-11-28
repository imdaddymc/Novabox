package com.novabox.tv.data

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Learned intros and credits, one small file per show in the app's own storage: about 20–60 KB
 * once a show is learned, up to about 150 KB while it is still listening. At most [MAX_SHOWS]
 * shows are kept (the least recently watched go first), so it never passes about 6 MB.
 */
object SkipStore {
    const val MAX_SHOWS = 40

    /** One background thread at low priority does the comparing, so playback never waits for it. */
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "skip-learn").apply { isDaemon = true }
    }

    /** Which show [r] is an episode of, or null for films and anything else. */
    fun showOf(r: PlayRequest.Vod): String? = when {
        r.direct && r.releaseName.isNotBlank() && DebridNext.isEpisode(r.releaseName) ->
            ReleaseNames.parse(r.releaseName).title.trim().lowercase().takeIf { it.isNotBlank() }?.let { "d:$it" }
        !r.direct && r.episode != null && r.item != null -> "p:${r.item.id}"
        else -> null
    }

    private fun dir() = File(AppGraph.appContext.filesDir, "skip").apply { mkdirs() }
    private fun file(show: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(show.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir(), "$hash.bin")
    }

    fun load(show: String): SkipPrints.Show? = runCatching {
        file(show).takeIf { it.isFile }?.let { f -> f.setLastModified(System.currentTimeMillis()); SkipPrints.decode(f.readBytes()) }
    }.getOrNull()

    /** Adds what was heard of one episode, in the background. */
    fun learnLater(show: String, heard: SkipPrints.Heard) {
        android.util.Log.i("NovaSkip", "$show ${heard.episode}: heard intro=${heard.intro != null} credits=${heard.outro != null}")
        if (heard.intro == null && heard.outro == null) return
        worker.execute {
            runCatching {
                val before = load(show) ?: SkipPrints.Show()
                val after = SkipPrints.learn(before, heard)
                android.util.Log.i("NovaSkip", "$show ${heard.episode}: intro ${after.intro?.size ?: "-"} codes (${after.introSamples.size} kept), credits ${after.outro?.size ?: "-"} codes (${after.outroSamples.size} kept)")
                val f = file(show)
                val tmp = File(f.path + ".tmp")
                tmp.writeBytes(SkipPrints.encode(after))
                tmp.renameTo(f)
                prune()
            }
        }
    }

    private fun prune() {
        val files = dir().listFiles { f -> f.name.endsWith(".bin") }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_SHOWS).forEach { it.delete() }
    }

    /** Space used, in bytes. */
    fun totalBytes(): Long = runCatching { dir().listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)

    fun clear() { runCatching { dir().listFiles()?.forEach { it.delete() } } }
}
