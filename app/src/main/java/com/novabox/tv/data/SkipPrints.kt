package com.novabox.tv.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Audio fingerprints for finding a show's intro and end credits by ear.
 *
 * While an episode plays, the sound of its first and last five minutes is turned into one 32-bit
 * code every 32 ms (how the loudness of 33 bands between 300 and 2,000 Hz rises and falls, in the
 * style of Haitsma and Kalker). When two episodes of a show share a long enough stretch of codes,
 * that stretch is the intro (at the start) or the credits (at the end). Nothing is uploaded and
 * nothing is asked of the viewer.
 */
object SkipPrints {
    const val RATE = 8000
    const val N = 1024
    const val HOP = 256
    /** Milliseconds per code. */
    const val FRAME_MS = HOP * 1000 / RATE // 32
    const val REGION_MS = 300_000L
    const val REGION_FRAMES = (REGION_MS / FRAME_MS).toInt() // 9375
    private const val BANDS = 33
    private const val LOW_HZ = 300.0
    private const val HIGH_HZ = 2000.0

    /** A learned stretch must be at least this long (10 s) and at most this (3 min). */
    const val MIN_LEARN = 10_000 / FRAME_MS
    const val MAX_LEARN = 180_000 / FRAME_MS
    /** While playing, this much (4 s) of a known intro or credits must be heard to be sure. */
    const val MIN_DETECT = 4_000 / FRAME_MS

    fun frameOf(ms: Long) = (ms / FRAME_MS).toInt()
    fun msOf(frame: Int) = frame.toLong() * FRAME_MS

    /** Codes for regions not heard yet: random, so they never match anything. */
    fun blank(size: Int = REGION_FRAMES, seed: Long = System.nanoTime()): IntArray {
        val r = java.util.Random(seed)
        return IntArray(size) { r.nextInt() }
    }

    // ------------------------------------------------------------ fingerprinting

    /**
     * Turns 8 kHz mono samples into codes. [onFrame] gets the frame number (counted from the
     * start of the video, one per 32 ms) and its code. [wanted] says whether a frame is in a
     * region being listened to; frames outside are skipped without the FFT.
     */
    class Fingerprinter(private val wanted: (Int) -> Boolean = { true }, private val onFrame: (Int, Int) -> Unit) {
        private val ring = FloatArray(N)
        private var s = 0L
        private var since = 0L
        private val re = DoubleArray(N)
        private val im = DoubleArray(N)
        private var prev: DoubleArray? = null
        private var prevFrame = -2
        private val cur = DoubleArray(BANDS)
        private val noise = java.util.Random()

        /** Starts again at [ms] into the video (after a seek, or at the start). */
        fun reset(ms: Long) {
            s = ms * RATE / 1000
            since = s
            prev = null
        }

        fun push(x: Float) {
            ring[(s % N).toInt()] = x
            s++
            val start = s - N
            if (start < since || start % HOP != 0L) return
            val frame = (start / HOP).toInt()
            if (!wanted(frame) && !wanted(frame + 1)) { prev = null; return }
            var energy = 0.0
            for (i in 0 until N) {
                val v = ring[((start + i) % N).toInt()].toDouble()
                energy += v * v
                re[i] = v * HANN[i]; im[i] = 0.0
            }
            fft(re, im)
            for (b in 0 until BANDS) {
                var e = 0.0
                for (k in BAND_LO[b] until BAND_HI[b]) e += re[k] * re[k] + im[k] * im[k]
                cur[b] = e
            }
            val p = prev
            val quiet = sqrt(energy / N) < 0.003
            if (p != null && prevFrame == frame - 1 && wanted(frame)) {
                var code = 0
                for (b in 0 until 32) {
                    val d = (cur[b] - cur[b + 1]) - (p[b] - p[b + 1])
                    if (d > 0) code = code or (1 shl b)
                }
                onFrame(frame, if (quiet) noise.nextInt() else code)
            }
            prev = (p ?: DoubleArray(BANDS)).also { cur.copyInto(it) }
            prevFrame = frame
        }
    }

    private val HANN = DoubleArray(N) { 0.5 - 0.5 * cos(2 * PI * it / (N - 1)) }
    private val BAND_LO = IntArray(BANDS)
    private val BAND_HI = IntArray(BANDS)
    private val COS = DoubleArray(N / 2) { cos(-2 * PI * it / N) }
    private val SIN = DoubleArray(N / 2) { sin(-2 * PI * it / N) }
    init {
        val binHz = RATE.toDouble() / N
        for (b in 0 until BANDS) {
            val lo = LOW_HZ * (HIGH_HZ / LOW_HZ).pow(b.toDouble() / BANDS)
            val hi = LOW_HZ * (HIGH_HZ / LOW_HZ).pow((b + 1).toDouble() / BANDS)
            BAND_LO[b] = (lo / binHz).toInt()
            BAND_HI[b] = maxOf(BAND_LO[b] + 1, (hi / binHz).toInt())
        }
    }

