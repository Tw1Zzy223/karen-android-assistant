package com.karen.assistant

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class ClonedVoice(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloned_voice", 0)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    fun reference(): File? = prefs.getString("sample", null)?.takeIf {
        it.matches(Regex("[a-f0-9-]{36}\\.wav"))
    }?.let { File(context.filesDir, "voice_samples/$it") }?.takeIf { it.isFile }
    fun save(samples: FloatArray) {
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.wav")
        try {
            file.outputStream().use { VoiceAudio.wav(it, samples) }
            val old = reference()
            check(prefs.edit().putString("sample", file.name).putBoolean("enabled", false).commit()) { "Не удалось сохранить настройки голоса" }
            // Only the current reference is retained; the service reads it directly.
            old?.delete()
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun clear() { stop(); enabled = false; reference()?.delete(); prefs.edit().remove("sample").apply() }
    fun speak(text: String, status: (String) -> Unit) {
        val ref = reference() ?: run { status("Сначала добавьте образец голоса"); return }
        if (!CloneModels.ready(context)) { status("Дождитесь подготовки встроенной модели в «Голос из видео»"); return }
        val ticket = stop()
        status("Создаю речь голосом из видео… (локально)")
        client = CloneServiceClient(context.applicationContext,
            { if (generation.get() == ticket) status(it) },
            { wav ->
                    if (generation.get() != ticket) { wav.delete(); return@CloneServiceClient }
                    try {
                        val player = MediaPlayer()
                        playing = player; playingFile = wav
                        player.setDataSource(wav.absolutePath)
                        playbackDeadline.phase(10000, "Проигрыватель не открыл аудио за 10 секунд")
                        player.setOnPreparedListener { if (generation.get() == ticket) {
                            playbackDeadline.phase(35000, "Озвучка остановлена по таймауту")
                            it.start(); status("Карен говорит голосом из видео")
                        } }
                        player.setOnCompletionListener { if (playing === it) { releasePlayer(); status("Готово") } }
                        player.setOnErrorListener { p, _, _ -> if (playing === p) releasePlayer(); status("Не удалось воспроизвести голос"); true }
                        player.prepareAsync()
                    } catch (_: Exception) { releasePlayer(); wav.delete(); status("Не удалось воспроизвести голос") }
            })
        playbackStatus = status
        client!!.start(text.take(220), ref.name)
    }
    companion object {
        private val main = Handler(Looper.getMainLooper())
        private val generation = AtomicLong()
        private var client: CloneServiceClient? = null
        private var playing: MediaPlayer? = null
        private var playingFile: File? = null
        private var playbackStatus: ((String) -> Unit)? = null
        private val playbackDeadline = PhaseDeadline(SpeechScheduler { delay, task ->
            val runnable = Runnable { task() }; main.postDelayed(runnable, delay)
            CancelTask { main.removeCallbacks(runnable) }
        }) { message -> val callback = playbackStatus; releasePlayer(); callback?.invoke(message) }
        private fun releasePlayer() { playbackDeadline.finish(); playing?.release(); playing = null; playingFile?.delete(); playingFile = null; playbackStatus = null }
        // Cancels only the app's dedicated :voice process, never other apps or the UI.
        fun stop(): Long {
            val ticket = generation.incrementAndGet()
            client?.stop(); client = null
            releasePlayer()
            return ticket
        }
    }
}
