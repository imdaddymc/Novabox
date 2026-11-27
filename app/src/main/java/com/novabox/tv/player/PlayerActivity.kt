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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
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
import com.novabox.tv.data.SettingsStore
import com.novabox.tv.data.WatchEntry
import com.novabox.tv.ui.FocusSurface
import com.novabox.tv.ui.Logo
import com.novabox.tv.ui.NovaButton
import com.novabox.tv.ui.NovaTheme
import com.novabox.tv.ui.Palette
import com.novabox.tv.ui.ProgressBar
import com.novabox.tv.ui.RowGap
import com.novabox.tv.ui.currentProgramme
import com.novabox.tv.ui.onBackKey
import kotlinx.coroutines.async
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

/** A speaker: the audio track. */
private val AudioIcon = materialIcon(name = "Audio") {
    materialPath {
        moveTo(3f, 9f); verticalLineTo(15f); horizontalLineTo(7f); lineTo(12f, 20f); verticalLineTo(4f); lineTo(7f, 9f); close()
        moveTo(16.5f, 12f); curveTo(16.5f, 10.2f, 15.5f, 8.7f, 14f, 8f); verticalLineTo(16f); curveTo(15.5f, 15.3f, 16.5f, 13.8f, 16.5f, 12f); close()
    }
}

/** A box with two caption lines. */
private val SubtitlesIcon = materialIcon(name = "Subtitles") {
    materialPath {
        moveTo(20f, 4f); horizontalLineTo(4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f); verticalLineTo(18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
        horizontalLineTo(20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); verticalLineTo(6f); curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f); close()
        moveTo(4f, 12f); horizontalLineTo(8f); verticalLineTo(14f); horizontalLineTo(4f); close()
        moveTo(14f, 18f); horizontalLineTo(4f); verticalLineTo(16f); horizontalLineTo(14f); close()
        moveTo(20f, 18f); horizontalLineTo(16f); verticalLineTo(16f); horizontalLineTo(20f); close()
        moveTo(20f, 14f); horizontalLineTo(10f); verticalLineTo(12f); horizontalLineTo(20f); close()
    }
}

