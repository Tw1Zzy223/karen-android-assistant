package com.karen.assistant

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object CloneModels {
    data class Part(val name: String, val size: Long, val sha256: String)
    val parts = listOf(
        Part("qwen-tokenizer-12hz-Q4_K_M.gguf", 254974752, "cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039"),
        Part("qwen-talker-0.6b-base-Q4_K_M.gguf", 628905056, "4b468ec7b1f62b90ef4ca316c0aa57deadfd54b2cf9651703ea753cedaf04226")
    )
    fun directory(context: Context) = File(context.filesDir, "clone_models").apply { mkdirs() }
    fun ready(context: Context): Boolean {
        val dir = directory(context)
        return parts.all { part -> File(dir, part.name).length() == part.size &&
            File(dir, part.name + ".verified").let { marker -> marker.isFile && marker.readText() == part.sha256 } }
    }
    // No network. A native engine needs real files, so unpack bundled GGUF once.
    @Synchronized fun prepare(context: Context, cancelled: AtomicBoolean, progress: (Int) -> Unit) {
        val dir = directory(context)
        val missing = parts.filterNot { part -> File(dir, part.name).length() == part.size && File(dir, part.name + ".verified").let { f -> f.isFile && f.readText() == part.sha256 } }
        if (missing.isEmpty()) { progress(100); return }
        check(dir.usableSpace > missing.sumOf { it.size } + 100_000_000L) { "Для распаковки модели освободите ещё 1 ГБ памяти" }
        val deadline = SystemClock.elapsedRealtime() + 180_000
        var completed = parts.filterNot { it in missing }.sumOf { it.size }
        val total = parts.sumOf { it.size }
        for (part in missing) {
            val target = File(dir, part.name)
            val temporary = File(dir, part.name + ".partial")
            try {
                context.assets.open("models/${part.name}").use { input -> temporary.outputStream().use { output ->
                    ModelBundleCopy.copy(input, output, part.size, part.sha256,
                        { cancelled.get() || SystemClock.elapsedRealtime() > deadline },
                        { bytes -> progress(((completed + bytes) * 100 / total).toInt()) })
                } }
                check(temporary.renameTo(target)) { "Не удалось сохранить встроенную модель" }
                File(dir, part.name + ".verified").writeText(part.sha256)
                completed += part.size
            } finally { temporary.delete() }
        }
        progress(100)
    }
}
