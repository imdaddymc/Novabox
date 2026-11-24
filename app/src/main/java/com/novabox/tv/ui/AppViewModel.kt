package com.novabox.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novabox.tv.data.AccountInfo
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.Channel
import com.novabox.tv.data.EpgEntry
import com.novabox.tv.data.Genre
import com.novabox.tv.data.Profile
import com.novabox.tv.data.Season
import com.novabox.tv.data.StalkerClient
import com.novabox.tv.data.VodItem
import com.novabox.tv.data.VodKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Route {
    data object Connecting : Route
    data object Home : Route
    data class Details(val item: VodItem) : Route
    data class EditProfile(val profile: Profile?, val firstRun: Boolean = false) : Route
    data class Error(val message: String) : Route
}

enum class HomeTab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series"), FAVORITES("Favourites"), SEARCH("Search"), SETTINGS("Settings") }

/** Paged list of movies or series for one category. */
class VodList {
    val gridState = androidx.compose.foundation.lazy.grid.LazyGridState()
    var lastOpened = -1
    val items = mutableStateListOf<VodItem>()
    var page by mutableStateOf(0)
    var hasMore by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

class AppViewModel : ViewModel() {
    val backStack = mutableStateListOf<Route>()
    val route: Route get() = backStack.lastOrNull() ?: Route.Connecting

    var tab by mutableStateOf(HomeTab.LIVE)
    var activeProfile by mutableStateOf<Profile?>(null)
        private set

    // Live
    var genres by mutableStateOf<List<Genre>>(emptyList())
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var selectedGenre by mutableStateOf<String?>(null) // null = all
    var liveLoading by mutableStateOf(false)
    val epg = mutableStateMapOf<String, List<EpgEntry>>()
    private var epgJob: Job? = null

    // VOD / series
    val categories = mutableStateMapOf<VodKind, List<Genre>>()
    val selectedCategory = mutableStateMapOf<VodKind, String>()
    private val lists = HashMap<String, VodList>()

    // Details
    var seasons by mutableStateOf<List<Season>>(emptyList())
    var seasonsLoading by mutableStateOf(false)

    // Search
    var searchQuery by mutableStateOf("")
    var searchChannels by mutableStateOf<List<Channel>>(emptyList())
    var searchMovies by mutableStateOf<List<VodItem>>(emptyList())
    var searchSeries by mutableStateOf<List<VodItem>>(emptyList())
    var searching by mutableStateOf(false)
    private var searchJob: Job? = null

    var account by mutableStateOf<AccountInfo?>(null)
    var toast by mutableStateOf<String?>(null)

    private var keepAliveJob: Job? = null

    val client: StalkerClient? get() = AppGraph.client

    fun start() {
        if (backStack.isNotEmpty()) return
        val p = AppGraph.profiles.active
        if (p == null) backStack.add(Route.EditProfile(null, firstRun = true)) else connect(p)
    }

