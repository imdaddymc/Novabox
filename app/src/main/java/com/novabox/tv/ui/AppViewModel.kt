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
import com.novabox.tv.data.PlayRequest
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
        backStack.clear()
        backStack.add(Route.Connecting)
        genres = emptyList(); channels = emptyList(); categories.clear(); lists.clear(); epg.clear(); account = null
        selectedGenre = null; selectedCategory.clear()
        connectJob?.cancel()
        keepAliveJob?.cancel()
        searchJob?.cancel(); searching = false
        searchQuery = ""; searchChannels = emptyList(); searchMovies = emptyList(); searchSeries = emptyList()
        val c = StalkerClient(p)
        AppGraph.client = c
        connectJob = viewModelScope.launch {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
        searching = false
        if (q.isBlank()) {
            searchChannels = emptyList(); searchMovies = emptyList(); searchSeries = emptyList(); return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            searching = true
            try {
                searchChannels = channels.filter { it.name.contains(q.trim(), ignoreCase = true) }.take(60)
                val m = async { runCatching { client?.vodPage(VodKind.MOVIE, "*", 1, q.trim())?.items }.getOrNull() ?: emptyList() }
                val s = async { runCatching { client?.vodPage(VodKind.SERIES, "*", 1, q.trim())?.items }.getOrNull() ?: emptyList() }
                searchMovies = m.await()
                searchSeries = s.await().map { it.copy(kind = VodKind.SERIES) }
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

    fun loadFreeTv(force: Boolean = false) {
        if (!force && (freeChannels.isNotEmpty() || freeLoading)) return
        freeJob?.cancel()
        freeLoading = true
        freeError = null
        val code = freeCountry
        freeJob = viewModelScope.launch {
            try {
                freeChannels = com.novabox.tv.data.FreeTv.channels(code, forceRefresh = force)
                freeGroup = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                freeChannels = emptyList()
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
        freeChannels = emptyList()
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

    fun playDebrid(title: String, link: String, alreadyDirect: Boolean, onPlay: (PlayRequest) -> Unit) {
        if (playJob?.isActive == true) return
        playJob = viewModelScope.launch {
            debridPlayStatus = "Getting the stream from Real-Debrid…"
            try {
                val url = if (alreadyDirect) link else com.novabox.tv.data.RealDebrid.unrestrict(link)
                debridPlayStatus = null
                onPlay(PlayRequest.Vod(title, url, null, subtitle = "Real-Debrid", direct = true))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                debridPlayStatus = rdMessage(e)
            }
        }
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
