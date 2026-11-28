package com.novabox.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.Channel
import com.novabox.tv.data.EpgEntry
import com.novabox.tv.data.PlayRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    // The preview's stream is let go first: many portals allow only one stream per box.
    val play: (PlayRequest) -> Unit = { r -> PreviewPlayer.stop(); onPlay(r) }
    val preview = remember { AppGraph.settings.livePreview }
    // The categories column narrows once a category is chosen and the focus moves on to the channels.
    var inCategories by remember { mutableStateOf(true) }
    // The preview plays only while the viewer is on the channels or their details, not on the tabs.
    var inChannels by remember { mutableStateOf(false) }
    val favs by AppGraph.favorites.channels.collectAsState()
    val recent by AppGraph.recent.channels.collectAsState()
    val list = remember(vm.selectedGenre, vm.channels, recent) { vm.channelsFor(vm.selectedGenre) }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.selectedGenre) { listState.scrollToItem(0) }

    if (vm.channels.isEmpty()) {
        CenterMessage(if (vm.liveLoading) "Loading channels…" else "No live channels on this portal")
        return
    }

    Row(Modifier.fillMaxSize().onFocusChanged { if (!it.hasFocus) inChannels = false }, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // Categories. ▶ goes into the guide's top row (it doesn't line up with the categories).
        val guideEntry = remember { androidx.compose.ui.focus.FocusRequester() }
        val guideItem = remember { androidx.compose.ui.focus.FocusRequester() }
        val catWidth by androidx.compose.animation.core.animateDpAsState(if (inCategories || vm.selectedGenre == AppViewModel.GUIDE) 210.dp else 128.dp, label = "categories")
        Column(Modifier.width(catWidth).fillMaxHeight().onFocusChanged { inCategories = it.hasFocus; if (it.hasFocus) inChannels = false }.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionRight || vm.selectedGenre != AppViewModel.GUIDE) return@onPreviewKeyEvent false
            runCatching { guideEntry.requestFocus() }.isSuccess
        }) {
            SectionTitle("CATEGORIES")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                item {
                    GenreItem("All channels", vm.channels.size.takeIf { inCategories }, vm.selectedGenre == null) { vm.selectedGenre = null }
                }
                item {
                    GenreItem("TV guide", null, vm.selectedGenre == AppViewModel.GUIDE, Modifier.focusRequester(guideItem)) { vm.selectedGenre = AppViewModel.GUIDE }
                }
                val recentCount = recent.count { r -> vm.channels.any { it.id == r.id } }
                if (recentCount > 0) item {
                    GenreItem("Recent", recentCount.takeIf { inCategories }, vm.selectedGenre == AppViewModel.RECENT) { vm.selectedGenre = AppViewModel.RECENT }
                }
                items(vm.genres, key = { it.id }) { g ->
                    val count = remember(g.id, vm.channels) { vm.channels.count { it.genreId == g.id } }
                    if (count > 0) GenreItem(g.title, count.takeIf { inCategories }, vm.selectedGenre == g.id) { vm.selectedGenre = g.id }
                }
            }
        }

        if (vm.selectedGenre == AppViewModel.GUIDE) {
            TvGuide(vm, list, play, Modifier.weight(1f), guideEntry, onLeave = { runCatching { guideItem.requestFocus() }.isSuccess })
            return@Row
        }

        // Channels
        Column(Modifier.weight(1f).fillMaxHeight().onFocusChanged { if (it.hasFocus) inChannels = true }) {
            SectionTitle(if (vm.selectedGenre == AppViewModel.RECENT) "RECENTLY WATCHED · ${list.size}" else "CHANNELS · ${list.size}")
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(list, key = { _, c -> c.id }) { index, c ->
                    val isFav = favs.any { it.id == c.id }
                    val inRecent = vm.selectedGenre == AppViewModel.RECENT
                    // Favourites are added from the details panel (▶) or the player menu; the ♥ marks them here.
                    FocusSurface(
                        onClick = { play(PlayRequest.Live(list, index)) },
                        onFocus = { focused = c; vm.loadEpg(c) },
                        onLongClick = if (inRecent) ({ AppGraph.recent.remove(c.id); vm.showToast("Removed from Recent") }) else null,
                        focusedScale = 1.02f,
                        modifier = Modifier.fillMaxWidth(),
                    ) { f ->
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.number.toString(), modifier = Modifier.width(44.dp), fontSize = 13.sp,
                                color = if (f) Color(0xFF39425A) else Palette.muted,
                            )
                            Logo(c.logo, c.name, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                val now = vm.epg[c.id]?.currentProgramme()
                                if (now != null) {
                                    Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
                                }
                            }
                            if (isFav) FavMark(true, 18.dp, tint = if (f) null else Palette.heart)
                        }
                    }
                }
            }
            if (vm.selectedGenre == AppViewModel.RECENT && list.isNotEmpty()) {
                Text("Hold OK on a channel to remove it from Recent.", fontSize = 12.sp, color = Palette.muted, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
        }

        // Info / EPG panel
        Column(Modifier.width(if (preview) 380.dp else 330.dp).fillMaxHeight().padding(top = 26.dp)) {
            val c = focused ?: list.firstOrNull()
            if (c != null) ChannelInfo(vm, c, favs.any { it.id == c.id }, preview, previewActive = inChannels && !inCategories,
                onRemoveRecent = if (vm.selectedGenre == AppViewModel.RECENT) ({ AppGraph.recent.remove(c.id); focused = null; vm.showToast("Removed from Recent") }) else null) {
                play(PlayRequest.Live(list, list.indexOf(c).coerceAtLeast(0)))
            }
        }
    }
}

