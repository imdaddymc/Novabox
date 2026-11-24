package com.novabox.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.novabox.tv.data.PlayRequest
import com.novabox.tv.data.VodKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun HomeTab.icon(): ImageVector = when (this) {
    HomeTab.LIVE -> Icons.Filled.PlayArrow
    HomeTab.MOVIES -> Icons.Filled.DateRange
    HomeTab.SERIES -> Icons.AutoMirrored.Filled.List
    HomeTab.FAVORITES -> Icons.Filled.Favorite
    HomeTab.SEARCH -> Icons.Filled.Search
    HomeTab.SETTINGS -> Icons.Filled.Settings
}

@Composable
fun HomeScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit, onExit: () -> Unit) {
    var tabsFocused by remember { mutableStateOf(false) }
    val tabFocus = remember { HomeTab.entries.associateWith { FocusRequester() } }

    LaunchedEffect(Unit) {
        val gridWillRestore = vm.restoreFocus && (vm.tab == HomeTab.MOVIES || vm.tab == HomeTab.SERIES)
        if (!gridWillRestore) {
            vm.restoreFocus = false
            runCatching { tabFocus.getValue(vm.tab).requestFocus() }
        }
    }

    // Back from the content jumps to the tab bar; Back on the tab bar leaves the app.
    BackHandler {
        if (tabsFocused) onExit() else runCatching { tabFocus.getValue(vm.tab).requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Nova", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Palette.text)
            Text("Box", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Palette.accent)
            Spacer(Modifier.width(36.dp))
            Row(
                Modifier.weight(1f).onFocusChanged { tabsFocused = it.hasFocus },
                horizontalArrangement = RowGap,
            ) {
                HomeTab.entries.forEach { t ->
                    FocusSurface(
                        onClick = { vm.tab = t },
                        onFocus = { vm.tab = t },
                        selected = vm.tab == t,
                        shape = RoundedCornerShape(50),
                        focusedScale = 1.08f,
                        container = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.focusRequester(tabFocus.getValue(t)),
                    ) { _ ->
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(t.icon(), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Clock()
        }
        Box(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, bottom = 24.dp)) {
            when (vm.tab) {
                HomeTab.LIVE -> LiveScreen(vm, onPlay)
                HomeTab.MOVIES -> VodScreen(vm, VodKind.MOVIE)
                HomeTab.SERIES -> VodScreen(vm, VodKind.SERIES)
                HomeTab.FAVORITES -> FavoritesScreen(vm, onPlay)
                HomeTab.SEARCH -> SearchScreen(vm, onPlay)
                HomeTab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); kotlinx.coroutines.delay(15_000) }
    }
    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), fontSize = 22.sp, color = Palette.muted, fontWeight = FontWeight.Medium)
}
