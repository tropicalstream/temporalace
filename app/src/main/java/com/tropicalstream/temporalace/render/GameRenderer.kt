package com.tropicalstream.temporalace.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.tropicalstream.temporalace.game.Enemy
import com.tropicalstream.temporalace.game.EnemyKind
import com.tropicalstream.temporalace.game.Eras
import com.tropicalstream.temporalace.game.Game
import com.tropicalstream.temporalace.game.Screen
import kotlin.math.cos
import kotlin.math.sin

/** Draws the game each frame. Reads Game state; owns no game logic. */
class GameRenderer(private val g: Game) {

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tri = Path()
    private val stars = FloatArray(120) { if (it % 2 == 0) (it * 53 % 640).toFloat() else (it * 97 % 480).toFloat() }

    fun draw(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.BLACK)
        g.onLayout(w, h)
        when (g.screen) {
            Screen.TITLE -> drawTitle(c, w, h)
            Screen.SETTINGS -> drawSettings(c, w, h)
            Screen.VICTORY -> drawVictory(c, w, h)
            else -> { drawWorld(c, w, h); if (g.screen == Screen.PAUSE) drawPause(c, w, h); if (g.screen == Screen.PORTAL) drawPortal(c, w, h); if (g.screen == Screen.GAMEOVER) drawGameOver(c, w, h) }
        }
    }

    // ---------- world ----------
    private fun drawWorld(c: Canvas, w: Int, h: Int) {
        val era = Eras.forLevel(g.level)
        drawBackground(c, w, h, era.bgHue, era.neon)
        g.particles.draw(c)
        g.pickups.forEach { pk ->
            val col = Neon.hsv(pk.hue, 0.85f, 1f)
            Neon.circle(c, pk.x, pk.y, pk.r + 2f + sin(pk.t * 6f) * 1.5f, col, 2f, 1f)
            Neon.fillCircle(c, pk.x, pk.y, 3f, col, 1f)
        }
        g.enemies.forEach { drawEnemy(c, it) }
        g.boss?.let { drawBoss(c, it, w, h) }
        g.bullets.draw(c)
        drawShip(c, w, h)
        drawHud(c, w, h)
        if (g.subtitleT > 0f) g.subtitle?.let { drawSubtitle(c, w, h, it) }
        if (g.flash > 0f) { val a = (g.flash * (if (g.settings.reduceFlash) 60f else 150f)).toInt().coerceIn(0, 255); fill.color = Neon.withAlpha(Color.WHITE, a); fill.style = Paint.Style.FILL; c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }
    }

    private fun drawBackground(c: Canvas, w: Int, h: Int, hue: Float, neon: Float) {
        // parallax star/grid motes tinted by era
        val col = Neon.hsv(hue, 0.5f, 0.5f + 0.3f * neon)
        fill.style = Paint.Style.FILL
        for (i in stars.indices step 2) {
            val sx = ((stars[i] - g.starPhase * (40f + (i % 5) * 24f)) % w + w) % w
            val sy = stars[i + 1]
            fill.color = Neon.withAlpha(col, 40 + (i % 4) * 25)
            c.drawCircle(sx, sy, 1f + (i % 3) * 0.6f, fill)
        }
        if (neon > 0.6f) {  // future eras get a horizon grid
            val gc = Neon.hsv(hue, 0.7f, 1f)
            var yy = h * 0.7f; var d = 10f
            while (yy < h) { Neon.line(c, 0f, yy, w.toFloat(), yy, gc, 1f, 0.3f); yy += d; d *= 1.3f }
        }
    }

    private fun drawShip(c: Canvas, w: Int, h: Int) {
        val p = g.player
        if (p.invulnTimer > 0f && ((p.invulnTimer * 12).toInt() % 2 == 0)) return  // flicker
        val col = Neon.hsv(190f, 0.7f, 1f)
        // thruster flame
        val fl = 8f + sin(p.thrusterPhase) * 3f
        Neon.line(c, p.x - p.r, p.y, p.x - p.r - fl, p.y, Neon.hsv(35f, 0.9f, 1f), 3f, 1f)
        // hull
        tri.reset(); tri.moveTo(p.x + p.r, p.y); tri.lineTo(p.x - p.r, p.y - p.r * 0.8f); tri.lineTo(p.x - p.r, p.y + p.r * 0.8f); tri.close()
        Neon.poly(c, tri, col, 2f, 1f, fill = true)
        // drones
        for (d in 1..p.drones) { val oy = p.y + (if (d == 1) -22f else 22f); Neon.fillCircle(c, p.x + 4f, oy, 4f, Neon.hsv(165f, 0.8f, 1f), 1f) }
        // shield
        if (p.shield > 0) Neon.circle(c, p.x, p.y, p.r + 6f, Neon.hsv(200f, 0.7f, 1f, 200), 2f, 0.8f)
    }

    private fun drawEnemy(c: Canvas, e: Enemy) {
        val hpF = (e.hp / e.hpMax).coerceIn(0.2f, 1f)
        val col = Neon.hsv(e.hue, 0.85f, 0.6f + 0.4f * hpF)
        when (e.kind) {
            EnemyKind.TURRET -> { Neon.circle(c, e.x, e.y, e.r, col, 2f, 1f); Neon.line(c, e.x, e.y, e.x - e.r - 4f, e.y, col, 2f, 1f) }
            EnemyKind.WEAVER -> { tri.reset(); tri.moveTo(e.x - e.r, e.y); tri.lineTo(e.x, e.y - e.r); tri.lineTo(e.x + e.r, e.y); tri.lineTo(e.x, e.y + e.r); tri.close(); Neon.poly(c, tri, col, 2f, 1f) }
            else -> { tri.reset(); tri.moveTo(e.x - e.r, e.y); tri.lineTo(e.x + e.r, e.y - e.r * 0.75f); tri.lineTo(e.x + e.r, e.y + e.r * 0.75f); tri.close(); Neon.poly(c, tri, col, 2f, 1f, fill = true) }
        }
    }

    private fun drawBoss(c: Canvas, b: com.tropicalstream.temporalace.game.Boss, w: Int, h: Int) {
        val col = Neon.hsv(b.def.hue, 0.9f, 1f)
        Neon.circle(c, b.x, b.y, b.r, col, 3f, 1f)
        Neon.circle(c, b.x, b.y, b.r * 0.6f, Neon.hsv(b.def.hue + 30f, 0.9f, 1f), 2f, 1f)
        val n = 6; for (k in 0 until n) { val a = k * 6.2832f / n + g.starPhase; Neon.line(c, b.x, b.y, b.x + cos(a) * (b.r + 8f), b.y + sin(a) * (b.r + 8f), col, 2f, 0.8f) }
        // name + health bar
        text.textAlign = Paint.Align.CENTER; text.clearShadowLayer(); text.color = col; text.alpha = 230; text.textSize = h * 0.045f
        c.drawText(b.def.name, w / 2f, h * 0.065f, text)
        val bw = w * 0.7f; val bx = (w - bw) / 2f; val by = h * 0.085f
        fill.style = Paint.Style.FILL; fill.color = Neon.withAlpha(col, 50); c.drawRect(bx, by, bx + bw, by + 5f, fill)
        fill.color = col; fill.alpha = 235; c.drawRect(bx, by, bx + bw * (b.hp / b.hpMax).coerceIn(0f, 1f), by + 5f, fill)
        text.alpha = 255
    }

    private fun drawHud(c: Canvas, w: Int, h: Int) {
        val p = g.player
        text.clearShadowLayer(); text.textAlign = Paint.Align.LEFT; text.textSize = h * 0.05f; text.color = Neon.hsv(190f, 0.5f, 1f); text.alpha = 230
        c.drawText("${g.score}", w * 0.03f, h * 0.06f, text)
        text.textAlign = Paint.Align.RIGHT
        c.drawText("SHIPS ${p.lives}   BOMBS ${p.bombs}", w * 0.97f, h * 0.06f, text)
        // powerup indicator row
        text.textAlign = Paint.Align.LEFT; text.textSize = h * 0.035f
        var xx = w * 0.03f; val yy = h * 0.115f
        fun tag(s: String, hue: Float) { text.color = Neon.hsv(hue, 0.8f, 1f); c.drawText(s, xx, yy, text); xx += text.measureText(s) + w * 0.02f }
        p.weapon?.let { tag(it.uppercase(), 190f) }
        if (p.shield > 0) tag("SH${p.shield}", 200f)
        if (p.overdriveTimer > 0f) tag("OVERDRIVE", 15f)
        if (p.slowTimer > 0f) tag("DILATE", 220f)
        if (p.multTimer > 0f) tag("x2", 50f)
        text.textAlign = Paint.Align.CENTER; text.alpha = 255
    }

    private fun drawSubtitle(c: Canvas, w: Int, h: Int, s: String) {
        text.textAlign = Paint.Align.CENTER; text.color = Color.WHITE
        text.setShadowLayer(6f, 0f, 0f, Color.BLACK); text.textSize = h * 0.05f; text.alpha = 235
        c.drawText(s, w / 2f, h * 0.9f, text); text.clearShadowLayer(); text.alpha = 255
    }

    // ---------- screens ----------
    private fun title(c: Canvas, w: Int, h: Int, s: String, y: Float, hue: Float, size: Float) {
        text.textAlign = Paint.Align.CENTER; text.color = Neon.hsv(hue, 0.85f, 1f); text.setShadowLayer(h * 0.02f, 0f, 0f, Neon.hsv(hue, 0.85f, 1f)); text.textSize = size
        c.drawText(s, w / 2f, y, text); text.clearShadowLayer()
    }

    private fun drawTitle(c: Canvas, w: Int, h: Int) {
        title(c, w, h, "TEMPORAL ACE", h * 0.28f, 300f, h * 0.13f)
        title(c, w, h, "six eras · thirty levels · one sky", h * 0.37f, 190f, h * 0.042f)
        val opts = ArrayList<String>().apply { add("NEW GAME"); if (g.hasSave()) add("CONTINUE"); add("SETTINGS") }
        var y = h * 0.55f
        opts.forEachIndexed { i, o -> menuRow(c, w, o, y, i == g.menuIndex, 190f); y += h * 0.11f }
        footer(c, w, h, "swipe ↑↓ · tap select   ·   in-game: move ship · tap pause · hold bomb")
    }

    private fun drawPause(c: Canvas, w: Int, h: Int) {
        scrim(c, w, h)
        title(c, w, h, "PAUSED", h * 0.28f, 190f, h * 0.1f)
        listOf("RESUME", "SETTINGS", "QUIT TO TITLE").forEachIndexed { i, o -> menuRow(c, w, o, h * 0.46f + i * h * 0.12f, i == g.menuIndex, 190f) }
    }

    private fun drawSettings(c: Canvas, w: Int, h: Int) {
        title(c, w, h, "SETTINGS", h * 0.13f, 285f, h * 0.08f)
        val rows = g.settingRows
        val visible = 6
        val start = (g.settingRow - visible / 2).coerceIn(0, maxOf(0, rows.size - visible))
        text.textAlign = Paint.Align.LEFT
        var y = h * 0.28f
        for (i in start until minOf(start + visible, rows.size)) {
            val sel = i == g.settingRow
            text.color = if (sel) Neon.hsv(190f, 0.9f, 1f) else Color.WHITE; text.alpha = if (sel) 255 else 130; text.textSize = h * 0.055f
            if (sel) text.setShadowLayer(h * 0.015f, 0f, 0f, Neon.hsv(190f, 0.9f, 1f)) else text.clearShadowLayer()
            c.drawText((if (sel) "▸ " else "  ") + rows[i], w * 0.12f, y, text)
            text.textAlign = Paint.Align.RIGHT
            c.drawText(g.settingValue(rows[i]), w * 0.88f, y, text)
            text.textAlign = Paint.Align.LEFT
            y += h * 0.10f
        }
        text.clearShadowLayer(); text.textAlign = Paint.Align.CENTER; text.alpha = 255
        footer(c, w, h, "swipe ↑↓ row · ←→ change · tap toggle · double-tap back")
    }

    private fun drawGameOver(c: Canvas, w: Int, h: Int) {
        scrim(c, w, h)
        title(c, w, h, "SHOT DOWN", h * 0.3f, 0f, h * 0.11f)
        title(c, w, h, "SCORE ${g.score}", h * 0.42f, 45f, h * 0.05f)
        listOf("RETRY", "TITLE").forEachIndexed { i, o -> menuRow(c, w, o, h * 0.56f + i * h * 0.12f, i == g.menuIndex, 190f) }
    }

    private fun drawVictory(c: Canvas, w: Int, h: Int) {
        drawBackground(c, w, h, 300f, 1f)
        title(c, w, h, "ACE OF ALL ERAS", h * 0.32f, 300f, h * 0.11f)
        title(c, w, h, "SCORE ${g.score}", h * 0.44f, 190f, h * 0.055f)
        menuRow(c, w, "TITLE", h * 0.62f, true, 190f)
    }

    private fun drawPortal(c: Canvas, w: Int, h: Int) {
        val cx = w / 2f; val cy = h / 2f
        val prog = 1f - (g.portalT / 2.2f)
        val col = Neon.hsv(280f + prog * 120f, 0.9f, 1f)
        for (k in 0 until 7) { val rr = (20f + k * 30f + prog * 200f) % 260f; Neon.circle(c, cx, cy, rr, col, 3f, 1f - rr / 300f) }
        title(c, w, h, "PORTAL — ${Eras.forLevel((g.level + 1).coerceAtMost(Eras.TOTAL)).name}", h * 0.5f, 300f, h * 0.06f)
    }

    private fun menuRow(c: Canvas, w: Int, s: String, y: Float, sel: Boolean, hue: Float) {
        text.textAlign = Paint.Align.CENTER; text.color = Neon.hsv(hue, 0.85f, if (sel) 1f else 0.5f); text.alpha = if (sel) 255 else 150; text.textSize = if (sel) 34f else 28f
        if (sel) text.setShadowLayer(14f, 0f, 0f, Neon.hsv(hue, 0.85f, 1f)) else text.clearShadowLayer()
        c.drawText((if (sel) "▸  " else "") + s, w / 2f, y, text); text.clearShadowLayer(); text.alpha = 255
    }

    private fun footer(c: Canvas, w: Int, h: Int, s: String) {
        text.textAlign = Paint.Align.CENTER; text.clearShadowLayer(); text.color = Color.WHITE; text.alpha = 100; text.textSize = h * 0.036f
        c.drawText(s, w / 2f, h * 0.95f, text); text.alpha = 255
    }

    private fun scrim(c: Canvas, w: Int, h: Int) { fill.style = Paint.Style.FILL; fill.color = Neon.withAlpha(Color.BLACK, 150); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill) }
}
