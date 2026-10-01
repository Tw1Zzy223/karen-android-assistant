package com.karen.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandEngine(private val context: Context, private val speak: (String) -> Unit) {
    fun run(raw: String) {
        // Resolve once: aliases cannot recursively call themselves.
        val action = CustomCommands(context).resolve(raw) ?: raw
        val command = IntentParser.parse(action)
        try {
            when (command.kind) {
                CommandKind.OPEN_APP -> openApp(command.argument)
                CommandKind.APP_SETTINGS -> openApp(command.argument, true)
                CommandKind.STORE -> openPlay(command.argument)
                CommandKind.TIME -> speak("Сейчас ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
                CommandKind.DATE -> speak("Сегодня ${SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ru")).format(Date())}")
                CommandKind.BATTERY -> {
                    val level = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    speak(if (level in 0..100) "Заряд батареи $level процентов" else "Android не передал уровень заряда")
                }
                CommandKind.VOLUME -> setVolume(command)
                CommandKind.BRIGHTNESS -> setBrightness(command)
                CommandKind.SCREENSHOT -> capture(MainActivity.ACTION_SCREENSHOT)
                CommandKind.RECORD -> capture(MainActivity.ACTION_RECORD)
                CommandKind.STOP_RECORD -> { context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.STOP)); speak("Останавливаю запись") }
                CommandKind.HOME -> { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Приложение свёрнуто") }
                CommandKind.SCAN_APPS -> {
                    val apps = AppCatalog(context).scan()
                    speak("Найдено ${apps.size} пакетов приложений. ${apps.count { it.launch != null }} можно запустить. Список доступен по кнопке «Все приложения».")
                }
                CommandKind.ANSWER -> speak(command.argument)
                CommandKind.GREETING -> speak("Привет. Я рядом. Скажи, что нужно сделать.")
                CommandKind.HELP -> speak("Я открываю приложения, меняю громкость и яркость, делаю снимок и запись экрана. Можно задать свою фразу в разделе «Мои команды».")
                CommandKind.QUESTION -> speak(LocalAnswers.answer(raw, if (AssistantMode.iron(context)) "Джарвис" else "Карен") ?: "Без подключённой модели я отвечаю на простые вопросы о времени, дате, батарее и считаю примеры. Для других вопросов можно нажать «Поиск ответа».")
                CommandKind.UNKNOWN -> speak("Не уверена, что нужно сделать. Например: сделай потише, открой телеграм или скачай приложение из Google Play. Свои фразы можно добавить в «Мои команды».")
            }
        } catch (_: SecurityException) { speak("Для команды нужно разрешение. Откройте настройки Карен") }
        catch (_: Exception) { speak("Команду не удалось выполнить. Попробуйте кнопки управления в Карен") }
    }
    companion object { internal fun percent(text: String): Int? = IntentParser.number(text) }
    private fun setVolume(command: ParsedCommand) {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max.coerceAtLeast(1)
        val value = (command.percent ?: if (command.delta != 0) current + command.delta else { speak("На сколько установить громкость? Например: звук на половину или громкость пятьдесят"); return }).coerceIn(0, 100)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * value / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
        speak("Громкость $value процентов")
    }
    private fun setBrightness(command: ParsedCommand) {
        if (!Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("Включите разрешение изменения настроек, затем повторите команду"); return
        }
        val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
        val value = (command.percent ?: if (command.delta != 0) current + command.delta else { speak("На сколько установить яркость? Например: яркость семьдесят"); return }).coerceIn(1, 100)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val success = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value * 255 / 100)
        speak(if (success) "Яркость $value процентов" else "Android не разрешил изменить яркость")
    }
    private fun capture(action: String) { context.startActivity(Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun openPlay(query: String) {
        if (query.isBlank()) { speak("Какое приложение найти в Google Play?"); return }
        val name = AppNames.canonical(query)
        val encoded = Uri.encode(name)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps")).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        speak("Открываю Google Play. Выберите приложение и нажмите установить.")
    }
    private fun openApp(query: String, settings: Boolean = false) {
        if (query.isBlank()) { speak("Назовите приложение или выберите его в списке"); return }
        val candidates = AppCatalog(context).candidates(query)
        if (candidates.isEmpty()) { speak("Не нашла $query. Посмотрите раздел «Все приложения»"); return }
        val best = candidates.first()
        if (candidates.size > 1 && candidates[1].second - best.second < 0.08 && context is Activity) {
            val options = candidates.take(6)
            AlertDialog.Builder(context).setTitle("Какое приложение открыть?")
                .setItems(options.map { it.first.label + " · " + it.first.packageName }.toTypedArray()) { _, i -> launch(options[i].first, settings) }
                .setNegativeButton("Отмена", null).show()
            speak("Нашла несколько похожих приложений. Выберите нужное.")
        } else launch(best.first, settings)
    }
    private fun launch(app: InstalledApp, settings: Boolean) {
        if (settings) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("Открываю настройки ${app.label}")
        } else if (app.launch != null) {
            context.startActivity(app.launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("Открываю ${app.label}")
        } else speak("У ${app.label} нет отдельного экрана запуска. Его системные настройки доступны в списке приложений.")
    }
}
