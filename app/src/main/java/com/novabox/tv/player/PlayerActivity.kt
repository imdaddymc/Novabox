package com.novabox.tv.player

import androidx.compose.foundation.layout.size
import androidx.tv.material3.Icon
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
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
import com.novabox.tv.data.SkipPrints
import com.novabox.tv.data.SkipStore
import com.novabox.tv.data.WatchEntry
import com.novabox.tv.ui.FocusSurface
import com.novabox.tv.ui.Logo
import com.novabox.tv.ui.NovaButton
import com.novabox.tv.ui.NovaTheme
import com.novabox.tv.ui.Palette
import com.novabox.tv.ui.FavMark
import com.novabox.tv.ui.ProgressBar
import com.novabox.tv.ui.RowGap
import com.novabox.tv.ui.currentProgramme
import com.novabox.tv.ui.onBackKey
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** A round icon button for the player controls. Its name shows underneath while it has focus. */
@Composable
private fun PlayerIconButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, icon: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier.size(if (primary) 64.dp else 56.dp).onFocusChanged { focused = it.isFocused },
            shape = androidx.compose.foundation.shape.CircleShape,
            container = if (primary) Palette.accent else Palette.card,
            focusedScale = 1.1f,
        ) { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
        }
        Text(
            label, fontSize = 12.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 14.sp,
            color = Color.White, modifier = Modifier.padding(top = 6.dp).height(30.dp).alpha(if (focused) 1f else 0f),
        )
    }
}

