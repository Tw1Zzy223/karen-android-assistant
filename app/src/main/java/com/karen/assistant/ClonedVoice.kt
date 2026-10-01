package com.karen.assistant

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
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
            prefs.edit().putString("sample", file.name).putBoolean("enabled", false).commit()
            // Keep at most the active reference; queued tasks snapshot samples under the manager lock.
            old?.delete()
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun clear() { stop(); enabled = false; reference()?.delete(); prefs.edit().remove("sample").apply() }
    fun speak(text: String, status: (String) -> Unit) {
        val ref = reference() ?: run { status("Сначала добавьте образец голоса"); return }
        if (!CloneModels.ready(context)) { status("Сначала скачайте модель в «Голос из видео»"); return }
        val ticket = stop()
        status("Создаю речь голосом из видео… (локально)")
        worker.execute {
            if (generation.get() != ticket) return@execute
            val engine: NativeClone
            var handle = 0L
            val wav = File(context.cacheDir, "clone-${UUID.randomUUID()}.wav")
            val refCopy = File(context.cacheDir, "ref-${UUID.randomUUID()}.wav")
            fun update(message: String) { main.post { if (generation.get() == ticket) status(message) } }
            try {
                ref.copyTo(refCopy)
                engine = NativeClone()
                synchronized(lock) {
                    if (generation.get() != ticket) return@execute
                    handle = engine.create(); native = engine; ptr = handle
                }
                update("Загружаю локальную модель голоса…")
                engine.load(handle, CloneModels.directory(context).absolutePath.toByteArray(Charsets.UTF_8))
                if (generation.get() != ticket) return@execute
                val audio = engine.generate(handle, text.take(220).toByteArray(Charsets.UTF_8), refCopy.absolutePath.toByteArray(Charsets.UTF_8), NativeClone.Progress { tokens ->
                    update("Создаю речь локально: $tokens аудиотокенов. Микрофон отменяет озвучку")
                })
                if (generation.get() != ticket) return@execute
                wav.outputStream().use { VoiceAudio.wav(it, audio) }
                main.post {
                    if (generation.get() != ticket) { wav.delete(); return@post }
                    try {
                        val player = MediaPlayer()
                        playing = player; playingFile = wav
                        player.setDataSource(wav.absolutePath)
                        player.setOnPreparedListener { if (generation.get() == ticket) { it.start(); status("Карен говорит голосом из видео") } }
                        player.setOnCompletionListener { if (playing === it) { releasePlayer(); status("Готово") } }
                        player.setOnErrorListener { p, _, _ -> if (playing === p) releasePlayer(); status("Не удалось воспроизвести голос"); true }
                        player.prepareAsync()
                    } catch (_: Exception) { releasePlayer(); wav.delete(); status("Не удалось воспроизвести голос") }
                }
            } catch (e: Exception) { wav.delete(); update("Не удалось создать речь: ${e.message?.take(160)}") }
            catch (_: LinkageError) { update("Этот движок требует Android ARM64") }
            finally {
                synchronized(lock) {
                    if (ptr == handle) { ptr = 0L; native = null }
                    if (handle != 0L) NativeClone().free(handle)
                }
                refCopy.delete()
            }
        }
    }
    companion object {
        private val main = Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadExecutor()
        private val lock = Any()
        private val generation = AtomicLong()
        private var native: NativeClone? = null
        private var ptr = 0L
        private var playing: MediaPlayer? = null
        private var playingFile: File? = null
        private fun releasePlayer() { playing?.release(); playing = null; playingFile?.delete(); playingFile = null }
        // All callers are on the main thread. Native cancellation is atomic, never frees a running context.
        fun stop(): Long {
            val ticket = generation.incrementAndGet()
            synchronized(lock) { if (ptr != 0L) native?.cancel(ptr) }
            releasePlayer()
            return ticket
        }
    }
}
