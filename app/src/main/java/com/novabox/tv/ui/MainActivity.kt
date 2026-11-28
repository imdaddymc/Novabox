package com.novabox.tv.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.PlayRequest
import com.novabox.tv.data.PlaybackHolder
import com.novabox.tv.player.PlayerActivity
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        vm.start()
        setContent {
            NovaTheme {
                ScreenBackground {
                    AppRoot(vm, onPlay = ::play, onExit = ::finish)
                    Toast(vm)
                }
            }
        }
    }

    private fun play(req: PlayRequest) {
        PlaybackHolder.request = req
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}

@Composable
private fun AppRoot(vm: AppViewModel, onPlay: (PlayRequest) -> Unit, onExit: () -> Unit) {
    val route = vm.route
    BackHandler(enabled = vm.backStack.size > 1) { vm.pop() }
    AnimatedContent(targetState = route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { r ->
        when (r) {
            Route.Connecting -> Connecting(vm)
            Route.Home -> HomeScreen(vm, onPlay, onExit)
            is Route.Details -> DetailsScreen(vm, r.item, onPlay)
            is Route.EditProfile -> ProfileEditScreen(vm, r.profile, r.firstRun)
            is Route.Error -> ErrorScreen(vm, r.message)
            Route.NetworkCheck -> NetworkCheckScreen(vm)
        }
    }
}

@Composable
private fun Connecting(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.novabox.tv.R.drawable.ic_lodestar_tile),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("Lodestar", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Palette.text)
        Spacer(Modifier.height(16.dp))
        Text(
            "Connecting to ${vm.activeProfile?.name?.ifBlank { null } ?: vm.activeProfile?.portalUrl ?: "portal"}…",
            color = Palette.muted, fontSize = 18.sp,
        )
        if (vm.liveLoading) {
            Spacer(Modifier.height(8.dp))
            Text("Loading channels", color = Palette.teal, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorScreen(vm: AppViewModel, message: String) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(80.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text("Couldn't connect", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val m = message.lowercase()
        val summary = when {
            "unable to resolve host" in m || "unknownhost" in m -> "The portal address couldn't be found. Check the portal URL in your profile, and that the TV is online."
            "timeout" in m || "timed out" in m -> "The portal didn't answer in time. It may be busy or down; try again in a few minutes."
            "failed to connect" in m || "refused" in m -> "The portal refused the connection. Check the address and port in the portal URL."
            "portal says" in m || ("mac" in m && ("not found" in m || "blocked" in m || "denied" in m || "not authorized" in m || "registered" in m)) ->
                "The portal didn't accept this MAC address. Ask your provider to register it, or enter the MAC they already have on file."
            else -> message.lineSequence().first()
        }
        Text(summary, color = Palette.text, fontSize = 20.sp)
        // Providers authorize one MAC address; show it big so it can be read out or compared.
        vm.activeProfile?.let { p ->
            Spacer(Modifier.height(14.dp))
            Text("MAC address sent:  ${p.mac.uppercase()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.teal)
            Text("Your provider must have this exact MAC registered. To use the MAC from another app or box, choose Edit profile.",
                color = Palette.muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Details: " + message.lineSequence().drop(if (summary == message.lineSequence().first()) 1 else 0).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { message },
            color = Palette.muted, fontSize = 13.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = RowGap) {
            NovaButton("Try again", { vm.activeProfile?.let(vm::connect) }, Modifier.focusRequester(fr), primary = true)
            NovaButton("Edit profile", { vm.push(Route.EditProfile(vm.activeProfile)) })
            NovaButton("Check network", { vm.push(Route.NetworkCheck) })
            NovaButton("Free TV", { vm.openFreeTvOnly() })
            NovaButton("Other profiles", {
                vm.backStack.clear(); vm.push(Route.Home); vm.tab = HomeTab.SETTINGS
            })
        }
    }
}

@Composable
private fun Toast(vm: AppViewModel) {
    val msg = vm.toast ?: return
    LaunchedEffect(msg) { delay(2500); vm.toast = null }
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            msg, color = Color.White, fontSize = 16.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xE0222C45)).padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
