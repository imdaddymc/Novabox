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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.novabox.tv.data.Channel
import com.novabox.tv.data.FreeTv
import com.novabox.tv.data.PlayRequest

/** Free TV's "HD · 1080p and up" category. */
private const val HD_GROUP = "__hd"

/** Free TV: public broadcaster streams by country. Needs no portal profile or login. */
@Composable
fun FreeTvScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    LaunchedEffect(Unit) { vm.loadFreeTv() }
    var pickCountry by remember { mutableStateOf(false) }
    val country = FreeTv.country(vm.freeCountry)
    val all = vm.freeChannels
    val groups = remember(all) { all.groupingBy { it.genreId }.eachCount().toList().sortedBy { it.first.lowercase() } }
    val hd = remember(all) { all.filter { FreeTv.height(it) >= FreeTv.HD_MIN } }
    val list = remember(all, vm.freeGroup) {
        when (vm.freeGroup) {
            null -> all
            HD_GROUP -> hd
            else -> all.filter { it.genreId == vm.freeGroup }
        }
    }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.freeGroup, vm.freeCountry) { listState.scrollToItem(0); focused = null }

    if (pickCountry) CountryDialog(vm.freeCountry, onPick = { vm.chooseFreeCountry(it); pickCountry = false }, onDismiss = { pickCountry = false })

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(210.dp).fillMaxHeight()) {
            SectionTitle("COUNTRY")
            FocusSurface(onClick = { pickCountry = true }, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { f ->
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(country.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("Change", fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (all.isNotEmpty()) {
                SectionTitle("CATEGORIES")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    item { GroupItem("All channels", all.size, vm.freeGroup == null) { vm.freeGroup = null } }
                    if (hd.isNotEmpty()) item(key = HD_GROUP) { GroupItem("HD · 1080p and up", hd.size, vm.freeGroup == HD_GROUP) { vm.freeGroup = HD_GROUP } }
                    groups.forEach { (g, n) -> item(key = g) { GroupItem(g, n, vm.freeGroup == g) { vm.freeGroup = g } } }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            when {
                vm.freeLoading -> CenterMessage("Loading free channels for ${country.name}…")
                vm.freeError != null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(vm.freeError ?: "", color = Palette.muted, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    NovaButton("Try again", { vm.loadFreeTv(force = true) }, icon = Icons.Filled.Refresh, primary = true)
                }
                all.isEmpty() -> CenterMessage("No free channels listed for ${country.name}")
                else -> {
                    SectionTitle("FREE CHANNELS · ${list.size}")
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                        itemsIndexed(list, key = { _, c -> c.id }) { index, c ->
                            FocusSurface(
                                onClick = { onPlay(PlayRequest.Live(list, index, direct = true)) },
                                onFocus = { focused = c },
                                focusedScale = 1.02f,
                                modifier = Modifier.fillMaxWidth(),
                            ) { f ->
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Logo(c.logo, c.name, 36.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text(listOf(c.genreId, c.note).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.width(330.dp).fillMaxHeight().padding(top = 26.dp)) {
            Column(Modifier.fillMaxWidth().background(Palette.bg2, RoundedCornerShape(16.dp)).padding(22.dp)) {
                val c = focused ?: list.firstOrNull()
                if (c != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Logo(c.logo, c.name, 60.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(c.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOf(c.genreId, c.note).filter { it.isNotBlank() }.joinToString(" · "), color = Palette.muted, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    NovaButton("Watch", { onPlay(PlayRequest.Live(list, list.indexOf(c).coerceAtLeast(0), direct = true)) }, icon = Icons.Filled.PlayArrow, primary = true)
                    Spacer(Modifier.height(22.dp))
                }
                SectionTitle("ABOUT FREE TV")
                Text(
                    "Channels that broadcasters stream free to the public, from the community list at iptv-org. " +
                        "No login needed. Some may be offline or only play in their home country.",
                    fontSize = 13.sp, color = Palette.muted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    vm.freeChecking ?: when {
                        vm.freeLastCheck == 0L -> "Streams are checked once a month; dead ones are hidden."
                        else -> "Checked " + java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(vm.freeLastCheck)) +
                            (if (vm.freeHidden > 0) " · ${vm.freeHidden} offline hidden" else " · all answering") + ". Checked again monthly."
                    },
                    fontSize = 13.sp, color = if (vm.freeChecking != null) Palette.teal else Palette.muted,
                )
                Spacer(Modifier.height(14.dp))
                NovaButton("Refresh list", { vm.loadFreeTv(force = true) }, icon = Icons.Filled.Refresh)
                Spacer(Modifier.height(8.dp))
                NovaButton(if (vm.freeChecking != null) "Checking…" else "Check channels now", { vm.checkFreeChannels() }, icon = Icons.Filled.Check)
            }
        }
    }
}

@Composable
private fun GroupItem(title: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    FocusSurface(onClick = onSelect, onFocus = onSelect, selected = selected, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) { f ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(count.toString(), fontSize = 12.sp, color = if (f) Color(0xFF39425A) else Palette.muted)
        }
    }
}

@Composable
private fun CountryDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val fr = remember { FocusRequester() }
    val start = FreeTv.countries.indexOfFirst { it.code == current }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(420.dp).heightIn(max = 470.dp).background(Palette.bg2, RoundedCornerShape(18.dp)).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.tv.material3.Icon(Icons.Filled.Place, null)
                Spacer(Modifier.width(10.dp))
                Text("Choose a country", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                itemsIndexed(FreeTv.countries, key = { _, c -> c.code }) { i, c ->
                    FocusSurface(
                        onClick = { onPick(c.code) }, selected = c.code == current, focusedScale = 1.02f,
                        modifier = Modifier.fillMaxWidth().then(if (i == start) Modifier.focusRequester(fr) else Modifier),
                    ) { _ ->
                        Text(c.name, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}
