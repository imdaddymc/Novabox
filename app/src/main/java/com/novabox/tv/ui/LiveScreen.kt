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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val favs by AppGraph.favorites.channels.collectAsState()
    val list = remember(vm.selectedGenre, vm.channels) { vm.channelsFor(vm.selectedGenre) }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.selectedGenre) { listState.scrollToItem(0) }

    if (vm.channels.isEmpty()) {
        CenterMessage(if (vm.liveLoading) "Loading channels…" else "No live channels on this portal")
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // Categories
        Column(Modifier.width(210.dp).fillMaxHeight()) {
            SectionTitle("CATEGORIES")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                item {
                    GenreItem("All channels", vm.channels.size, vm.selectedGenre == null) { vm.selectedGenre = null }
                }
                items(vm.genres, key = { it.id }) { g ->
                    val count = remember(g.id, vm.channels) { vm.channels.count { it.genreId == g.id } }
                    if (count > 0) GenreItem(g.title, count, vm.selectedGenre == g.id) { vm.selectedGenre = g.id }
                }
            }
        }

        // Channels
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SectionTitle("CHANNELS · ${list.size}")
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(list, key = { _, c -> c.id }) { index, c ->
                    val isFav = favs.any { it.id == c.id }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusSurface(
                            onClick = { onPlay(PlayRequest.Live(list, index)) },
                            onFocus = { focused = c; vm.loadEpg(c) },
                            focusedScale = 1.02f,
                            modifier = Modifier.weight(1f),
                        ) { f ->
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    c.number.toString(), modifier = Modifier.width(38.dp), fontSize = 13.sp,
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
                                if (isFav) Text("★", color = Palette.gold, fontSize = 18.sp)
                            }
                        }
                        // A dedicated, focusable favourite toggle: no colour button needed.
                        FocusSurface(
                            onClick = { AppGraph.favorites.toggle(c); vm.showToast(if (isFav) "Removed from favourites" else "Added to favourites") },
                            shape = RoundedCornerShape(50),
                            focusedScale = 1.12f,
                            modifier = Modifier.size(44.dp),
                        ) { f ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (isFav) "★" else "☆", fontSize = 20.sp, color = if (isFav && !f) Palette.gold else Color.Unspecified)
                            }
                        }
                    }
                }
            }
        }

        // Info / EPG panel
        Column(Modifier.width(330.dp).fillMaxHeight().padding(top = 26.dp)) {
            val c = focused ?: list.firstOrNull()
            if (c != null) ChannelInfo(vm, c, favs.any { it.id == c.id }) {
                onPlay(PlayRequest.Live(list, list.indexOf(c).coerceAtLeast(0)))
            }
        }
    }
}

@Composable
private fun GenreItem(title: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    FocusSurface(onClick = onSelect, onFocus = onSelect, selected = selected, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) { f ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(count.toString(), fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
        }
    }
}

@Composable
private fun ChannelInfo(vm: AppViewModel, c: Channel, isFav: Boolean, onWatch: () -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    LaunchedEffect(c.id) { vm.loadEpg(c) }
    Column(
        Modifier.fillMaxWidth().background(Palette.bg2, RoundedCornerShape(16.dp)).padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(c.logo, c.name, 60.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(c.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Channel ${c.number}", color = Palette.muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = RowGap) {
            NovaButton("Watch", onWatch, icon = Icons.Filled.PlayArrow, primary = true)
            NovaButton(
                if (isFav) "Remove" else "Favourite",
                { AppGraph.favorites.toggle(c) },
                icon = Icons.Filled.Star, tint = if (isFav) Palette.gold else null,
            )
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("PROGRAMME GUIDE")
        val entries = vm.epg[c.id]
        when {
            entries == null -> Text("Loading guide…", color = Palette.muted, fontSize = 14.sp)
            entries.isEmpty() -> Text("No guide data for this channel", color = Palette.muted, fontSize = 14.sp)
            else -> entries.take(5).forEachIndexed { i, e ->
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
