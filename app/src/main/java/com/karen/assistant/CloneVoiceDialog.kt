package com.karen.assistant

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.atomic.AtomicBoolean

class CloneVoiceDialog(private val context: Context, private val status: (String) -> Unit, private val pick: () -> Unit) {
    private val voice = ClonedVoice(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private fun label(text: String) = TextView(context).apply { this.text = text; setTextColor(0xFF501522.toInt()); setPadding(12, 12, 12, 12) }
    fun show() {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 12) }
        val state = label("Образец: ${if (voice.reference() != null) "добавлен" else "нет"}\nМодель: ${if (CloneModels.ready(context)) "готова" else "нужно скачать ~884 МБ"}\nКлонирование: ${if (voice.enabled) "включено" else "выключено"}")
        column.addView(label("Карен создаёт новые фразы голосом из образца. Всё на телефоне, без API и отправки голоса на сервер. Скорость и сходство экспериментальные. После загрузки модель работает без интернета.\n\nTelegram: скачайте видео в чате → «Поделиться» → «Карен». Нужен сам файл; ссылки t.me не поддерживаются. Выберите 3–15 секунд одного говорящего без музыки."))
        column.addView(state)
        val cancelled = AtomicBoolean(false)
        var downloading = false
        fun button(title: String, click: () -> Unit) { column.addView(Button(context).apply { text = title; setOnClickListener { click() } }) }
        button("ВЫБРАТЬ ВИДЕО ИЛИ АУДИО") { if (!downloading) pick() }
        button("СКАЧАТЬ МОДЕЛЬ (~884 МБ)") {
            if (downloading || CloneModels.ready(context)) return@button
            cancelled.set(false); downloading = true; state.text = "Загрузка… Не закрывайте приложение"
            Thread({
                try {
                    CloneModels.download(context.applicationContext, cancelled) { percent -> main.post { state.text = "Загрузка модели: $percent%" } }
                    main.post { downloading = false; state.text = "Модель скачана и проверена. Добавьте образец и включите голос" }
                } catch (e: Exception) { main.post { downloading = false; state.text = "${e.message}. Скачанные целые файлы сохранены" } }
            }, "karen-clone-model-download").start()
        }
        button("ВКЛЮЧИТЬ ГОЛОС ИЗ ОБРАЗЦА") {
            if (voice.reference() == null || !CloneModels.ready(context)) { state.text = "Нужны образец голоса и скачанная модель"; return@button }
            voice.enabled = true; state.text = "Карен будет говорить новые ответы голосом из образца"
        }
        button("ПРОСЛУШАТЬ НОВУЮ ФРАЗУ") {
            voice.speak("Привет! Я Карен. Это новая фраза, созданная на телефоне голосом из вашего образца.") { state.text = it; status(it) }
        }
        button("СТОП / ОТМЕНИТЬ ЗАГРУЗКУ") { cancelled.set(true); ClonedVoice.stop(); state.text = "Останавливаю. При загрузке ожидание сети может занять до 20 секунд" }
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
                    main.post { status("Образец сохранён. Откройте «Голос из видео», скачайте модель и включите голос") }
                } catch (e: Exception) { main.post { status("Не удалось извлечь голос: ${e.message?.take(180)}") } }
            }, "karen-video-voice-import").start()
            dialog.dismiss()
        } }
        dialog.show()
    }
}
