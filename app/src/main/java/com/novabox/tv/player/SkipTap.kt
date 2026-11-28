package com.novabox.tv.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.novabox.tv.data.SkipPrints
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Listens to the decoded sound of an episode, only during its first and last five minutes, and
 * keeps their fingerprints (see [SkipPrints]). Runs on the player's own thread: about 31 small
 * FFTs per second of audio while listening, nothing at all in between. Sound sent to the TV
 * undecoded (Dolby passthrough) can't be heard here, so those videos just don't get the feature.
 */
@UnstableApi
class SkipTap : TeeAudioProcessor.AudioBufferSink {
    /** Off for films, live TV and when the setting is off. */
    @Volatile var enabled = false
    /** Where the next sound starts, in ms: set on every seek (and at the start). */
    @Volatile var anchorMs = 0L
    /** The video's length once known; the credits region needs it. */
    @Volatile var durationMs = 0L

    @Volatile var intro = SkipPrints.blank(); private set
    @Volatile var outro = SkipPrints.blank(); private set
    private var introHeard = BooleanArray(SkipPrints.REGION_FRAMES)
    private var outroHeard = BooleanArray(SkipPrints.REGION_FRAMES)
    @Volatile var introCount = 0; private set
    @Volatile var outroCount = 0; private set

    /** First frame of the credits region (the last five minutes), or -1 while the length isn't known. */
    fun outroStartFrame(): Int {
        val d = durationMs
        return if (d >= 2 * SkipPrints.REGION_MS) SkipPrints.frameOf(d - SkipPrints.REGION_MS + SkipPrints.FRAME_MS - 1) else -1
    }

    private fun wanted(frame: Int): Boolean {
        if (frame < SkipPrints.REGION_FRAMES) return true
        val o = outroStartFrame()
        return o >= 0 && frame >= o && frame - o < SkipPrints.REGION_FRAMES
    }

    private val fp = SkipPrints.Fingerprinter(::wanted) { frame, code ->
        if (frame in 0 until SkipPrints.REGION_FRAMES) {
            intro[frame] = code
            if (!introHeard[frame]) { introHeard[frame] = true; introCount++ }
        }
        val o = outroStartFrame()
        val i = frame - o
        if (o >= 0 && i in 0 until SkipPrints.REGION_FRAMES) {
            outro[i] = code
            if (!outroHeard[i]) { outroHeard[i] = true; outroCount++ }
        }
    }
    private val down = SkipPrints.Downsampler(fp::push)

    private var rate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var framesSinceFlush = 0L
    private var flushAnchor = 0L
    private var listening = false

    /** A new episode: forget what was heard. Call from the main thread before it plays. */
    fun begin(on: Boolean) {
        enabled = on
        anchorMs = 0; durationMs = 0
        intro = SkipPrints.blank(); outro = SkipPrints.blank()
        introHeard = BooleanArray(SkipPrints.REGION_FRAMES); outroHeard = BooleanArray(SkipPrints.REGION_FRAMES)
        introCount = 0; outroCount = 0
        listening = false
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        rate = sampleRateHz; channels = channelCount; this.encoding = encoding
        framesSinceFlush = 0
        flushAnchor = anchorMs
        listening = false
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!enabled || rate <= 0 || channels <= 0) return
        val bytesPer = when (encoding) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_FLOAT -> 4; else -> return }
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frames = b.remaining() / (bytesPer * channels)
        val startMs = flushAnchor + framesSinceFlush * 1000 / rate
        framesSinceFlush += frames
        val endMs = startMs + frames * 1000L / rate
        // Outside both regions: nothing to do. Listening starts again cleanly where it's needed.
        if (!wanted(SkipPrints.frameOf(startMs)) && !wanted(SkipPrints.frameOf(endMs) + 32)) { listening = false; return }
        if (!listening) {
            fp.reset(startMs); down.reset(rate); listening = true
        }
        val pos = b.position()
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val at = pos + (f * channels + c) * bytesPer
                sum += if (bytesPer == 2) b.getShort(at) / 32768f else b.getFloat(at)
            }
            down.push(sum / channels)
        }
    }

    /** What was heard of this episode, for learning. Too little of a region heard counts as nothing. */
    fun heard(episode: String, introFound: Boolean, outroFound: Boolean) = SkipPrints.Heard(
        episode,
        intro.takeIf { introCount >= SkipPrints.frameOf(90_000) }?.copyOf(),
        outro.takeIf { outroCount >= SkipPrints.frameOf(60_000) }?.copyOf(),
        introFound, outroFound,
        introFull = introCount >= SkipPrints.frameOf(270_000), outroFull = outroCount >= SkipPrints.frameOf(270_000),
    )
}
