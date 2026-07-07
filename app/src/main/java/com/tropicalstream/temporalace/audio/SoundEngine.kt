package com.tropicalstream.temporalace.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Fully synthesized SFX (no asset files) per the handoff recipes. MODE_STATIC on a
 *  single-thread executor; everything runCatching-wrapped so audio never crashes. */
class SoundEngine {
    private val sr = 44100
    @Volatile private var vol = 0.8f
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "ace-audio").apply { isDaemon = true } }

    fun setSfxVol(v: Float) { vol = v }

    fun laser() = play(sweep(1500.0, 500.0, 55, 0.35f, square = true))
    fun hit() = play(mix(noise(28, 0.3f, false), tone(200.0, 40, 0.3f)))
    fun explode() = play(mix(noise(280, 0.5f, true), tone(60.0, 260, 0.4f)))
    fun powerup() = play(concat(tone(523.0, 45, 0.35f), tone(659.0, 45, 0.35f), tone(880.0, 90, 0.4f)))
    fun portal() = play(mix(sweep(300.0, 1200.0, 600, 0.3f), detune(520.0, 5.0, 600, 0.2f)))
    fun bomb() = play(mix(sweep(180.0, 40.0, 320, 0.5f), noise(320, 0.45f, true)))
    fun boss() = play(tone(90.0, 220, 0.35f, square = true))

    // ---- synthesis ----
    private fun env(i: Int, n: Int, atk: Float = 0.02f, rel: Float = 0.2f): Float {
        val a = (n * atk).toInt().coerceAtLeast(1); val r = (n * rel).toInt().coerceAtLeast(1)
        return when { i < a -> i.toFloat() / a; i > n - r -> (n - i).toFloat() / r; else -> 1f }
    }

    private fun tone(freq: Double, ms: Int, v: Float, square: Boolean = false): FloatArray {
        val n = sr * ms / 1000; val out = FloatArray(n)
        for (i in 0 until n) { var s = sin(2 * PI * freq * i / sr); if (square) s = if (s >= 0) 1.0 else -1.0; out[i] = (s * env(i, n) * v).toFloat() }
        return out
    }
    private fun sweep(f0: Double, f1: Double, ms: Int, v: Float, square: Boolean = false): FloatArray {
        val n = sr * ms / 1000; val out = FloatArray(n); var ph = 0.0
        for (i in 0 until n) { val f = f0 + (f1 - f0) * i / n; ph += 2 * PI * f / sr; var s = sin(ph); if (square) s = if (s >= 0) 1.0 else -1.0; out[i] = (s * env(i, n) * v).toFloat() }
        return out
    }
    private fun detune(freq: Double, cents: Double, ms: Int, v: Float): FloatArray {
        val n = sr * ms / 1000; val out = FloatArray(n)
        for (i in 0 until n) { val a = sin(2 * PI * freq * i / sr); val b = sin(2 * PI * (freq + cents) * i / sr); out[i] = ((a + b) * 0.5 * env(i, n) * v).toFloat() }
        return out
    }
    private fun noise(ms: Int, v: Float, brown: Boolean): FloatArray {
        val n = sr * ms / 1000; val out = FloatArray(n); var last = 0.0
        for (i in 0 until n) { val wnoise = Random.nextDouble(-1.0, 1.0); val s = if (brown) { last = (last + 0.02 * wnoise).coerceIn(-1.0, 1.0); last * 3.0 } else wnoise
            out[i] = (s.coerceIn(-1.0, 1.0) * exp(-3.0 * i / n) * v).toFloat() }
        return out
    }
    private fun concat(vararg p: FloatArray): FloatArray { val out = FloatArray(p.sumOf { it.size }); var o = 0; for (a in p) { System.arraycopy(a, 0, out, o, a.size); o += a.size }; return out }
    private fun mix(a: FloatArray, b: FloatArray): FloatArray { val n = maxOf(a.size, b.size); val out = FloatArray(n); for (i in 0 until n) out[i] = (a.getOrElse(i) { 0f } + b.getOrElse(i) { 0f }).coerceIn(-1f, 1f); return out }

    private fun play(f: FloatArray) {
        val g = vol
        if (g <= 0.01f) return
        exec.execute {
            runCatching {
                val pcm = ShortArray(f.size) { (f[it] * g * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort() }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                track.write(pcm, 0, pcm.size); track.play()
                Thread.sleep(pcm.size * 1000L / sr + 50)
                runCatching { track.stop() }; track.release()
            }
        }
    }

    fun release() { exec.shutdownNow() }
}
