package com.novabox.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.Channel
import com.novabox.tv.data.PlayRequest
import com.novabox.tv.data.VodItem
import com.novabox.tv.data.VodKind

// ------------------------------------------------------------------ favourites

@Composable
fun FavoritesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by AppGraph.favorites.channels.collectAsState()
    val vod by AppGraph.favorites.vod.collectAsState()
    if (channels.isEmpty() && vod.isEmpty()) {
        CenterMessage("No favourites yet. Use the ☆ button next to any channel, film or series to add it here.")
        return
    }
    ResultSections(
        vm, onPlay,
        channels = channels,
        movies = vod.filter { it.kind == VodKind.MOVIE },
        series = vod.filter { it.kind == VodKind.SERIES },
        channelTitle = "FAVOURITE CHANNELS",
    )
}

@Composable
private fun ResultSections(
    vm: AppViewModel,
    onPlay: (PlayRequest) -> Unit,
    channels: List<Channel>,
    movies: List<VodItem>,
    series: List<VodItem>,
    channelTitle: String = "CHANNELS",
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(26.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (header != null) item { header() }
        if (channels.isNotEmpty()) item {
            Column {
                SectionTitle("$channelTitle · ${channels.size}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                        ChannelTile(c) { onPlay(PlayRequest.Live(channels, i)) }
                    }
                }
            }
        }
        if (movies.isNotEmpty()) item { PosterRow("MOVIES · ${movies.size}", movies, vm) }
        if (series.isNotEmpty()) item { PosterRow("SERIES · ${series.size}", series, vm) }
    }
}

@Composable
private fun ChannelTile(c: Channel, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, focusedScale = 1.08f, modifier = Modifier.width(190.dp)) { _ ->
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(c.logo, c.name, 72.dp)
            Spacer(Modifier.padding(4.dp))
            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PosterRow(title: String, items: List<VodItem>, vm: AppViewModel) {
    Column {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
            items(items, key = { "${it.kind}-${it.id}" }) { v ->
                PosterCard(v.name, v.poster, v.year, onClick = { vm.openDetails(v) }, modifier = Modifier.width(160.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ search

@Composable
fun SearchScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val searchBox = remember { FocusRequester() }

    if (editing) {
        TextInputDialog("Search channels, films and series", vm.searchQuery, onValue = { vm.search(it) }, onDismiss = { editing = false })
    }
    val empty = vm.searchChannels.isEmpty() && vm.searchMovies.isEmpty() && vm.searchSeries.isEmpty()
    ResultSections(
        vm, onPlay, vm.searchChannels, vm.searchMovies, vm.searchSeries,
        header = {
            Column {
                FocusSurface(onClick = { editing = true }, modifier = Modifier.fillMaxWidth(0.6f).focusRequester(searchBox), focusedScale = 1.02f) { f ->
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, null)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            vm.searchQuery.ifBlank { "Press OK to type a search" }, fontSize = 18.sp,
                            color = if (vm.searchQuery.isBlank() && !f) Palette.muted else Color.Unspecified,
                        )
                    }
                }
                Spacer(Modifier.padding(6.dp))
                when {
                    vm.searching -> Text("Searching…", color = Palette.muted)
                    vm.searchQuery.isNotBlank() && empty -> Text("No results for “${vm.searchQuery}”", color = Palette.muted)
                }
            }
        },
    )
}

/** On TV the keyboard only opens when the user presses OK on a field, never on focus. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onValue: (String) -> Unit,
    onDismiss: () -> Unit,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() }; kb?.show() }
    Dialog(onDismissRequest = { onValue(value.text); onDismiss() }) {
        Column(Modifier.background(Palette.bg2, RoundedCornerShape(18.dp)).padding(28.dp).width(620.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.padding(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; if (!password) onValue(it.text) },
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = keyboardType),
                keyboardActions = KeyboardActions(onDone = { onValue(value.text); onDismiss() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.text, unfocusedTextColor = Palette.text,
                    focusedBorderColor = Palette.accent, cursorColor = Palette.accent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
            Spacer(Modifier.padding(8.dp))
            Row(horizontalArrangement = RowGap) {
                NovaButton("Done", { onValue(value.text); onDismiss() }, primary = true)
                NovaButton("Clear", { value = TextFieldValue(""); if (!password) onValue("") })
            }
        }
    }
}

