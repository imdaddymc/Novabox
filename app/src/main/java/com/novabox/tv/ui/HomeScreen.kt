package com.novabox.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
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
    var lastBack by remember { mutableStateOf(0L) }
    BackHandler {
        if (!tabsFocused) {
            runCatching { tabFocus.getValue(vm.tab).requestFocus() }
        } else if (System.currentTimeMillis() - lastBack < 2500) {
            onExit()
        } else {
            lastBack = System.currentTimeMillis()
            vm.showToast("Press Back again to exit")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(Palette.accent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("N", fontSize = 22.sp, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White) }
            Spacer(Modifier.width(20.dp))
            Row(
                Modifier.weight(1f).onFocusChanged { tabsFocused = it.hasFocus }
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(t.icon(), null, Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(t.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
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
    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), modifier = Modifier.padding(start = 12.dp), fontSize = 20.sp, color = Palette.muted, fontWeight = FontWeight.Medium)
}
