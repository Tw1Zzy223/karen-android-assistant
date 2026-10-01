package com.karen.assistant

import android.content.Context
import org.json.JSONArray

class OwnerVoice(context: Context) {
    private val prefs = context.getSharedPreferences("owner_voice", Context.MODE_PRIVATE)
    val enabled get() = prefs.getBoolean("enabled", false)
    fun samples(): List<FloatArray> = try {
        val json = JSONArray(prefs.getString("samples", "[]"))
        (0 until json.length()).map { index -> val row = json.getJSONArray(index); FloatArray(row.length()) { row.getDouble(it).toFloat() } }
    } catch (_: Exception) { emptyList() }
    fun save(samples: List<FloatArray>) {
        require(samples.size >= 3)
        val json = JSONArray()
        samples.forEach { row -> json.put(JSONArray(row.map { it.toDouble() })) }
        prefs.edit().putString("samples", json.toString()).putBoolean("enabled", true).apply()
    }
    fun accepts(voice: FloatArray) = SpeakerMath.accepts(samples(), voice)
    fun disable() { prefs.edit().putBoolean("enabled", false).apply() }
    fun clear() { prefs.edit().clear().apply() }
}
