package com.novabox.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SkipPrintsTest {
    /** Made-up music: notes with a few harmonics, changing every 150–400 ms. */
    private fun music(seconds: Double, rate: Int, seed: Long): FloatArray {
        val r = java.util.Random(seed)
        val out = FloatArray((seconds * rate).toInt())
        var i = 0
        while (i < out.size) {
            val len = ((0.15 + r.nextDouble() * 0.25) * rate).toInt()
            val f = 110.0 * Math.pow(2.0, r.nextInt(36) / 12.0)
            val f2 = 110.0 * Math.pow(2.0, r.nextInt(36) / 12.0)
            val amp = 0.1 + r.nextDouble() * 0.3
            for (k in 0 until len) {
                if (i + k >= out.size) break
                val t = (i + k).toDouble() / rate
                val env = minOf(1.0, k / (0.01 * rate)) * (1 - k.toDouble() / len * 0.5)
                out[i + k] = (amp * env * (sin(2 * PI * f * t) + 0.5 * sin(4 * PI * f * t) + 0.4 * sin(2 * PI * f2 * t))).toFloat()
            }
            i += len
        }
        return out
    }

    private fun episode(rate: Int, before: Double, intro: FloatArray?, introRate: Int, total: Double, seed: Long, noise: Double, gain: Float): FloatArray {
        val a = music(total, rate, seed)
        if (intro != null) {
            // The intro, resampled to this episode's rate.
            val at = (before * rate).toInt()
            val n = (intro.size.toDouble() * rate / introRate).toInt()
            for (k in 0 until n) {
                val src = k.toDouble() * introRate / rate
                val j = src.toInt().coerceAtMost(intro.size - 2)
                if (at + k < a.size) a[at + k] = gain * (intro[j] + (intro[j + 1] - intro[j]) * (src - j).toFloat())
            }
        }
        val r = java.util.Random(seed + 99)
        for (k in a.indices) a[k] += (r.nextGaussian() * noise).toFloat()
        return a
    }

    /** What the player's audio tap produces: codes for the first [frames] frames. */
    private fun codes(audio: FloatArray, rate: Int, frames: Int, startMs: Long = 0): IntArray {
        val out = SkipPrints.blank(frames)
        val fp = SkipPrints.Fingerprinter({ it in 0 until frames }) { f, c -> if (f in 0 until frames) out[f] = c }
        fp.reset(startMs)
        val ds = SkipPrints.Downsampler(fp::push).also { it.reset(rate) }
        val from = (startMs * rate / 1000).toInt()
        for (k in from until audio.size) ds.push(audio[k])
        return out
    }

    private val intro = music(30.0, 48000, 7)

    @Test fun findsTheSharedIntroBetweenTwoEpisodes() {
        val e1 = episode(48000, 61.3, intro, 48000, 150.0, 1, 0.002, 1f)
        val e2 = episode(44100, 95.013, intro, 48000, 150.0, 2, 0.01, 0.7f)
        val frames = SkipPrints.frameOf(150_000)
        val c1 = codes(e1, 48000, frames); val c2 = codes(e2, 44100, frames)
        val m = SkipPrints.longestCommon(c2, c1)
        assertNotNull(m); m!!
        val startS = SkipPrints.msOf(m.aStart) / 1000.0
        val lenS = SkipPrints.msOf(m.length) / 1000.0
        println("match at ${startS}s for ${lenS}s (other at ${SkipPrints.msOf(m.bStart) / 1000.0}s)")
        assertTrue("start $startS", abs(startS - 95.0) < 1.0)
        assertTrue("length $lenS", abs(lenS - 30.0) < 2.0)
        assertTrue(abs(SkipPrints.msOf(m.bStart) / 1000.0 - 61.3) < 1.0)
    }

    @Test fun unrelatedEpisodesShareNothing() {
        val frames = SkipPrints.frameOf(150_000)
        val c1 = codes(episode(48000, 0.0, null, 48000, 150.0, 11, 0.002, 1f), 48000, frames)
        val c2 = codes(episode(48000, 0.0, null, 48000, 150.0, 12, 0.002, 1f), 48000, frames)
        assertNull(SkipPrints.longestCommon(c1, c2))
    }

    @Test fun silenceIsNotAnIntro() {
        val frames = SkipPrints.frameOf(60_000)
        val quiet = FloatArray(48000 * 60)
        assertNull(SkipPrints.longestCommon(codes(quiet, 48000, frames), codes(quiet.copyOf(), 48000, frames)))
    }

    @Test fun learnsAfterTwoEpisodesThenRecognisesTheThird() {
        val frames = SkipPrints.frameOf(150_000)
        fun ep(n: Int, at: Double) = codes(episode(48000, at, intro, 48000, 150.0, 100L + n, 0.003, 1f), 48000, frames)
        var show = SkipPrints.Show()
        show = SkipPrints.learn(show, SkipPrints.Heard("e1", ep(1, 40.0), null, false, false))
        assertNull(show.intro)
        // The same episode again teaches nothing.
        show = SkipPrints.learn(show, SkipPrints.Heard("e1", ep(1, 40.0), null, false, false))
        assertNull(show.intro); assertEquals(1, show.introSamples.size)
        show = SkipPrints.learn(show, SkipPrints.Heard("e2", ep(2, 72.5), null, false, false))
        val print = assertNotNull(show.intro).let { show.intro!! }
        assertTrue(show.introSamples.isEmpty())
        assertTrue(abs(SkipPrints.msOf(print.size) / 1000.0 - 30) < 2)

        // Episode 3, heard only up to 8 s into its intro (it starts at 20 s): already recognised.
        val c3 = ep(3, 20.0)
        val heard = SkipPrints.frameOf(28_000)
        val m = SkipPrints.longestCommon(c3, SkipPrints.Index(print), SkipPrints.MIN_DETECT, aUpTo = heard)
        assertNotNull(m); m!!
        val introEnd = SkipPrints.msOf(m.aStart + (print.size - m.bStart)) / 1000.0
        println("intro ends at $introEnd s")
        assertTrue("end $introEnd", abs(introEnd - 50.0) < 1.5)

        // Stored and read back.
        val back = SkipPrints.decode(SkipPrints.encode(show))!!
        assertTrue(back.intro!!.contentEquals(print))
    }

    @Test fun heardAfterASeekLinesUpWithTheVideo() {
        val e1 = episode(48000, 70.0, intro, 48000, 150.0, 21, 0.002, 1f)
        val frames = SkipPrints.frameOf(150_000)
        val full = codes(e1, 48000, frames)
        val seeked = codes(e1, 48000, frames, startMs = 50_017)
        val m = SkipPrints.longestCommon(seeked, full)!!
        assertEquals(m.aStart, m.bStart)
    }

    @Test fun aChangedIntroIsLearnedAgain() {
        var show = SkipPrints.Show(intro = IntArray(400) { it })
        repeat(3) { show = SkipPrints.learn(show, SkipPrints.Heard("e$it", SkipPrints.blank(100), null, introFound = false, outroFound = false)) }
        assertNull(show.intro)
    }

    @Test fun cost() {
        val a = music(60.0, 48000, 5)
        val t0 = System.nanoTime()
        codes(a, 48000, SkipPrints.frameOf(60_000))
        val ms = (System.nanoTime() - t0) / 1e6
        val c1 = SkipPrints.blank(); val c2 = SkipPrints.blank()
        val t1 = System.nanoTime()
        SkipPrints.longestCommon(c1, c2)
        println("COST 60 s of audio in $ms ms; compare two 5-minute samples in ${(System.nanoTime() - t1) / 1e6} ms")
    }
}

