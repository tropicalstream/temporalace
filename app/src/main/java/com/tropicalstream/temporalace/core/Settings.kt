package com.tropicalstream.temporalace.core

import android.content.Context

/** Persisted settings (SharedPreferences). Applied live by the game/render. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("temporalace_settings", Context.MODE_PRIVATE)

    var gain: Float get() = p.getFloat("gain", 1.4f); set(v) { p.edit().putFloat("gain", v).apply() }
    var invertY: Boolean get() = p.getBoolean("invertY", false); set(v) { p.edit().putBoolean("invertY", v).apply() }
    var headSteer: Boolean get() = p.getBoolean("headSteer", false); set(v) { p.edit().putBoolean("headSteer", v).apply() }
    var sfxVol: Float get() = p.getFloat("sfx", 0.8f); set(v) { p.edit().putFloat("sfx", v).apply() }
    var musicVol: Float get() = p.getFloat("music", 0.7f); set(v) { p.edit().putFloat("music", v).apply() }
    var subtitles: Boolean get() = p.getBoolean("subs", true); set(v) { p.edit().putBoolean("subs", v).apply() }
    var reduceFlash: Boolean get() = p.getBoolean("reduceFlash", false); set(v) { p.edit().putBoolean("reduceFlash", v).apply() }
    var colorblind: Int get() = p.getInt("cb", 0); set(v) { p.edit().putInt("cb", v).apply() }   // 0 none,1 deuteran,2 tritan
    var fpsCap: Int get() = p.getInt("fps", 30); set(v) { p.edit().putInt("fps", v).apply() }
}
