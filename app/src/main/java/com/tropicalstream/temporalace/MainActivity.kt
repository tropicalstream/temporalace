package com.tropicalstream.temporalace

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.tropicalstream.temporalace.audio.SoundEngine
import com.tropicalstream.temporalace.core.SaveManager
import com.tropicalstream.temporalace.core.Settings
import com.tropicalstream.temporalace.game.Game
import com.tropicalstream.temporalace.input.TrackpadGestureEngine
import com.tropicalstream.temporalace.net.CompanionServer
import com.tropicalstream.temporalace.render.GameRenderer
import com.tropicalstream.temporalace.ui.BinocularSbsLayout

class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var sound: SoundEngine
    private lateinit var game: Game
    private lateinit var renderer: GameRenderer
    private lateinit var view: GameView
    private val gestures = TrackpadGestureEngine()
    private var server: CompanionServer? = null

    private var running = false
    private var lastMs = 0L
    // raw movement-delta tracking (right pad only)
    private var lastX = 0f; private var lastY = 0f
    private var havePrev = false; private var firstAfterDown = false

    override fun attachBaseContext(newBase: Context) {
        val cfg = Configuration(newBase.resources.configuration).apply { densityDpi = DisplayMetrics.DENSITY_MEDIUM }
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        settings = Settings(this)
        sound = SoundEngine().also { it.setSfxVol(settings.sfxVol) }
        game = Game(settings, SaveManager(this), sound)
        renderer = GameRenderer(game)
        view = GameView(this, renderer)
        setContentView(BinocularSbsLayout(this).apply { setBackgroundColor(Color.BLACK); addView(view) })

        gestures.onTap = { game.onTap() }
        gestures.onDoubleTap = { game.onBack() }
        gestures.onLongTap = { game.onBomb() }
        gestures.onSwipeVertical = { d -> game.onNavV(d) }
        gestures.onSwipeHorizontal = { d -> game.onNavH(d) }

        runCatching { CompanionServer(CompanionServer.PORT, this).also { it.start(5000, false); server = it } }
            .onFailure { Log.w("TemporalAce", "server: ${it.message}") }
    }

    private fun immersive() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.setBackgroundColor(Color.BLACK)
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val target = if (settings.fpsCap >= 60) 15L else 32L
            if (now - lastMs >= target) {
                val dt = if (lastMs == 0L) 0.016f else (now - lastMs) / 1000f
                lastMs = now
                game.update(dt)
                view.invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onResume() {
        super.onResume()
        running = true; lastMs = 0L
        Choreographer.getInstance().removeFrameCallback(frame)
        Choreographer.getInstance().postFrameCallback(frame)
    }

    override fun onPause() {
        super.onPause()
        running = false
        game.onPauseSave()   // autosave + pause (sleep button fires this mid-wear)
    }

    override fun onDestroy() {
        super.onDestroy()
        gestures.release(); sound.release(); runCatching { server?.stop() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Consume BACK/ESCAPE so a temple long-press (which the X3 emits as BACK)
        // can NEVER exit the game — route it to pause/back instead. Leave via the
        // system launcher/home only.
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) game.onBack()
            return true
        }
        if (gestures.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("kept as a belt-and-suspenders guard against accidental exit")
    override fun onBackPressed() { game.onBack() }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!gestures.isLeftArmDevice(ev.deviceId)) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = ev.x; lastY = ev.y; havePrev = true; firstAfterDown = true }
                MotionEvent.ACTION_MOVE -> if (havePrev) {
                    val dx = ev.x - lastX; val dy = ev.y - lastY; lastX = ev.x; lastY = ev.y
                    if (firstAfterDown) firstAfterDown = false else game.onMoveDelta(dx, dy)  // drop first delta
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> havePrev = false
            }
        }
        if (gestures.onTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (gestures.onGenericMotion(ev)) return true
        return super.dispatchGenericMotionEvent(ev)
    }
}

/** The single logical 640×480 child that BinocularSbsLayout draws to both eyes. */
class GameView(context: Context, private val renderer: GameRenderer) : View(context) {
    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas, width, height)
    }
}
