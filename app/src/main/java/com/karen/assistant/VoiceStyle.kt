package com.karen.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object VoiceStyle {
    fun apply(context: Context, tts: TextToSpeech) {
        val prefs = context.getSharedPreferences("voice_style", Context.MODE_PRIVATE)
        tts.language = Locale("ru", "RU")
        val available = tts.voices.orEmpty().filter { it.locale.language == "ru" }.sortedByDescending { it.quality }
        val selected = available.firstOrNull { it.name == prefs.getString("voice", "") }
            ?: available.firstOrNull { !it.isNetworkConnectionRequired && (it.name.contains("ruf") || it.name.contains("female")) }
            ?: available.firstOrNull { !it.isNetworkConnectionRequired }
            ?: available.firstOrNull()
        selected?.let { tts.voice = it }
        tts.setSpeechRate(prefs.getFloat("rate", 0.94f))
        tts.setPitch(prefs.getFloat("pitch", 1.08f))
    }
}
