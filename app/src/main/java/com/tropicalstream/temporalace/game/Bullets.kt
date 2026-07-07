package com.tropicalstream.temporalace.game

import android.graphics.Canvas
import com.tropicalstream.temporalace.render.Neon

/** Pooled bullets (player + enemy share the pool via [hostile]). */
class Bullets(private val cap: Int = 512) {
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val r = FloatArray(cap); val dmg = FloatArray(cap)
    val hostile = BooleanArray(cap); val color = IntArray(cap)
    val pierce = IntArray(cap); val homing = BooleanArray(cap); val alive = BooleanArray(cap)
    private var cursor = 0

    fun spawn(px: Float, py: Float, pvx: Float, pvy: Float, radius: Float, damage: Float, isHostile: Boolean, col: Int, pierceCount: Int = 0, home: Boolean = false): Int {
        val i = alloc()
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; r[i] = radius; dmg[i] = damage
        hostile[i] = isHostile; color[i] = col; pierce[i] = pierceCount; homing[i] = home; alive[i] = true
        return i
    }

    private fun alloc(): Int {
        for (n in 0 until cap) { cursor = (cursor + 1) % cap; if (!alive[cursor]) return cursor }
        return cursor
    }

    fun update(dt: Float, w: Int, h: Int) {
        val m = 40f
        for (i in 0 until cap) {
            if (!alive[i]) continue
            x[i] += vx[i] * dt; y[i] += vy[i] * dt
            if (x[i] < -m || x[i] > w + m || y[i] < -m || y[i] > h + m) alive[i] = false
        }
    }

    fun draw(c: Canvas) {
        for (i in 0 until cap) {
            if (!alive[i]) continue
            Neon.fillCircle(c, x[i], y[i], r[i], color[i], 0.8f)
        }
    }

    fun clear() { for (i in 0 until cap) alive[i] = false }
    val capacity get() = cap
}
