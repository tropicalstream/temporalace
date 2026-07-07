package com.tropicalstream.temporalace.game

import com.tropicalstream.temporalace.render.Neon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One boss phase: begins when hp fraction drops to [enterAt]; drives a movement id,
 *  1–2 bullet-pattern ids, and a spoken taunt line. */
class BossPhase(val enterAt: Float, val move: Int, val patterns: IntArray, val taunt: String)

/** Static personality + fight definition. Voice line ids resolve to mp3 in the
 *  level's /voice dir if the companion generated them; else the text is subtitled. */
class BossDef(
    val name: String,
    val hue: Float,
    val hpBase: Float,
    val gimmick: String,          // "escort" | "rewind" | "none"
    val intro: String,
    val defeat: String,
    val phases: List<BossPhase>
)

object BossDefs {
    val byEra: List<BossDef> = listOf(
        BossDef("BARON KRÄHE", 40f, 220f, "escort",
            "So — a challenger climbs into my sky.",
            "Impossible... shot down by a novice...",
            listOf(
                BossPhase(1f, 1, intArrayOf(1), "Let us waltz, little sparrow."),
                BossPhase(0.6f, 2, intArrayOf(1, 2), "My wingmen! To me!"),
                BossPhase(0.3f, 1, intArrayOf(4), "I do not LOSE.")
            )),
        BossDef("ADMIRAL KUROSHIO", 205f, 300f, "escort",
            "You dare breach the fleet?",
            "The tide... turns against me...",
            listOf(
                BossPhase(1f, 4, intArrayOf(1), "Anti-air batteries — fire!"),
                BossPhase(0.6f, 1, intArrayOf(2, 1), "Launch the escort squadron!"),
                BossPhase(0.3f, 2, intArrayOf(5), "For the fleet!")
            )),
        BossDef("MiG PHANTOM-9", 180f, 340f, "none",
            "Radar lock acquired. You are already dead.",
            "Ejecting... this is not over...",
            listOf(
                BossPhase(1f, 4, intArrayOf(4), "Supersonic. Keep up."),
                BossPhase(0.6f, 2, intArrayOf(2, 4), "Missile bay open."),
                BossPhase(0.3f, 1, intArrayOf(3), "Afterburner — MAXIMUM.")
            )),
        BossDef("WRAITH", 300f, 380f, "none",
            "You cannot hit what you cannot see.",
            "Detected... at last... clever...",
            listOf(
                BossPhase(1f, 3, intArrayOf(1), "I am the silence between radar sweeps."),
                BossPhase(0.6f, 3, intArrayOf(2), "Vanish."),
                BossPhase(0.3f, 2, intArrayOf(5, 4), "Then I will be LOUD.")
            )),
        BossDef("HIVE-MOTHER 07", 148f, 440f, "escort",
            "Organic pilot detected. Inefficient. Deleting.",
            "Swarm integrity... failing... failing...",
            listOf(
                BossPhase(1f, 1, intArrayOf(3), "Deploying children."),
                BossPhase(0.6f, 4, intArrayOf(3, 2), "You are outnumbered by design."),
                BossPhase(0.3f, 2, intArrayOf(5, 3), "We are legion.")
            )),
        BossDef("CHRONOS PRIME", 320f, 520f, "rewind",
            "I have already watched you fall. A thousand times.",
            "This timeline... was not... foreseen...",
            listOf(
                BossPhase(1f, 3, intArrayOf(3, 2), "Time bends to me."),
                BossPhase(0.55f, 2, intArrayOf(5), "I will simply UNMAKE that hit."),
                BossPhase(0.28f, 3, intArrayOf(3, 4, 2), "Witness the end of eras.")
            ))
    )

    fun forLevel(level: Int): BossDef = byEra[Eras.eraIndex(level)]
}

class Boss(val def: BossDef, diff: Float) {
    var x = 0f; var y = 0f
    val hpMax = def.hpBase * (0.7f + 0.3f * diff)
    var hp = hpMax
    val r = 34f
    var alive = true
    private var phaseIndex = 0
    private var t = 0f
    private var baseY = 0f
    private var spin = 0f
    private val timers = FloatArray(3)
    private var blinkT = 0f
    private var rewindUsed = false

