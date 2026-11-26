package com.novabox.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
            "NovaBox plays what is already in your Real-Debrid account. You link it with a code, so no password is typed on the TV.",
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
    var editing by remember { mutableStateOf(false) }
    var focusedDownload by remember { mutableStateOf<RealDebrid.Download?>(null) }
    var confirmUnlink by remember { mutableStateOf(false) }
    if (editing) TextInputDialog("Search your Real-Debrid library", vm.debridQuery, onValue = { vm.debridQuery = it }, onDismiss = { editing = false })
    if (confirmUnlink) ConfirmDialog("Unlink Real-Debrid from this TV?", onYes = { vm.unlinkDebrid(); confirmUnlink = false }, onNo = { confirmUnlink = false }, yesLabel = "Unlink")

    val q = vm.debridQuery
    val torrents = remember(vm.debridTorrents, q) { vm.debridTorrents.filter { RealDebrid.matches(it.name, q) } }
    val downloads = remember(vm.debridDownloads, q) {
        vm.debridDownloads.filter { RealDebrid.isVideo(it.name) && RealDebrid.matches(it.name, q) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.debridShelf, q) { listState.scrollToItem(0) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // Left: account, search, shelves
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            SectionTitle("REAL-DEBRID")
            val u = vm.debridUser
            Text(u?.username ?: "…", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    u == null -> ""
                    u.premium -> "Premium until ${u.expiration.take(10)}"
                    else -> "Not premium: streams won't play"
                },
                fontSize = 13.sp, color = if (u != null && !u.premium) Palette.danger else Palette.muted,
            )
            Spacer(Modifier.height(14.dp))
            FocusSurface(onClick = { editing = true }, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { f ->
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(q.ifBlank { "Search" }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (q.isBlank() && !f) Palette.muted else Color.Unspecified)
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("LIBRARY")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                AppViewModel.DebridShelf.entries.forEach { shelf ->
                    val n = if (shelf == AppViewModel.DebridShelf.TORRENTS) torrents.size else downloads.size
                    FocusSurface(
                        onClick = { vm.debridShelf = shelf }, onFocus = { vm.debridShelf = shelf },
                        selected = vm.debridShelf == shelf, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth(),
                    ) { f ->
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(shelf.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(n.toString(), fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaButton("Refresh", { vm.loadDebrid(force = true) }, icon = Icons.Filled.Refresh)
            }
            Spacer(Modifier.height(8.dp))
            NovaButton("Unlink", { confirmUnlink = true }, icon = Icons.Filled.Close)
        }

        // Middle: the list
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val isTorrents = vm.debridShelf == AppViewModel.DebridShelf.TORRENTS
            val count = if (isTorrents) torrents.size else downloads.size
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
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                        if (isTorrents) items(torrents, key = { "t" + it.id }) { t ->
                            LibraryRow(
                                title = RealDebrid.pretty(t.name),
                                detail = RealDebrid.size(t.bytes) + " · " + torrentStatus(t),
                                selected = vm.debridOpen?.id == t.id,
                                onClick = { vm.openTorrent(t) },
                                onFocus = {},
                            )
                        } else items(downloads, key = { "d" + it.id }) { d ->
                            LibraryRow(
                                title = RealDebrid.pretty(d.name),
                                detail = RealDebrid.size(d.bytes) + " · " + d.generated.take(10),
                                selected = false,
                                onClick = { vm.playDebrid(RealDebrid.pretty(d.name), d.url, alreadyDirect = true, onPlay) },
                                onFocus = { focusedDownload = d },
                            )
                        }
                    }
                }
            }
        }

        // Right: files of the open torrent, or the focused download
        Column(Modifier.width(340.dp).fillMaxHeight().padding(top = 26.dp)) {
            Column(Modifier.fillMaxWidth().background(Palette.bg2, RoundedCornerShape(16.dp)).padding(22.dp)) {
                vm.debridPlayStatus?.let {
                    Text(it, fontSize = 14.sp, color = if (it.startsWith("Getting")) Palette.accent else Palette.danger)
                    Spacer(Modifier.height(12.dp))
                }
                if (vm.debridShelf == AppViewModel.DebridShelf.TORRENTS) {
                    val t = vm.debridOpen
                    if (t == null) {
                        SectionTitle("FILES")
                        Text("Press OK on a torrent to see its video files.", fontSize = 14.sp, color = Palette.muted)
                    } else {
                        Text(RealDebrid.pretty(t.name), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(RealDebrid.size(t.bytes) + " · " + torrentStatus(t), fontSize = 13.sp, color = Palette.muted)
                        Spacer(Modifier.height(14.dp))
                        val files = vm.debridFiles
                        when {
                            t.status != "downloaded" -> Text("This torrent isn't ready on Real-Debrid yet, so it can't play.", fontSize = 14.sp, color = Palette.muted)
                            files == null -> Text("Loading files…", fontSize = 14.sp, color = Palette.muted)
                            files.isEmpty() -> Text("No video files in this torrent.", fontSize = 14.sp, color = Palette.muted)
                            else -> {
                                SectionTitle("VIDEO FILES · ${files.size}")
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                                    items(files, key = { it.link }) { f ->
                                        FocusSurface(onClick = { vm.playDebrid(RealDebrid.pretty(f.name), f.link, alreadyDirect = false, onPlay) }, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth()) { foc ->
                                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(RealDebrid.pretty(f.name), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    Text(RealDebrid.size(f.bytes), fontSize = 12.sp, color = if (foc) Color(0xFF39425A) else Palette.muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val d = focusedDownload ?: downloads.firstOrNull()
                    if (d != null) {
                        Text(RealDebrid.pretty(d.name), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(RealDebrid.size(d.bytes) + " · added " + d.generated.take(10), fontSize = 13.sp, color = Palette.muted)
                        Spacer(Modifier.height(16.dp))
                        NovaButton("Play", { vm.playDebrid(RealDebrid.pretty(d.name), d.url, alreadyDirect = true, onPlay) }, icon = Icons.Filled.PlayArrow, primary = true)
                    } else {
                        Text("Links you have unrestricted on Real-Debrid show here.", fontSize = 14.sp, color = Palette.muted)
                    }
                }
            }
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

@Composable
private fun LibraryRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    FocusSurface(onClick = onClick, onFocus = onFocus, selected = selected, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth()) { f ->
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, maxLines = 1, fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
        }
    }
}
