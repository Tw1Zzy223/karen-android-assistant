package com.karen.assistant

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AudioAnswers(private val context: Context) {
    private val prefs = context.getSharedPreferences("audio_answers", 0)
    fun all(): Map<String, String> = try {
        val json = JSONObject(prefs.getString("entries", "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getString(it) }
    } catch (_: Exception) { emptyMap() }
    fun import(uri: Uri, label: String): String {
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "audio_answers").apply { mkdirs() }
        val output = File(dir, id)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                output.outputStream().use { stream ->
                    val bytes = ByteArray(8192); var total = 0
                    while (true) {
                        val read = input.read(bytes); if (read < 0) break
                        total += read; check(total <= 15 * 1024 * 1024) { "Audio exceeds 15 MB" }; stream.write(bytes, 0, read)
                    }
                    check(total > 0)
                }
            } ?: error("No input")
            val entries = all().toMutableMap(); entries[id] = label
            prefs.edit().putString("entries", JSONObject(entries as Map<*, *>).toString()).apply()
            return id
        } catch (e: Exception) { output.delete(); throw e }
    }
    fun play(id: String, onError: () -> Unit) {
        stop()
        if (!all().containsKey(id)) { onError(); return }
        try {
            val player = MediaPlayer()
            current = player
            player.setDataSource(File(context.filesDir, "audio_answers/$id").absolutePath)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { if (current === it) stop() }
            player.setOnErrorListener { p, _, _ -> if (current === p) stop(); onError(); true }
            player.prepareAsync()
        } catch (_: Exception) { stop(); onError() }
    }
    companion object {
        private var current: MediaPlayer? = null
        fun stop() { val old = current; current = null; try { old?.release() } catch (_: Exception) {} }
    }
}