/** A circular arrow: start over / last channel. */
private val RestartIcon = materialIcon(name = "Restart") {
    materialPath {
        moveTo(12f, 5f); verticalLineTo(1f); lineTo(7f, 6f); lineTo(12f, 11f); verticalLineTo(7f)
        curveTo(15.31f, 7f, 18f, 9.69f, 18f, 13f); reflectiveCurveTo(15.31f, 19f, 12f, 19f); reflectiveCurveTo(6f, 16.31f, 6f, 13f)
        horizontalLineTo(4f); curveTo(4f, 17.42f, 7.58f, 21f, 12f, 21f); reflectiveCurveTo(20f, 17.42f, 20f, 13f); reflectiveCurveTo(16.42f, 5f, 12f, 5f); close()
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
        val headers = if (direct) mapOf("User-Agent" to com.novabox.tv.data.FreeTv.USER_AGENT)
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
        val settings = AppGraph.settings
        player.trackSelectionParameters = preferredTracks(player.trackSelectionParameters, settings.audioLang, settings.subtitleLang)
            .buildUpon().setForceHighestSupportedBitrate(settings.bestQuality)
            .apply { maxVideoSize(AppGraph.client?.profile?.resolution)?.let { (w, h) -> setMaxVideoSize(w, h) } }
            .build()
        if (settings.sharpen) player.setVideoEffects(listOf(SharpenEffect()))
        hdrSurface = settings.hdrOutput && displaySupportsHdr(this)
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                tracks = t
                if (settings.matchFrameRate) matchFrameRate(this@PlayerActivity, videoFrameRate(t))
            }
            override fun onPlayerError(error: PlaybackException) {
                // Some TVs can't run the sharpen filter: carry on without it rather than fail.
                if (sharpenOn && error.errorCode in setOf(PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED)) {
                    sharpenOn = false
                    player.setVideoEffects(emptyList())
                    player.prepare()
                }
            }
        })
        sharpenOn = settings.sharpen
        vodReq = req as? PlayRequest.Vod

        setContent {
            NovaTheme {
                when (req) {
                    is PlayRequest.Live -> LivePlayer(req)
                    is PlayRequest.Vod -> {
                        // Autoplay moves on to the next episode in place.
                        var current by remember { mutableStateOf(req) }
                        androidx.compose.runtime.key(current) { VodPlayer(current) { n -> vodReq = n; current = n } }
                    }
                }
            }
        }
    }

    private var sharpenOn = false
    /** Films use a SurfaceView instead of a TextureView so HDR reaches the TV. */
    private var hdrSurface = false

    /** The stream's audio and subtitle tracks, for the track panels. */
    private var tracks by mutableStateOf(Tracks.EMPTY)

    /** The film or episode playing, for saving where the user stopped. */
    private var vodReq: PlayRequest.Vod? = null

    /** Saves the position for "Continue watching". [ended]: the video played to the end. */
    private fun saveProgress(ended: Boolean = false) {
        val r = vodReq ?: return
        if (r.key.isBlank() || !::player.isInitialized) return
        val dur = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        val pos = if (ended) dur else player.currentPosition.coerceAtLeast(0)
        val prev = AppGraph.watch.get(r.key)
        // A few seconds in isn't worth remembering, unless it replaces an older position.
        if (!ended && pos < WatchEntry.MIN_RESUME_MS && prev == null) return
        AppGraph.watch.save(
            WatchEntry(r.key, r.title, r.subtitle, r.poster, r.cmd, r.episode, r.direct, pos, dur, 0, ended || prev?.watched == true, r.item)
        )
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
        saveProgress()
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
    private fun Video(useController: Boolean, modifier: Modifier = Modifier, texture: Boolean = false, liftSubtitles: Boolean = false) {
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
            // While the controls or the info bar are up, subtitles move above them instead of hiding behind.
            update = { it.subtitleView?.setBottomPaddingFraction(if (liftSubtitles) 0.36f else androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION) },
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
                override fun onPlayerError(error: PlaybackException) {
                    // Handled in onCreate: playback restarts without the sharpen filter.
                    if (error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED) return
                    onError(error.errorCodeName)
                }
            }
            player.addListener(l)
            onDispose { player.removeListener(l) }
        }
    }

    // ---------------------------------------------------------------- VOD

    @Composable
    private fun VodPlayer(req: PlayRequest.Vod, onNext: (PlayRequest.Vod) -> Unit) {
        var status by remember { mutableStateOf<String?>("Loading…") }
        var controls by remember { mutableStateOf(true) }
        var hideAt by remember { mutableLongStateOf(System.currentTimeMillis() + 5000) }
        var playing by remember { mutableStateOf(true) }
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        val rootFocus = remember { FocusRequester() }
        var panel by remember { mutableStateOf<Int?>(null) }
        // Where playback resumed, for the "Start over" button; 0 when it started at the beginning.
        var resumedAt by remember { mutableLongStateOf(0L) }
        // Autoplay (Real-Debrid shows): the next episode is looked up near the end, then offered
        // with a countdown once this one finishes.
        val scope = rememberCoroutineScope()
        var nextLookup by remember { mutableStateOf<kotlinx.coroutines.Deferred<PlayRequest.Vod?>?>(null) }
        var ended by remember { mutableStateOf(false) }
        var upNext by remember { mutableStateOf<PlayRequest.Vod?>(null) }
        fun lookUpNext() = nextLookup ?: req.next?.let { find -> scope.async { runCatching { find(req.releaseName) }.getOrNull() }.also { nextLookup = it } }

        LaunchedEffect(req) {
            try {
                val saved = req.key.takeIf { it.isNotBlank() }?.let { AppGraph.watch.get(it) }
                val startAt = req.startAtMs ?: saved?.takeIf { it.inProgress }?.positionMs ?: 0L
                if (req.direct) play(req.cmd)
                else play((AppGraph.client ?: error("Not connected")).vodLink(req.cmd, req.episode))
                if (startAt > 0) { player.seekTo(startAt); resumedAt = startAt }
                status = null
                // Slow portals can take seconds to answer: count the 5 s from now, not from opening.
                hideAt = System.currentTimeMillis() + 5000
            } catch (e: Exception) {
                status = "Couldn't start playback: ${e.message}"
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.takeIf { it > 0 } ?: 0
                playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
                if (controls && panel == null && System.currentTimeMillis() > hideAt && player.isPlaying) controls = false
                if (req.next != null && nextLookup == null && duration > 0 && position >= duration - 120_000) lookUpNext()
                delay(500)
            }
        }
        LaunchedEffect(Unit) {
            while (true) { delay(10_000); if (player.isPlaying) saveProgress() }
        }
        DisposableEffect(Unit) {
            val l = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        saveProgress(ended = true)
                        if (req.next == null) finish() else ended = true
                    }
                }
            }
            player.addListener(l)
            onDispose { player.removeListener(l) }
        }
        LaunchedEffect(controls) { if (!controls) runCatching { rootFocus.requestFocus() } }
        LaunchedEffect(ended) {
            if (!ended) return@LaunchedEffect
            status = "Looking for the next episode…"
            controls = false
            val n = lookUpNext()?.await()
            if (n == null) finish() else { status = null; upNext = n }
        }
        usePlayerError { status = "This video couldn't play: ${streamProblem(it)}. Press Back to return." }

        fun poke() { controls = true; hideAt = System.currentTimeMillis() + 5000 }
        fun seek(deltaMs: Long) {
            val max = if (duration > 0) duration else Long.MAX_VALUE
            player.seekTo((player.currentPosition + deltaMs).coerceIn(0, max))
            position = player.currentPosition
            poke()
        }
        fun toggle() { if (player.isPlaying) player.pause() else player.play(); playing = !playing; poke() }

        BackHandler(enabled = controls && panel == null) { controls = false }

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
                    if (e.type != KeyEventType.KeyDown || controls || panel != null) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { seek(-10_000); true }
                        Key.DirectionRight -> { seek(30_000); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown, Key.Menu -> { poke(); true }
                        else -> false
                    }
                }
        ) {
            Video(useController = false, texture = !hdrSurface, liftSubtitles = controls)
            ResolutionBadge(Modifier.align(Alignment.TopEnd))
            status?.let {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(req.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (req.subtitle.isNotBlank()) Text(req.subtitle, color = Palette.muted)
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Palette.muted)
                }
            }
            AnimatedVisibility(controls && panel == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                VodControls(req, playing, position, duration, resumedAt,
                    onBack10 = { seek(-10_000) }, onToggle = { toggle() }, onFwd30 = { seek(30_000) },
                    onStartOver = { player.seekTo(0); position = 0; resumedAt = 0; poke() },
                    onTracks = { panel = it }, onExit = { finish() })
            }
            TrackPanelHost(panel, Modifier.align(Alignment.CenterEnd)) { panel = null; poke() }
            upNext?.let { n ->
                UpNext(n, Modifier.align(Alignment.BottomEnd), onPlay = { onNext(n) }, onCancel = { finish() })
            }
        }
    }

    /** "Up next" with a 10-second countdown; OK plays now, Back or Cancel leaves the player. */
    @Composable
    private fun UpNext(next: PlayRequest.Vod, modifier: Modifier, onPlay: () -> Unit, onCancel: () -> Unit) {
        var left by remember { mutableIntStateOf(10) }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(60); runCatching { first.requestFocus() }
            while (left > 0) { delay(1000); left-- }
            onPlay()
        }
        Column(
            modifier.padding(40.dp).width(420.dp).background(Color(0xF00A0E18), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(22.dp).onBackKey(onCancel)
        ) {
            Text("UP NEXT", fontSize = 12.sp, color = Palette.teal, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(next.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Starts in $left s", fontSize = 14.sp, color = Palette.muted)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = RowGap) {
                NovaButton("Play now", onPlay, Modifier.focusRequester(first), icon = Icons.Filled.PlayArrow, primary = true)
                NovaButton("Cancel", onCancel)
            }
        }
    }

    /** The Audio or Subtitles panel on the right, when [type] is set. */
    @Composable
    private fun TrackPanelHost(type: Int?, modifier: Modifier, onClose: () -> Unit) {
        BackHandler(enabled = type != null) { onClose() }
        AnimatedVisibility(
            type != null, modifier = modifier,
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            // Keep the last type while the panel slides out.
            var shown by remember { mutableIntStateOf(C.TRACK_TYPE_AUDIO) }
            if (type != null) shown = type
            TrackPanel(shown, onClose)
        }
    }

    @Composable
    private fun TrackPanel(type: Int, onClose: () -> Unit) {
        val options = trackOptions(tracks, type)
        val first = remember { FocusRequester() }
        LaunchedEffect(type) { delay(60); runCatching { first.requestFocus() } }
        val focusIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
        Column(
            Modifier.width(400.dp).fillMaxHeight().background(Color(0xF00A0E18)).padding(24.dp).onBackKey(onClose)
        ) {
            Text(if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitles", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (type == C.TRACK_TYPE_AUDIO) "NovaBox picks this language first next time." else "Your choice is used for the next videos too.",
                fontSize = 13.sp, color = Palette.muted,
            )
            Spacer(Modifier.height(14.dp))
            if (options.isEmpty() || (type == C.TRACK_TYPE_AUDIO && options.size < 2)) {
                Text(
                    if (type == C.TRACK_TYPE_AUDIO) "This stream has only one audio track." else "This stream has no subtitles.",
                    color = Palette.muted, fontSize = 15.sp,
                )
                Spacer(Modifier.height(14.dp))
                NovaButton("Close", onClose, Modifier.focusRequester(first))
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                itemsIndexed(options) { i, o ->
                    FocusSurface(
                        onClick = {
                            applyTrack(player, type, o)
                            if (type == C.TRACK_TYPE_AUDIO) o.lang?.let { AppGraph.settings.audioLang = it }
                            else if (o.lang != null) AppGraph.settings.subtitleLang = o.lang
                            onClose()
                        },
                        selected = o.selected, focusedScale = 1.02f,
                        modifier = Modifier.fillMaxWidth().then(if (i == focusIndex) Modifier.focusRequester(first) else Modifier),
                    ) { _ ->
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(o.label, Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (o.selected) Text("✓", color = Palette.teal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun VodControls(
        req: PlayRequest.Vod, playing: Boolean, position: Long, duration: Long, resumedAt: Long,
        onBack10: () -> Unit, onToggle: () -> Unit, onFwd30: () -> Unit, onStartOver: () -> Unit,
        onTracks: (Int) -> Unit, onExit: () -> Unit,
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
            if (resumedAt > 0) Text("Resumed from ${formatTime(resumedAt)}", fontSize = 14.sp, color = Palette.teal)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), fontSize = 14.sp, color = Color.White, modifier = Modifier.width(80.dp))
                ProgressBar(if (duration > 0) position.toFloat() / duration else 0f, Modifier.weight(1f))
                Text(if (duration > 0) formatTime(duration) else "--:--", fontSize = 14.sp, color = Palette.muted,
                    modifier = Modifier.width(80.dp).padding(start = 12.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = RowGap, verticalAlignment = Alignment.CenterVertically) {
                NovaButton("10s", onBack10, icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                NovaButton(if (playing) "Pause" else "Play", onToggle, Modifier.focusRequester(first),
                    icon = if (playing) PauseIcon else Icons.Filled.PlayArrow, primary = true)
                NovaButton("30s", onFwd30, icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
                if (resumedAt > 0) NovaButton("Start over", onStartOver, icon = RestartIcon)
                NovaButton("Audio", { onTracks(C.TRACK_TYPE_AUDIO) }, icon = AudioIcon)
                NovaButton("Subtitles", { onTracks(C.TRACK_TYPE_TEXT) }, icon = SubtitlesIcon)
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
        var channels by remember { mutableStateOf(req.channels) }
        var index by remember { mutableIntStateOf(req.index.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
        // The channel watched before this one, for "Last channel". Starts from the recent list.
        var lastChannel by remember {
            mutableStateOf(if (req.direct) null else AppGraph.recent.channels.value.firstOrNull { it.id != req.channels.getOrNull(req.index)?.id })
        }
        var panel by remember { mutableStateOf<Int?>(null) }
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
        // Free TV lists the same channel more than once (other feeds or qualities); when one feed
        // fails, the next one is tried before giving up.
        val triedFeeds = remember { mutableSetOf<String>() }

        LaunchedEffect(index) {
            val c = channel ?: return@LaunchedEffect
            status = "Tuning…"
            retries = 0
            epg = emptyList()
            showBanner = true
            bannerUntil = System.currentTimeMillis() + 5000
            if (req.direct) {
                triedFeeds.clear(); triedFeeds += c.cmd
                play(c.cmd)
                status = null
                return@LaunchedEffect
            }
            AppGraph.settings.lastChannelId = c.id
            val before = AppGraph.recent.channels.value.firstOrNull()
            if (before != null && before.id != c.id) lastChannel = before
            AppGraph.recent.add(c)
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
        LaunchedEffect(showMenu, showList, panel) {
            if (!showMenu && !showList && panel == null) runCatching { rootFocus.requestFocus() }
        }
        usePlayerError { code ->
            val backup = channel?.takeIf { req.direct }?.let { c ->
                channels.firstOrNull { it.name.equals(c.name, ignoreCase = true) && it.cmd !in triedFeeds }
            }
            if (backup != null) {
                triedFeeds += backup.cmd
                status = "This feed isn't working. Trying another feed for ${backup.name}…"
                play(backup.cmd)
                scope.launch {
                    repeat(40) { delay(500); if (player.isPlaying) { status = null; return@launch } }
                }
                return@usePlayerError
            }
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
        fun jumpBack() {
            val target = lastChannel ?: return
            var i = channels.indexOfFirst { it.id == target.id }
            if (i < 0) {
                // Not in this category: switch to the full list, which has it.
                val all = AppGraph.channels
                i = all.indexOfFirst { it.id == target.id }
                if (i < 0) return
                channels = all
            }
            index = i
        }

        BackHandler(enabled = (showMenu || showList) && panel == null) { showMenu = false; showList = false }

        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .focusRequester(rootFocus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || showMenu || showList || panel != null) return@onKeyEvent false
                    when (e.key) {
                        Key.LastChannel -> { jumpBack(); true }
                        Key.DirectionUp, Key.ChannelUp -> { zap(+1); true }
                        Key.DirectionDown, Key.ChannelDown -> { zap(-1); true }
                        Key.DirectionLeft -> { showList = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu, Key.DirectionRight -> { showMenu = true; true }
                        Key.Info -> { showBanner = true; bannerUntil = System.currentTimeMillis() + 5000; true }
                        else -> false
                    }
                }
        ) {
            Video(useController = false, liftSubtitles = showMenu || showBanner)
            ResolutionBadge(Modifier.align(Alignment.TopEnd))

            status?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(18.dp))
            }

            AnimatedVisibility((showBanner || showMenu) && panel == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                if (channel != null) InfoBar(channel, epg, favs.any { it.id == channel.id }, showMenu, canFavourite = !req.direct,
                    lastChannel = lastChannel?.takeIf { it.id != channel.id },
                    audioChoices = trackOptions(tracks, C.TRACK_TYPE_AUDIO).size,
                    subtitleChoices = trackOptions(tracks, C.TRACK_TYPE_TEXT).size,
                    onFav = { AppGraph.favorites.toggle(channel) },
                    onList = { showMenu = false; showList = true },
                    onLast = { jumpBack() },
                    onTracks = { panel = it },
                    onPrev = { zap(-1) }, onNext = { zap(+1) },
                    onClose = { finish() },
                )
            }
            TrackPanelHost(panel, Modifier.align(Alignment.CenterEnd)) { panel = null; showMenu = false }

            AnimatedVisibility(
                showList,
                enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ChannelDrawer(channels, index, favs.map { it.id }.toSet(), onClose = { showList = false }) { i -> index = i; showList = false }
            }
        }
    }

    @Composable
    private fun InfoBar(
        c: Channel, epg: List<EpgEntry>, isFav: Boolean, menu: Boolean, canFavourite: Boolean,
        lastChannel: Channel?, audioChoices: Int, subtitleChoices: Int,
        onFav: () -> Unit, onList: () -> Unit, onLast: () -> Unit, onTracks: (Int) -> Unit,
        onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit,
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = RowGap) {
                    if (canFavourite) NovaButton(if (isFav) "Remove favourite" else "Favourite", onFav, Modifier.focusRequester(first), icon = Icons.Filled.Star, tint = if (isFav) Palette.gold else null)
                    NovaButton("Channel list", onList, if (canFavourite) Modifier else Modifier.focusRequester(first), icon = Icons.AutoMirrored.Filled.List)
                    if (lastChannel != null) NovaButton("Back to ${lastChannel.name.let { if (it.length > 18) it.take(17) + "…" else it }}", onLast, icon = RestartIcon)
                    // Only offered when there is a choice, to keep the bar short on single-track channels.
                    if (audioChoices > 1) NovaButton("Audio", { onTracks(C.TRACK_TYPE_AUDIO) }, icon = AudioIcon)
                    if (subtitleChoices > 0) NovaButton("Subtitles", { onTracks(C.TRACK_TYPE_TEXT) }, icon = SubtitlesIcon)
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
    private fun ChannelDrawer(channels: List<Channel>, current: Int, favIds: Set<String>, onClose: () -> Unit, onPick: (Int) -> Unit) {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 4).coerceAtLeast(0))
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(50); runCatching { fr.requestFocus() } }
        Column(Modifier.width(440.dp).fillMaxHeight().background(Color(0xF00A0E18)).padding(20.dp).onBackKey(onClose)) {
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
