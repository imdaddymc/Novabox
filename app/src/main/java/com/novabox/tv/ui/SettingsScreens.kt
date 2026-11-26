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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val profiles by AppGraph.profiles.profiles.collectAsState()
    var confirmDelete by remember { mutableStateOf<Profile?>(null) }
    var vpnNote by remember { mutableStateOf<String?>(null) }
    var pickVpn by remember { mutableStateOf(false) }
    vpnNote?.let { InfoDialog(it) { vpnNote = null } }
    if (pickVpn) VpnPickerDialog(vm) { pickVpn = false }
    val addFocus = remember { FocusRequester() }
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
                    NovaButton("Add profile", { vm.push(Route.EditProfile(null)) }, icon = Icons.Filled.Add, modifier = Modifier.padding(top = 6.dp).focusRequester(addFocus))
                }
            }
        }
        Column(
            Modifier.width(360.dp).fillMaxHeight().background(Palette.bg2, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState()).padding(22.dp)
        ) {
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
            UpdatesSection(vm)
            Spacer(Modifier.height(20.dp))
            SectionTitle("DEVELOPER TOOLS")
            Text(
                if (vm.devTools) "Unlocked. The Debrid tab (wrench icon) plays films and episodes from your own Real-Debrid account."
                else "Unlock to add a Debrid tab for playing films and episodes from your own Real-Debrid account.",
                fontSize = 13.sp, color = Palette.muted,
            )
            Spacer(Modifier.height(10.dp))
            // One button whose label flips, so focus stays put when pressed.
            NovaButton(
                if (vm.devTools) "Hide developer tools" else "Unlock developer tools",
                { vm.setDeveloperTools(!vm.devTools) },
                icon = Icons.Filled.Build,
            )
            if (vm.devTools) {
                Spacer(Modifier.height(20.dp))
                SectionTitle("VPN APP")
                LaunchedEffect(Unit) { while (true) { vm.refreshVpn(); kotlinx.coroutines.delay(5000) } }
                Text(
                    when {
                        vm.vpnApp == null -> "Optional. Pick the VPN app you use (like Surfshark) and NovaBox can open it for you. Debrid works with or without a VPN."
                        vm.vpnOn -> "${vm.vpnApp!!.name} · a VPN is on."
                        else -> "${vm.vpnApp!!.name} · no VPN is on right now."
                    },
                    fontSize = 13.sp, color = if (vm.vpnApp != null && vm.vpnOn) Palette.teal else Palette.muted,
                )
                Spacer(Modifier.height(10.dp))
                vm.vpnApp?.let { a ->
                    NovaButton("Open ${a.name}", { vpnNote = vm.openVpnApp() }, icon = Icons.Filled.Lock)
                    Spacer(Modifier.height(8.dp))
                }
                NovaButton(if (vm.vpnApp == null) "Choose VPN app" else "Change VPN app", { pickVpn = true }, icon = Icons.Filled.Edit)
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("DEVICE")
            vm.client?.let {
                Text("Serial: ${it.serial}", fontSize = 13.sp, color = Palette.muted)
            }
            Text("Channels loaded: ${vm.channels.size}", fontSize = 13.sp, color = Palette.muted)
        }
    }

    confirmDelete?.let { p ->
        ConfirmDialog("Delete “${p.name.ifBlank { p.portalUrl }}”?", onYes = {
            vm.deleteProfile(p); confirmDelete = null
            vm.viewModelScope.launch { kotlinx.coroutines.delay(150); runCatching { addFocus.requestFocus() } }
        }, onNo = { confirmDelete = null })
    }
}