    /** In-place radix-2 FFT of size [N]. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= N) {
            val step = N / len
            for (i in 0 until N step len) {
                for (k in 0 until len / 2) {
                    val wr = COS[k * step]; val wi = SIN[k * step]
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                }
            }
            len = len shl 1
        }
    }

    /**
     * Brings decoded audio of any sample rate down to 8 kHz mono for the [Fingerprinter]:
     * a gentle low-pass, then evenly spaced samples.
     */
    class Downsampler(private val out: (Float) -> Unit) {
        private var step = 6.0
        private var next = 0.0
        private var n = 0L
        private var last = 0f
        private val lp1 = Biquad(); private val lp2 = Biquad()

        fun reset(sourceRate: Int) {
            step = sourceRate.toDouble() / RATE
            next = 0.0; n = 0; last = 0f
            lp1.lowPass(sourceRate, 3000.0); lp2.lowPass(sourceRate, 3000.0)
        }

        fun push(x: Float) {
            val y = lp2.run(lp1.run(x))
            while (next <= n) {
                val frac = (next - (n - 1)).toFloat()
                out(last + (y - last) * frac)
                next += step
            }
            last = y; n++
        }
    }

    private class Biquad {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var z1 = 0.0; var z2 = 0.0
        fun lowPass(rate: Int, hz: Double) {
            val w = 2 * PI * hz / rate
            val alpha = sin(w) / (2 * 0.7071)
            val c = cos(w)
            val a0 = 1 + alpha
            b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0
            a1 = -2 * c / a0; a2 = (1 - alpha) / a0
            z1 = 0.0; z2 = 0.0
        }
        fun run(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y.toFloat()
        }
    }

    // ------------------------------------------------------------ matching

    /** [a] frames [aStart, aStart+length) sound like [b] frames from [bStart]. */
    data class Match(val aStart: Int, val bStart: Int, val length: Int)

    /** Finds where pieces of [b] turn up, by looking up each half of every code. Build once per print. */
    class Index(val codes: IntArray) {
        internal val headHi = IntArray(65536) { -1 }
        internal val headLo = IntArray(65536) { -1 }
        internal val nextHi = IntArray(codes.size)
        internal val nextLo = IntArray(codes.size)
        init {
            for (j in codes.indices.reversed()) {
                val h = codes[j] ushr 16; val l = codes[j] and 0xFFFF
                nextHi[j] = headHi[h]; headHi[h] = j
                nextLo[j] = headLo[l]; headLo[l] = j
            }
        }
        internal inline fun each(code: Int, f: (Int) -> Unit) {
            var j = headHi[code ushr 16]; var n = 0
            while (j >= 0 && n++ < 64) { f(j); j = nextHi[j] }
            j = headLo[code and 0xFFFF]; n = 0
            while (j >= 0 && n++ < 64) { f(j); j = nextLo[j] }
        }
    }

    private const val WINDOW = 32
    /**
     * A one-second window matches when under 40 % of its bits differ. The same sound comes out at
     * 25–35 % (it never lines up to the millisecond), unrelated sound at about 50 %.
     */
    private const val WINDOW_BITS = WINDOW * 32 * 40 / 100
    /** Up to 2 s of disagreement (a sound effect over the music) doesn't break a match. */
    private const val GAP = 2_000 / FRAME_MS

    /** The longest stretch [a] and [b] share, at least [minFrames] long; null when there is none. */
    fun longestCommon(a: IntArray, b: Index, minFrames: Int = MIN_LEARN, aUpTo: Int = a.size): Match? {
        val bs = b.codes
        val size = aUpTo + bs.size
        val votes = IntArray(size)
        for (i in 0 until aUpTo) b.each(a[i]) { j -> votes[j - i + aUpTo]++ }
        // The few offsets with the most votes, neighbours counted together.
        val picks = ArrayList<Int>()
        repeat(4) {
            var best = -1; var bestV = 5
            for (d in 1 until size - 1) {
                if (picks.any { kotlin.math.abs(it - d) < 4 }) continue
                val v = votes[d - 1] + votes[d] + votes[d + 1]
                if (v > bestV) { bestV = v; best = d }
            }
            if (best >= 0) picks += best
        }
        var found: Match? = null
        for (p in picks) for (d in (p - 1)..(p + 1)) {
            val m = runAt(a, bs, d - aUpTo, aUpTo) ?: continue
            if (m.length >= minFrames && (found == null || m.length > found.length)) found = m
        }
        return found
    }

    fun longestCommon(a: IntArray, b: IntArray, minFrames: Int = MIN_LEARN) = longestCommon(a, Index(b), minFrames)

