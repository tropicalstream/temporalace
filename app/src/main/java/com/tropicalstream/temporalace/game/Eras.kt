package com.tropicalstream.temporalace.game

/**
 * Six eras of aviation, five levels each = 30. Historical eras use restrained,
 * period-flavoured palettes (still bright — black is transparent on the waveguide);
 * future eras go full neon. `neon` drives glow/saturation and particle intensity.
 * Portals at every level end (and the era boundary is the dramatic jump).
 */
data class Era(
    val name: String,
    val enemyHue: Float,
    val accentHue: Float,
    val sat: Float,
    val neon: Float,
    val bgHue: Float,
    val silhouette: String   // reference note for art/asset generation
)

object Eras {
    val list = listOf(
        Era("THE GREAT WAR", 38f, 52f, 0.55f, 0.35f, 32f, "WWI biplane, twin wings, exposed struts"),
        Era("PACIFIC THEATER", 205f, 190f, 0.62f, 0.45f, 210f, "WWII fighter, radial engine, single wing"),
        Era("JET AGE", 182f, 158f, 0.72f, 0.6f, 190f, "Cold-War swept-wing jet, silver"),
        Era("STEALTH ERA", 300f, 280f, 0.78f, 0.72f, 268f, "faceted stealth delta, matte, angular"),
        Era("DRONE SWARM", 148f, 118f, 0.88f, 0.88f, 158f, "near-future autonomous drone, ring rotors"),
        Era("NEON INFINITY", 320f, 190f, 0.98f, 1f, 300f, "far-future light-craft, pure energy edges")
    )

    const val TOTAL = 30

    fun forLevel(level: Int): Era = list[((level - 1) / 5).coerceIn(0, list.size - 1)]
    fun isBoss(level: Int) = level % 5 == 0
    fun isSubBoss(level: Int) = level % 5 == 3 || level % 5 == 4
    fun eraIndex(level: Int) = ((level - 1) / 5).coerceIn(0, list.size - 1)

    /** Difficulty scalar 1.0 → ~3.4 across the campaign. */
    fun difficulty(level: Int): Float = 1f + (level - 1) * 0.083f
}
