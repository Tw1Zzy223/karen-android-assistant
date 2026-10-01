package com.karen.assistant

import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class OwnerVoiceDialog(private val activity: AppCompatActivity) {
    fun show() {
        val profile = OwnerVoice(activity)
        val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; val p = (20 * resources.displayMetrics.density).toInt(); setPadding(p, p, p, p) }
        val status = TextView(activity).apply {
            setTextColor(0xFF501522.toInt())
            text = if (profile.enabled) "Фильтр голоса включён. Голосовые команды проверяются локально."
                else "Запишите три образца своим обычным голосом. Потребуется загрузить около 58 МБ моделей Vosk. Голосовой отпечаток хранится на телефоне. Фильтр экспериментальный: возможны ошибки, он не защищает от записи голоса и не заменяет блокировку телефона. Текстовые команды и кнопки остаются доступными."
        }
        val record = Button(activity).apply { text = "ПОДГОТОВИТЬ МОДЕЛИ / ЗАПИСАТЬ ОБРАЗЦЫ" }
        layout.addView(status); layout.addView(record)
        val dialog = AlertDialog.Builder(activity).setTitle("Только мой голос").setView(layout)
            .setNegativeButton("Закрыть", null).setNeutralButton("Отключить фильтр") { _, _ -> profile.disable() }
            .setPositiveButton("Удалить образцы") { _, _ -> profile.clear() }.create()
        val samples = mutableListOf<FloatArray>()
        var input: VoiceInput? = null
        var dismissed = false
        var preparing = false
        val main = Handler(Looper.getMainLooper())
        val phrases = listOf("Карен это мой голос я разрешаю тебе выполнять мои команды", "Сегодня я настраиваю помощника на своём телефоне говорю спокойно и чётко", "Карен пожалуйста слушай мой голос и помогай управлять моим телефоном")
        fun prompt() { status.text = "Образец ${samples.size + 1}/3. Нажмите запись, дождитесь «Слушаю» и скажите:\n\n${phrases[samples.size]}"; record.text = "ЗАПИСАТЬ ОБРАЗЕЦ ${samples.size + 1}" }
        fun configure() {
            input = VoiceInput(activity, { _, message -> if (!dismissed) status.text = message }, {}, { vector ->
                if (samples.isNotEmpty() && samples.none { SpeakerMath.similarity(it, vector) >= 0.45 }) {
                    main.post { if (!dismissed) status.text = "Образец сильно отличается. Говорите тем же голосом в тихом месте и запишите снова" }; return@VoiceInput
                }
                samples.add(vector)
                if (samples.size >= 3) { profile.save(samples); record.isEnabled = false; main.post { if (!dismissed) status.text = "Образцы сохранены. Фильтр включён: при голосовой команде проверяется говорящий. Для коротких команд добавляйте «Карен, пожалуйста»." } }
                else main.postDelayed({ if (!dismissed) prompt() }, 500)
            })
            prompt()
        }
        record.setOnClickListener {
            if (input != null) { input!!.toggle(); return@setOnClickListener }
            if (preparing) return@setOnClickListener
            preparing = true; record.isEnabled = false
            Thread({
                try {
                    OfflineModels.prepare(activity.applicationContext) { message -> main.post { if (!dismissed) status.text = message } }
                    main.post { if (!dismissed) { record.isEnabled = true; preparing = false; configure() } }
                } catch (_: Throwable) {
                    main.post { if (!dismissed) { preparing = false; record.isEnabled = true; status.text = "Не удалось подготовить модели. Проверьте интернет и свободную память, затем повторите." } }
                }
            }, "karen-model-setup").start()
        }
        dialog.setOnDismissListener { dismissed = true; input?.destroy() }
        dialog.show()
    }
}
