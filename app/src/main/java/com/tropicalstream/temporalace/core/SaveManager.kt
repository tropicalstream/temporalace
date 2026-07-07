package com.tropicalstream.temporalace.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Snapshot of run progress for autosave/resume. Level-boundary granularity keeps
 *  restore honest and simple: a mistaken exit resumes at the current level's start
 *  with score, lives, and powerup loadout intact. */
data class RunSnapshot(
    val level: Int,
    val score: Int,
    val lives: Int,
    val loadout: List<String>,   // powerup ids held
    val seed: Long
)

class SaveManager(context: Context) {
    private val p = context.getSharedPreferences("temporalace_save", Context.MODE_PRIVATE)

    fun hasSave(): Boolean = p.contains("snap")

    fun save(s: RunSnapshot) {
        val o = JSONObject()
            .put("level", s.level).put("score", s.score).put("lives", s.lives)
            .put("seed", s.seed).put("loadout", JSONArray(s.loadout))
        p.edit().putString("snap", o.toString()).apply()
    }

    fun load(): RunSnapshot? {
        val raw = p.getString("snap", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("loadout") ?: JSONArray()
            val load = ArrayList<String>()
            for (i in 0 until arr.length()) load.add(arr.getString(i))
            RunSnapshot(o.getInt("level"), o.getInt("score"), o.getInt("lives"), load, o.optLong("seed", 1))
        }.getOrNull()
    }

    fun clear() = p.edit().remove("snap").apply()
}
