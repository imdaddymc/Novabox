package com.novabox.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novabox.tv.data.AccountInfo
import com.novabox.tv.data.ListCache
import com.novabox.tv.data.StalkerClient
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.Channel
import com.novabox.tv.data.EpgEntry
import com.novabox.tv.data.Genre
import com.novabox.tv.data.PlayRequest
import com.novabox.tv.data.Profile
import com.novabox.tv.data.Season
import com.novabox.tv.data.VodItem
import com.novabox.tv.data.VodKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

sealed interface Route {
    data object Connecting : Route
    data object Home : Route
    data class Details(val item: VodItem) : Route
    data class EditProfile(val profile: Profile?, val firstRun: Boolean = false) : Route
    data class Error(val message: String) : Route
    data object NetworkCheck : Route
}

enum class HomeTab(val label: String) { LIVE("Live TV"), FREE_TV("Free TV"), MOVIES("Movies"), SERIES("Series"), FAVORITES("Favourites"), SEARCH("Search"), DEBRID("Debrid"), SETTINGS("Settings") }

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
    private var connectJob: Job? = null

    val client: StalkerClient? get() = AppGraph.client

    fun start() {
        startAutoUpdates()
        if (backStack.isNotEmpty()) return
        val p = AppGraph.profiles.active
        when {
            p != null -> connect(p)
            AppGraph.settings.skippedSetup -> openFreeTvOnly()
            else -> backStack.add(Route.EditProfile(null, firstRun = true))
        }
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
        AppGraph.watch.switchProfile(p.id)
        AppGraph.recent.switchProfile(p.id)
        backStack.clear()
        backStack.add(Route.Connecting)
        genres = emptyList(); channels = emptyList(); categories.clear(); lists.clear(); epg.clear(); guideAsked.clear(); account = null
        selectedGenre = null; selectedCategory.clear()
        connectJob?.cancel()
        keepAliveJob?.cancel()
        searchJob?.cancel(); searching = false
        searchQuery = ""; searchChannels = emptyList(); searchMovies = emptyList(); searchSeries = emptyList()
        val c = StalkerClient(p)
        AppGraph.client = c
        categoriesFresh.clear()
        // Saved lists open the app straight away; the portal is asked for fresh ones in the background.
        val saved = ListCache.load(p)
        if (saved != null) {
            genres = saved.genres; channels = saved.channels
            AppGraph.channels = channels; AppGraph.genres = genres
            findLogos()
            if (saved.movieCategories.isNotEmpty()) { categories[VodKind.MOVIE] = saved.movieCategories; selectDefaultCategory(VodKind.MOVIE) }
            if (saved.seriesCategories.isNotEmpty()) { categories[VodKind.SERIES] = saved.seriesCategories; selectDefaultCategory(VodKind.SERIES) }
            backStack.clear()
            backStack.add(Route.Home)
        }
        connectJob = viewModelScope.launch {
            try {
                c.login()
                liveLoading = saved == null
                // A failed child of async would cancel this whole coroutine and crash the app, even with
                // the await wrapped, so each call catches its own errors (portals often fail one of them).
                val (g, ch) = coroutineScope {
                    val g = async { runCatching { c.liveGenres() }.getOrDefault(emptyList()) }
                    val ch = async { runCatching { c.allChannels() } }
                    g.await() to ch.await()
                }
                val fresh = ch.getOrElse { e -> if (g.isEmpty() && saved == null) throw e else null }
                // A failed refresh keeps the saved lists rather than emptying the screen.
                if (fresh != null && (fresh.isNotEmpty() || saved == null)) {
                    if (g.isNotEmpty() || saved == null) genres = g
                    if (fresh != channels) channels = fresh
                    AppGraph.channels = channels
                    AppGraph.genres = genres
                    saveLists()
                    findLogos()
                }
                liveLoading = false
                if (saved == null) {
                    backStack.clear()
                    backStack.add(Route.Home)
                }
                startKeepAlive()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                liveLoading = false
                // With saved lists showing, only a refusal by the portal (MAC not accepted) leaves them;
                // a network hiccup just says so, and playing a channel tries the portal again.
                if (saved != null && e !is com.novabox.tv.data.PortalRefused) {
                    showToast("Couldn't reach the portal. Showing your saved lists.")
                } else {
                    backStack.clear()
                    backStack.add(Route.Error(e.message ?: "Connection failed"))
                }
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

    fun channelsFor(genreId: String?): List<Channel> = when (genreId) {
        null, GUIDE -> channels
        RECENT -> AppGraph.recent.channels.value.mapNotNull { r -> channels.firstOrNull { it.id == r.id } }
        else -> channels.filter { it.genreId == genreId }
    }

    private val guideAsked = mutableSetOf<String>()
    private val guideLimit = kotlinx.coroutines.sync.Semaphore(4)

    /** Loads a longer guide for one row of the TV guide; a few channels at a time. */
    fun loadGuide(c: Channel) {
        if (!guideAsked.add(c.id)) return
        viewModelScope.launch {
            val list = guideLimit.withPermit { runCatching { client?.shortEpg(c.id, 12) }.getOrNull() }
            if (list != null) epg[c.id] = list else guideAsked.remove(c.id)
        }
    }

    companion object {
        /** Live TV pseudo-categories. */
        const val GUIDE = "__guide"
        const val RECENT = "__recent"
    }

    fun loadEpg(c: Channel) {
        epgJob?.cancel()
        val cached = epg[c.id]
        val now = System.currentTimeMillis()
        if (cached != null && (cached.isEmpty() || cached.any { it.start <= now && now < it.end })) return
        epgJob = viewModelScope.launch {
            delay(350) // wait until focus settles while scrolling
            val list = runCatching { client?.shortEpg(c.id, 6) }.getOrNull() ?: emptyList()
            epg[c.id] = list
        }
    }

    // ------------------------------------------------------------- vod

    /** Categories loaded from the portal this session (saved ones are shown until then). */
    private val categoriesFresh = mutableSetOf<VodKind>()

    fun ensureCategories(kind: VodKind) {
        if (kind in categoriesFresh) return
        categoriesFresh += kind
        if (!categories.containsKey(kind)) categories[kind] = emptyList()
        viewModelScope.launch {
            val cats = runCatching { client?.vodCategories(kind) }.getOrNull() ?: emptyList()
            if (cats.isEmpty() && categories[kind].orEmpty().isNotEmpty()) { categoriesFresh -= kind; return@launch }
            if (cats != categories[kind]) categories[kind] = cats
            selectDefaultCategory(kind)
            saveLists()
        }
    }

    private fun selectDefaultCategory(kind: VodKind) {
        val cats = categories[kind].orEmpty()
        if (selectedCategory[kind] == null || cats.none { it.id == selectedCategory[kind] }) {
            selectedCategory[kind] = cats.firstOrNull { it.id == "*" }?.id ?: cats.firstOrNull()?.id ?: "*"
        }
    }

    /** Logos for channels the portal sends none for (see Logos). */
    private fun findLogos() {
        val ch = channels; val g = genres
        viewModelScope.launch { com.novabox.tv.data.Logos.prepare(ch, g) }
    }

    /** Saves the current lists for the next start (off the main thread). */
    private fun saveLists() {
        val p = activeProfile ?: return
        val l = ListCache.Lists(genres, channels, categories[VodKind.MOVIE].orEmpty(), categories[VodKind.SERIES].orEmpty(), owner = ListCache.ownerOf(p))
        if (l.channels.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { ListCache.save(p.id, l) }
    }

    fun list(kind: VodKind, cat: String): VodList = lists.getOrPut("$kind/$cat") { VodList() }

    fun loadMore(kind: VodKind, cat: String) {
        val l = list(kind, cat)
        if (l.loading || !l.hasMore) return
        l.loading = true
        viewModelScope.launch {
            try {
                // A page can come back empty once shows are left out of Movies: keep going (up to 5 pages).
                var page = client!!.vodPage(kind, cat, l.page + 1)
                var skipped = 0
                while (page.items.isEmpty() && page.fetched > 0 && page.hasMore && skipped < 5) {
                    skipped++
                    page = client!!.vodPage(kind, cat, page.page + 1)
                }
                l.items.addAll(page.items)
                l.page = page.page
                l.hasMore = page.hasMore && page.fetched > 0
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
        searching = false
        if (q.isBlank()) {
            searchChannels = emptyList(); searchMovies = emptyList(); searchSeries = emptyList(); return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            searching = true
            try {
                searchChannels = channels.filter { it.name.contains(q.trim(), ignoreCase = true) }.take(60)
                val m = async { runCatching { client?.vodPage(VodKind.MOVIE, "*", 1, q.trim(), keepShows = true)?.items }.getOrNull() ?: emptyList() }
                val s = async { runCatching { client?.vodPage(VodKind.SERIES, "*", 1, q.trim())?.items }.getOrNull() ?: emptyList() }
                // Shows a portal files under Movies are listed with the series.
                val (films, vodShows) = m.await().partition { it.kind == VodKind.MOVIE }
                searchMovies = films
                searchSeries = s.await().map { it.copy(kind = VodKind.SERIES) } + vodShows
            } finally {
                searching = false
            }
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
        ListCache.delete(p.id)
        if (activeProfile?.id == p.id) {
            val next = AppGraph.profiles.active
            if (next != null) connect(next) else { backStack.clear(); backStack.add(Route.EditProfile(null, true)) }
        }
    }

    fun showToast(msg: String) { toast = msg }

    // ------------------------------------------------------------- free TV

    var freeCountry by mutableStateOf(AppGraph.settings.freeTvCountry)
        private set
    var freeChannels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var freeLoading by mutableStateOf(false)
        private set
    var freeError by mutableStateOf<String?>(null)
        private set
    var freeGroup by mutableStateOf<String?>(null)
    private var freeJob: Job? = null
    /** The whole list from iptv-org; [freeChannels] leaves out streams the monthly check found dead. */
    private var freeAll: List<Channel> = emptyList()
    var freeHidden by mutableStateOf(0)
        private set
    /** "Checking 40 of 257…" while the channel check runs, else null. */
    var freeChecking by mutableStateOf<String?>(null)
        private set
    var freeLastCheck by mutableStateOf(0L)
        private set
    private var checkJob: Job? = null

    private fun applyFreeCheck() {
        val dead = AppGraph.freeCheck.dead()
        val alive = freeAll.filterNot { it.cmd in dead }
        // If everything looks dead the TV was probably offline during the check: show all.
        freeChannels = if (alive.isEmpty()) freeAll else alive
        freeHidden = freeAll.size - freeChannels.size
        freeLastCheck = AppGraph.freeCheck.lastCheck(freeCountry)
    }

    /** Checks every Free TV stream and hides the dead ones. Runs by itself once a month. */
    fun checkFreeChannels() {
        if (checkJob?.isActive == true || freeAll.isEmpty()) return
        val code = freeCountry
        val list = freeAll
        checkJob = viewModelScope.launch {
            freeChecking = "Checking channels…"
            try {
                AppGraph.freeCheck.check(code, list) { done, total ->
                    viewModelScope.launch { freeChecking = "Checking $done of $total…" }
                }
                if (code == freeCountry) applyFreeCheck()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                freeChecking = null
            }
        }
    }

    fun loadFreeTv(force: Boolean = false) {
        if (!force && (freeChannels.isNotEmpty() || freeLoading)) return
        freeJob?.cancel()
        freeLoading = true
        freeError = null
        val code = freeCountry
        freeJob = viewModelScope.launch {
            try {
                freeAll = com.novabox.tv.data.FreeTv.channels(code, forceRefresh = force)
                applyFreeCheck()
                freeGroup = null
                if (AppGraph.freeCheck.isDue(code)) checkFreeChannels()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                freeChannels = emptyList(); freeAll = emptyList(); freeHidden = 0
                freeError = e.message ?: "Couldn't load free channels"
            } finally {
                freeLoading = false
            }
        }
    }

    fun chooseFreeCountry(code: String) {
        if (code == freeCountry && freeChannels.isNotEmpty()) return
        freeCountry = code
        AppGraph.settings.freeTvCountry = code
        freeChannels = emptyList(); freeAll = emptyList(); freeHidden = 0
        checkJob?.cancel(); freeChecking = null
        freeGroup = null
        freeLoading = false
        loadFreeTv()
    }

    /** Opens the app on Free TV without any portal profile (from first run or a failed login). */
    fun openFreeTvOnly() {
        connectJob?.cancel()
        liveLoading = false
        if (channels.isEmpty()) { AppGraph.client = null; keepAliveJob?.cancel() }
        if (AppGraph.profiles.profiles.value.isEmpty()) AppGraph.settings.skippedSetup = true
        backStack.clear()
        backStack.add(Route.Home)
        tab = HomeTab.FREE_TV
    }

    // ------------------------------------------------------------- developer tools: Real-Debrid

    var devTools by mutableStateOf(AppGraph.settings.devTools)
        private set
    val visibleTabs: List<HomeTab> get() = HomeTab.entries.filter { it != HomeTab.DEBRID || devTools }

    fun setDeveloperTools(on: Boolean) {
        devTools = on
        AppGraph.settings.devTools = on
        if (!on && tab == HomeTab.DEBRID) tab = HomeTab.SETTINGS
    }

    sealed interface DebridLogin {
        data object Idle : DebridLogin
        data object Starting : DebridLogin
        data class Waiting(val code: com.novabox.tv.data.RealDebrid.DeviceCode) : DebridLogin
        data class Failed(val message: String) : DebridLogin
    }

    enum class DebridShelf(val label: String) { TORRENTS("Torrents"), DOWNLOADS("Downloads") }

    var debridLinked by mutableStateOf(com.novabox.tv.data.RealDebrid.linked)
        private set
    var debridLogin by mutableStateOf<DebridLogin>(DebridLogin.Idle)
        private set
    var debridUser by mutableStateOf<com.novabox.tv.data.RealDebrid.User?>(null)
        private set
    var debridTorrents by mutableStateOf<List<com.novabox.tv.data.RealDebrid.Torrent>>(emptyList())
        private set
    var debridDownloads by mutableStateOf<List<com.novabox.tv.data.RealDebrid.Download>>(emptyList())
        private set
    var debridLoading by mutableStateOf(false)
        private set
    var debridError by mutableStateOf<String?>(null)
    var debridShelf by mutableStateOf(DebridShelf.TORRENTS)
    var debridQuery by mutableStateOf("")
    /** The torrent whose files are open in the side panel, and its files (null while loading). */
    var debridOpen by mutableStateOf<com.novabox.tv.data.RealDebrid.Torrent?>(null)
        private set
    var debridFiles by mutableStateOf<List<com.novabox.tv.data.RealDebrid.RdFile>?>(null)
        private set
    /** Matched title and poster per release name (null value: looked up, no match). */
    val debridPosters = mutableStateMapOf<String, com.novabox.tv.data.TitleInfo?>()
    private val posterAsked = HashSet<String>()

    fun wantPoster(name: String) {
        if (!posterAsked.add(name)) return
        viewModelScope.launch {
            try {
                debridPosters[name] = com.novabox.tv.data.Posters.lookup(com.novabox.tv.data.ReleaseNames.parse(name))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                posterAsked.remove(name) // offline: try again next time the card shows
            }
        }
    }

    /** Shown while a link is being turned into a stream, or why that failed. */
    var debridPlayStatus by mutableStateOf<String?>(null)
    private var debridJob: Job? = null
    private var loginJob: Job? = null
    private var filesJob: Job? = null

    private fun rdMessage(e: Exception): String = when {
        e is java.net.UnknownHostException -> "Real-Debrid couldn't be reached. Check the TV is online."
        e is com.novabox.tv.data.RealDebrid.RdException && (e.code == 401 || e.code == 403) ->
            "Real-Debrid refused the request (${e.message}). If your premium has ended or the link was removed, unlink and link again."
        else -> "Real-Debrid: ${e.message ?: "something went wrong"}"
    }

    fun startDebridLogin() {
        loginJob?.cancel()
        debridLogin = DebridLogin.Starting
        loginJob = viewModelScope.launch {
            try {
                val code = com.novabox.tv.data.RealDebrid.startLogin()
                debridLogin = DebridLogin.Waiting(code)
                while (System.currentTimeMillis() < code.expiresAt) {
                    kotlinx.coroutines.delay(code.intervalSec * 1000L)
                    if (com.novabox.tv.data.RealDebrid.pollLogin(code)) {
                        debridLinked = true
                        debridLogin = DebridLogin.Idle
                        loadDebrid(force = true)
                        return@launch
                    }
                }
                debridLogin = DebridLogin.Failed("The code expired before it was entered. Get a new code to try again.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                debridLogin = DebridLogin.Failed(rdMessage(e))
            }
        }
    }

    fun cancelDebridLogin() { loginJob?.cancel(); debridLogin = DebridLogin.Idle }

    fun unlinkDebrid() {
        debridJob?.cancel(); filesJob?.cancel()
        com.novabox.tv.data.RealDebrid.unlink()
        debridLinked = false
        debridUser = null; debridTorrents = emptyList(); debridDownloads = emptyList()
        debridOpen = null; debridFiles = null; debridError = null; debridQuery = ""; debridPlayStatus = null
    }

    fun loadDebrid(force: Boolean = false) {
        if (!debridLinked) return
        if (!force && (debridUser != null || debridLoading)) return
        debridJob?.cancel()
        debridLoading = true
        debridError = null
        debridJob = viewModelScope.launch {
            try {
                debridUser = com.novabox.tv.data.RealDebrid.user()
                debridTorrents = com.novabox.tv.data.RealDebrid.torrents()
                debridOpen?.let { open ->
                    val fresh = debridTorrents.firstOrNull { it.id == open.id }
                    debridOpen = null; debridFiles = null
                    if (fresh != null) openTorrent(fresh)
                }
                debridDownloads = com.novabox.tv.data.RealDebrid.downloads()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                debridError = rdMessage(e)
            } finally {
                debridLoading = false
            }
        }
    }

    fun openTorrent(t: com.novabox.tv.data.RealDebrid.Torrent) {
        if (debridOpen?.id == t.id && debridFiles != null) return
        filesJob?.cancel()
        debridOpen = t
        debridFiles = null
        debridPlayStatus = null
        if (t.status != "downloaded") { debridFiles = emptyList(); return }
        filesJob = viewModelScope.launch {
            debridFiles = try {
                com.novabox.tv.data.RealDebrid.torrentFiles(t.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                debridPlayStatus = rdMessage(e); emptyList()
            }
        }
    }

    /** Unrestricts an RD link (torrent files) and opens the film player. */
    private var playJob: Job? = null

    /** [releaseName] keys the saved progress, so two shows' "S01E02" don't share it. */
    fun playDebrid(title: String, link: String, alreadyDirect: Boolean, onPlay: (PlayRequest) -> Unit, releaseName: String = title) {
        if (playJob?.isActive == true) return
        playJob = viewModelScope.launch {
            debridPlayStatus = "Getting the stream from Real-Debrid…"
            try {
                val url = if (alreadyDirect) link else com.novabox.tv.data.RealDebrid.unrestrict(link)
                debridPlayStatus = null
                onPlay(debridRequest(title, url, releaseName))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                debridPlayStatus = rdMessage(e)
            }
        }
    }

    private fun debridRequest(title: String, url: String, releaseName: String) = PlayRequest.Vod(
        title, url, null, subtitle = "Real-Debrid", direct = true,
        key = com.novabox.tv.data.WatchEntry.debridKey(releaseName),
        releaseName = releaseName,
        next = if (com.novabox.tv.data.DebridNext.isEpisode(releaseName)) ::nextDebridEpisode else null,
    )

    /**
     * The episode after [releaseName] in the user's Real-Debrid library, ready to play: looked for
     * in the open torrent, then the downloads list, then other torrents of the show (season packs
     * included). Null if there isn't one.
     */
    suspend fun nextDebridEpisode(releaseName: String): PlayRequest.Vod? = runCatching {
        val rd = com.novabox.tv.data.RealDebrid
        val next = com.novabox.tv.data.DebridNext
        debridFiles?.let { files -> next.pick(releaseName, files) { it.name } }?.let { f ->
            return@runCatching debridRequest(rd.pretty(f.name), rd.unrestrict(f.link), f.name)
        }
        next.pick(releaseName, debridDownloads.filter { rd.isVideo(it.name) }) { it.name }?.let { d ->
            return@runCatching debridRequest(rd.pretty(d.name), d.url, d.name)
        }
        for (t in next.holders(releaseName, debridTorrents.filter { it.status == "downloaded" }) { it.name }) {
            val f = next.pick(releaseName, rd.torrentFiles(t.id)) { it.name } ?: continue
            return@runCatching debridRequest(rd.pretty(f.name), rd.unrestrict(f.link), f.name)
        }
        null
    }.getOrNull()

    // ------------------------------------------------------------- VPN app (optional)

    /** The VPN app picked in Settings; null if none. NovaBox never requires a VPN. */
    var vpnApp by mutableStateOf(AppGraph.settings.vpnApp?.split('|', limit = 2)?.takeIf { it.size == 2 }?.let { com.novabox.tv.data.VpnApps.VpnApp(it[0], it[1]) })
        private set
    var vpnOn by mutableStateOf(false)
        private set
    fun refreshVpn() { vpnOn = com.novabox.tv.data.VpnApps.active(AppGraph.appContext) }
    fun installedVpnApps() = com.novabox.tv.data.VpnApps.installed(AppGraph.appContext)
    fun chooseVpnApp(app: com.novabox.tv.data.VpnApps.VpnApp?) {
        vpnApp = app
        AppGraph.settings.vpnApp = app?.let { "${it.pkg}|${it.name}" }
    }
    /** Opens the picked VPN app; returns a message if it can't. */
    fun openVpnApp(): String? {
        val app = vpnApp ?: return "Pick your VPN app first in Settings → VPN app."
        return if (com.novabox.tv.data.VpnApps.open(AppGraph.appContext, app.pkg)) null
        else "${app.name} doesn't seem to be installed any more. Pick another VPN app in Settings."
    }

    // ------------------------------------------------------------- app updates

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val info: com.novabox.tv.data.UpdateInfo) : UpdateState
        data class NeedsPermission(val info: com.novabox.tv.data.UpdateInfo) : UpdateState
        data class Downloading(val info: com.novabox.tv.data.UpdateInfo, val progress: Float) : UpdateState
        data class Installing(val info: com.novabox.tv.data.UpdateInfo) : UpdateState
        data class Failed(val message: String, val info: com.novabox.tv.data.UpdateInfo? = null) : UpdateState
    }

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    fun checkForUpdates() {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        update = UpdateState.Checking
        viewModelScope.launch {
            update = try {
                com.novabox.tv.data.Updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't check for updates: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun installUpdate(info: com.novabox.tv.data.UpdateInfo) {
        val ctx = AppGraph.appContext
        if (!com.novabox.tv.data.Updater.canInstall(ctx)) {
            update = UpdateState.NeedsPermission(info)
            com.novabox.tv.data.Updater.openInstallPermission(ctx)
            return
        }
        update = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val apk = com.novabox.tv.data.Updater.download(ctx, info) { p ->
                    viewModelScope.launch { if (update is UpdateState.Downloading) update = UpdateState.Downloading(info, p) }
                }
                update = UpdateState.Installing(info)
                com.novabox.tv.data.Updater.install(ctx, apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                update = UpdateState.Failed("Update failed: ${e.message ?: e.javaClass.simpleName}", info)
            }
        }
    }

    // ------------------------------------------------------------- automatic updates

    var autoUpdate by mutableStateOf(AppGraph.settings.autoUpdate)
        private set
    /** Version name of an update downloaded by the daily check and waiting to install; null if none. */
    var readyToInstall by mutableStateOf<String?>(null)
        private set
    private var autoJob: Job? = null

    fun setAutoUpdates(on: Boolean) {
        autoUpdate = on
        AppGraph.settings.autoUpdate = on
        if (on) {
            AppGraph.settings.lastUpdateCheck = 0 // check straight away
            // Installing at start-up needs this permission already granted.
            if (!com.novabox.tv.data.Updater.canInstall(AppGraph.appContext)) com.novabox.tv.data.Updater.openInstallPermission(AppGraph.appContext)
            autoJob?.cancel(); autoJob = null
            startAutoUpdates()
        } else {
            readyToInstall = null
        }
    }

    /** Daily check while NovaBox runs (TVs often leave it open), plus once at every launch. */
    private fun startAutoUpdates() {
        if (autoJob?.isActive == true) return
        val ctx = AppGraph.appContext
        autoJob = viewModelScope.launch {
            // An update downloaded earlier that the start-up install didn't finish: offer it now, as NovaBox opens.
            if (AppGraph.settings.autoUpdate) readyToInstall = com.novabox.tv.data.Updater.readyUpdate(ctx)?.second
            while (isActive) {
                if (AppGraph.settings.autoUpdate) runCatching { com.novabox.tv.data.Updater.autoCheck(ctx) }
                delay(3 * 3600_000L)
            }
        }
    }

    fun installReadyUpdate() {
        val ctx = AppGraph.appContext
        val ready = com.novabox.tv.data.Updater.readyUpdate(ctx) ?: run { readyToInstall = null; return }
        readyToInstall = null
        if (!com.novabox.tv.data.Updater.canInstall(ctx)) { com.novabox.tv.data.Updater.openInstallPermission(ctx); return }
        runCatching { com.novabox.tv.data.Updater.install(ctx, ready.first, quiet = true) }
            .onFailure { update = UpdateState.Failed("Update failed: ${it.message ?: it.javaClass.simpleName}", null) }
    }

    /** "Later": ask again at the next launch. */
    fun postponeReadyUpdate() { readyToInstall = null }

    /** Called when the installer reports a failure or the user cancels Android's prompt. */
    fun updateInstallFailed(message: String) {
        val info = (update as? UpdateState.Installing)?.info
        update = UpdateState.Failed(message, info)
    }

    // ------------------------------------------------------------- network check

    val netChecks = mutableStateListOf<com.novabox.tv.data.CheckResult>()
    var netRunning by mutableStateOf(false)
        private set
    private var netJob: Job? = null

    fun runNetworkCheck() {
        if (netRunning) return
        val diag = com.novabox.tv.data.NetworkDiagnostics(AppGraph.appContext)
        netChecks.clear()
        netChecks.addAll(diag.titles.map { com.novabox.tv.data.CheckResult(it) })
        netRunning = true
        netJob = viewModelScope.launch {
            try {
                diag.run(activeProfile ?: AppGraph.profiles.active, channels.firstOrNull()) { i, r ->
                    viewModelScope.launch { if (i < netChecks.size) netChecks[i] = r }
                }
            } finally {
                netRunning = false
            }
        }
    }
}
