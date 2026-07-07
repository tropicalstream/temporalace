package com.tropicalstream.temporalace.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Pooled neon particles (explosions, thruster, powerup sparkle, portal motes). */
class Particles(private val cap: Int = 900) {
    private val x = FloatArray(cap); private val y = FloatArray(cap)
    private val vx = FloatArray(cap); private val vy = FloatArray(cap)
    private val life = FloatArray(cap); private val maxLife = FloatArray(cap)
    private val size = FloatArray(cap); private val color = IntArray(cap)
    private val alive = BooleanArray(cap)
    private var cursor = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun burst(px: Float, py: Float, color: Int, count: Int, speed: Float, spread: Float = 1f, lifeS: Float = 0.8f) {
        repeat(count) {
            val i = alloc()
            val ang = Random.nextFloat() * 6.2832f
            val sp = speed * (0.3f + Random.nextFloat() * spread)
            x[i] = px; y[i] = py; vx[i] = cos(ang) * sp; vy[i] = sin(ang) * sp
            val l = lifeS * (0.5f + Random.nextFloat())
            life[i] = l; maxLife[i] = l; size[i] = 1.6f + Random.nextFloat() * 2.6f
            this.color[i] = color; alive[i] = true
        }
    }

    fun spark(px: Float, py: Float, vxx: Float, vyy: Float, color: Int, lifeS: Float = 0.5f) {
        val i = alloc()
        x[i] = px; y[i] = py; vx[i] = vxx; vy[i] = vyy
        life[i] = lifeS; maxLife[i] = lifeS; size[i] = 2f; this.color[i] = color; alive[i] = true
    }

    private fun alloc(): Int {
        for (n in 0 until cap) { cursor = (cursor + 1) % cap; if (!alive[cursor]) return cursor }
        return cursor
    }

    fun update(dt: Float) {
        for (i in 0 until cap) {
            if (!alive[i]) continue
            life[i] -= dt
            if (life[i] <= 0f) { alive[i] = false; continue }
            x[i] += vx[i] * dt; y[i] += vy[i] * dt
            vx[i] *= 0.94f; vy[i] = vy[i] * 0.94f + 20f * dt
        }
    }

    fun draw(c: Canvas) {
        for (i in 0 until cap) {
            if (!alive[i]) continue
            val a = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            paint.color = color[i]; paint.alpha = (a * 230).toInt()
            c.drawCircle(x[i], y[i], size[i] * a, paint)
            paint.color = Color.WHITE; paint.alpha = (a * 90).toInt()
            c.drawCircle(x[i], y[i], size[i] * a * 0.4f, paint)
        }
    }

    fun clear() { for (i in 0 until cap) alive[i] = false }
}
