package com.karen.assistant

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class ClonedVoice(private val context: Context) {
    private val iron = AssistantMode.iron(context)
    private val prefs = context.getSharedPreferences(if (iron) "cloned_voice_iron" else "cloned_voice", 0)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    private fun customReference(): File? = prefs.getString("sample", null)?.takeIf {
        it.matches(Regex("[a-f0-9-]{36}\\.wav"))
    }?.let { File(context.filesDir, "voice_samples/$it") }?.takeIf { it.isFile }
    private val bundledName get() = if (iron) "743aa192-d41c-4207-8a61-3ef59d715c32.wav" else "0578be5e-64fb-4938-82d6-d8f69a2a7031.wav"
    fun reference(): File? = if (prefs.getBoolean("bundled", false)) File(context.filesDir, "voice_samples/$bundledName").takeIf { it.isFile } else customReference()
    fun useBundledReference(onlyIfNew: Boolean = false) = synchronized(referenceLock) {
        if (onlyIfNew && prefs.getInt("preset_revision", 0) == 1) return@synchronized
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val file = File(dir, bundledName)
        if (!file.isFile) {
            val partial = File(dir, "$bundledName.partial")
            try {
                context.assets.open(if (iron) "voices/iron.wav" else "voices/spider.wav").use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(partial.length() > 144000) { "Образец слишком короткий" }
                check(partial.renameTo(file)) { "Не удалось сохранить встроенный голос" }
            } finally { partial.delete() }
        }
        check(prefs.edit().putBoolean("bundled", true).putBoolean("enabled", true).putInt("preset_revision", 1).commit())
    }
    fun save(samples: FloatArray) {
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.wav")
        try {
            file.outputStream().use { VoiceAudio.wav(it, samples) }
            val old = customReference()
            check(prefs.edit().putString("sample", file.name).putBoolean("bundled", false).putBoolean("enabled", false).commit()) { "Не удалось сохранить настройки голоса" }
            // Only the current reference is retained; the service reads it directly.
            old?.delete()
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun clear() {
        stop(); enabled = false
        if (prefs.getBoolean("bundled", false)) prefs.edit().putBoolean("bundled", false).apply()
        else { customReference()?.delete(); prefs.edit().remove("sample").apply() }
    }
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
                            it.start(); status("${AssistantMode.name(context)} говорит голосом из образца")
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
        private val referenceLock = Any()
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
