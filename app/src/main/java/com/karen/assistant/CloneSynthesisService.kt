package com.karen.assistant

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.*
import java.io.File
import java.util.UUID

/** Heavy native work lives ONLY in :voice. It can be killed without killing the UI. */
class CloneSynthesisService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var reply: Messenger? = null
    private var busy = false
    private var finished = false
    private var stage = ""
    private var stageStart = 0L
    private val deadline = PhaseDeadline(SpeechScheduler { delay, task ->
        val runnable = Runnable { task() }; main.postDelayed(runnable, delay)
        CancelTask { main.removeCallbacks(runnable) }
    }) { message -> end(ERROR, message); main.postDelayed({ killWorker() }, 200) }
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!busy || finished) return
            val elapsed = (SystemClock.elapsedRealtime() - stageStart) / 1000
            send(STATUS, "$stage — ${elapsed} с. Можно нажать «Стоп»")
            main.postDelayed(this, 1000)
        }
    }
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            CANCEL -> { killWorker(); true }
            START -> {
                if (!busy) {
                    reply = message.replyTo
                    val text = message.data.getString("text").orEmpty().take(220)
                    val sample = message.data.getString("sample").orEmpty()
                    if (!sample.matches(Regex("[a-f0-9-]{36}\\.wav")) || text.isBlank()) end(ERROR, "Некорректный образец или текст")
                    else start(text, sample)
                }
                true
            }
            else -> false
        }
    })
    override fun onCreate() {
        super.onCreate()
        check(Application.getProcessName() == "$packageName:voice") { "Voice engine must run in a separate process" }
    }
    private fun phase(label: String, limit: Long) {
        stage = label; stageStart = SystemClock.elapsedRealtime()
        deadline.phase(limit, "$label превысила ${limit / 1000} секунд. Движок остановлен. Можно повторить или выбрать системный голос")
        send(PHASE, label, limit)
    }
    private fun start(text: String, sample: String) {
        busy = true; finished = false
        phase("Загрузка модели в память", 90000)
        main.post(heartbeat)
        Thread({
            var engine: NativeClone? = null
            var handle = 0L
            val reference = File(filesDir, "voice_samples/$sample")
            val output = File(cacheDir, "clone-${UUID.randomUUID()}.wav")
            try {
                check(CloneModels.ready(this)) { "Встроенная модель ещё не подготовлена" }
                check(reference.isFile) { "Образец голоса удалён" }
                engine = NativeClone(); handle = engine.create()
                engine.load(handle, CloneModels.directory(this).absolutePath.toByteArray(Charsets.UTF_8))
                main.post { if (!finished) phase("Создание речи", 120000) }
                val audio = engine.generate(handle, text.toByteArray(Charsets.UTF_8), reference.absolutePath.toByteArray(Charsets.UTF_8), NativeClone.Progress {
                    // Heartbeat is independent of native progress: neither stage can extend its deadline.
                })
                output.outputStream().use { VoiceAudio.wav(it, audio) }
                main.post { if (!finished) end(RESULT, output.name) else output.delete() }
            } catch (e: Exception) {
                output.delete()
                android.util.Log.e("KarenVoice", "Voice engine failure", e)
                main.post { if (!finished) end(ERROR, "Не удалось создать речь: ${e.message?.take(180)}") }
            } catch (e: LinkageError) {
                output.delete()
                main.post { if (!finished) end(ERROR, "Не удалось загрузить нативный движок ARM64: ${e.message?.take(120)}") }
            } catch (_: OutOfMemoryError) {
                output.delete()
                main.post { if (!finished) end(ERROR, "Недостаточно памяти для синтеза. Закройте тяжёлые приложения или выберите системный голос") }
            } finally {
                if (handle != 0L) engine?.free(handle)
            }
        }, "karen-native-voice").start()
    }
    private fun send(kind: Int, value: String, limit: Long = 0) {
        try { reply?.send(Message.obtain(null, kind).apply { data = Bundle().apply { putString("value", value); putLong("limit", limit) } }) }
        catch (_: RemoteException) { killWorker() }
    }
    private fun end(kind: Int, value: String) {
        finished = true; busy = false; deadline.finish(); main.removeCallbacks(heartbeat); send(kind, value)
    }
    private fun killWorker() {
        // Never kill the application/UI process, another app, or a computed remote PID.
        if (Application.getProcessName() == "$packageName:voice") android.os.Process.killProcess(android.os.Process.myPid())
    }
    override fun onBind(intent: Intent?): IBinder = receiver.binder
    override fun onDestroy() { deadline.finish(); main.removeCallbacksAndMessages(null); super.onDestroy(); killWorker() }
    companion object {
        const val START = 1; const val CANCEL = 2; const val STATUS = 3
        const val RESULT = 4; const val ERROR = 5; const val PHASE = 6
    }
}
