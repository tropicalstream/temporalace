package com.tropicalstream.temporalace.game

enum class PowerCat { WEAPON, DEFENSE, UTILITY }

/**
 * Strategy-first, budget-balanced catalog. WEAPON pickups are mutually exclusive
 * (grabbing one replaces your primary — the core build choice). DEFENSE/UTILITY
 * layer with caps. Each has a distinct hue → distinct particle signature so a build
 * is readable at a glance.
 */
data class PowerDef(
    val id: String,
    val name: String,
    val cat: PowerCat,
    val hue: Float,
    val durationS: Float,       // 0 = instant/permanent-until-replaced
    val weight: Float,          // drop weight
    val blurb: String
)

object Powerups {
    val all: List<PowerDef> = listOf(
        // ---- WEAPON (exclusive primary; long duration = commit to a build) ----
        PowerDef("spread", "SPREAD", PowerCat.WEAPON, 190f, 20f, 1.0f, "3-way fan"),
        PowerDef("rapid", "RAPID FIRE", PowerCat.WEAPON, 60f, 20f, 1.0f, "double cadence"),
        PowerDef("pierce", "PIERCER", PowerCat.WEAPON, 300f, 20f, 0.9f, "shots pass through"),
        PowerDef("homing", "SEEKERS", PowerCat.WEAPON, 140f, 18f, 0.8f, "curving missiles"),
        PowerDef("beam", "LANCE BEAM", PowerCat.WEAPON, 5f, 16f, 0.7f, "piercing lance"),
        PowerDef("wave", "WAVE CANNON", PowerCat.WEAPON, 270f, 18f, 0.7f, "wide sine wave"),
        // ---- DEFENSE ----
        PowerDef("shield", "SHIELD", PowerCat.DEFENSE, 200f, 0f, 1.0f, "+1 hit bubble"),
        PowerDef("armor", "ARMOR", PowerCat.DEFENSE, 30f, 15f, 0.8f, "halve damage"),
        PowerDef("phase", "PHASE", PowerCat.DEFENSE, 285f, 4f, 0.7f, "brief invuln"),
        PowerDef("repair", "REPAIR", PowerCat.DEFENSE, 120f, 0f, 0.6f, "restore hull"),
        // ---- UTILITY ----
        PowerDef("thrust", "THRUSTERS", PowerCat.UTILITY, 45f, 16f, 0.9f, "faster handling"),
        PowerDef("drone", "OPTION", PowerCat.UTILITY, 165f, 0f, 0.7f, "+1 escort gun"),
        PowerDef("magnet", "MAGNET", PowerCat.UTILITY, 90f, 14f, 0.8f, "draw pickups"),
        PowerDef("slow", "DILATE", PowerCat.UTILITY, 220f, 8f, 0.55f, "slow enemy time"),
        PowerDef("mult", "SCORE x2", PowerCat.UTILITY, 50f, 12f, 0.7f, "double score"),
        PowerDef("overdrive", "OVERDRIVE", PowerCat.UTILITY, 15f, 8f, 0.45f, "all-boost surge"),
        PowerDef("bomb", "BOMB +1", PowerCat.UTILITY, 0f, 0f, 0.6f, "stock a bomb")
    )

    private val byId = all.associateBy { it.id }
    fun def(id: String): PowerDef? = byId[id]

    /** Weighted pick, era slightly biases toward later-tier utilities. */
    fun roll(rng: kotlin.random.Random): PowerDef {
        val total = all.sumOf { it.weight.toDouble() }
        var r = rng.nextDouble() * total
        for (d in all) { r -= d.weight; if (r <= 0) return d }
        return all.first()
    }
}
