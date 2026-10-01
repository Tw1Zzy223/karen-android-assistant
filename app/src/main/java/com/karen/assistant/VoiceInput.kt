package com.karen.assistant

import android.content.*
import android.os.*
import android.speech.*
import androidx.core.content.ContextCompat
import java.util.UUID

class VoiceInput(private val context: Context, private val state: (Boolean, String) -> Unit, private val result: (String) -> Unit, private val enrollment: ((FloatArray) -> Unit)? = null) {
    private val handler = Handler(Looper.getMainLooper())
    private val owner = UUID.randomUUID().toString()
    private var destroyed = false
    private val session = RecognitionSession(
        SpeechScheduler { delay, task ->
            val runnable = Runnable { task() }
            handler.postDelayed(runnable, delay)
            CancelTask { handler.removeCallbacks(runnable) }
        },
        { local, events -> createTransport(local, events) },
        state,
        { phrases ->
            PhraseAliases.best(phrases, CustomCommands(context).all())?.let { result(it) }
        }
    )
    private val claimReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.getStringExtra("owner") != owner && session.active) session.cancel()
        }
    }
    init {
        ContextCompat.registerReceiver(context, claimReceiver, IntentFilter(CLAIM_MIC), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    val listening get() = session.active
    fun toggle() { if (listening) cancel() else start() }
    fun start() {
        if (destroyed) return
        AudioAnswers.stop()
        if (OwnerVoice(context).enabled || enrollment != null) {
            if (!OfflineModels.installed(context)) { state(false, "Сначала подготовьте модели в разделе «Только мой голос»"); return }
            context.sendBroadcast(Intent(CLAIM_MIC).setPackage(context.packageName).putExtra("owner", owner))
            session.start(true); return
        }
        val local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!local && !SpeechRecognizer.isRecognitionAvailable(context)) { state(false, "Нет службы распознавания. Включите её в Android или введите команду текстом"); return }
        context.sendBroadcast(Intent(CLAIM_MIC).setPackage(context.packageName).putExtra("owner", owner))
        session.start(local)
    }
    private fun createTransport(local: Boolean, events: SpeechEvents): SpeechTransport? {
        if (OwnerVoice(context).enabled || enrollment != null) return OfflineVoiceTransport(context, events, enrollment)
        val recognizer = if (local && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else return null
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = events.ready()
            override fun onBeginningOfSpeech() = events.beginning()
            override fun onEndOfSpeech() = events.ended()
            override fun onResults(results: Bundle) = events.results(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            override fun onError(error: Int) = events.error(error)
            override fun onPartialResults(partialResults: Bundle) = events.partial(partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        return object : SpeechTransport {
            override fun start() {
                recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5))
            }
            override fun close() { try { recognizer.cancel() } finally { recognizer.destroy() } }
        }
    }
    fun cancel() { session.cancel() }
    fun destroy() {
        if (destroyed) return
        destroyed = true; session.cancel(false)
        context.unregisterReceiver(claimReceiver)
    }
    companion object { const val CLAIM_MIC = "com.karen.assistant.CLAIM_MIC" }
}
