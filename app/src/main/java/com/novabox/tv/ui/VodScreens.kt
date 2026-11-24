package com.novabox.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.PlayRequest
import com.novabox.tv.data.VodItem
import com.novabox.tv.data.VodKind

@Composable
fun VodScreen(vm: AppViewModel, kind: VodKind) {
    LaunchedEffect(kind) { vm.ensureCategories(kind) }
    val cats = vm.categories[kind].orEmpty()
    val cat = vm.selectedCategory[kind]
    val favs by AppGraph.favorites.vod.collectAsState()

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            SectionTitle("CATEGORIES")
            if (cats.isEmpty()) Text("Loading…", color = Palette.muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                items(cats, key = { it.id }) { g ->
                    FocusSurface(
                        onClick = { vm.selectedCategory[kind] = g.id },
                        onFocus = { vm.selectedCategory[kind] = g.id },
                        selected = g.id == cat, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth(),
                    ) { _ ->
                        Text(g.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (cat != null) VodGrid(vm, kind, cat, favs)
        }
    }
}

@Composable
private fun VodGrid(vm: AppViewModel, kind: VodKind, cat: String, favs: List<VodItem>) {
    val list = remember(kind, cat) { vm.list(kind, cat) }
    val state = list.gridState
    val restore = remember { FocusRequester() }
    LaunchedEffect(kind, cat) {
        if (list.items.isEmpty()) vm.loadMore(kind, cat)
        if (vm.restoreFocus) {
            vm.restoreFocus = false
            if (list.lastOpened >= 0) {
                state.scrollToItem(list.lastOpened)
                kotlinx.coroutines.delay(60)
                runCatching { restore.requestFocus() }
            }
        }
    }
    if (list.items.isEmpty()) {
        CenterMessage(
            when {
                list.loading -> "Loading…"
                list.error != null -> "Couldn't load: ${list.error}"
                else -> "Nothing in this category"
            }
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        itemsIndexed(list.items, key = { i, it -> "${it.id}-$i" }) { index, item ->
            if (index >= list.items.size - 12) LaunchedEffect(list.items.size) { vm.loadMore(kind, cat) }
            PosterCard(
                title = item.name,
                poster = item.poster,
                subtitle = listOf(item.year, item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { list.lastOpened = index; vm.openDetails(item) },
                favorite = favs.any { it.id == item.id && it.kind == item.kind },
                modifier = if (index == list.lastOpened) Modifier.focusRequester(restore) else Modifier,
            )
        }
    }
}

@Composable
fun DetailsScreen(vm: AppViewModel, item: VodItem, onPlay: (PlayRequest) -> Unit) {
    val favs by AppGraph.favorites.vod.collectAsState()
    val isFav = favs.any { it.id == item.id && it.kind == item.kind }
    val playFocus = remember { FocusRequester() }
    var seasonIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        if (item.poster.isNotBlank()) {
            AsyncImage(item.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.18f)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Palette.bg, Palette.bg.copy(alpha = 0.85f), Color.Transparent))))
        Row(Modifier.fillMaxSize().padding(56.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Box(Modifier.width(280.dp).clip(RoundedCornerShape(16.dp)).background(Palette.card).aspectPoster(), contentAlignment = Alignment.Center) {
                Text(item.name.take(1), fontSize = 64.sp, color = Palette.muted)
                if (item.poster.isNotBlank()) AsyncImage(item.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text(item.name, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(item.year, item.rating.takeIf { it.isNotBlank() }?.let { "IMDb $it" } ?: "", item.duration, item.genres)
                            .filter { it.isNotBlank() }.forEach { Chip(it) }
                    }
                }
                if (item.description.isNotBlank()) item {
                    Text(item.description, fontSize = 16.sp, color = Palette.text.copy(alpha = 0.85f), maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.85f))
                }
                if (item.director.isNotBlank() || item.actors.isNotBlank()) item {
                    Column {
                        if (item.director.isNotBlank()) Text("Director: ${item.director}", fontSize = 14.sp, color = Palette.muted)
                        if (item.actors.isNotBlank()) Text("Cast: ${item.actors}", fontSize = 14.sp, color = Palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Row(horizontalArrangement = RowGap, modifier = Modifier.padding(top = 8.dp)) {
                        if (item.kind == VodKind.MOVIE) {
                            NovaButton("Play", { onPlay(PlayRequest.Vod(item.name, item.cmd, null)) }, Modifier.focusRequester(playFocus), icon = Icons.Filled.PlayArrow, primary = true)
                        }
                        NovaButton(
                            if (isFav) "Remove favourite" else "Add to favourites",
                            { AppGraph.favorites.toggle(item) },
                            if (item.kind == VodKind.SERIES) Modifier.focusRequester(playFocus) else Modifier,
                            icon = Icons.Filled.Star, tint = if (isFav) Palette.gold else null,
                        )
                        NovaButton("Back", { vm.pop() })
                    }
                }
                if (item.kind == VodKind.SERIES) {
                    item {
                        when {
                            vm.seasonsLoading -> Text("Loading episodes…", color = Palette.muted)
                            vm.seasons.isEmpty() -> Text("No episodes found", color = Palette.muted)
                        }
                    }
                    if (vm.seasons.size > 1) item {
                        Column {
                            SectionTitle("SEASONS")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                                itemsIndexed(vm.seasons) { i, s ->
                                    FocusSurface(onClick = { seasonIdx = i }, onFocus = { seasonIdx = i }, selected = i == seasonIdx, shape = RoundedCornerShape(50)) { _ ->
                                        Text(s.name, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                    vm.seasons.getOrNull(seasonIdx)?.let { season ->
                        item {
                            Column {
                                SectionTitle("EPISODES")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                                    items(season.episodes) { ep ->
                                        FocusSurface(
                                            onClick = { onPlay(PlayRequest.Vod(item.name, season.cmd, ep, "${season.name} · Episode $ep")) },
                                            focusedScale = 1.08f,
                                        ) { _ ->
                                            Column(Modifier.width(120.dp).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("EP", fontSize = 11.sp, color = Palette.muted)
                                                Text("$ep", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
