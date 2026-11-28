package com.novabox.tv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one small, silent preview player of the Live tab. Kept light for Google TV boxes: low
 * resolution, a short buffer, no sound, and it only starts once the viewer rests on a channel.
 * Stopped before the full player opens, since many portals allow one stream per box.
 */
object PreviewPlayer {
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    fun get(context: android.content.Context): ExoPlayer = player ?: run {
        val headers = AppGraph.client?.playbackHeaders().orEmpty()
        val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).followRedirects(true).build()
        val ds = OkHttpDataSource.Factory(http).setUserAgent(headers["User-Agent"]).setDefaultRequestProperties(headers - "User-Agent")
        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(4_000, 8_000, 1_000, 1_500).build())
            .build().also { p ->
                p.volume = 0f
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(854, 480).setForceLowestBitrate(true).build()
                player = p
            }
    }

    /** Frees the stream (and the portal's connection) now. */
    fun stop() {
        player?.release()
        player = null
    }
}

/** A 16:9 muted preview of [channel], starting after the focus has rested on it for a moment. */
@OptIn(UnstableApi::class)
@Composable
fun LivePreview(channel: Channel, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Only while the viewer is on the channel list (not browsing categories) and the app is in view.
    val visible = active && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    var playerState by remember { mutableStateOf<ExoPlayer?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { PreviewPlayer.stop(); playerState = null } }
    LaunchedEffect(channel.id, visible) {
        if (!visible) { PreviewPlayer.stop(); playerState = null; return@LaunchedEffect }
        playerState?.stop()
        note = null
        // Moving down the list shouldn't open a stream for every channel passed.
        delay(900)
        val url = runCatching { AppGraph.client?.liveLink(channel) }.getOrNull()
        if (url.isNullOrBlank()) { note = "No preview"; return@LaunchedEffect }
        val p = PreviewPlayer.get(context)
        p.setMediaItem(MediaItem.Builder().setUri(url).apply { if (url.contains(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8) }.build())
        p.prepare(); p.play()
        playerState = p
    }
    DisposableEffect(playerState) {
        val p = playerState
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { note = "No preview" }
            override fun onRenderedFirstFrame() { note = null }
        }
        p?.addListener(l)
        onDispose { p?.removeListener(l) }
    }

    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.Black)) {
        playerState?.let { p ->
            AndroidView(
                factory = { ctx ->
                    (android.view.LayoutInflater.from(ctx).inflate(com.novabox.tv.R.layout.player_texture, null) as PlayerView).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        isFocusable = false
                        player = p
                    }
                },
                update = { it.player = p },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!active) Text("Preview starts when you pick a channel", fontSize = 13.sp, color = Palette.muted, modifier = Modifier.align(Alignment.Center))
        else note?.let { Text(it, fontSize = 13.sp, color = Palette.muted, modifier = Modifier.align(Alignment.Center)) }
    }
}
