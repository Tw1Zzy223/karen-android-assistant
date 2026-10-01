package com.karen.assistant

import android.content.Context
import org.json.JSONObject

class CustomCommands(context: Context) {
    private val prefs = context.getSharedPreferences("custom_commands", Context.MODE_PRIVATE)
    fun all(): Map<String, String> = try {
        val json = JSONObject(prefs.getString("commands", "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getString(it) }
    } catch (_: Exception) { emptyMap() }
    fun save(phrase: String, action: String) {
        val normalized = IntentParser.normalize(phrase)
        require(normalized.isNotBlank() && action.isNotBlank())
        val commands = all().toMutableMap(); commands[normalized] = action
        prefs.edit().putString("commands", JSONObject(commands as Map<*, *>).toString()).apply()
    }
    fun remove(phrase: String) {
        val commands = all().toMutableMap(); commands.remove(phrase)
        prefs.edit().putString("commands", JSONObject(commands as Map<*, *>).toString()).apply()
    }
    fun resolve(phrase: String): String? = PhraseAliases.resolve(all(), phrase)
}
