package com.karen.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandEngine(private val context: Context, private val speak: (String) -> Unit) {
    fun run(raw: String) {
        val text = raw.lowercase(Locale("ru"))
        when {
            "сколько времени" in text || "который час" in text -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                speak("Сейчас $time")
            }
            "громк" in text -> setVolume(text)
            "ярк" in text -> setBrightness(text)
            "скрин" in text || "снимок экрана" in text -> openCapture(MainActivity.ACTION_SCREENSHOT)
            "запись" in text && ("экран" in text || "видео" in text) -> openCapture(MainActivity.ACTION_RECORD)
            "закрой" in text || "сверни" in text -> {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Перехожу на главный экран")
            }
            "открой" in text || "запусти" in text -> openApp(text)
            else -> speak("Я пока понимаю команды открытия приложений, громкости, яркости, времени, скриншота и записи экрана.")
        }
    }

    private fun setVolume(text: String) {
        val percent = Regex("\\d{1,3}").find(text)?.value?.toIntOrNull()?.coerceIn(0, 100)
        if (percent == null) { speak("Назовите громкость от нуля до ста"); return }
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, 0)
        speak("Громкость $percent процентов")
    }

    private fun setBrightness(text: String) {
        val percent = Regex("\\d{1,3}").find(text)?.value?.toIntOrNull()?.coerceIn(1, 100)
        if (percent == null) { speak("Назовите яркость от одного до ста"); return }
        if (!Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("Нужно разрешить изменение системных настроек")
            return
        }
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, percent * 255 / 100)
        speak("Яркость $percent процентов")
    }

    private fun openCapture(action: String) {
        context.startActivity(Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        speak("Нужно подтвердить действие в системном окне")
    }

    private fun openApp(text: String) {
        val requested = text.replace(Regex(".*?(открой|запусти)\\s+"), "").trim()
        val apps = context.packageManager.getInstalledApplications(0)
        val match = apps.firstOrNull { context.packageManager.getApplicationLabel(it).toString().lowercase(Locale("ru")).contains(requested) }
        if (match == null) { speak("Приложение $requested не найдено"); return }
        val launch = context.packageManager.getLaunchIntentForPackage(match.packageName)
        if (launch == null) { speak("Это приложение нельзя запустить"); return }
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        speak("Открываю ${context.packageManager.getApplicationLabel(match)}")
    }
}
