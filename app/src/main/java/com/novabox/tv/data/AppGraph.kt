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

    var client: StalkerClient? = null

    /** Channels loaded for the current profile, used for zapping in the player. */
    var channels: List<Channel> = emptyList()
    var genres: List<Genre> = emptyList()

    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        val app = context.applicationContext
        profiles = ProfileStore(app)
        favorites = FavoritesStore(app)
        settings = SettingsStore(app)
        initialised = true
    }
}

/** What the player should open next. */
sealed interface PlayRequest {
    data class Live(val channels: List<Channel>, val index: Int) : PlayRequest
    data class Vod(val title: String, val cmd: String, val episode: Int?, val subtitle: String = "") : PlayRequest
}

object PlaybackHolder {
    var request: PlayRequest? = null
}
