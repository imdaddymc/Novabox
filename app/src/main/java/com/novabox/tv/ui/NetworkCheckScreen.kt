package com.novabox.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.novabox.tv.data.CheckResult
import com.novabox.tv.data.CheckState

@Composable
fun NetworkCheckScreen(vm: AppViewModel) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        vm.runNetworkCheck()
        runCatching { fr.requestFocus() }
    }
    val checks = vm.netChecks
    val done = !vm.netRunning && checks.isNotEmpty()
    val fails = checks.count { it.state == CheckState.FAIL }
    val warns = checks.count { it.state == CheckState.WARN }

    Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
        Column(Modifier.width(330.dp)) {
            Text("Network check", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Tests your connection step by step, from the TV's network to your portal and a live channel.",
                color = Palette.muted, fontSize = 15.sp,
            )
            Spacer(Modifier.height(28.dp))
            val (headline, colour) = when {
                !done -> "Testing…" to Palette.accent
                fails > 0 -> "$fails problem${if (fails > 1) "s" else ""} found" to Palette.danger
                warns > 0 -> "Working, with $warns warning${if (warns > 1) "s" else ""}" to Palette.gold
                else -> "Everything looks good" to Palette.teal
            }
            Text(headline, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colour)
            if (done) {
                Spacer(Modifier.height(6.dp))
                Text(advice(checks), color = Palette.muted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NovaButton(if (vm.netRunning) "Testing…" else "Run again", { vm.runNetworkCheck() }, Modifier.focusRequester(fr), icon = Icons.Filled.Refresh, primary = true)
                NovaButton("Back", { vm.pop() }, icon = Icons.AutoMirrored.Filled.ArrowBack)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            checks.forEachIndexed { i, c -> CheckRow(i + 1, c) }
        }
    }
}

@Composable
private fun CheckRow(n: Int, c: CheckResult) {
    val (mark, colour) = when (c.state) {
        CheckState.OK -> "✓" to Palette.teal
        CheckState.WARN -> "!" to Palette.gold
        CheckState.FAIL -> "✕" to Palette.danger
        CheckState.RUNNING -> "…" to Palette.accent
        CheckState.SKIPPED -> "–" to Palette.muted
        CheckState.PENDING -> "$n" to Palette.muted
    }
    Row(
        Modifier.fillMaxWidth().background(Palette.bg2, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(colour.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Text(mark, color = colour, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (c.state == CheckState.PENDING) Palette.muted else Color.Unspecified)
            if (c.detail.isNotBlank()) {
                Text(c.detail, fontSize = 13.sp, color = if (c.state == CheckState.FAIL) Palette.danger.copy(alpha = 0.9f) else Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun advice(checks: List<CheckResult>): String {
    fun failed(i: Int) = checks.getOrNull(i)?.state == CheckState.FAIL
    return when {
        failed(0) -> "The TV isn't on a network. Reconnect Wi-Fi or plug in the cable."
        failed(1) -> "The TV is on your network but can't reach the internet. Restart your router."
        failed(3) -> "The portal address can't be found. Check the URL in your profile, or ask your provider."
        failed(4) -> "The portal server isn't answering. It may be down; try again later or ask your provider."
        failed(5) -> "The portal rejected the login. Check the MAC address and that your subscription is active."
        failed(6) -> "Logged in, but the stream didn't play. Try another channel or ask your provider."
        failed(2) -> "Your connection is slow. Use a cable or move closer to the router for HD."
        checks.any { it.state == CheckState.WARN } -> "It should work, but you may see some buffering."
        else -> "Your network and portal are working normally."
    }
}
