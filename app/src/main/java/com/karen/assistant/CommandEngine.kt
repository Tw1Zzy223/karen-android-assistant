package com.karen.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandEngine(private val context: Context, private val speak: (String) -> Unit) {
    fun run(raw: String) {
        val text = raw.lowercase(Locale.ROOT).replace('ё', 'е').trim()
        try {
            when {
                "открой" in text || "запусти" in text -> openApp(text)
                "время" in text || "времени" in text || "час" in text -> speak("Сейчас ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
                "громк" in text || "громче" in text || "звук" in text || "тише" in text -> setVolume(text)
                "ярк" in text -> setBrightness(text)
                "скрин" in text || "снимок экрана" in text -> capture(MainActivity.ACTION_SCREENSHOT)
                "запис" in text && ("останов" in text || "выключ" in text) -> { context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.STOP)); speak("Останавливаю запись") }
                "запис" in text -> capture(MainActivity.ACTION_RECORD)
                "закрой" in text || "сверни" in text -> { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Приложение свёрнуто") }
                "привет" in text -> speak("Привет! Я Карен. Могу открыть приложение, изменить громкость и яркость или назвать время.")
                else -> speak("Попробуйте: открой Telegram, громкость пятьдесят, сделай тише или яркость семьдесят")
            }
        } catch (_: SecurityException) { speak("Для команды нужно разрешение. Откройте настройки Карен") }
        catch (_: Exception) { speak("Команду не удалось выполнить. Попробуйте кнопки управления в Карен") }
    }
    companion object {
    internal fun percent(text: String): Int? {
        Regex("\\d{1,3}").find(text)?.value?.toIntOrNull()?.let { return it.coerceIn(0, 100) }
        val numbers = mapOf("ноль" to 0, "один" to 1, "два" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19, "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90, "сто" to 100)
        val found = text.split(Regex("[^а-я]+")).mapNotNull { numbers[it] }
        return if (found.isEmpty()) null else found.sum().coerceIn(0, 100)
    }
    }
    private fun setVolume(text: String) {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max.coerceAtLeast(1)
        val value = when {
            "без звука" in text || "выключи звук" in text -> 0
            "максим" in text -> 100
            else -> percent(text) ?: when { "тише" in text || "уменьш" in text -> current - 15; "громче" in text || "увелич" in text -> current + 15; else -> { speak("Скажите громкость и число от нуля до ста"); return } }
        }.coerceIn(0, 100)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * value / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
        speak("Громкость $value процентов")
    }
    private fun setBrightness(text: String) {
        if (!Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("Включите разрешение изменения настроек, затем повторите команду"); return
        }
        val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
        val value = (percent(text) ?: when { "уменьш" in text -> current - 15; "увелич" in text -> current + 15; else -> { speak("Скажите яркость и число от одного до ста"); return } }).coerceIn(1, 100)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val success = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value * 255 / 100)
        speak(if (success) "Яркость $value процентов" else "Android не разрешил изменить яркость")
    }
    private fun capture(action: String) { context.startActivity(Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun openApp(text: String) {
        val requested = text.replace(Regex("^.*?(открой|запусти)\\s+"), "").replace("приложение ", "").trim()
        if (requested.isBlank()) { speak("Назовите приложение"); return }
        val aliases = mapOf("телеграм" to "telegram", "телеграмм" to "telegram", "ютуб" to "youtube", "ватсап" to "whatsapp", "вацап" to "whatsapp", "хром" to "chrome", "вк" to "vk")
        val name = aliases[requested] ?: requested
        val apps = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val match = apps.firstOrNull { it.loadLabel(context.packageManager).toString().lowercase(Locale.ROOT) == name }
            ?: apps.firstOrNull { it.loadLabel(context.packageManager).toString().lowercase(Locale.ROOT).contains(name) || it.activityInfo.packageName.lowercase(Locale.ROOT).contains(name) }
        if (match == null) { speak("Не нашла $requested. Выберите приложение в списке Карен"); return }
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(match.activityInfo.packageName, match.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        speak("Открываю ${match.loadLabel(context.packageManager)}")
    }
}