    fun push(r: Route) { backStack.add(r) }
    /** Set when coming back from a details page, so the grid can re-focus the poster. */
    var restoreFocus = false

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        if (backStack.last() is Route.Details) restoreFocus = true
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun connect(p: Profile) {
        AppGraph.profiles.activeId = p.id
        activeProfile = p
        AppGraph.favorites.switchProfile(p.id)
        backStack.clear()
        backStack.add(Route.Connecting)
        genres = emptyList(); channels = emptyList(); categories.clear(); lists.clear(); epg.clear(); account = null
        selectedGenre = null; selectedCategory.clear()
        val c = StalkerClient(p)
        AppGraph.client = c
        viewModelScope.launch {
            try {
                c.login()
                liveLoading = true
                val g = async { c.liveGenres() }
                val ch = async { c.allChannels() }
                genres = runCatching { g.await() }.getOrDefault(emptyList())
                channels = ch.await()
                AppGraph.channels = channels
                AppGraph.genres = genres
                liveLoading = false
                backStack.clear()
                backStack.add(Route.Home)
                startKeepAlive()
            } catch (e: Exception) {
                liveLoading = false
                backStack.clear()
                backStack.add(Route.Error(e.message ?: "Connection failed"))
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = viewModelScope.launch {
            while (isActive) {
                delay(120_000)
                client?.keepAlive()
            }
        }
    }

    // ------------------------------------------------------------- live

    fun channelsFor(genreId: String?): List<Channel> =
        if (genreId == null) channels else channels.filter { it.genreId == genreId }

    fun loadEpg(c: Channel) {
        epgJob?.cancel()
        if (epg.containsKey(c.id)) return
        epgJob = viewModelScope.launch {
            delay(350) // wait until focus settles while scrolling
            val list = runCatching { client?.shortEpg(c.id, 6) }.getOrNull() ?: emptyList()
            epg[c.id] = list
        }
    }

    // ------------------------------------------------------------- vod

    fun ensureCategories(kind: VodKind) {
        if (categories.containsKey(kind)) return
        categories[kind] = emptyList()
        viewModelScope.launch {
            val cats = runCatching { client?.vodCategories(kind) }.getOrNull() ?: emptyList()
            categories[kind] = cats
            if (selectedCategory[kind] == null) {
                selectedCategory[kind] = cats.firstOrNull { it.id == "*" }?.id ?: cats.firstOrNull()?.id ?: "*"
            }
        }
    }

    fun list(kind: VodKind, cat: String): VodList = lists.getOrPut("$kind/$cat") { VodList() }

    fun loadMore(kind: VodKind, cat: String) {
        val l = list(kind, cat)
        if (l.loading || !l.hasMore) return
        l.loading = true
        viewModelScope.launch {
            try {
                val page = client!!.vodPage(kind, cat, l.page + 1)
                l.items.addAll(page.items)
                l.page = page.page
                l.hasMore = page.hasMore && page.items.isNotEmpty()
                l.error = null
            } catch (e: Exception) {
                l.error = e.message
                l.hasMore = false
            } finally {
                l.loading = false
            }
        }
    }

    fun openDetails(item: VodItem) {
        seasons = emptyList()
        push(Route.Details(item))
        if (item.kind == VodKind.SERIES) {
            seasonsLoading = true
            viewModelScope.launch {
                seasons = runCatching { client?.seasons(item) }.getOrNull() ?: emptyList()
                seasonsLoading = false
            }
        }
    }

    // ------------------------------------------------------------- search

    fun search(q: String) {
        searchQuery = q
        searchJob?.cancel()
        if (q.isBlank()) {
            searchChannels = emptyList(); searchMovies = emptyList(); searchSeries = emptyList(); return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            searching = true
            searchChannels = channels.filter { it.name.contains(q, ignoreCase = true) }.take(60)
            val m = async { runCatching { client?.vodPage(VodKind.MOVIE, "*", 1, q)?.items }.getOrNull() ?: emptyList() }
            val s = async { runCatching { client?.vodPage(VodKind.SERIES, "*", 1, q)?.items }.getOrNull() ?: emptyList() }
            searchMovies = m.await()
            searchSeries = s.await().map { it.copy(kind = VodKind.SERIES) }
            searching = false
        }
    }

    // ------------------------------------------------------------- settings

    fun loadAccount() {
        viewModelScope.launch { account = runCatching { client?.accountInfo() }.getOrNull() }
    }

    fun saveProfile(p: Profile, connectNow: Boolean) {
        AppGraph.profiles.upsert(p)
        if (connectNow) connect(p) else pop()
    }

    fun deleteProfile(p: Profile) {
        AppGraph.profiles.delete(p.id)
        if (activeProfile?.id == p.id) {
            val next = AppGraph.profiles.active
            if (next != null) connect(next) else { backStack.clear(); backStack.add(Route.EditProfile(null, true)) }
        }
    }

    fun showToast(msg: String) { toast = msg }
}
