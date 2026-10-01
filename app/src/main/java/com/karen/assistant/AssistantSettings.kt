package com.karen.assistant

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class AssistantSettings(private val activity: AppCompatActivity, private val tts: TextToSpeech) {
    private fun field(hintText: String) = EditText(activity).apply { hint = hintText; setTextColor(AssistantMode.textColor(activity)); setHintTextColor(AssistantMode.hintColor(activity)) }
    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad / 2, pad, pad / 2)
    }
    fun commands() {
        val store = CustomCommands(activity)
        val entries = store.all().toList()
        AlertDialog.Builder(activity).setTitle("Мои команды")
            .setItems(entries.map { "${it.first} → ${it.second}" }.toTypedArray()) { _, index ->
                val item = entries[index]
                AlertDialog.Builder(activity).setTitle(item.first).setMessage(item.second)
                    .setPositiveButton("Изменить") { _, _ -> edit(item.first, item.second) }
                    .setNeutralButton("Удалить") { _, _ -> store.remove(item.first); commands() }
                    .setNegativeButton("Назад", null).show()
            }.setPositiveButton("Добавить") { _, _ -> edit() }.setNegativeButton("Закрыть", null).show()
    }
    private fun edit(oldPhrase: String = "", oldAction: String = "") {
        val layout = column()
        val phrase = field("Своя фраза, например «мне скучно»").apply { setText(oldPhrase) }
        val type = Spinner(activity)
        val labels = arrayOf("Открыть приложение", "Громкость (%)", "Яркость (%)", "Главный экран", "Скриншот", "Запись экрана", "Остановить запись", "Назвать время", "Найти в Google Play", "Произнести мой ответ", "Мой аудиоответ")
        type.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
        val target = AutoCompleteTextView(activity).apply {
            hint = "Название, число или текст ответа"; setTextColor(AssistantMode.textColor(activity)); threshold = 1
            setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, AppCatalog(activity).scan().filter { it.launch != null }.map { it.label }))
        }
        // Existing entries can also be edited as a canonical command.
        val canonical = field("Или готовая команда: «открой Telegram»").apply { setText(oldAction) }
        val audioEntries = AudioAnswers(activity).all().toList()
        val audioChoices = Spinner(activity).apply { adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, audioEntries.map { it.second }.ifEmpty { listOf("Сначала импортируйте аудио") }) }
        layout.addView(phrase); layout.addView(type); layout.addView(target); layout.addView(canonical); layout.addView(audioChoices)
        val dialog = AlertDialog.Builder(activity).setTitle("Своя команда").setView(layout).setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = target.text.toString().trim()
                val command = canonical.text.toString().trim().ifBlank {
                    when (type.selectedItemPosition) {
                        0 -> "открой $value"; 1 -> "громкость $value"; 2 -> "яркость $value"; 3 -> "сверни приложение"
                        4 -> "сделай скриншот"; 5 -> "запиши экран"; 6 -> "останови запись"; 7 -> "сколько времени"
                        8 -> "скачай $value"; 9 -> "ответ: $value"; else -> "аудио:" + (audioEntries.getOrNull(audioChoices.selectedItemPosition)?.first ?: "")
                    }
                }
                if (phrase.text.isBlank()) { phrase.error = "Введите фразу"; return@setOnClickListener }
                val parsed = IntentParser.parse(command)
                val validAudio = command.startsWith("аудио:") && AudioAnswers(activity).all().containsKey(command.substringAfter(':'))
                if ((!validAudio && parsed.kind in setOf(CommandKind.UNKNOWN, CommandKind.QUESTION)) ||
                    (parsed.kind in setOf(CommandKind.OPEN_APP, CommandKind.STORE, CommandKind.ANSWER) && parsed.argument.isBlank()) ||
                    (parsed.kind in setOf(CommandKind.VOLUME, CommandKind.BRIGHTNESS) && parsed.percent == null && parsed.delta == 0)) {
                    canonical.error = "Укажите приложение, число или понятную команду"; return@setOnClickListener
                }
                val store = CustomCommands(activity)
                if (oldPhrase.isNotBlank()) store.remove(oldPhrase)
                store.save(phrase.text.toString(), command)
                dialog.dismiss(); commands()
            }
        }
        dialog.show()
    }
    fun voices() {
        val options = tts.voices.orEmpty().filter { it.locale.language == "ru" }.sortedWith(compareBy({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))
        if (options.isEmpty()) {
            AlertDialog.Builder(activity).setTitle("Русский голос недоступен").setMessage("Добавьте русский голос в настройках синтеза речи Android.")
                .setPositiveButton("Настройки") { _, _ -> activity.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }.setNegativeButton("Назад", null).show()
            return
        }
        val prefs = activity.getSharedPreferences("voice_style", 0)
        val layout = column()
        val voices = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options.mapIndexed { i, voice -> "Голос ${i + 1} · ${if (voice.isNetworkConnectionRequired) "онлайн" else "локальный"} · ${voice.name}" })
            setSelection(options.indexOfFirst { it.name == tts.voice?.name }.coerceAtLeast(0))
        }
        val rate = SeekBar(activity).apply { max = 60; progress = ((prefs.getFloat("rate", .94f) - .7f) * 100).toInt() }
        val pitch = SeekBar(activity).apply { max = 60; progress = ((prefs.getFloat("pitch", 1.08f) - .8f) * 100).toInt() }
        layout.addView(voices)
        layout.addView(TextView(activity).apply { text = "Темп речи"; setTextColor(AssistantMode.textColor(activity)) }); layout.addView(rate)
        layout.addView(TextView(activity).apply { text = "Высота голоса"; setTextColor(AssistantMode.textColor(activity)) }); layout.addView(pitch)
        fun apply() { prefs.edit().putString("voice", options[voices.selectedItemPosition].name).putFloat("rate", .7f + rate.progress / 100f).putFloat("pitch", .8f + pitch.progress / 100f).apply(); VoiceStyle.apply(activity, tts) }
        val dialog = AlertDialog.Builder(activity).setTitle("Голос ${AssistantMode.name(activity)}").setView(layout)
            .setPositiveButton("Сохранить") { _, _ -> apply() }.setNeutralButton("Прослушать", null).setNegativeButton("Назад") { _, _ -> VoiceStyle.apply(activity, tts) }.create()
        dialog.setOnCancelListener { VoiceStyle.apply(activity, tts) }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            tts.voice = options[voices.selectedItemPosition]; tts.setSpeechRate(.7f + rate.progress / 100f); tts.setPitch(.8f + pitch.progress / 100f)
            tts.speak("Я рядом. Системы готовы. Чем могу помочь?", TextToSpeech.QUEUE_FLUSH, null, "voice_preview")
        } }
        dialog.show()
    }
    fun allApps() {
        val apps = AppCatalog(activity).scan()
        val layout = column()
        val search = field("Поиск по всем приложениям")
        val info = TextView(activity).apply { text = "${apps.size} установлено · ${apps.count { it.launch != null }} запускаются. Обновляется при открытии списка."; setTextColor(AssistantMode.textColor(activity)) }
        val list = ListView(activity)
        var shown = apps
        fun refresh(query: String) {
            shown = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
            list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, shown.map { it.label + if (it.launch == null) " · без экрана запуска" else "" })
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        layout.addView(info); layout.addView(search); layout.addView(list, LinearLayout.LayoutParams(-1, (330 * activity.resources.displayMetrics.density).toInt()))
        refresh("")
        list.setOnItemClickListener { _, _, index, _ ->
            val app = shown[index]
            AlertDialog.Builder(activity).setTitle(app.label).setMessage(app.packageName)
                .setPositiveButton(if (app.launch == null) "Нет экрана запуска" else "Открыть") { _, _ -> app.launch?.let { activity.startActivity(it) } }
                .setNeutralButton("Настройки приложения") { _, _ -> activity.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) }
                .setNegativeButton("Назад", null).show()
        }
        AlertDialog.Builder(activity).setTitle("Все приложения").setView(layout).setNegativeButton("Закрыть", null).show()
    }
}
