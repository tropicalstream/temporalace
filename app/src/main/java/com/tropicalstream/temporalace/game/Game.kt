package com.tropicalstream.temporalace.game

import com.tropicalstream.temporalace.audio.SoundEngine
import com.tropicalstream.temporalace.core.RunSnapshot
import com.tropicalstream.temporalace.core.SaveManager
import com.tropicalstream.temporalace.core.Settings
import com.tropicalstream.temporalace.render.Neon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class Pickup {
    var x = 0f; var y = 0f; var vx = -55f; var vy = 0f; val r = 9f
    var id = "shield"; var hue = 200f; var alive = true; var t = 0f
}

enum class Screen { TITLE, PLAY, PAUSE, SETTINGS, PORTAL, GAMEOVER, VICTORY }

/**
 * The whole game: fixed-timestep sim driven by MainActivity. Renderer reads these
 * fields. Autosaves at level boundaries, every 5s, and on demand (onPause).
 */
class Game(
    val settings: Settings,
    private val save: SaveManager,
    private val sound: SoundEngine
) {
    var w = 640; var h = 480; private var inited = false

    var screen = Screen.TITLE
    val player = Player()
    val bullets = Bullets()
    val enemies = ArrayList<Enemy>()
    val pickups = ArrayList<Pickup>()
    val particles = Particles()
    var boss: Boss? = null

    var level = 1; var score = 0
    var subtitle: String? = null; var subtitleT = 0f
    var portalT = 0f
    var flash = 0f
    var menuIndex = 0
    var settingRow = 0
    private var settingsReturn = Screen.TITLE
    private var rng = Random(1)
    private var runSeed = 1L

    private var quota = 0; private var spawned = 0
    private var spawnTimer = 0f
    private var bossSpawned = false
    private var saveTimer = 0f
    var starPhase = 0f

    fun onLayout(width: Int, height: Int) {
        w = width; h = height
        if (!inited) { player.reset(w, h); inited = true }
    }

    // ---------- top-level update ----------
    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceAtMost(0.05f)
        starPhase += dt
        flash = (flash - dt * 2f).coerceAtLeast(0f)
        if (subtitleT > 0f) subtitleT -= dt
        when (screen) {
            Screen.PLAY -> updatePlay(dt)
            Screen.PORTAL -> { particles.update(dt); portalT -= dt; if (portalT <= 0f) advanceAfterPortal() }
            else -> particles.update(dt)
        }
    }

    private fun updatePlay(dt: Float) {
        val slow = if (player.slowTimer > 0f) 0.45f else 1f
        val edt = dt * slow

        player.update(dt)
        autoFire(dt)
        directWaves(dt)

        // enemies
        var ei = 0
        while (ei < enemies.size) {
            val e = enemies[ei]
            e.update(edt, player.x, player.y, bullets, Eras.difficulty(level))
            if (!e.alive) enemies.removeAt(ei) else ei++
        }
        boss?.let { b ->
            b.update(edt, player.x, player.y, bullets, w, h)
            b.pendingTaunt?.let { showSub(it); b.pendingTaunt = null }
            if (b.spawnEscortWave) { b.spawnEscortWave = false; spawnFormation(2, EnemyKind.DIVER) }
        }

        steerHoming()
        bullets.update(edt, w, h)
        updatePickups(edt)
        particles.update(dt)

        collide()
        checkLevelComplete()

        saveTimer += dt
        if (saveTimer >= 5f) { saveTimer = 0f; autosave() }
    }

    // ---------- firing ----------
    private fun autoFire(dt: Float) {
        if (player.fireTimer > 0f) return
        player.fireTimer = player.fireInterval
        val col = Neon.hsv(190f, 0.6f, 1f)
        val bx = player.x + player.r; val by = player.y
        when (player.weapon) {
            null, "rapid" -> bullets.spawn(bx, by, 440f, 0f, 3.5f, 1f, false, col)
            "spread" -> for (o in -1..1) bullets.spawn(bx, by, 420f, o * 130f, 3.5f, 1f, false, col)
            "pierce" -> bullets.spawn(bx, by, 460f, 0f, 4.5f, 1.4f, false, Neon.hsv(300f, 0.8f, 1f), pierceCount = 4)
            "homing" -> for (o in -1..1) bullets.spawn(bx, by, 300f, o * 90f, 4f, 1f, false, Neon.hsv(140f, 0.8f, 1f), home = true)
            "beam" -> bullets.spawn(bx, by, 720f, 0f, 5.5f, 0.8f, false, Neon.hsv(5f, 0.9f, 1f), pierceCount = 99)
            "wave" -> { val yo = sin(player.thrusterPhase) * 60f; bullets.spawn(bx, by, 400f, yo, 5f, 1.2f, false, Neon.hsv(270f, 0.85f, 1f)) }
        }
        // option drones
        for (d in 1..player.drones) {
            val oy = player.y + (if (d == 1) -22f else 22f)
            bullets.spawn(player.x + 6f, oy, 430f, 0f, 3f, 0.7f, false, Neon.hsv(165f, 0.8f, 1f))
        }
        sound.laser()
    }

    private fun steerHoming() {
        for (i in 0 until bullets.capacity) {
            if (!bullets.alive[i] || bullets.hostile[i] || !bullets.homing[i]) continue
            var best: Any? = null; var bd = Float.MAX_VALUE; var tx = 0f; var ty = 0f
            for (e in enemies) { val d = hypot(e.x - bullets.x[i], e.y - bullets.y[i]); if (d < bd) { bd = d; tx = e.x; ty = e.y; best = e } }
            boss?.let { val d = hypot(it.x - bullets.x[i], it.y - bullets.y[i]); if (d < bd) { bd = d; tx = it.x; ty = it.y; best = it } }
            if (best != null) {
                val a = atan2(ty - bullets.y[i], tx - bullets.x[i])
                val sp = 340f
                bullets.vx[i] += (cos(a) * sp - bullets.vx[i]) * 0.09f
                bullets.vy[i] += (sin(a) * sp - bullets.vy[i]) * 0.09f
            }
        }
    }

    // ---------- spawn director ----------
    private fun directWaves(dt: Float) {
        val bossLvl = Eras.isBoss(level)
        spawnTimer -= dt
        if (spawned < quota && spawnTimer <= 0f) {
            val kinds = enumValues<EnemyKind>()
            val kind = kinds[rng.nextInt(if (level < 3) 2 else kinds.size)]
            spawnFormation(2 + rng.nextInt(3), kind)
            spawnTimer = (1.6f - Eras.difficulty(level) * 0.2f).coerceAtLeast(0.5f)
        }
        if (bossLvl && spawned >= quota && !bossSpawned && enemies.isEmpty()) {
            bossSpawned = true
            val b = Boss(BossDefs.forLevel(level), Eras.difficulty(level)); b.place(w, h); boss = b
            sound.portal()
        }
        if (Eras.isSubBoss(level) && spawned >= quota / 2 && enemies.count { it.kind == EnemyKind.TURRET } == 0 && rng.nextFloat() < 0.02f) {
            spawnFormation(1, EnemyKind.TURRET)  // escorting sub-boss turret
        }
    }

    private fun spawnFormation(count: Int, kind: EnemyKind) {
        val era = Eras.forLevel(level)
        val baseY = 60f + rng.nextFloat() * (h - 120f)
        for (i in 0 until count) {
            val e = Enemy()
            e.spawn(kind, w + 20f + i * 34f, (baseY + i * 30f) % (h - 80f) + 50f, era.enemyHue, Eras.difficulty(level))
            enemies.add(e)
            spawned++
        }
    }

    // ---------- pickups ----------
    private fun updatePickups(dt: Float) {
        var i = 0
        while (i < pickups.size) {
            val pk = pickups[i]
            pk.t += dt
            if (player.magnetTimer > 0f && hypot(player.x - pk.x, player.y - pk.y) < 160f) {
                val a = atan2(player.y - pk.y, player.x - pk.x); pk.vx += cos(a) * 400f * dt; pk.vy += sin(a) * 400f * dt
            }
            pk.x += pk.vx * dt; pk.y += pk.vy * dt; pk.vx *= 0.99f; pk.vy *= 0.98f
            if (pk.x < -20f) pk.alive = false
            if (!pk.alive) pickups.removeAt(i) else i++
        }
    }

    private fun dropMaybe(x: Float, y: Float, chance: Float) {
        if (rng.nextFloat() > chance) return
        val def = Powerups.roll(rng)
        val pk = Pickup(); pk.x = x; pk.y = y; pk.id = def.id; pk.hue = def.hue
        pk.vx = -40f - rng.nextFloat() * 30f; pk.vy = (rng.nextFloat() - 0.5f) * 40f
        pickups.add(pk)
    }

    // ---------- collisions ----------
    private fun collide() {
        // player bullets vs enemies / boss
        for (i in 0 until bullets.capacity) {
            if (!bullets.alive[i] || bullets.hostile[i]) continue
            val bx = bullets.x[i]; val by = bullets.y[i]; val br = bullets.r[i]
            for (e in enemies) {
                if (!e.alive) continue
                if (hypot(e.x - bx, e.y - by) < e.r + br) {
                    particles.burst(bx, by, bullets.color[i], 4, 120f, 0.6f, 0.35f)
                    if (e.hit(bullets.dmg[i])) killEnemy(e)
                    if (bullets.pierce[i] > 0) bullets.pierce[i]-- else bullets.alive[i] = false
                    break
                }
            }
            if (!bullets.alive[i]) continue
            boss?.let { b ->
                if (b.alive && hypot(b.x - bx, b.y - by) < b.r + br) {
                    particles.burst(bx, by, bullets.color[i], 5, 140f, 0.7f, 0.35f)
                    if (b.hit(bullets.dmg[i])) killBoss(b)
                    if (bullets.pierce[i] > 0) bullets.pierce[i]-- else bullets.alive[i] = false
                }
            }
        }
        // enemy bullets vs player
        for (i in 0 until bullets.capacity) {
            if (!bullets.alive[i] || !bullets.hostile[i]) continue
            if (hypot(player.x - bullets.x[i], player.y - bullets.y[i]) < player.r + bullets.r[i]) {
                bullets.alive[i] = false; hurtPlayer()
            }
        }
        // enemy bodies vs player
        for (e in enemies) if (e.alive && e.dist(player.x, player.y) < e.r + player.r) { e.hit(3f); if (!e.alive) killEnemy(e); hurtPlayer() }
        boss?.let { if (it.alive && hypot(it.x - player.x, it.y - player.y) < it.r + player.r) hurtPlayer() }
        // pickups vs player
        var i = 0
        while (i < pickups.size) {
            val pk = pickups[i]
            if (hypot(player.x - pk.x, player.y - pk.y) < player.r + pk.r + 6f) {
                Powerups.def(pk.id)?.let { player.apply(it); showSub(it.name); particles.burst(pk.x, pk.y, Neon.hsv(pk.hue, 0.85f, 1f), 18, 160f, 1f, 0.7f) }
                sound.powerup(); pk.alive = false; pickups.removeAt(i)
            } else i++
        }
    }

    private fun killEnemy(e: Enemy) {
        e.alive = false
        score += e.score * player.scoreMult
        particles.burst(e.x, e.y, Neon.hsv(e.hue, 0.9f, 1f), 22, 200f, 1f, 0.7f)
        sound.explode()
        dropMaybe(e.x, e.y, if (e.kind == EnemyKind.TURRET) 0.55f else 0.16f)
    }

    private fun killBoss(b: Boss) {
        score += 5000 * player.scoreMult
        for (k in 0 until 8) particles.burst(b.x + (rng.nextFloat() - 0.5f) * 60f, b.y + (rng.nextFloat() - 0.5f) * 60f, Neon.hsv(b.def.hue + k * 20f, 0.9f, 1f), 26, 260f, 1.2f, 1f)
        flash = 1f; sound.explode()
        boss = null
    }

    private fun hurtPlayer() {
        if (player.takeHit()) {
            particles.burst(player.x, player.y, Neon.hsv(0f, 0.9f, 1f), 40, 260f, 1.2f, 1f)
            flash = 1f; sound.explode()
            player.lives--
            if (player.lives < 0) { screen = Screen.GAMEOVER; menuIndex = 0; save.clear() }
            else { player.reset(w, h, keepMeta = true) }
        } else sound.hit()
    }

    // ---------- level flow ----------
    private fun checkLevelComplete() {
        val bossLvl = Eras.isBoss(level)
        val done = if (bossLvl) bossSpawned && boss == null else spawned >= quota && enemies.isEmpty()
        if (done) {
            if (level >= Eras.TOTAL) { screen = Screen.VICTORY; menuIndex = 0; save.clear() }
            else { screen = Screen.PORTAL; portalT = 2.2f; sound.portal() }
        }
    }

    private fun advanceAfterPortal() {
        level++
        startLevel(level)
    }

    private fun startLevel(lvl: Int) {
        level = lvl
        enemies.clear(); pickups.clear(); bullets.clear(); boss = null
        bossSpawned = false; spawned = 0
        quota = if (Eras.isBoss(lvl)) 6 + lvl / 4 else 8 + lvl
        spawnTimer = 1.2f
        player.reset(w, h, keepMeta = true)
        screen = Screen.PLAY
        showSub("${Eras.forLevel(lvl).name}  —  LEVEL $lvl")
        autosave()
    }

    private fun newRun() {
        runSeed = System.currentTimeMillis()
        rng = Random(runSeed)
        level = 1; score = 0
        player.reset(w, h)
        startLevel(1)
    }

    private fun continueRun() {
        val s = save.load() ?: return newRun()
        runSeed = s.seed
        rng = Random(s.seed)
        score = s.score; player.reset(w, h)
        player.lives = s.lives
        s.loadout.forEach { id -> Powerups.def(id)?.let { player.apply(it) } }
        startLevel(s.level)
    }

    private fun autosave() {
        save.save(RunSnapshot(level, score, player.lives, listOfNotNull(player.weapon), runSeed))
    }

    fun onPauseSave() { if (screen == Screen.PLAY) { autosave(); screen = Screen.PAUSE; menuIndex = 0 } }

    private fun showSub(s: String) { if (settings.subtitles) { subtitle = s; subtitleT = 3.2f } }

    // ---------- input ----------
    fun onMoveDelta(dx: Float, dy: Float) {
        if (screen == Screen.PLAY) player.moveDelta(dx, dy, settings.gain, settings.invertY, w, h)
    }

    fun onBomb() {
        if (screen != Screen.PLAY || player.bombs <= 0) return
        player.bombs--; player.invulnTimer = maxOf(player.invulnTimer, 1.2f); flash = 1f
        for (i in 0 until bullets.capacity) if (bullets.alive[i] && bullets.hostile[i]) bullets.alive[i] = false
        for (e in enemies) if (e.alive && e.hit(6f)) killEnemy(e)
        boss?.let { if (it.hit(60f)) killBoss(it) }
        particles.burst(player.x, player.y, Neon.hsv(190f, 0.7f, 1f), 60, 340f, 1.4f, 1.1f)
        sound.bomb()
    }

    fun onTap() {
        when (screen) {
            Screen.PLAY -> onPauseSave()
            Screen.TITLE -> confirmTitle()
            Screen.PAUSE -> confirmPause()
            Screen.SETTINGS -> confirmSetting()
            Screen.PORTAL -> {}
            Screen.GAMEOVER -> { if (menuIndex == 0) newRun() else toTitle() }
            Screen.VICTORY -> toTitle()
        }
    }

    fun onBack() {
        when (screen) {
            Screen.PLAY -> onPauseSave()
            Screen.PAUSE -> { screen = Screen.PLAY }
            Screen.SETTINGS -> { screen = settingsReturn; menuIndex = 0 }
            else -> {}
        }
    }

    fun onNavV(dir: Int) {
        val step = if (dir >= 0) 1 else -1
        when (screen) {
            Screen.SETTINGS -> settingRow = (settingRow + step + SETTING_ROWS) % SETTING_ROWS
            Screen.TITLE -> menuIndex = (menuIndex + step + titleCount()) % titleCount()
            Screen.PAUSE -> menuIndex = (menuIndex + step + 3) % 3
            Screen.GAMEOVER -> menuIndex = (menuIndex + step + 2) % 2
            else -> {}
        }
    }

    fun onNavH(dir: Int) { if (screen == Screen.SETTINGS) adjustSetting(dir) }

    private fun titleCount() = if (save.hasSave()) 3 else 2
    private fun toTitle() { screen = Screen.TITLE; menuIndex = 0; boss = null; enemies.clear(); bullets.clear(); pickups.clear() }

    private fun confirmTitle() {
        val hasSave = save.hasSave()
        when {
            menuIndex == 0 -> newRun()
            menuIndex == 1 && hasSave -> continueRun()
            else -> { settingsReturn = Screen.TITLE; screen = Screen.SETTINGS; settingRow = 0 }
        }
    }
    private fun confirmPause() {
        when (menuIndex) {
            0 -> screen = Screen.PLAY
            1 -> { settingsReturn = Screen.PAUSE; screen = Screen.SETTINGS; settingRow = 0 }
            else -> toTitle()
        }
    }

    // ---------- settings ----------
    val settingRows = listOf("STEERING", "SENSITIVITY", "INVERT Y", "SFX VOLUME", "MUSIC VOLUME", "SUBTITLES", "REDUCE FLASH", "COLORBLIND", "FPS CAP", "WIPE SAVE", "BACK")
    private val SETTING_ROWS get() = settingRows.size

    private fun confirmSetting() {
        when (settingRows[settingRow]) {
            "INVERT Y" -> settings.invertY = !settings.invertY
            "STEERING" -> settings.headSteer = !settings.headSteer
            "SUBTITLES" -> settings.subtitles = !settings.subtitles
            "REDUCE FLASH" -> settings.reduceFlash = !settings.reduceFlash
            "WIPE SAVE" -> save.clear()
            "BACK" -> { screen = settingsReturn; menuIndex = 0 }
            else -> {}
        }
    }
    private fun adjustSetting(dir: Int) {
        val d = if (dir >= 0) 1 else -1
        when (settingRows[settingRow]) {
            "SENSITIVITY" -> settings.gain = (settings.gain + d * 0.2f).coerceIn(0.8f, 4f)
            "SFX VOLUME" -> settings.sfxVol = (settings.sfxVol + d * 0.1f).coerceIn(0f, 1f).also { sound.setSfxVol(it) }
            "MUSIC VOLUME" -> settings.musicVol = (settings.musicVol + d * 0.1f).coerceIn(0f, 1f)
            "COLORBLIND" -> settings.colorblind = (settings.colorblind + d + 3) % 3
            "FPS CAP" -> settings.fpsCap = if (settings.fpsCap == 30) 60 else 30
            "INVERT Y" -> settings.invertY = !settings.invertY
            "STEERING" -> settings.headSteer = !settings.headSteer
            "SUBTITLES" -> settings.subtitles = !settings.subtitles
            "REDUCE FLASH" -> settings.reduceFlash = !settings.reduceFlash
        }
    }

    fun settingValue(row: String): String = when (row) {
        "STEERING" -> if (settings.headSteer) "HEAD" else "PAD"
        "SENSITIVITY" -> "%.1f".format(settings.gain)
        "INVERT Y" -> onOff(settings.invertY)
        "SFX VOLUME" -> "${(settings.sfxVol * 100).toInt()}%"
        "MUSIC VOLUME" -> "${(settings.musicVol * 100).toInt()}%"
        "SUBTITLES" -> onOff(settings.subtitles)
        "REDUCE FLASH" -> onOff(settings.reduceFlash)
        "COLORBLIND" -> listOf("OFF", "DEUTERAN", "TRITAN")[settings.colorblind.coerceIn(0, 2)]
        "FPS CAP" -> "${settings.fpsCap}"
        else -> ""
    }
    private fun onOff(b: Boolean) = if (b) "ON" else "OFF"
    fun hasSave() = save.hasSave()
}