    /** The longest matching run with [b] shifted by [shift] (a[i] lines up with b[i + shift]). */
    private fun runAt(a: IntArray, b: IntArray, shift: Int, aUpTo: Int): Match? {
        val from = maxOf(0, -shift)
        val to = minOf(aUpTo, b.size - shift)
        if (to - from < WINDOW) return null
        val err = IntArray(to - from) { Integer.bitCount(a[from + it] xor b[from + it + shift]) }
        var sum = 0
        for (k in 0 until WINDOW) sum += err[k]
        var bestStart = -1; var bestEnd = -1
        var runStart = -1; var lastGood = -1
        for (k in 0..err.size - WINDOW) {
            if (k > 0) sum += err[k + WINDOW - 1] - err[k - 1]
            if (sum <= WINDOW_BITS) {
                if (runStart < 0 || k - lastGood > GAP) runStart = k
                lastGood = k
                if (lastGood - runStart > bestEnd - bestStart) { bestStart = runStart; bestEnd = lastGood }
            }
        }
        if (bestStart < 0) return null
        return Match(from + bestStart, from + bestStart + shift, bestEnd - bestStart + WINDOW)
    }

    // ------------------------------------------------------------ learning and storage

    class Sample(val episode: String, val codes: IntArray)

    /** What is known about one show. [introMisses]: episodes heard in full where the intro wasn't found. */
    data class Show(
        val intro: IntArray? = null,
        val outro: IntArray? = null,
        val introSamples: List<Sample> = emptyList(),
        val outroSamples: List<Sample> = emptyList(),
        val introMisses: Int = 0,
        val outroMisses: Int = 0,
    )

    /**
     * What one episode's listening adds. [intro]/[outro] are its codes, or null when too little
     * was heard; [introFound]/[outroFound] say whether a known print was recognised in it.
     */
    class Heard(
        val episode: String, val intro: IntArray?, val outro: IntArray?, val introFound: Boolean, val outroFound: Boolean,
        /** Nearly all of the region was heard, so a known print not found there is a real miss. */
        val introFull: Boolean = true, val outroFull: Boolean = true,
    )

    fun learn(show: Show, h: Heard): Show {
        var s = show
        // A known print that keeps not turning up (the show changed its intro) is learned again.
        if (s.intro != null && h.intro != null && h.introFull) {
            s = if (h.introFound) s.copy(introMisses = 0) else s.copy(introMisses = s.introMisses + 1)
            if (s.introMisses >= 3) s = s.copy(intro = null, introMisses = 0)
        }
        if (s.outro != null && h.outro != null && h.outroFull) {
            s = if (h.outroFound) s.copy(outroMisses = 0) else s.copy(outroMisses = s.outroMisses + 1)
            if (s.outroMisses >= 3) s = s.copy(outro = null, outroMisses = 0)
        }
        if (s.intro == null && h.intro != null) {
            val (print, samples) = learnPart(h.episode, h.intro, s.introSamples)
            s = s.copy(intro = print, introSamples = samples)
        }
        if (s.outro == null && h.outro != null) {
            val (print, samples) = learnPart(h.episode, h.outro, s.outroSamples)
            s = s.copy(outro = print, outroSamples = samples)
        }
        return s
    }

    private fun learnPart(episode: String, codes: IntArray, samples: List<Sample>): Pair<IntArray?, List<Sample>> {
        for (other in samples.asReversed()) {
            if (other.episode == episode) continue
            val m = longestCommon(codes, other.codes) ?: continue
            if (m.length in MIN_LEARN..MAX_LEARN) return codes.copyOfRange(m.aStart, m.aStart + m.length) to emptyList()
        }
        // Keep the two latest episodes to compare the next one with.
        return null to (samples.filter { it.episode != episode } + Sample(episode, codes)).takeLast(2)
    }

    fun encode(s: Show): ByteArray {
        val b = ByteArrayOutputStream()
        DataOutputStream(b).use { o ->
            fun codes(c: IntArray?) { o.writeInt(c?.size ?: -1); c?.forEach(o::writeInt) }
            o.writeInt(MAGIC); o.writeInt(1)
            codes(s.intro); codes(s.outro)
            o.writeInt(s.introMisses); o.writeInt(s.outroMisses)
            for (list in listOf(s.introSamples, s.outroSamples)) {
                o.writeInt(list.size)
                list.forEach { o.writeUTF(it.episode.take(200)); codes(it.codes) }
            }
        }
        return b.toByteArray()
    }

    fun decode(bytes: ByteArray): Show? = runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { i ->
            if (i.readInt() != MAGIC || i.readInt() != 1) return null
            fun codes(): IntArray? = i.readInt().let { n -> if (n < 0) null else IntArray(n.coerceAtMost(REGION_FRAMES)) { i.readInt() } }
            val intro = codes(); val outro = codes()
            val im = i.readInt(); val om = i.readInt()
            val lists = List(2) { List(i.readInt().coerceIn(0, 2)) { Sample(i.readUTF(), codes() ?: IntArray(0)) } }
            Show(intro, outro, lists[0], lists[1], im, om)
        }
    }.getOrNull()

    private const val MAGIC = 0x4E42534B // "NBSK"
}
