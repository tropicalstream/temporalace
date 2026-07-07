package com.tropicalstream.temporalace.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class EnemyKind { GRUNT, WEAVER, DIVER, TURRET, RAMMER }

/** A basic hostile. Moves right→left with a per-kind pattern and fires aimed or
 *  fixed shots. Scaled by campaign difficulty. */
class Enemy {
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var hp = 1f; var hpMax = 1f
    var r = 14f
    var kind = EnemyKind.GRUNT
    var hue = 0f
    var score = 100
    var fireTimer = 1f
    var t = 0f
    var baseY = 0f
    var alive = true
    var diving = false

    fun spawn(kind: EnemyKind, x: Float, y: Float, hue: Float, diff: Float) {
        this.kind = kind; this.x = x; this.y = y; baseY = y; this.hue = hue
        t = 0f; alive = true; diving = false
        when (kind) {
            EnemyKind.GRUNT -> { hp = 2f; r = 13f; vx = -90f; score = 100; fireTimer = rnd(0.8f, 1.6f) }
            EnemyKind.WEAVER -> { hp = 2f; r = 12f; vx = -70f; score = 150; fireTimer = rnd(1.2f, 2f) }
            EnemyKind.DIVER -> { hp = 3f; r = 14f; vx = -60f; score = 200; fireTimer = 999f }
            EnemyKind.TURRET -> { hp = 6f; r = 17f; vx = -34f; score = 300; fireTimer = rnd(0.6f, 1.2f) }
            EnemyKind.RAMMER -> { hp = 4f; r = 15f; vx = -110f; score = 250; fireTimer = 999f }
        }
        hp *= diff; hpMax = hp
    }

    fun update(dt: Float, px: Float, py: Float, bullets: Bullets, diff: Float) {
        t += dt
        when (kind) {
            EnemyKind.GRUNT -> {}
            EnemyKind.WEAVER -> y = baseY + sin(t * 3f) * 46f
            EnemyKind.DIVER -> {
                if (!diving && x < 560f) { diving = true; val a = atan2(py - y, px - x); vx = cos(a) * 190f; vy = sin(a) * 190f }
            }
            EnemyKind.TURRET -> y = baseY + sin(t * 1.2f) * 20f
            EnemyKind.RAMMER -> { val a = atan2(py - y, px - x); vx += cos(a) * 30f * dt; vy += sin(a) * 30f * dt }
        }
        x += vx * dt; y += vy * dt

        fireTimer -= dt
        if (fireTimer <= 0f && x < 640f && (kind == EnemyKind.GRUNT || kind == EnemyKind.WEAVER || kind == EnemyKind.TURRET)) {
            fireAimed(px, py, bullets)
            fireTimer = when (kind) {
                EnemyKind.TURRET -> rnd(1.0f, 1.8f) / diff
                else -> rnd(1.4f, 2.4f) / diff
            }
        }
        if (x < -30f) alive = false
    }

    private fun fireAimed(px: Float, py: Float, bullets: Bullets) {
        val a = atan2(py - y, px - x)
        val sp = 150f
        val col = com.tropicalstream.temporalace.render.Neon.hsv(hue + 20f, 0.9f, 1f)
        if (kind == EnemyKind.TURRET) {
            for (o in -1..1) bullets.spawn(x, y, cos(a + o * 0.24f) * sp, sin(a + o * 0.24f) * sp, 4f, 1f, true, col)
        } else {
            bullets.spawn(x, y, cos(a) * sp, sin(a) * sp, 4f, 1f, true, col)
        }
    }

    fun hit(dmg: Float): Boolean { hp -= dmg; return hp <= 0f }
    fun dist(ox: Float, oy: Float) = hypot(x - ox, y - oy)

    private fun rnd(a: Float, b: Float) = a + kotlin.random.Random.nextFloat() * (b - a)
}
