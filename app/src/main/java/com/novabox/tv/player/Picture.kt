package com.novabox.tv.player

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.abs
import kotlin.math.roundToInt

/** Settings → Picture: a light unsharp mask on the GPU, for soft, low-bitrate channels. */
@OptIn(UnstableApi::class)
class SharpenEffect(private val amount: Float = 0.35f) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = SharpenProgram(useHdr, amount)
}

@OptIn(UnstableApi::class)
private class SharpenProgram(useHdr: Boolean, private val amount: Float) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try {
        GlProgram(VERTEX, FRAGMENT).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var width = 1
    private var height = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth; height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexelSize", floatArrayOf(1f / width, 1f / height))
            program.setFloatUniform("uAmount", amount)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}"""
        const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uAmount;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 n = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb
         + texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexelSize.x, 0.0)).rgb
         + texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb
         + texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexelSize.y)).rgb;
  vec3 sharp = c.rgb + uAmount * (4.0 * c.rgb - n);
  gl_FragColor = vec4(clamp(sharp, 0.0, 1.0), c.a);
}"""
    }
}

/** The frame rate of the video track playing, or 0 if the stream doesn't say. */
fun videoFrameRate(tracks: Tracks): Float {
    for (g in tracks.groups) {
        if (g.type != C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until g.length) if (g.isTrackSelected(i)) {
            val r = g.getTrackFormat(i).frameRate
            if (r != Format.NO_VALUE.toFloat() && r > 0) return r
        }
    }
    return 0f
}

/**
 * The refresh rate to switch the TV to for [fps]: the lowest supported rate that is a whole
 * multiple of it (24 → 24 Hz, else 48, 120…). Null when none fits, so the TV stays as it is.
 */
internal fun bestRefreshRate(fps: Float, rates: List<Float>): Float? {
    if (fps <= 0) return null
    fun err(r: Float) = (r / fps).let { abs(it - it.roundToInt()) }
    // Lowest multiple first; among equals (24 vs 23.976 Hz) the closest.
    return rates.filter { r -> r / fps >= 0.99f && err(r) < 0.01f }
        .minWithOrNull(compareBy<Float>({ (it / fps).roundToInt() }, { err(it) }))
}

/** Settings → Match frame rate: asks the TV for the display mode that fits the video. */
@Suppress("DEPRECATION")
fun matchFrameRate(activity: Activity, fps: Float) {
    val display: Display = (if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay) ?: return
    val cur = display.mode
    val same = display.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
    val rate = bestRefreshRate(fps, same.map { it.refreshRate }) ?: return
    val mode = same.firstOrNull { it.refreshRate == rate } ?: return
    if (mode.modeId == activity.window.attributes.preferredDisplayModeId || (mode.modeId == cur.modeId && activity.window.attributes.preferredDisplayModeId == 0)) return
    activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = mode.modeId }
}

/** The TV can show HDR (HDR10, HLG or Dolby Vision). */
@Suppress("DEPRECATION")
fun displaySupportsHdr(activity: Activity): Boolean {
    if (Build.VERSION.SDK_INT < 24) return false
    val display = (if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay) ?: return false
    return display.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
}

/** "1920x1080" from the profile's screen resolution, or null for no limit. */
fun maxVideoSize(resolution: String?): Pair<Int, Int>? {
    val m = Regex("""(\d{3,4})\s*[xX×]\s*(\d{3,4})""").find(resolution ?: return null) ?: return null
    return m.groupValues[1].toInt() to m.groupValues[2].toInt()
}