    var pendingTaunt: String? = def.intro
    var spawnEscortWave = false

    fun place(w: Int, h: Int) { x = w * 0.82f; y = h / 2f; baseY = y }

    private val phase get() = def.phases[phaseIndex]

    fun update(dt: Float, px: Float, py: Float, bullets: Bullets, w: Int, h: Int) {
        t += dt; spin += dt
        // phase progression
        val frac = hp / hpMax
        while (phaseIndex < def.phases.size - 1 && frac <= def.phases[phaseIndex + 1].enterAt) {
            phaseIndex++
            pendingTaunt = phase.taunt
            timers[0] = 0f; timers[1] = 0f; timers[2] = 0f
            if (def.gimmick == "escort") spawnEscortWave = true
            if (def.gimmick == "rewind" && !rewindUsed && phaseIndex == 1) {
                rewindUsed = true; hp = (hp + hpMax * 0.28f).coerceAtMost(hpMax * 0.6f)
                pendingTaunt = "I will simply UNMAKE that hit."
            }
        }
        move(dt, py, w, h)
        var slot = 0
        for (pid in phase.patterns) { emit(pid, dt, slot, px, py, bullets); slot++ }
    }

    private fun move(dt: Float, py: Float, w: Int, h: Int) {
        when (phase.move) {
            1 -> y = baseY + sin(t * 1.4f) * (h * 0.28f)
            2 -> { x = w * 0.78f + cos(t * 1.1f) * (w * 0.06f); y = baseY + sin(t * 2.2f) * (h * 0.30f) }
            3 -> { blinkT -= dt; if (blinkT <= 0f) { blinkT = 2.4f; y = h * (0.2f + Random.nextFloat() * 0.6f) } }
            4 -> y += ((py - y) * 0.6f) * dt
        }
    }

    private fun emit(pid: Int, dt: Float, slot: Int, px: Float, py: Float, bullets: Bullets) {
        timers[slot] -= dt
        val col = Neon.hsv(def.hue + slot * 30f, 0.9f, 1f)
        when (pid) {
            1 -> if (timers[slot] <= 0f) { timers[slot] = 0.8f
                val a = atan2(py - y, px - x)
                for (o in -1..1) bullets.spawn(x, y, cos(a + o * 0.22f) * 170f, sin(a + o * 0.22f) * 170f, 5f, 1f, true, col) }
            2 -> if (timers[slot] <= 0f) { timers[slot] = 1.5f
                val n = 20; for (k in 0 until n) { val a = k * 6.2832f / n + spin * 0.4f
                    bullets.spawn(x, y, cos(a) * 130f, sin(a) * 130f, 4f, 1f, true, col) } }
            3 -> if (timers[slot] <= 0f) { timers[slot] = 0.10f
                for (k in 0 until 3) { val a = spin * 3.2f + k * 2.094f
                    bullets.spawn(x, y, cos(a) * 150f, sin(a) * 150f, 4f, 1f, true, col) } }
            4 -> if (timers[slot] <= 0f) { timers[slot] = 0.22f
                val a = atan2(py - y, px - x); bullets.spawn(x, y, cos(a) * 240f, sin(a) * 240f, 4f, 1f, true, col) }
            5 -> if (timers[slot] <= 0f) { timers[slot] = 1.2f
                val n = 24; val off = if ((t.toInt()) % 2 == 0) 0f else 0.13f
                for (k in 0 until n) { val a = k * 6.2832f / n + off
                    bullets.spawn(x, y, cos(a) * 115f, sin(a) * 115f, 4f, 1f, true, col) } }
        }
    }

    fun hit(dmg: Float): Boolean { hp -= dmg; if (hp <= 0f) { alive = false; pendingTaunt = def.defeat }; return !alive }
    fun phaseFrac() = (phaseIndex + 1f) / def.phases.size
}