/** A circular arrow with the number of seconds inside: back (anticlockwise) or forward. */
@Composable
private fun SkipIcon(back: Boolean, seconds: Int) {
    Box(contentAlignment = Alignment.Center) {
        Icon(RestartIcon, null, Modifier.size(34.dp).graphicsLayer { if (!back) scaleX = -1f })
        Text("$seconds", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
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
        // The decoded sound also goes past the intro/credits listener (see SkipTap).
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(skipTap)))
                    .build()
        }
        player = ExoPlayer.Builder(this, renderers)
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
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) skipTap.anchorMs = new.positionMs
            }
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
    /** Hears episodes' intros and credits (see [SkipTap]). */
    private val skipTap = SkipTap()
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

    /** [forceHls]: treat the stream as HLS even when its address doesn't end in .m3u8. */
    private fun play(url: String, forceHls: Boolean = false) {
        streamSerial++
        skipTap.anchorMs = 0
        val item = MediaItem.Builder().setUri(url).apply {
            when {
                forceHls || url.contains(".m3u8", ignoreCase = true) -> setMimeType(MimeTypes.APPLICATION_M3U8)
                url.contains(".mpd", ignoreCase = true) -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
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

    /**
     * A small "1080p" tag in the top-right corner that fades out 3 seconds after a stream starts. Kept
     * well inside the edges: many TVs crop the outer few percent of the picture (overscan).
     */
    @Composable
    private fun ResolutionBadge(modifier: Modifier = Modifier) {
        var label by remember { mutableStateOf("") }
        var shows by remember { mutableIntStateOf(0) }
        var visible by remember { mutableStateOf(false) }
        val serial = streamSerial
        DisposableEffect(serial) {
            var shown = false
            // The stream's own picture size (what the channel sends), not the decoder's output, which
            // some TVs keep at the previous channel's size when they reuse the decoder.
            fun show() {
                val f = player.videoFormat
                val w = f?.width?.takeIf { it > 0 } ?: player.videoSize.width
                val h = f?.height?.takeIf { it > 0 } ?: player.videoSize.height
                if (!shown && w > 0 && h > 0) {
                    shown = true
                    label = resolutionLabel(w, h)
                    shows++
                }
            }
            val l = object : Player.Listener {
                // Every new stream draws a first frame, even when its size is the same as the last one's.
                override fun onRenderedFirstFrame() = show()
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
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(tween(700)), modifier = modifier.padding(top = 48.dp, end = 80.dp)) {
            Text(
                label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.background(Color(0x8C000000), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }

    @Composable
    private fun usePlayerError(onError: (String) -> Unit) {
        // The listener lives for the whole screen, so it must call the latest handler: an old one
        // would still point at the first channel watched.
        val handler by androidx.compose.runtime.rememberUpdatedState(onError)
        DisposableEffect(Unit) {
            val l = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Handled in onCreate: playback restarts without the sharpen filter.
                    if (error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED) return
                    // A live stream that fell behind (slow network) just jumps back to live; not a failure.
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        player.seekToDefaultPosition(); player.prepare(); return
                    }
                    handler(error.errorCodeName)
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
        var okDown by remember { mutableStateOf(false) }
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

        // Skip intro and Up next at the credits, for episodes: learned by ear from earlier episodes
        // of the same show (see SkipPrints). Nothing shows until a show's intro or credits is known.
        val show = remember(req) { SkipStore.showOf(req)?.takeIf { AppGraph.settings.skipIntros } }
        var prints by remember { mutableStateOf<SkipPrints.Show?>(null) }
        // Where this episode's intro was heard (start to end, in ms), and where its credits start.
        var introSpan by remember { mutableStateOf<LongRange?>(null) }
        var outroAt by remember { mutableLongStateOf(-1L) }
        // Up next was offered at the credits: Cancel then just carries on watching.
        var fromCredits by remember { mutableStateOf(false) }
        DisposableEffect(req) {
            skipTap.begin(show != null)
            onDispose {
                skipTap.enabled = false
                if (show != null) SkipStore.learnLater(show, skipTap.heard(req.key.ifBlank { req.title + req.subtitle }, introSpan != null, outroAt >= 0))
            }
        }
        LaunchedEffect(show) {
            if (show != null) prints = withContext(Dispatchers.IO) { SkipStore.load(show) }
        }
        LaunchedEffect(prints) {
            val p = prints ?: return@LaunchedEffect
            val introPrint = p.intro; val outroPrint = p.outro
            val (introIdx, outroIdx) = withContext(Dispatchers.Default) { introPrint?.let { SkipPrints.Index(it) } to outroPrint?.let { SkipPrints.Index(it) } }
            while (true) {
                delay(2000)
                if (!player.isPlaying) continue
                val frame = SkipPrints.frameOf(player.currentPosition)
                if (introIdx != null && introPrint != null && introSpan == null && frame < SkipPrints.REGION_FRAMES) {
                    val codes = skipTap.intro.copyOf()
                    val m = withContext(Dispatchers.Default) { SkipPrints.longestCommon(codes, introIdx, SkipPrints.MIN_DETECT, aUpTo = frame.coerceAtLeast(1)) }
                    if (m != null) introSpan = SkipPrints.msOf(m.aStart - m.bStart).coerceAtLeast(0)..SkipPrints.msOf(m.aStart + introPrint.size - m.bStart)
                    if (m != null) android.util.Log.i("NovaSkip", "intro heard: $introSpan")
                }
                val o = skipTap.outroStartFrame()
                if (outroIdx != null && outroPrint != null && outroAt < 0 && o >= 0 && frame > o) {
                    val codes = skipTap.outro.copyOf()
                    val heardTo = (frame - o).coerceAtMost(SkipPrints.REGION_FRAMES)
                    val m = withContext(Dispatchers.Default) { SkipPrints.longestCommon(codes, outroIdx, SkipPrints.MIN_DETECT, aUpTo = heardTo) }
                    if (m != null) outroAt = SkipPrints.msOf(o + m.aStart - m.bStart)
                    if (m != null) android.util.Log.i("NovaSkip", "credits heard from $outroAt")
                }
            }
        }
        // The credits have started: the episode counts as watched, and the next one is offered.
        LaunchedEffect(outroAt) {
            if (outroAt < 0 || ended || upNext != null) return@LaunchedEffect
            saveProgress(ended = true)
            val n = lookUpNext()?.await() ?: return@LaunchedEffect
            if (!ended && upNext == null) { fromCredits = true; upNext = n }
        }

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
                skipTap.durationMs = duration
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
        val inIntro = introSpan?.let { position >= it.first - 500 && position < it.last - 1500 } == true
        fun skipIntro() { introSpan?.let { player.seekTo(it.last); position = it.last } }

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
                        // The remote's own audio-language and subtitle buttons, where it has them.
                        Key.MediaAudioTrack -> { panel = C.TRACK_TYPE_AUDIO; true }
                        Key.Captions -> { panel = C.TRACK_TYPE_TEXT; true }
                        else -> { if (controls) hideAt = System.currentTimeMillis() + 5000; false }
                    }
                }
                .onKeyEvent { e ->
                    // The controls open when OK is let go, so the same press can't also press a button.
                    if (e.type == KeyEventType.KeyUp && okDown && e.key in setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                        okDown = false; if (inIntro) skipIntro() else poke(); return@onKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown || controls || panel != null) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { seek(-10_000); true }
                        Key.DirectionRight -> { seek(30_000); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { okDown = true; true }
                        Key.DirectionUp, Key.DirectionDown, Key.Menu -> { poke(); true }
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
                    onTracks = { panel = it }, onExit = { finish() },
                    onSeekTo = { t -> player.seekTo(t); position = t; poke() },
                    onSkipIntro = if (inIntro) ({ skipIntro(); poke() }) else null)
            }
            AnimatedVisibility(inIntro && !controls && upNext == null && panel == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd)) {
                SkipIntroPill()
            }
            TrackPanelHost(panel, Modifier.align(Alignment.CenterEnd)) { panel = null; poke() }
            upNext?.let { n ->
                UpNext(n, Modifier.align(Alignment.BottomEnd), onPlay = { onNext(n) },
                    onCancel = { if (fromCredits && !ended) { upNext = null; fromCredits = false } else finish() })
            }
        }
    }

    /** Shown during a show's intro while the controls are hidden: OK jumps to the end of it. */
    @Composable
    private fun SkipIntroPill() {
        Row(
            Modifier.padding(48.dp).background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.SkipNext, null, Modifier.size(24.dp), tint = Color(0xFF0A0E18))
            Spacer(Modifier.width(8.dp))
            Text("Skip intro", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0A0E18))
            Spacer(Modifier.width(12.dp))
            Text("OK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A6478))
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
                            // The sound restarts from here: the intro/credits listener must know where it is.
                            skipTap.anchorMs = player.currentPosition
                            applyTrack(player, type, o)
                            if (type == C.TRACK_TYPE_AUDIO) o.lang?.let { AppGraph.settings.audioLang = it }
                            else if (o.lang != null) AppGraph.settings.subtitleLang = o.lang
                            // Also for the next channel or episode in this player, not only the next time it opens.
                            player.trackSelectionParameters = preferredTracks(player.trackSelectionParameters,
                                if (type == C.TRACK_TYPE_AUDIO) o.lang else null, if (type == C.TRACK_TYPE_TEXT) o.lang else null)
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
        onTracks: (Int) -> Unit, onExit: () -> Unit, onSeekTo: (Long) -> Unit, onSkipIntro: (() -> Unit)? = null,
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
            SeekBar(position, duration, onSeekTo)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = RowGap, verticalAlignment = Alignment.CenterVertically) {
                // Icon buttons, like a TV remote's transport keys; the focused one shows its name underneath.
                PlayerIconButton("Back 10 seconds", onBack10) { SkipIcon(back = true, seconds = 10) }
                PlayerIconButton(if (playing) "Pause" else "Play", onToggle, Modifier.focusRequester(first), primary = true) {
                    Icon(if (playing) PauseIcon else Icons.Filled.PlayArrow, null, Modifier.size(30.dp))
                }
                PlayerIconButton("Forward 30 seconds", onFwd30) { SkipIcon(back = false, seconds = 30) }
                if (onSkipIntro != null) PlayerIconButton("Skip intro", onSkipIntro) { Icon(Icons.Filled.SkipNext, null, Modifier.size(28.dp)) }
                if (resumedAt > 0) PlayerIconButton("Start over", onStartOver) { Icon(RestartIcon, null, Modifier.size(26.dp)) }
                PlayerIconButton("Audio", { onTracks(C.TRACK_TYPE_AUDIO) }) { Icon(AudioIcon, null, Modifier.size(26.dp)) }
                PlayerIconButton("Subtitles", { onTracks(C.TRACK_TYPE_TEXT) }) { Icon(SubtitlesIcon, null, Modifier.size(26.dp)) }
                // Exit sits on its own at the far right, away from the playback buttons.
                Spacer(Modifier.weight(1f))
                PlayerIconButton("Exit player", onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(26.dp)) }
            }
        }
    }

    /**
     * The progress bar. Press ▲ from the buttons to reach it, then ◀ ▶ to scrub: holding a direction
     * speeds up (10 s, then 30 s, 1 min, and bigger jumps). The time to jump to is shown above the
     * bar, and playback jumps there on OK or a moment after you stop moving.
     */
    @Composable
    private fun SeekBar(position: Long, duration: Long, onSeekTo: (Long) -> Unit) {
        var focused by remember { mutableStateOf(false) }
        var target by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(target) {
            val t = target ?: return@LaunchedEffect
            delay(900); onSeekTo(t); target = null
        }
        val shown = target ?: position
        val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(position), fontSize = 14.sp, color = Color.White, modifier = Modifier.width(80.dp))
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.weight(1f).height(56.dp)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) target?.let { t -> onSeekTo(t); target = null }
                    }
                    .onPreviewKeyEvent { e ->
                        // Back while scrubbing drops the new spot and stays where playback is.
                        if (e.key == Key.Back && target != null) { if (e.type == KeyEventType.KeyUp) target = null; return@onPreviewKeyEvent true }
                        if (e.type != KeyEventType.KeyDown || duration <= 0) return@onPreviewKeyEvent false
                        val dir = when (e.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { target?.let { t -> onSeekTo(t); target = null }; return@onPreviewKeyEvent true }
                            else -> return@onPreviewKeyEvent false
                        }
                        val held = e.nativeKeyEvent.repeatCount
                        val step = when {
                            held < 3 -> 10_000L
                            held < 10 -> 30_000L
                            held < 20 -> 60_000L
                            else -> maxOf(120_000L, duration / 40)
                        }
                        target = ((target ?: position) + dir * step).coerceIn(0L, duration)
                        true
                    }
                    .focusable(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val barH = if (focused) 8.dp else 4.dp
                Box(Modifier.fillMaxWidth().height(barH).background(Color(0x33FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                Box(Modifier.fillMaxWidth(fraction).height(barH).background(Palette.teal, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                if (focused) {
                    val x = maxWidth * fraction
                    Box(Modifier.offset(x = x - 10.dp).size(20.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape))
                    Text(
                        formatTime(shown), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0A0E18),
                        modifier = Modifier.offset(x = (x - 30.dp).coerceIn(0.dp, maxWidth - 60.dp), y = (-26).dp)
                            .width(60.dp).background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .padding(vertical = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Text(if (duration > 0) formatTime(duration) else "--:--", fontSize = 14.sp, color = Palette.muted,
                modifier = Modifier.width(80.dp).padding(start = 12.dp))
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
        // OK was pressed with the menu closed; the menu opens when it is let go.
        var okDown by remember { mutableStateOf(false) }
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
        // Set (to the reason) when three tries failed: the viewer is asked whether to try again.
        var stuck by remember { mutableStateOf<String?>(null) }
        // Free TV lists the same channel more than once (other feeds or qualities); when one feed
        // fails, the next one is tried before giving up.
        val triedFeeds = remember { mutableSetOf<String>() }

        LaunchedEffect(index) {
            val c = channel ?: return@LaunchedEffect
            status = "Tuning…"
            retries = 0
            stuck = null
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
        // Waits for the stream to start, then clears the message; a steady stream earns fresh retries.
        fun watchStart() {
            scope.launch {
                repeat(60) { delay(500); if (player.isPlaying) { status = null; delay(30_000); if (player.isPlaying) retries = 0; return@launch } }
            }
        }
        fun retune(c: Channel, forceHls: Boolean = false) {
            scope.launch {
                if (req.direct) play(c.cmd, forceHls)
                else runCatching { AppGraph.client?.liveLink(c) }.getOrNull()?.let { play(it) }
                watchStart()
            }
        }
        usePlayerError { code ->
            val c = channel ?: return@usePlayerError
            // Free TV: another feed of the same channel is tried first.
            val backup = channels.takeIf { req.direct }?.firstOrNull { it.name.equals(c.name, ignoreCase = true) && it.cmd !in triedFeeds }
            if (backup != null) {
                triedFeeds += backup.cmd
                status = "This feed isn't working. Trying another feed for ${backup.name}…"
                play(backup.cmd)
                watchStart()
                return@usePlayerError
            }
            // Three tries, then the viewer decides whether to keep trying.
            if (retries >= MAX_RETRIES) {
                status = null
                stuck = streamProblem(code)
                return@usePlayerError
            }
            retries++
            status = "This stream isn't loading. Trying again ($retries of $MAX_RETRIES)…"
            // Some streams are HLS behind an address without .m3u8; the second try says so explicitly.
            val hls = req.direct && ("UNRECOGNIZED" in code || "PARSING" in code)
            scope.launch { delay(2000); if (channel?.id == c.id) retune(c, forceHls = hls) }
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
                    if (e.type == KeyEventType.KeyUp && okDown && e.key in setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                        okDown = false; showMenu = true; return@onKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown || showMenu || showList || panel != null) return@onKeyEvent false
                    when (e.key) {
                        Key.LastChannel -> { jumpBack(); true }
                        Key.DirectionUp, Key.ChannelUp -> { zap(+1); true }
                        Key.DirectionDown, Key.ChannelDown -> { zap(-1); true }
                        Key.DirectionLeft -> { showList = true; true }
                        // The menu opens when OK is let go, so the same press can't also press its first button.
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { okDown = true; true }
                        Key.Menu, Key.DirectionRight -> { showMenu = true; true }
                        Key.Info -> { showBanner = true; bannerUntil = System.currentTimeMillis() + 5000; true }
                        Key.MediaAudioTrack -> { panel = C.TRACK_TYPE_AUDIO; true }
                        Key.Captions -> { panel = C.TRACK_TYPE_TEXT; true }
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

            stuck?.let { why ->
                StreamStuck(channel?.name.orEmpty(), why, Modifier.align(Alignment.Center),
                    onRetry = {
                        stuck = null; retries = 0
                        channel?.let { c -> triedFeeds.clear(); triedFeeds += c.cmd; status = "Trying ${c.name} again…"; retune(c) }
                    },
                    onExit = { finish() })
            }

            AnimatedVisibility(
                showList,
                enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ChannelDrawer(channels, index, favs.map { it.id }.toSet(), onClose = { showList = false }) { i -> index = i; showList = false }
            }
        }
    }

    /** After three failed tries: try again, or leave. ▲ ▼ still change channel. */
    @Composable
    private fun StreamStuck(name: String, why: String, modifier: Modifier, onRetry: () -> Unit, onExit: () -> Unit) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
        Column(
            modifier.width(560.dp).background(Color(0xF00A0E18), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(26.dp),
        ) {
            Text("${name.ifBlank { "This channel" }} isn't loading", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("It didn't start after $MAX_RETRIES tries: $why. Try loading it again, or press ▲ ▼ for another channel.", fontSize = 15.sp, color = Palette.muted)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = RowGap) {
                NovaButton("Try again", onRetry, Modifier.focusRequester(first), icon = RestartIcon, primary = true)
                NovaButton("Close player", onExit)
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
                // Wide margins: many TVs crop the picture's edges (overscan).
                .padding(start = 80.dp, end = 80.dp, top = 60.dp, bottom = 44.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(c.logo, c.name, 64.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.number}", color = Palette.teal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isFav) { Spacer(Modifier.width(10.dp)); FavMark(true, 22.dp) }
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
                    // Channel up/down and leaving are the remote's own ▲ ▼ and Back, so they aren't repeated here.
                    NovaButton("Channel list", onList, Modifier.focusRequester(first), icon = Icons.AutoMirrored.Filled.List)
                    if (lastChannel != null) NovaButton("Back to ${lastChannel.name.let { if (it.length > 14) it.take(13).trimEnd() + "…" else it }}", onLast, icon = RestartIcon)
                    // Always offered, so it's easy to find; the count shows when there's a choice of languages.
                    NovaButton(if (audioChoices > 1) "Audio ($audioChoices)" else "Audio", { onTracks(C.TRACK_TYPE_AUDIO) }, icon = AudioIcon)
                    if (subtitleChoices > 0) NovaButton("Subtitles", { onTracks(C.TRACK_TYPE_TEXT) }, icon = SubtitlesIcon)
                    if (canFavourite) NovaButton(if (isFav) "Unfavourite" else "Favourite", onFav, icon = Icons.Filled.Favorite, tint = if (isFav) Palette.heart else null)
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text("OK: menu   ·   ▲▼: change channel   ·   ◀: channel list   ·   Back: exit", fontSize = 13.sp, color = Palette.muted)
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
                            if (c.id in favIds) FavMark(true, 18.dp)
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
/** Automatic tries before the viewer is asked whether to keep trying. */
private const val MAX_RETRIES = 3

internal fun streamProblem(code: String): String = when {
    "BAD_HTTP_STATUS" in code || "FILE_NOT_FOUND" in code -> "the stream is offline"
    "NETWORK" in code || "TIMEOUT" in code -> "it couldn't be reached"
    "PARSING" in code || "DECODER" in code || "DECODING" in code || "UNSUPPORTED" in code -> "its format isn't supported on this TV"
    "DRM" in code -> "it's copy-protected"
    else -> "the stream failed"
}
