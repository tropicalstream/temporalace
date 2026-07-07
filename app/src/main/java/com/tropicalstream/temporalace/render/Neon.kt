package com.tropicalstream.temporalace.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Neon draw helpers: 2-pass glow (wide faint halo + crisp core) on black. No
 *  BlurMaskFilter, so it stays on the hardware-accelerated path. */
object Neon {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float, glow: Float = 1f) {
        paint.style = Paint.Style.STROKE
        paint.color = color; paint.alpha = (55 * glow).toInt().coerceIn(0, 255); paint.strokeWidth = w * 3f
        c.drawLine(x1, y1, x2, y2, paint)
        paint.alpha = 255; paint.strokeWidth = w
        c.drawLine(x1, y1, x2, y2, paint)
    }

    fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, w: Float, glow: Float = 1f) {
        paint.style = Paint.Style.STROKE
        paint.color = color; paint.alpha = (50 * glow).toInt().coerceIn(0, 255); paint.strokeWidth = w * 3f
        c.drawCircle(x, y, r, paint)
        paint.alpha = 255; paint.strokeWidth = w
        c.drawCircle(x, y, r, paint)
    }

    fun fillCircle(c: Canvas, x: Float, y: Float, r: Float, color: Int, glow: Float = 1f) {
        paint.style = Paint.Style.FILL
        for (i in 3 downTo 1) { paint.color = color; paint.alpha = (24 * glow).toInt() + (3 - i) * 8; c.drawCircle(x, y, r * (1f + i * 0.45f), paint) }
        paint.color = color; paint.alpha = 255; c.drawCircle(x, y, r, paint)
    }

    fun poly(c: Canvas, path: Path, color: Int, w: Float, glow: Float = 1f, fill: Boolean = false) {
        if (fill) { paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = 60; c.drawPath(path, paint) }
        paint.style = Paint.Style.STROKE
        paint.color = color; paint.alpha = (55 * glow).toInt().coerceIn(0, 255); paint.strokeWidth = w * 3f
        c.drawPath(path, paint)
        paint.alpha = 255; paint.strokeWidth = w
        c.drawPath(path, paint)
    }

    fun hsv(h: Float, s: Float, v: Float, a: Int = 255): Int {
        val rgb = Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }

    fun withAlpha(color: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
}
