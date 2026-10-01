package com.karen.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceInput(private val context: Context, private val state: (Boolean, String) -> Unit, private val result: (String) -> Unit) {
    private var recognizer: SpeechRecognizer? = null
    var listening = false
        private set
    fun toggle() { if (listening) cancel() else start() }
    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { state(false, "Нет службы распознавания. Включите её в Android или введите команду текстом"); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { state(true, "Слушаю… Говорите команду") }
                override fun onBeginningOfSpeech() { state(true, "Распознаю речь…") }
                override fun onEndOfSpeech() { state(true, "Обрабатываю…") }
                override fun onResults(results: Bundle) {
                    if (!listening) return
                    listening = false
                    val alternatives = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val phrase = PhraseAliases.best(alternatives, CustomCommands(context).all())
                    if (phrase.isNullOrBlank()) state(false, "Не разобрала фразу. Попробуйте ещё раз")
                    else { state(false, "Вы: $phrase"); result(phrase) }
                }
                override fun onError(error: Int) {
                    if (!listening) return
                    listening = false
                    state(false, when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Разрешите микрофон в настройках Карен"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Нет связи со службой речи. Проверьте интернет"
                        SpeechRecognizer.ERROR_AUDIO -> "Микрофон занят другим приложением"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не расслышала. Нажмите микрофон и повторите"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Включите русский язык в настройках службы речи Android"
                        else -> "Ошибка службы речи: $error. Нажмите микрофон ещё раз"
                    })
                }
                override fun onPartialResults(partialResults: Bundle) { partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { state(true, it) } }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        listening = true; state(true, "Включаю микрофон…")
        try { recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)) }
        catch (_: Exception) { listening = false; state(false, "Не удалось включить службу речи. Проверьте доступ к микрофону") }
    }
    fun cancel() { listening = false; recognizer?.cancel(); state(false, "Микрофон выключен") }
    fun destroy() { recognizer?.destroy(); recognizer = null; listening = false }
}
