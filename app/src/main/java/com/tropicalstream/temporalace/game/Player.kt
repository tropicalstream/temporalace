package com.tropicalstream.temporalace.game

/** The player's ship. Autoshoots; only movement is controlled. Powerups mutate the
 *  timer/flag state read by [Game] for firing, defense and scoring. */
class Player {
    var x = 0f; var y = 0f
    var tx = 0f; var ty = 0f
    val r = 11f
    val hpMax = 4
    var hp = hpMax
    var lives = 3
    var bombs = 2

    var weapon: String? = null       // null = default single shot
    var weaponTimer = 0f
    var shield = 0                   // absorbs N hits
    var invulnTimer = 0f
    var armorTimer = 0f
    var thrustTimer = 0f
    var magnetTimer = 0f
    var slowTimer = 0f
    var multTimer = 0f
    var overdriveTimer = 0f
    var drones = 0

    var fireTimer = 0f
    var thrusterPhase = 0f

    fun reset(w: Int, h: Int, keepMeta: Boolean = false) {
        x = w * 0.18f; y = h / 2f; tx = x; ty = y
        hp = hpMax; invulnTimer = 1.5f
        weapon = null; weaponTimer = 0f
        shield = 0; armorTimer = 0f; thrustTimer = 0f; magnetTimer = 0f
        slowTimer = 0f; multTimer = 0f; overdriveTimer = 0f
        if (!keepMeta) { lives = 3; bombs = 2; drones = 0 }
    }

    fun moveDelta(dx: Float, dy: Float, gain: Float, invertY: Boolean, w: Int, h: Int) {
        tx = (tx + dx * gain).coerceIn(r + 4f, w - r - 4f)
        ty = (ty + (if (invertY) -dy else dy) * gain).coerceIn(r + 30f, h - r - 34f)
    }

    fun update(dt: Float) {
        x += (tx - x) * 0.35f
        y += (ty - y) * 0.35f
        thrusterPhase += dt * 22f
        fireTimer -= dt
        weaponTimer = dec(weaponTimer, dt).also { if (it <= 0f && weapon != null) weapon = null }
        invulnTimer = dec(invulnTimer, dt); armorTimer = dec(armorTimer, dt)
        thrustTimer = dec(thrustTimer, dt); magnetTimer = dec(magnetTimer, dt)
        slowTimer = dec(slowTimer, dt); multTimer = dec(multTimer, dt); overdriveTimer = dec(overdriveTimer, dt)
    }

    private fun dec(t: Float, dt: Float) = if (t > 0f) (t - dt).coerceAtLeast(0f) else 0f

    fun apply(def: PowerDef) {
        when (def.cat) {
            PowerCat.WEAPON -> { weapon = def.id; weaponTimer = def.durationS }
            PowerCat.DEFENSE -> when (def.id) {
                "shield" -> shield = (shield + 1).coerceAtMost(3)
                "armor" -> armorTimer = def.durationS
                "phase" -> invulnTimer = maxOf(invulnTimer, def.durationS)
                "repair" -> hp = hpMax
            }
            PowerCat.UTILITY -> when (def.id) {
                "thrust" -> thrustTimer = def.durationS
                "drone" -> drones = (drones + 1).coerceAtMost(2)
                "magnet" -> magnetTimer = def.durationS
                "slow" -> slowTimer = def.durationS
                "mult" -> multTimer = def.durationS
                "overdrive" -> { overdriveTimer = def.durationS }
                "bomb" -> bombs = (bombs + 1).coerceAtMost(6)
            }
        }
    }

    /** @return true if this hit destroyed the ship (a life lost). */
    fun takeHit(): Boolean {
        if (invulnTimer > 0f) return false
        if (shield > 0) { shield--; invulnTimer = 0.6f; return false }
        val dmg = if (armorTimer > 0f) 1 else 2
        hp -= dmg
        invulnTimer = 1.0f
        return hp <= 0
    }

    val speedMul: Float get() = 1f + (if (thrustTimer > 0f) 0.45f else 0f) + (if (overdriveTimer > 0f) 0.55f else 0f)
    val fireInterval: Float
        get() {
            var i = 0.16f
            if (weapon == "rapid") i *= 0.5f
            if (weapon == "beam") i = 0.05f
            if (overdriveTimer > 0f) i *= 0.6f
            return i
        }
    val scoreMult: Int get() = if (multTimer > 0f || overdriveTimer > 0f) 2 else 1
}
