package com.novabox.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import com.novabox.tv.data.ShelfGroups
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.novabox.tv.data.PlayRequest
import com.novabox.tv.data.RealDebrid
import com.novabox.tv.data.WatchEntry
import androidx.compose.runtime.collectAsState

/** Developer tools: the user's own Real-Debrid torrents and downloads. */
@Composable
fun DebridScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    if (!vm.debridLinked) DebridLink(vm) else DebridLibrary(vm, onPlay)
}

@Composable
private fun DebridLink(vm: AppViewModel) {
    val buttonFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val login = vm.debridLogin
    // The button changes with each step; keep the remote's focus on it rather than the tab bar.
    // Not on first show, so moving along the tab bar past this tab doesn't pull focus down.
    val firstShow = remember { mutableStateOf(true) }
    LaunchedEffect(login::class) {
        if (firstShow.value) { firstShow.value = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(100); runCatching { buttonFocus.requestFocus() }
    }
    val fm = Modifier.focusRequester(buttonFocus)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Link your Real-Debrid account", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Lodestar plays what is already in your Real-Debrid account. You link it with a code, so no password is typed on the TV.",
            color = Palette.muted, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(640.dp),
        )
        Spacer(Modifier.height(28.dp))
        when (val s = vm.debridLogin) {
            AppViewModel.DebridLogin.Idle -> NovaButton("Get a link code", { vm.startDebridLogin() }, fm, icon = Icons.Filled.Build, primary = true)
            AppViewModel.DebridLogin.Starting -> NovaButton("Getting a code…", {}, fm, icon = Icons.Filled.Refresh)
            is AppViewModel.DebridLogin.Waiting -> {
                Column(
                    Modifier.background(Palette.bg2, RoundedCornerShape(18.dp)).padding(horizontal = 40.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("On your phone or computer, go to", color = Palette.muted, fontSize = 16.sp)
                    Text(s.code.verificationUrl.removePrefix("https://"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
                    Spacer(Modifier.height(14.dp))
                    Text("and enter this code", color = Palette.muted, fontSize = 16.sp)
                    Text(s.code.userCode, fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("Waiting for the code… this screen updates by itself.", color = Palette.muted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(20.dp))
                NovaButton("Cancel", { vm.cancelDebridLogin() }, fm, icon = Icons.Filled.Close)
            }
            is AppViewModel.DebridLogin.Failed -> {
                Text(s.message, color = Palette.danger, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                NovaButton("Get a new code", { vm.startDebridLogin() }, fm, icon = Icons.Filled.Refresh, primary = true)
            }
        }
    }
}

@Composable
private fun DebridLibrary(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    LaunchedEffect(Unit) { vm.loadDebrid() }
    // Lengths of episodes already played, shown on their rows.
    val watch by com.novabox.tv.data.AppGraph.watch.entries.collectAsState()
    // Keep the VPN badge current while this tab is showing (information only; nothing is blocked)
    LaunchedEffect(Unit) { while (true) { vm.refreshVpn(); kotlinx.coroutines.delay(5000) } }
    var vpnNote by remember { mutableStateOf<String?>(null) }
    var pickVpn by remember { mutableStateOf(false) }
    vpnNote?.let { InfoDialog(it) { vpnNote = null } }
    if (pickVpn) VpnPickerDialog(vm) { pickVpn = false }
    var editing by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    if (editing) TextInputDialog("Search your Real-Debrid library", vm.debridQuery, onValue = { vm.debridQuery = it }, onDismiss = { editing = false })
    if (confirmUnlink) ConfirmDialog("Unlink Real-Debrid from this TV?", onYes = { vm.unlinkDebrid(); confirmUnlink = false }, onNo = { confirmUnlink = false }, yesLabel = "Unlink")

    val q = vm.debridQuery
    val torrents = remember(vm.debridTorrents, q) { vm.debridTorrents.filter { RealDebrid.matches(it.name, q) } }
    val downloads = remember(vm.debridDownloads, q) {
        vm.debridDownloads.filter { RealDebrid.isVideo(it.name) && RealDebrid.matches(it.name, q) }
    }
    val torrentCards = remember(torrents) { ShelfGroups.group(torrents, { "t" + it.id }, { it.name }) }
    val downloadCards = remember(downloads) { ShelfGroups.group(downloads, { "d" + it.id }, { it.name }) }
    /** The card whose details are in the side panel. */
    var openKey by remember { mutableStateOf<String?>(null) }
    // OK on a show or torrent card moves focus to its first episode or file, so films or other
    // cards in between don't have to be crossed (crossing a film swaps the panel to that film).
    val panelFirst = remember { androidx.compose.ui.focus.FocusRequester() }
    var jumpToPanel by remember { mutableStateOf(false) }
    /** True while the remote is in the sidebar; otherwise it shrinks to icons. */
    var sideOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(vm.debridShelf, q) { gridState.scrollToItem(0) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left: account, search, shelves. Only icons until the remote moves into it.
        val sideWidth by animateDpAsState(if (sideOpen) 196.dp else 60.dp, label = "debridSide")
        Column(Modifier.width(sideWidth).fillMaxHeight().onFocusChanged { sideOpen = it.hasFocus }) {
            val u = vm.debridUser
            if (sideOpen) {
                SectionTitle("REAL-DEBRID", maxLines = 1)
                Text(u?.username ?: "…", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        u == null -> ""
                        u.premium -> "Premium until ${u.expiration.take(10)}"
                        else -> "Not premium: streams won't play"
                    },
                    fontSize = 13.sp, color = if (u != null && !u.premium) Palette.danger else Palette.muted, maxLines = 1,
                )
            } else {
                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AccountCircle, "Real-Debrid account", Modifier.size(30.dp),
                        tint = if (u != null && !u.premium) Palette.danger else Palette.muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                SideItem(Icons.Filled.Search, q.ifBlank { "Search" }, sideOpen, onClick = { editing = true },
                    muted = q.isBlank(), active = q.isNotBlank())
                if (sideOpen) { Spacer(Modifier.height(10.dp)); SectionTitle("LIBRARY", maxLines = 1) } else Spacer(Modifier.height(16.dp))
                AppViewModel.DebridShelf.entries.forEach { shelf ->
                    val n = if (shelf == AppViewModel.DebridShelf.TORRENTS) torrentCards.size else downloadCards.size
                    SideItem(
                        if (shelf == AppViewModel.DebridShelf.TORRENTS) Icons.Filled.List else Icons.Filled.KeyboardArrowDown,
                        shelf.label, sideOpen, count = n, selected = vm.debridShelf == shelf,
                        onClick = { vm.debridShelf = shelf }, onFocus = { vm.debridShelf = shelf },
                    )
                }
                Spacer(Modifier.height(16.dp))
                SideItem(
                    Icons.Filled.Lock,
                    vm.vpnApp?.let { if (vm.vpnOn) "VPN on" else "Open ${it.name}" } ?: "VPN app",
                    sideOpen,
                    onClick = { if (vm.vpnApp == null) pickVpn = true else vpnNote = vm.openVpnApp() },
                    tint = if (vm.vpnOn) Palette.teal else null,
                )
                SideItem(Icons.Filled.Refresh, "Refresh", sideOpen, onClick = { vm.loadDebrid(force = true) })
                SideItem(Icons.Filled.Close, "Unlink", sideOpen, onClick = { confirmUnlink = true })
            }
        }

        // Middle: poster cards, one per film and one per show
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val isTorrents = vm.debridShelf == AppViewModel.DebridShelf.TORRENTS
            val count = if (isTorrents) torrentCards.size else downloadCards.size
            when {
                vm.debridLoading && vm.debridUser == null -> CenterMessage("Loading your Real-Debrid library…")
                vm.debridError != null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(vm.debridError ?: "", color = Palette.muted, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    NovaButton("Try again", { vm.loadDebrid(force = true) }, icon = Icons.Filled.Refresh, primary = true)
                }
                count == 0 -> CenterMessage(
                    if (q.isNotBlank()) "Nothing in ${vm.debridShelf.label.lowercase()} matches “$q”"
                    else if (isTorrents) "No torrents in your Real-Debrid account yet" else "No video downloads in your Real-Debrid history yet"
                )
                else -> {
                    SectionTitle("${vm.debridShelf.label.uppercase()} · $count" + if (q.isNotBlank()) " · “$q”" else "")
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        if (isTorrents) gridItems(torrentCards, key = { it.key }) { g ->
                            val first = g.items.first()
                            val detail = if (g.isShow) seasonsLabel(g.items.map { it.name }) else torrentStatus(first)
                            DebridCard(vm, first.name, detail, selected = openKey == g.key, onClick = {
                                openKey = g.key
                                vm.openTorrent(g.items.firstOrNull { it.status == "downloaded" } ?: first)
                                jumpToPanel = true
                            }, onFocus = {})
                        } else gridItems(downloadCards, key = { it.key }) { g ->
                            val first = g.items.first()
                            val detail = if (g.isShow) "${g.items.size} episode" + (if (g.items.size == 1) "" else "s") else (episodeTag(first.name) ?: RealDebrid.size(first.bytes))
                            DebridCard(vm, first.name, detail, selected = openKey == g.key, onClick = {
                                openKey = g.key
                                if (g.isShow) jumpToPanel = true
                                if (!g.isShow) vm.playDebrid(RealDebrid.pretty(first.name), first.url, alreadyDirect = true, onPlay, releaseName = first.name)
                            }, onFocus = { if (!g.isShow || openKey == null) openKey = g.key })
                        }
                    }
                }
            }
        }

        LaunchedEffect(jumpToPanel) {
            if (!jumpToPanel) return@LaunchedEffect
            // Give the file list up to 5 s to load, then stay on the card.
            repeat(50) {
                kotlinx.coroutines.delay(100)
                if (runCatching { panelFirst.requestFocus() }.isSuccess) { jumpToPanel = false; return@LaunchedEffect }
            }
            jumpToPanel = false
        }

        // Right: seasons and files of the open torrent card, or the episodes / film of a download card
        val panelWidth by animateDpAsState(if (sideOpen) 290.dp else 340.dp, label = "debridPanel")
        Column(Modifier.width(panelWidth).fillMaxHeight().padding(top = 26.dp)) {
            Column(Modifier.fillMaxWidth().background(Palette.bg2, RoundedCornerShape(16.dp)).padding(20.dp)) {
                vm.debridPlayStatus?.let {
                    Text(it, fontSize = 14.sp, color = if (it.startsWith("Getting")) Palette.accent else Palette.danger)
                    Spacer(Modifier.height(12.dp))
                }
                if (vm.debridShelf == AppViewModel.DebridShelf.TORRENTS) {
                    val g = torrentCards.firstOrNull { it.key == openKey }
                    val t = vm.debridOpen?.takeIf { open -> g?.items?.any { it.id == open.id } == true }
                    if (g == null || t == null) {
                        SectionTitle("FILES")
                        Text("Press OK on a poster to see its video files.", fontSize = 14.sp, color = Palette.muted)
                    } else {
                        DetailHeader(vm, t.name)
                        Spacer(Modifier.height(10.dp))
                        if (g.isShow && g.items.size > 1) {
                            // One chip per season torrent of this show
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                g.items.forEach { st ->
                                    FocusSurface(onClick = { vm.openTorrent(st) }, selected = st.id == t.id, shape = RoundedCornerShape(50), focusedScale = 1.06f) { _ ->
                                        Text(seasonChip(st), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(RealDebrid.size(t.bytes) + " · " + torrentStatus(t), fontSize = 13.sp, color = Palette.muted)
                        Spacer(Modifier.height(10.dp))
                        val files = vm.debridFiles
                        when {
                            t.status != "downloaded" -> Text("This torrent isn't ready on Real-Debrid yet, so it can't play.", fontSize = 14.sp, color = Palette.muted)
                            files == null -> Text("Loading files…", fontSize = 14.sp, color = Palette.muted)
                            files.isEmpty() -> Text("No video files in this torrent.", fontSize = 14.sp, color = Palette.muted)
                            else -> {
                                SectionTitle("VIDEO FILES · ${files.size}")
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                                    itemsIndexed(files, key = { _, f -> f.link }) { i, f ->
                                        PlayRow(episodeTag(f.name) ?: RealDebrid.pretty(f.name), listOfNotNull(episodeLength(null, watch[WatchEntry.debridKey(f.name)]), RealDebrid.size(f.bytes)).joinToString(" · "), if (i == 0) Modifier.focusRequester(panelFirst) else Modifier) { vm.playDebrid(RealDebrid.pretty(f.name), f.link, alreadyDirect = false, onPlay, releaseName = f.name) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val g = downloadCards.firstOrNull { it.key == openKey } ?: downloadCards.firstOrNull()
                    if (g == null) {
                        Text("Links you have unrestricted on Real-Debrid show here.", fontSize = 14.sp, color = Palette.muted)
                    } else if (g.isShow) {
                        DetailHeader(vm, g.items.first().name)
                        Spacer(Modifier.height(12.dp))
                        SectionTitle("EPISODES · ${g.items.size}")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                            itemsIndexed(g.items, key = { _, d -> d.id }) { i, d ->
                                PlayRow(episodeTag(d.name) ?: RealDebrid.pretty(d.name), listOfNotNull(episodeLength(null, watch[WatchEntry.debridKey(d.name)]), RealDebrid.size(d.bytes)).joinToString(" · ") + " · added " + d.generated.take(10), if (i == 0) Modifier.focusRequester(panelFirst) else Modifier) {
                                    vm.playDebrid(RealDebrid.pretty(d.name), d.url, alreadyDirect = true, onPlay, releaseName = d.name)
                                }
                            }
                        }
                    } else {
                        val d = g.items.first()
                        DetailHeader(vm, d.name)
                        Text(RealDebrid.size(d.bytes) + " · added " + d.generated.take(10), fontSize = 13.sp, color = Palette.muted, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(16.dp))
                        NovaButton("Play", { vm.playDebrid(RealDebrid.pretty(d.name), d.url, alreadyDirect = true, onPlay, releaseName = d.name) }, icon = Icons.Filled.PlayArrow, primary = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayRow(title: String, detail: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, focusedScale = 1.02f, modifier = modifier.fillMaxWidth()) { foc ->
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 12.sp, color = if (foc) Color(0xFF39425A) else Palette.muted, maxLines = 1)
            }
        }
    }
}

/** "Season 1" or "Seasons 1–3" for a show card's torrents. */
private fun seasonsLabel(names: List<String>): String {
    val seasons = names.mapNotNull { com.novabox.tv.data.ReleaseNames.parse(it).season }.distinct().sorted()
    return when {
        seasons.isEmpty() -> "Series"
        seasons.size == 1 -> "Season ${seasons[0]}"
        seasons.last() - seasons.first() + 1 == seasons.size -> "Seasons ${seasons.first()}–${seasons.last()}"
        else -> "${seasons.size} seasons"
    }
}

private fun seasonChip(t: RealDebrid.Torrent): String {
    val r = com.novabox.tv.data.ReleaseNames.parse(t.name)
    val base = r.season?.let { s -> "Season $s" + (r.episode?.let { " Ep $it" } ?: "") } ?: RealDebrid.pretty(t.name)
    return if (t.status == "downloaded") base else "$base (not ready)"
}

/** "S01E03" for an episode file, else null. */
private fun episodeTag(name: String): String? {
    val r = com.novabox.tv.data.ReleaseNames.parse(name)
    return r.season?.let { s -> "S%02d".format(s) + (r.episode?.let { "E%02d".format(it) } ?: "") }
}

/** A poster card for a torrent or download: matched poster and title, with the release's detail underneath. */
@Composable
private fun DebridCard(vm: AppViewModel, name: String, detail: String, selected: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    LaunchedEffect(name) { vm.wantPoster(name) }
    val info = vm.debridPosters[name]
    val title = info?.title ?: com.novabox.tv.data.ReleaseNames.parse(name).title.ifBlank { RealDebrid.pretty(name) }
    val sub = listOfNotNull(info?.year?.takeIf { it.isNotBlank() }, detail.takeIf { it.isNotBlank() }).joinToString(" · ")
    FocusSurface(onClick = onClick, onFocus = onFocus, selected = selected, focusedScale = 1.06f, modifier = Modifier.fillMaxWidth()) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectPoster().background(Color(0xFF1C2540)), contentAlignment = Alignment.Center) {
                Text(title.take(1).uppercase(), fontSize = 34.sp, color = Palette.muted, fontWeight = FontWeight.Bold)
                if (!info?.poster.isNullOrBlank()) coil.compose.AsyncImage(
                    model = info!!.poster, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            // Room for two lines of title so names aren't cut short
            Column(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp)
                Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = if (focused) Color(0xFF39425A) else Palette.muted)
            }
        }
    }
}

/** Poster, matched title and year at the top of the side panel, with the full release name under it. */
@Composable
private fun DetailHeader(vm: AppViewModel, name: String) {
    LaunchedEffect(name) { vm.wantPoster(name) }
    val info = vm.debridPosters[name]
    Row {
        Box(Modifier.width(84.dp).aspectPoster().clip(RoundedCornerShape(8.dp)).background(Color(0xFF222C45)), contentAlignment = Alignment.Center) {
            Text((info?.title ?: RealDebrid.pretty(name)).take(1).uppercase(), fontSize = 28.sp, color = Palette.muted, fontWeight = FontWeight.Bold)
            if (!info?.poster.isNullOrBlank()) coil.compose.AsyncImage(
                model = info!!.poster, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(info?.title ?: RealDebrid.pretty(name), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (info != null) Text(listOf(info.year, if (info.series) "Series" else "Film").filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = Palette.muted)
            if (!info?.cast.isNullOrBlank()) Text(info!!.cast, fontSize = 12.sp, color = Palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(RealDebrid.pretty(name), fontSize = 11.sp, color = Palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun torrentStatus(t: RealDebrid.Torrent): String = when (t.status) {
    "downloaded" -> "Ready"
    "downloading" -> "Downloading ${t.progress}%"
    "queued" -> "Queued"
    "waiting_files_selection" -> "Waiting for file selection"
    "magnet_conversion" -> "Converting"
    "uploading", "compressing" -> "Finishing"
    "error", "magnet_error", "virus", "dead" -> "Failed on Real-Debrid"
    else -> t.status.replace('_', ' ')
}


/** A sidebar entry: icon and label when the sidebar is open, the icon alone when it is shrunk. */
@Composable
private fun SideItem(
    icon: ImageVector, label: String, open: Boolean, onClick: () -> Unit,
    count: Int? = null, selected: Boolean = false, onFocus: (() -> Unit)? = null, muted: Boolean = false, active: Boolean = false,
    tint: Color? = null,
) {
    FocusSurface(onClick = onClick, onFocus = onFocus, selected = selected || (!open && active), focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) { f ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (open) 14.dp else 0.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (open) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(icon, label, Modifier.size(20.dp), tint = if (!f && tint != null) tint else androidx.tv.material3.LocalContentColor.current)
            if (open) {
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    color = if (muted && !f) Palette.muted else Color.Unspecified)
                if (count != null) Text(count.toString(), fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
            }
        }
    }
}
