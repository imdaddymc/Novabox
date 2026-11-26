package com.novabox.tv.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.novabox.tv.data.AppGraph
import com.novabox.tv.data.Channel
import com.novabox.tv.data.EpgEntry
import com.novabox.tv.data.PlayRequest
import com.novabox.tv.data.PlaybackHolder
import com.novabox.tv.ui.FocusSurface
import com.novabox.tv.ui.Logo
import com.novabox.tv.ui.NovaButton
import com.novabox.tv.ui.NovaTheme
import com.novabox.tv.ui.Palette
import com.novabox.tv.ui.ProgressBar
import com.novabox.tv.ui.RowGap
import com.novabox.tv.ui.currentProgramme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val PauseIcon = materialIcon(name = "Pause") {
    materialPath {
        moveTo(6f, 19f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(6f); close()
        moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); close()
    }
}

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val req = PlaybackHolder.request ?: run { finish(); return }

        val direct = (req as? PlayRequest.Live)?.direct == true || (req as? PlayRequest.Vod)?.direct == true
        val headers = if (direct) mapOf("User-Agent" to "NovaBox/${com.novabox.tv.BuildConfig.VERSION_NAME} (Android TV)")
            else AppGraph.client?.playbackHeaders().orEmpty()
        val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(true).build()
        val dataSource = OkHttpDataSource.Factory(http)
            .setUserAgent(headers["User-Agent"])
            .setDefaultRequestProperties(headers - "User-Agent")
        val buffer = AppGraph.settings.bufferSeconds * 1000
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(maxOf(15_000, buffer * 4), 50_000, buffer.coerceAtLeast(1000), buffer.coerceAtLeast(1500))
                    .build()
            )
            .build()
        player.playWhenReady = true

        setContent {
            NovaTheme {
                when (req) {
                    is PlayRequest.Live -> LivePlayer(req)
                    is PlayRequest.Vod -> VodPlayer(req)
                }
            }
        }
    }

    /** Bumped on every new stream so the resolution tag shows once per stream. */
    private var streamSerial by mutableIntStateOf(0)

    private fun play(url: String) {
        streamSerial++
        val item = MediaItem.Builder().setUri(url).apply {
            if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized && player.mediaItemCount > 0) player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
    }

    @Composable
    private fun Video(useController: Boolean, modifier: Modifier = Modifier, texture: Boolean = false) {
        AndroidView(
            factory = { ctx ->
                (if (texture) android.view.LayoutInflater.from(ctx).inflate(com.novabox.tv.R.layout.player_texture, null) as PlayerView else PlayerView(ctx)).apply {
                    player = this@PlayerActivity.player
                    this.useController = useController
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                    if (useController) {
                        setShowNextButton(false); setShowPreviousButton(false)
                        controllerShowTimeoutMs = 4000
                        isFocusable = true
                        post { requestFocus() }
                    } else {
                        isFocusable = false
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
        )
    }

    /** A small "1080p" tag in the top-right corner that fades out 3 seconds after a stream starts. */
    @Composable
    private fun ResolutionBadge(modifier: Modifier = Modifier) {
        var label by remember { mutableStateOf("") }
        var shows by remember { mutableIntStateOf(0) }
        var visible by remember { mutableStateOf(false) }
        val serial = streamSerial
        DisposableEffect(serial) {
            var shown = false
            val l = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (!shown && videoSize.width > 0 && videoSize.height > 0) {
                        shown = true
                        label = resolutionLabel(videoSize.width, videoSize.height)
                        shows++
                    }
                }
            }
            player.addListener(l)
            onDispose { player.removeListener(l) }
        }
        LaunchedEffect(shows) {
            if (shows == 0) return@LaunchedEffect
            visible = true
            delay(3000)
            visible = false
        }
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(tween(700)), modifier = modifier.padding(top = 28.dp, end = 32.dp)) {
            Text(
                label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.background(Color(0x8C000000), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }

    @Composable
    private fun usePlayerError(onError: (String) -> Unit) {
        DisposableEffect(Unit) {
            val l = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { onError(error.errorCodeName) }
            }
            player.addListener(l)
            onDispose { player.removeListener(l) }
        }
    }

    // ---------------------------------------------------------------- VOD

    @Composable
    private fun VodPlayer(req: PlayRequest.Vod) {
        var status by remember { mutableStateOf<String?>("Loading…") }
        var controls by remember { mutableStateOf(true) }
        var hideAt by remember { mutableLongStateOf(System.currentTimeMillis() + 5000) }
        var playing by remember { mutableStateOf(true) }
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        val rootFocus = remember { FocusRequester() }

        LaunchedEffect(req) {
            try {
                if (req.direct) play(req.cmd)
                else play((AppGraph.client ?: error("Not connected")).vodLink(req.cmd, req.episode))
                status = null
            } catch (e: Exception) {
                status = "Couldn't start playback: ${e.message}"
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.takeIf { it > 0 } ?: 0
                playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
                if (controls && System.currentTimeMillis() > hideAt && player.isPlaying) controls = false
                delay(500)
            }
        }
        LaunchedEffect(controls) { if (!controls) runCatching { rootFocus.requestFocus() } }
        usePlayerError { status = "This video couldn't play: ${streamProblem(it)}. Press Back to return." }

        fun poke() { controls = true; hideAt = System.currentTimeMillis() + 5000 }
        fun seek(deltaMs: Long) {
            val max = if (duration > 0) duration else Long.MAX_VALUE
            player.seekTo((player.currentPosition + deltaMs).coerceIn(0, max))
            position = player.currentPosition
            poke()
        }
        fun toggle() { if (player.isPlaying) player.pause() else player.play(); playing = !playing; poke() }

        BackHandler(enabled = controls) { controls = false }

        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.MediaPlayPause -> { toggle(); true }
                        Key.MediaPlay -> { player.play(); poke(); true }
                        Key.MediaPause -> { player.pause(); poke(); true }
                        Key.MediaFastForward -> { seek(30_000); true }
                        Key.MediaRewind -> { seek(-10_000); true }
                        else -> { if (controls) hideAt = System.currentTimeMillis() + 5000; false }
                    }
                }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || controls) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { seek(-10_000); true }
                        Key.DirectionRight -> { seek(30_000); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown, Key.Menu -> { poke(); true }
                        else -> false
                    }
                }
        ) {
            Video(useController = false, texture = true)
            ResolutionBadge(Modifier.align(Alignment.TopEnd))
            status?.let {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(req.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (req.subtitle.isNotBlank()) Text(req.subtitle, color = Palette.muted)
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Palette.muted)
                }
            }
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                VodControls(req, playing, position, duration,
                    onBack10 = { seek(-10_000) }, onToggle = { toggle() }, onFwd30 = { seek(30_000) }, onExit = { finish() })
            }
        }
    }

    @Composable
    private fun VodControls(
        req: PlayRequest.Vod, playing: Boolean, position: Long, duration: Long,
        onBack10: () -> Unit, onToggle: () -> Unit, onFwd30: () -> Unit, onExit: () -> Unit,
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(50); runCatching { first.requestFocus() } }
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 56.dp, end = 56.dp, top = 80.dp, bottom = 36.dp)
        ) {
            Text(req.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (req.subtitle.isNotBlank()) Text(req.subtitle, fontSize = 16.sp, color = Palette.muted)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), fontSize = 14.sp, color = Color.White, modifier = Modifier.width(80.dp))
                ProgressBar(if (duration > 0) position.toFloat() / duration else 0f, Modifier.weight(1f))
                Text(if (duration > 0) formatTime(duration) else "--:--", fontSize = 14.sp, color = Palette.muted,
                    modifier = Modifier.width(80.dp).padding(start = 12.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = RowGap, verticalAlignment = Alignment.CenterVertically) {
                NovaButton("10s", onBack10, icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                NovaButton(if (playing) "Pause" else "Play", onToggle, Modifier.focusRequester(first),
                    icon = if (playing) PauseIcon else Icons.Filled.PlayArrow, primary = true)
                NovaButton("30s", onFwd30, icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
                NovaButton("Exit player", onExit, icon = Icons.AutoMirrored.Filled.ArrowBack)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val t = ms / 1000
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    // ---------------------------------------------------------------- live

    @Composable
    private fun LivePlayer(req: PlayRequest.Live) {
        val channels = req.channels
        var index by remember { mutableIntStateOf(req.index.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
        var showMenu by remember { mutableStateOf(false) }
        var showList by remember { mutableStateOf(false) }
        var bannerUntil by remember { mutableLongStateOf(System.currentTimeMillis() + 5000) }
        var showBanner by remember { mutableStateOf(true) }
        var status by remember { mutableStateOf<String?>(null) }
        var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
        val favs by AppGraph.favorites.channels.collectAsState()
        val scope = rememberCoroutineScope()
        val rootFocus = remember { FocusRequester() }
        val channel = channels.getOrNull(index)
        var retries by remember { mutableIntStateOf(0) }

        LaunchedEffect(index) {
            val c = channel ?: return@LaunchedEffect
            status = "Tuning…"
            retries = 0
            epg = emptyList()
            showBanner = true
            bannerUntil = System.currentTimeMillis() + 5000
            if (req.direct) {
                play(c.cmd)
                status = null
                return@LaunchedEffect
            }
            AppGraph.settings.lastChannelId = c.id
            try {
                val client = AppGraph.client ?: error("Not connected")
                play(client.liveLink(c))
                status = null
                epg = runCatching { client.shortEpg(c.id, 3) }.getOrDefault(emptyList())
            } catch (e: Exception) {
                status = "Couldn't tune: ${e.message}"
            }
        }
        LaunchedEffect(bannerUntil) {
            delay((bannerUntil - System.currentTimeMillis()).coerceAtLeast(0))
            showBanner = false
        }
        LaunchedEffect(showMenu, showList) {
            if (!showMenu && !showList) runCatching { rootFocus.requestFocus() }
        }
        usePlayerError { code ->
            if (retries >= (if (req.direct) 1 else 3)) {
                status = "This channel isn't playing right now: ${streamProblem(code)}. Press ▲ or ▼ for another channel."
                return@usePlayerError
            }
            retries++
            status = "The stream stopped. Trying again…"
            scope.launch {
                delay(1500)
                channel?.let { c ->
                    if (req.direct) { play(c.cmd); status = null }
                    else runCatching { AppGraph.client?.liveLink(c) }.getOrNull()?.let { play(it); status = null }
                }
            }
        }

        fun zap(delta: Int) {
            if (channels.isEmpty()) return
            index = (index + delta + channels.size) % channels.size
        }

        BackHandler(enabled = showMenu || showList) { showMenu = false; showList = false }

        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .focusRequester(rootFocus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || showMenu || showList) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionUp, Key.ChannelUp -> { zap(+1); true }
                        Key.DirectionDown, Key.ChannelDown -> { zap(-1); true }
                        Key.DirectionLeft -> { showList = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu, Key.DirectionRight -> { showMenu = true; true }
                        Key.Info -> { showBanner = true; bannerUntil = System.currentTimeMillis() + 5000; true }
                        else -> false
                    }
                }
        ) {
            Video(useController = false)
            ResolutionBadge(Modifier.align(Alignment.TopEnd))

            status?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(18.dp))
            }

            AnimatedVisibility(showBanner || showMenu, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                if (channel != null) InfoBar(channel, epg, favs.any { it.id == channel.id }, showMenu, canFavourite = !req.direct,
                    onFav = { AppGraph.favorites.toggle(channel) },
                    onList = { showMenu = false; showList = true },
                    onPrev = { zap(-1) }, onNext = { zap(+1) },
                    onClose = { finish() },
                )
            }

            AnimatedVisibility(
                showList,
                enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ChannelDrawer(channels, index, favs.map { it.id }.toSet()) { i -> index = i; showList = false }
            }
        }
    }

    @Composable
    private fun InfoBar(
        c: Channel, epg: List<EpgEntry>, isFav: Boolean, menu: Boolean, canFavourite: Boolean,
        onFav: () -> Unit, onList: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit,
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(menu) { if (menu) runCatching { first.requestFocus() } }
        val now = epg.currentProgramme()
        val next = epg.getOrNull(epg.indexOf(now) + 1)
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 56.dp, end = 56.dp, top = 60.dp, bottom = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(c.logo, c.name, 64.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.number}", color = Palette.teal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isFav) Text("  ★", color = Palette.gold, fontSize = 22.sp)
                    }
                    if (c.note.isNotBlank()) Text(c.note, fontSize = 14.sp, color = Palette.muted)
                    if (now != null) {
                        Text("Now: ${now.title}", fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ProgressBar(now.progressSafe(), Modifier.width(420.dp).padding(vertical = 6.dp))
                    }
                    if (next != null) Text("Next: ${next.title}", fontSize = 14.sp, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (menu) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = RowGap) {
                    if (canFavourite) NovaButton(if (isFav) "Remove favourite" else "Favourite", onFav, Modifier.focusRequester(first), icon = Icons.Filled.Star, tint = if (isFav) Palette.gold else null)
                    NovaButton("Channel list", onList, if (canFavourite) Modifier else Modifier.focusRequester(first), icon = Icons.AutoMirrored.Filled.List)
                    NovaButton("Previous", onPrev, icon = Icons.Filled.KeyboardArrowDown)
                    NovaButton("Next", onNext, icon = Icons.Filled.KeyboardArrowUp)
                    NovaButton("Exit player", onClose, icon = Icons.AutoMirrored.Filled.ArrowBack)
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text("OK: menu   ·   ▲▼: change channel   ·   ◀: channel list", fontSize = 13.sp, color = Palette.muted)
            }
        }
    }

    @Composable
    private fun ChannelDrawer(channels: List<Channel>, current: Int, favIds: Set<String>, onPick: (Int) -> Unit) {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 4).coerceAtLeast(0))
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(50); runCatching { fr.requestFocus() } }
        Column(Modifier.width(440.dp).fillMaxHeight().background(Color(0xF00A0E18)).padding(20.dp)) {
            Text("Channels", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                    FocusSurface(
                        onClick = { onPick(i) }, selected = i == current, focusedScale = 1.02f,
                        modifier = Modifier.fillMaxWidth().then(if (i == current) Modifier.focusRequester(fr) else Modifier),
                    ) { _ ->
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${c.number}", Modifier.width(44.dp), fontSize = 13.sp)
                            Logo(c.logo, c.name, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            if (c.id in favIds) Text("★", color = Palette.gold)
                        }
                    }
                }
            }
        }
    }
}

private fun EpgEntry.progressSafe(): Float {
    val now = System.currentTimeMillis()
    return if (end > start && start > 0) ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f
}


/** "4K", "1080p", "720p"… judged mainly by width so letterboxed films (1920×800) still read as 1080p. */
internal fun resolutionLabel(width: Int, height: Int): String {
    val w = maxOf(width, height); val h = minOf(width, height)
    return when {
        w >= 3800 || h >= 2100 -> "4K"
        w >= 2500 || h >= 1400 -> "1440p"
        w >= 1900 || h >= 1060 -> "1080p"
        w >= 1260 || h >= 700 -> "720p"
        else -> "${h}p"
    }
}

/** Plain words for a Media3 error code name, for the on-screen message. */
internal fun streamProblem(code: String): String = when {
    "BAD_HTTP_STATUS" in code || "FILE_NOT_FOUND" in code -> "the stream is offline"
    "NETWORK" in code || "TIMEOUT" in code -> "it couldn't be reached"
    "PARSING" in code || "DECODER" in code || "DECODING" in code || "UNSUPPORTED" in code -> "its format isn't supported on this TV"
    "DRM" in code -> "it's copy-protected"
    else -> "the stream failed"
}
