package com.karen.assistant

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.atomic.AtomicBoolean

class CloneVoiceDialog(private val context: Context, private val status: (String) -> Unit, private val pick: () -> Unit, private val gallery: () -> Unit) {
    private val voice = ClonedVoice(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private fun label(text: String) = TextView(context).apply { this.text = text; setTextColor(0xFF501522.toInt()); setPadding(12, 12, 12, 12) }
    fun show() {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 12) }
        val state = label("Образец: ${if (voice.reference() != null) "добавлен" else "нет"}\nМодель: ${if (CloneModels.ready(context)) "готова" else "встроена в APK, нужна подготовка"}\nКлонирование: ${if (voice.enabled) "включено" else "выключено"}")
        column.addView(label("Модель уже внутри APK. При первом запуске автоматически распаковывается и проверяется, интернет не нужен. Для распаковки нужен ещё ~1 ГБ свободной памяти.\n\n«Видео из галереи» открывает системную галерею видео. Telegram: скачайте видео → «Поделиться» → «Карен». Нужен сам файл, не ссылка t.me. Выберите 3–15 секунд одного говорящего без музыки.\n\nСинтез экспериментальный. Загрузка в память ограничена 90 секундами, создание речи — 120. «Стоп» завершает только процесс голоса, не приложение."))
        column.addView(state)
        val cancelled = AtomicBoolean(false)
        var downloading = false
        fun button(title: String, click: () -> Unit) { column.addView(Button(context).apply { text = title; setOnClickListener { click() } }) }
        button("ВИДЕО ИЗ ГАЛЕРЕИ") { gallery() }
        button("ВИДЕО ИЛИ АУДИО ИЗ ФАЙЛОВ") { pick() }
        button("ПОДГОТОВИТЬ ВСТРОЕННУЮ МОДЕЛЬ") {
            if (downloading || CloneModels.ready(context)) return@button
            cancelled.set(false); downloading = true; state.text = "Подготовка из APK… Интернет не используется"
            Thread({
                try {
                    CloneModels.prepare(context.applicationContext, cancelled) { percent -> main.post { state.text = "Подготовка модели из APK: $percent%" } }
                    main.post { downloading = false; state.text = "Встроенная модель проверена и готова. Добавьте образец и включите голос" }
                } catch (e: Exception) { main.post { downloading = false; state.text = "${e.message}. Готовые файлы сохранены" } }
            }, "karen-clone-model-download").start()
        }
        button("ВКЛЮЧИТЬ ГОЛОС ИЗ ОБРАЗЦА") {
            if (voice.reference() == null || !CloneModels.ready(context)) { state.text = "Нужны образец голоса и подготовленная встроенная модель"; return@button }
            voice.enabled = true; state.text = "Карен будет говорить новые ответы голосом из образца"
        }
        button("ПРОСЛУШАТЬ НОВУЮ ФРАЗУ") {
            voice.speak("Привет! Я Карен. Это новая фраза, созданная на телефоне голосом из вашего образца.") { state.text = it; status(it) }
        }
        button("СТОП") { cancelled.set(true); ClonedVoice.stop(); state.text = "Озвучка остановлена. Подготовка остановится на следующем блоке файла" }
        button("ВЕРНУТЬ СИСТЕМНЫЙ ГОЛОС") { voice.enabled = false; ClonedVoice.stop(); state.text = "Клонирование выключено. Образец сохранён" }
        button("УДАЛИТЬ ОБРАЗЕЦ") { voice.clear(); state.text = "Образец удалён, клонирование выключено" }
        val scroll = ScrollView(context).apply { addView(column) }
        AlertDialog.Builder(context).setTitle("Голос Карен из видео").setView(scroll).setNegativeButton("Закрыть", null).show()
            .setOnDismissListener { cancelled.set(true); ClonedVoice.stop() }
    }
    fun import(uri: Uri) {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 12) }
        column.addView(label("Выберите фрагмент с чистой речью одного человека. Начало — секунды от начала видео; длина — от 3 до 15 секунд. Видео не отправляется на сервер."))
        val start = EditText(context).apply { hint = "Начало, секунды"; inputType = 2; setText("0"); setTextColor(0xFF501522.toInt()) }
        val duration = EditText(context).apply { hint = "Длительность, секунды"; inputType = 2; setText("10"); setTextColor(0xFF501522.toInt()) }
        val consent = CheckBox(context).apply { text = "Это мой голос, либо у меня есть разрешение на его копирование"; setTextColor(0xFF501522.toInt()) }
        column.addView(start); column.addView(duration); column.addView(consent)
        val dialog = AlertDialog.Builder(context).setTitle("Извлечь образец голоса").setView(column).setPositiveButton("Извлечь", null).setNegativeButton("Отмена", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val offset = start.text.toString().toIntOrNull()
            val length = duration.text.toString().toIntOrNull()
            if (!consent.isChecked) { consent.error = "Подтвердите право использовать голос"; return@setOnClickListener }
            if (offset == null || offset !in 0..3600) { start.error = "От 0 до 3600 секунд"; return@setOnClickListener }
            if (length == null || length !in 3..15) { duration.error = "От 3 до 15 секунд"; return@setOnClickListener }
            ClonedVoice.stop(); status("Извлекаю звук из видео…")
            Thread({
                try {
                    val samples = VideoVoiceImporter.decode(context.applicationContext, uri, offset, length)
                    voice.save(samples)
                    main.post { status("Образец сохранён. Откройте «Голос из видео» и включите голос после подготовки модели") }
                } catch (e: Exception) { main.post { status("Не удалось извлечь голос: ${e.message?.take(180)}") } }
            }, "karen-video-voice-import").start()
            dialog.dismiss()
        } }
        dialog.show()
    }
}
