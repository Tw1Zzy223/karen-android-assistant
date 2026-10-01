package com.karen.assistant

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

object CloneModels {
    data class Part(val name: String, val size: Long, val sha256: String)
    val parts = listOf(
        Part("qwen-tokenizer-12hz-Q4_K_M.gguf", 254974752, "cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039"),
        Part("qwen-talker-0.6b-base-Q4_K_M.gguf", 628905056, "4b468ec7b1f62b90ef4ca316c0aa57deadfd54b2cf9651703ea753cedaf04226")
    )
    fun directory(context: Context) = File(context.filesDir, "clone_models").apply { mkdirs() }
    fun ready(context: Context) = parts.all {
        val dir = directory(context)
        File(dir, it.name).length() == it.size && File(dir, it.name + ".verified").isFile
    }
    // Only fixed public model URLs; no voice sample is uploaded.
    @Synchronized fun download(context: Context, cancelled: AtomicBoolean, progress: (Int) -> Unit) {
        val dir = directory(context)
        check(dir.usableSpace > parts.sumOf { it.size } + 100_000_000L) { "Освободите минимум 1 ГБ памяти" }
        var completed = 0L
        val total = parts.sumOf { it.size }
        for (part in parts) {
            check(!cancelled.get()) { "Загрузка отменена" }
            val target = File(dir, part.name)
            if (target.length() == part.size && File(dir, part.name + ".verified").isFile) { completed += part.size; continue }
            val temporary = File(dir, part.name + ".partial")
            val connection = URL("https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/resolve/main/${part.name}").openConnection() as HttpURLConnection
            connection.connectTimeout = 20000; connection.readTimeout = 20000
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                check(connection.responseCode == 200) { "Ошибка загрузки HTTP ${connection.responseCode}" }
                var count = 0L
                var last = -1
                connection.inputStream.use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        check(!cancelled.get()) { "Загрузка отменена" }
                        val n = input.read(buffer); if (n < 0) break
                        count += n; check(count <= part.size) { "Размер модели изменился" }
                        output.write(buffer, 0, n); digest.update(buffer, 0, n)
                        val percent = ((completed + count) * 100 / total).toInt()
                        if (percent != last) { progress(percent); last = percent }
                    }
                } }
                check(count == part.size && digest.digest().joinToString("") { "%02x".format(it) } == part.sha256) { "Проверка модели не прошла. Повторите загрузку" }
                check(temporary.renameTo(target)) { "Не удалось сохранить модель" }
                File(dir, part.name + ".verified").writeText(part.sha256)
                completed += part.size
            } finally { connection.disconnect(); temporary.delete() }
        }
        progress(100)
    }
}