@Composable
private fun GenreItem(title: String, count: Int?, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    FocusSurface(onClick = onSelect, onFocus = onSelect, selected = selected, focusedScale = 1.03f, modifier = modifier.fillMaxWidth()) { f ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (count != null) Text(count.toString(), fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
        }
    }
}

@Composable
private fun ChannelInfo(vm: AppViewModel, c: Channel, isFav: Boolean, preview: Boolean, previewActive: Boolean, onRemoveRecent: (() -> Unit)? = null, onWatch: () -> Unit) {
    val fmt = rememberTimeFormat(showAmPm = false)
    LaunchedEffect(c.id) { vm.loadEpg(c) }
    Column(
        Modifier.fillMaxWidth().background(Palette.bg2, RoundedCornerShape(16.dp)).padding(if (preview) 16.dp else 22.dp)
    ) {
        if (preview) {
            LivePreview(c, previewActive)
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(c.logo, c.name, if (preview) 40.dp else 60.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(c.name, fontSize = if (preview) 18.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = if (preview) 1 else 2, overflow = TextOverflow.Ellipsis)
                Text("Channel ${c.number}", color = Palette.muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(if (preview) 12.dp else 18.dp))
        Row(horizontalArrangement = RowGap) {
            NovaButton("Watch", onWatch, icon = Icons.Filled.PlayArrow, primary = true)
            NovaButton(
                if (isFav) "Remove" else "Favourite",
                { AppGraph.favorites.toggle(c) },
                icon = Icons.Filled.Favorite, tint = if (isFav) Palette.heart else null,
            )
        }
        if (onRemoveRecent != null) {
            Spacer(Modifier.height(8.dp))
            NovaButton("Remove from Recent", onRemoveRecent, icon = Icons.Filled.Close)
        }
        Spacer(Modifier.height(if (preview) 12.dp else 22.dp))
        SectionTitle("PROGRAMME GUIDE")
        val entries = vm.epg[c.id]
        when {
            entries == null -> Text("Loading guide…", color = Palette.muted, fontSize = 14.sp)
            entries.isEmpty() -> Text("No guide data for this channel", color = Palette.muted, fontSize = 14.sp)
            else -> entries.take(if (preview) 3 else 5).forEachIndexed { i, e ->
                val live = e.isLive()
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (e.start > 0) "${fmt.format(Date(e.start))}–${fmt.format(Date(e.end))}" else "",
                            fontSize = 13.sp, color = if (live) Palette.teal else Palette.muted, modifier = Modifier.width(100.dp),
                        )
                        Text(e.title, fontSize = 15.sp, fontWeight = if (live) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (live) {
                        ProgressBar(e.progress(), Modifier.padding(top = 6.dp))
                        if (e.description.isNotBlank()) {
                            Text(e.description, fontSize = 13.sp, color = Palette.muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(Palette.teal, RoundedCornerShape(2.dp)))
    }
}

fun EpgEntry.isLive(now: Long = System.currentTimeMillis()) = start in 1..now && end > now
fun EpgEntry.progress(now: Long = System.currentTimeMillis()) = if (end > start) (now - start).toFloat() / (end - start) else 0f
fun List<EpgEntry>.currentProgramme() = firstOrNull { it.isLive() } ?: firstOrNull()
