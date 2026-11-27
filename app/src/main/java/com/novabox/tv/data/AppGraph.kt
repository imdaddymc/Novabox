package com.novabox.tv.data

import android.content.Context

/** Tiny service locator shared by the browser screens and the player. */
object AppGraph {
    lateinit var profiles: ProfileStore
        private set
    lateinit var favorites: FavoritesStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var watch: WatchStore
        private set
    lateinit var recent: RecentChannelsStore
        private set
    lateinit var freeCheck: FreeTvCheck
        private set

    lateinit var appContext: Context
        private set

    var client: StalkerClient? = null

    /** Channels loaded for the current profile, used for zapping in the player. */
    var channels: List<Channel> = emptyList()
    var genres: List<Genre> = emptyList()

    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        val app = context.applicationContext
        appContext = app
        profiles = ProfileStore(app)
        favorites = FavoritesStore(app)
        settings = SettingsStore(app)
        watch = WatchStore(app)
        recent = RecentChannelsStore(app)
        freeCheck = FreeTvCheck(app)
        // Real-Debrid progress is kept outside any profile, so it is there in Free-TV-only mode too.
        watch.switchProfile("")
        initialised = true
    }
}

/** What the player should open next. */
sealed interface PlayRequest {
    /** [direct]: the channel's cmd is a plain stream URL (Free TV), not a portal command. */
    data class Live(val channels: List<Channel>, val index: Int, val direct: Boolean = false) : PlayRequest
    /** [direct]: cmd is a plain stream URL (Real-Debrid), not a portal command. */
    data class Vod(
        val title: String, val cmd: String, val episode: Int?, val subtitle: String = "", val direct: Boolean = false,
        /** Where progress is saved (see [WatchEntry]); blank means not tracked. */
        val key: String = "",
        val poster: String = "",
        val item: VodItem? = null,
        /** Start here instead of the saved position (0 = from the start). Null: resume if saved. */
        val startAtMs: Long? = null,
        /** Real-Debrid file name, for finding the next episode. */
        val releaseName: String = "",
        /** Finds the next episode to autoplay (Real-Debrid shows); null when there is none to look for. */
        val next: (suspend (String) -> Vod?)? = null,
    ) : PlayRequest
}

object PlaybackHolder {
    var request: PlayRequest? = null
}
