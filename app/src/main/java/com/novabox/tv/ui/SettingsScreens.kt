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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.Profile

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val profiles by AppGraph.profiles.profiles.collectAsState()
    var confirmDelete by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(vm.activeProfile?.id) { if (vm.account == null) vm.loadAccount() }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SectionTitle("PORTAL PROFILES")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                items(profiles, key = { it.id }) { p ->
                    val active = p.id == vm.activeProfile?.id
                    Column(
                        Modifier.fillMaxWidth().background(if (active) Palette.accent.copy(alpha = 0.15f) else Palette.bg2, RoundedCornerShape(14.dp)).padding(18.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                            Text(p.name.ifBlank { "Unnamed profile" } + if (active) "  · active" else "", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(p.portalUrl, fontSize = 13.sp, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("MAC ${p.mac} · ${p.stbModel}", fontSize = 13.sp, color = Palette.muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NovaButton(if (active) "Reconnect" else "Connect", { vm.connect(p) }, icon = if (active) Icons.Filled.Refresh else Icons.Filled.Check, primary = !active)
                            NovaButton("Edit", { vm.push(Route.EditProfile(p)) }, icon = Icons.Filled.Edit)
                            NovaButton("Delete", { confirmDelete = p }, icon = Icons.Filled.Delete)
                        }
                    }
                }
                item {
                    NovaButton("Add profile", { vm.push(Route.EditProfile(null)) }, icon = Icons.Filled.Add, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Column(Modifier.width(360.dp).fillMaxHeight().background(Palette.bg2, RoundedCornerShape(16.dp)).padding(22.dp)) {
            SectionTitle("ACCOUNT")
            val acc = vm.account
            if (acc == null) Text("No account details from this portal", color = Palette.muted, fontSize = 14.sp)
            else acc.fields.take(10).forEach { (k, v) ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(k, color = Palette.muted, fontSize = 14.sp, modifier = Modifier.width(140.dp))
                    Text(v, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("NETWORK")
            NovaButton("Run network check", { vm.push(Route.NetworkCheck) }, icon = Icons.Filled.Refresh)
            Spacer(Modifier.height(20.dp))
            SectionTitle("DEVICE")
            vm.client?.let {
                Text("Serial: ${it.serial}", fontSize = 13.sp, color = Palette.muted)
            }
            Text("Channels loaded: ${vm.channels.size}", fontSize = 13.sp, color = Palette.muted)
            Spacer(Modifier.height(20.dp))
            SectionTitle("CONTROLS")
            Text(
                "D-pad, OK and Back do everything. Player: OK menu, ▲▼ channel, ◀ list.",
                fontSize = 13.sp, color = Palette.muted,
            )
            Spacer(Modifier.height(20.dp))
            Text("NovaBox 1.0.0", fontSize = 12.sp, color = Palette.muted)
        }
    }

    confirmDelete?.let { p ->
        ConfirmDialog("Delete “${p.name.ifBlank { p.portalUrl }}”?", onYes = { vm.deleteProfile(p); confirmDelete = null }, onNo = { confirmDelete = null })
    }
}

@Composable
fun ConfirmDialog(title: String, onYes: () -> Unit, onNo: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onNo) {
        Column(Modifier.background(Palette.bg2, RoundedCornerShape(18.dp)).padding(28.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = RowGap) {
                NovaButton("Cancel", onNo, Modifier.focusRequester(fr))
                NovaButton("Delete", onYes, primary = true)
            }
        }
    }
}

// ------------------------------------------------------------------ profile editor

private data class Field(val label: String, val get: (Profile) -> String, val set: (Profile, String) -> Profile, val hint: String = "", val password: Boolean = false, val type: KeyboardType = KeyboardType.Text)

private val fields = listOf(
    Field("Profile name", { it.name }, { p, v -> p.copy(name = v) }, "e.g. Living room"),
    Field("Portal URL", { it.portalUrl }, { p, v -> p.copy(portalUrl = v.trim()) }, "http://example.com:8080/c/", type = KeyboardType.Uri),
    Field("MAC address", { it.mac }, { p, v -> p.copy(mac = v.trim().uppercase()) }, "00:1A:79:XX:XX:XX"),
    Field("Serial number", { it.serial }, { p, v -> p.copy(serial = v.trim()) }, "Leave empty to generate"),
    Field("Device ID", { it.deviceId }, { p, v -> p.copy(deviceId = v.trim()) }, "Leave empty to generate"),
    Field("Device ID 2", { it.deviceId2 }, { p, v -> p.copy(deviceId2 = v.trim()) }, "Leave empty to copy Device ID"),
    Field("Signature", { it.signature }, { p, v -> p.copy(signature = v.trim()) }, "Leave empty to generate"),
    Field("Portal login", { it.login }, { p, v -> p.copy(login = v) }, "Only if your portal asks for one"),
    Field("Portal password", { it.password }, { p, v -> p.copy(password = v) }, "Only if your portal asks for one", password = true),
    Field("Time zone", { it.timezone }, { p, v -> p.copy(timezone = v.trim()) }, "e.g. Europe/London"),
    Field("Custom user agent", { it.userAgent }, { p, v -> p.copy(userAgent = v) }, "Leave empty for the MAG default"),
)

@Composable
fun ProfileEditScreen(vm: AppViewModel, initial: Profile?, firstRun: Boolean) {
    var p by remember { mutableStateOf(initial ?: Profile(name = "My portal")) }
    var editing by remember { mutableStateOf<Field?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun validate(): Boolean {
        error = when {
            p.portalUrl.isBlank() -> "Enter the portal URL"
            !Profile.isValidMac(p.mac) -> "The MAC address should look like 00:1A:79:12:34:56"
            else -> null
        }
        return error == null
    }

    Row(Modifier.fillMaxSize().padding(56.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(360.dp)) {
            Text(if (firstRun) "Welcome to NovaBox" else if (initial == null) "New profile" else "Edit profile", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Enter the portal URL and MAC address your IPTV provider gave you. Use the D-pad to pick a field and press OK to type.",
                color = Palette.muted, fontSize = 15.sp,
            )
            Spacer(Modifier.height(24.dp))
            error?.let { Text(it, color = Palette.danger, fontSize = 15.sp); Spacer(Modifier.height(12.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NovaButton("Save and connect", { if (validate()) vm.saveProfile(p, connectNow = true) }, primary = true, icon = Icons.Filled.Check)
                if (!firstRun) {
                    NovaButton("Save", { if (validate()) vm.saveProfile(p, connectNow = false) })
                    NovaButton("Cancel", { vm.pop() })
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
            items(fields.size) { i ->
                val f = fields[i]
                Column {
                FieldRow(f.label, f.get(p).let { if (f.password && it.isNotEmpty()) "••••••" else it }, f.hint, Modifier.then(if (i == 0) Modifier.focusRequester(first) else Modifier)) { editing = f }
                if (f.label == "MAC address") {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = RowGap) {
                        NovaButton("Random MAC", { p = p.copy(mac = Profile.randomMac()) })
                    }
                }
                }
            }
            item {
                Column {
                    SectionTitle("STB MODEL")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                        items(Profile.MODELS) { m ->
                            FocusSurface(onClick = { p = p.copy(stbModel = m) }, selected = p.stbModel == m, shape = RoundedCornerShape(50)) { _ ->
                                Text(m, Modifier.padding(horizontal = 16.dp, vertical = 9.dp), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { f ->
        TextInputDialog(
            f.label, f.get(p),
            onValue = { p = f.set(p, it) },
            onDismiss = { editing = null },
            password = f.password, keyboardType = f.type,
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.02f) { focused ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, modifier = Modifier.width(200.dp), color = if (focused) Color(0xFF39425A) else Palette.muted)
            Text(
                value.ifBlank { hint }, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (value.isBlank()) (if (focused) Color(0xFF6A7390) else Palette.muted.copy(alpha = 0.6f)) else Color.Unspecified,
                fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
            )
        }
    }
}