@Composable
private fun UpdatesSection(vm: AppViewModel) {
    val installError by com.novabox.tv.data.Updater.installError.collectAsState()
    LaunchedEffect(installError) { installError?.let { vm.updateInstallFailed(it); com.novabox.tv.data.Updater.installError.value = null } }
    SectionTitle("APP UPDATES")
    Text("NovaBox ${com.novabox.tv.data.Updater.currentVersion}", fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    val s = vm.update
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // After the user allows installs in Android's screen and comes back, carry on by itself.
    LaunchedEffect(s) {
        if (s is AppViewModel.UpdateState.NeedsPermission) {
            while (!com.novabox.tv.data.Updater.canInstall(ctx)) kotlinx.coroutines.delay(1000)
            vm.installUpdate(s.info)
        }
    }
    val line = when (s) {
        AppViewModel.UpdateState.Idle -> "Your profiles and favourites are kept when you update."
        AppViewModel.UpdateState.Checking -> "Checking…"
        AppViewModel.UpdateState.UpToDate -> "You have the latest version."
        is AppViewModel.UpdateState.Available -> "Version ${s.info.versionName} is available." + (if (s.info.notes.isNotBlank()) " ${s.info.notes}" else "")
        is AppViewModel.UpdateState.NeedsPermission -> "Allow NovaBox to install apps in the screen that opened, then press Back. The update continues by itself."
        is AppViewModel.UpdateState.Downloading -> if (s.progress >= 0) "Downloading… ${(s.progress * 100).toInt()}%" else "Downloading…"
        is AppViewModel.UpdateState.Installing -> "Choose \"Update\" on the screen Android shows. NovaBox then closes to update; open it again from your apps. Your profiles and favourites are kept."
        is AppViewModel.UpdateState.Failed -> s.message
    }
    Text(line, fontSize = 13.sp, color = if (s is AppViewModel.UpdateState.Failed) Palette.danger else Palette.muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
    if (s is AppViewModel.UpdateState.Downloading && s.progress >= 0) ProgressBar(s.progress, Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(10.dp))
    // One button whose label changes, rather than a different button per state, so the
    // remote's focus stays on it while the state moves along.
    val (label, action) = when (s) {
        is AppViewModel.UpdateState.Available -> "Download and install" to { vm.installUpdate(s.info) }
        is AppViewModel.UpdateState.NeedsPermission -> "Download and install" to { vm.installUpdate(s.info) }
        is AppViewModel.UpdateState.Failed ->
            if (s.info != null) "Try again" to { vm.installUpdate(s.info) } else "Check for updates" to { vm.checkForUpdates() }
        AppViewModel.UpdateState.Checking -> "Checking…" to {}
        is AppViewModel.UpdateState.Downloading -> "Downloading…" to {}
        is AppViewModel.UpdateState.Installing -> "Install again" to { vm.installUpdate(s.info) }
        else -> "Check for updates" to { vm.checkForUpdates() }
    }
    val ready = s is AppViewModel.UpdateState.Available || s is AppViewModel.UpdateState.NeedsPermission
    NovaButton(label, action, icon = if (ready) Icons.Filled.Check else Icons.Filled.Refresh, primary = ready)
    Spacer(Modifier.height(10.dp))
    // One button whose label flips, so focus stays on it.
    NovaButton(
        if (vm.autoUpdate) "Automatic updates: On" else "Automatic updates: Off",
        { vm.setAutoUpdates(!vm.autoUpdate) },
        icon = if (vm.autoUpdate) Icons.Filled.Check else Icons.Filled.Close,
    )
    Text(
        if (vm.autoUpdate) "Checks once a day and downloads new versions. They install when the TV next starts up."
        else "Turn on to check once a day and install new versions when the TV starts up.",
        fontSize = 12.sp, color = Palette.muted, modifier = Modifier.padding(top = 6.dp),
    )
}

/** Shown when NovaBox opens and the daily check has an update downloaded and waiting. */
@Composable
fun UpdateReadyDialog(vm: AppViewModel) {
    val version = vm.readyToInstall ?: return
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); runCatching { fr.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.postponeReadyUpdate() }) {
        Column(Modifier.width(520.dp).background(Palette.bg2, RoundedCornerShape(18.dp)).padding(28.dp)) {
            Text("NovaBox $version is ready", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("It downloaded automatically. Install it now? NovaBox closes to update; your profiles and favourites are kept.", fontSize = 14.sp, color = Palette.muted)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = RowGap) {
                NovaButton("Install now", { vm.installReadyUpdate() }, Modifier.focusRequester(fr), icon = Icons.Filled.Check, primary = true)
                NovaButton("Later", { vm.postponeReadyUpdate() })
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, onYes: () -> Unit, onNo: () -> Unit, yesLabel: String = "Delete") {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onNo) {
        Column(Modifier.background(Palette.bg2, RoundedCornerShape(18.dp)).padding(28.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = RowGap) {
                NovaButton("Cancel", onNo, Modifier.focusRequester(fr))
                NovaButton(yesLabel, onYes, primary = true)
            }
        }
    }
}

@Composable
fun InfoDialog(message: String, onDismiss: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(Palette.bg2, RoundedCornerShape(18.dp)).padding(28.dp).width(460.dp)) {
            Text(message, fontSize = 17.sp)
            Spacer(Modifier.height(20.dp))
            NovaButton("OK", onDismiss, Modifier.focusRequester(fr), primary = true)
        }
    }
}

/** Lists the VPN apps on the TV; picking one saves it, "None" clears it. */
@Composable
fun VpnPickerDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    // Reading every app's services can take a moment on a TV with many apps, so do it off the main thread.
    val found by androidx.compose.runtime.produceState<List<com.novabox.tv.data.VpnApps.VpnApp>?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.installedVpnApps() }
    }
    val apps = found.orEmpty()
    val fr = remember { FocusRequester() }
    LaunchedEffect(found) { if (found != null) { kotlinx.coroutines.delay(50); runCatching { fr.requestFocus() } } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(Palette.bg2, RoundedCornerShape(18.dp)).padding(28.dp).width(460.dp)) {
            Text("Choose your VPN app", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (found == null) "Looking for VPN apps on this TV…"
                else if (apps.isEmpty()) "No VPN apps are installed on this TV. Install one (for example Surfshark) from the Google Play Store, then come back here."
                else "NovaBox will open it for you from Settings and the Debrid tab, so you can check it's connected.",
                fontSize = 14.sp, color = Palette.muted,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.size) { i ->
                    val a = apps[i]
                    NovaButton(a.name, { vm.chooseVpnApp(a); onDismiss() }, if (i == 0) Modifier.focusRequester(fr) else Modifier,
                        icon = if (vm.vpnApp?.pkg == a.pkg) Icons.Filled.Check else Icons.Filled.Lock)
                }
                item {
                    NovaButton(if (vm.vpnApp == null) "Close" else "Don't use a VPN app", { vm.chooseVpnApp(null); onDismiss() },
                        if (apps.isEmpty()) Modifier.focusRequester(fr) else Modifier, icon = Icons.Filled.Close)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ profile editor

private data class Field(val label: String, val get: (Profile) -> String, val set: (Profile, String) -> Profile, val hint: String = "", val password: Boolean = false, val type: KeyboardType = KeyboardType.Text)

private val fields = listOf(
    Field("Profile name", { it.name }, { p, v -> p.copy(name = v) }, "e.g. Living room"),
    Field("Portal URL", { it.portalUrl }, { p, v -> p.copy(portalUrl = v.trim()) }, "http://example.com:8080/c/", type = KeyboardType.Uri),
    Field("MAC address", { it.mac }, { p, v -> p.copy(mac = Profile.normalizeMac(v)) }, "00:1A:79:XX:XX:XX (colons optional)"),
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
                } else {
                    NovaButton("Skip and watch Free TV", { vm.openFreeTvOnly() })
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
