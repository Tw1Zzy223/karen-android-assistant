package com.karen.assistant

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object ModelBundleCopy {
    fun copy(input: InputStream, output: OutputStream, size: Long, sha256: String,
             cancelled: () -> Boolean, progress: (Long) -> Unit) {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(262144)
        var count = 0L
        var lastPercent = -1L
        while (true) {
            check(!cancelled()) { "Подготовка модели отменена или превысила 180 секунд. Повторите подготовку" }
            val n = input.read(buffer)
            if (n < 0) break
            count += n; check(count <= size) { "Неверный размер встроенной модели" }
            output.write(buffer, 0, n); hash.update(buffer, 0, n)
            val percent = count * 100 / size
            if (percent != lastPercent) { progress(count); lastPercent = percent }
        }
        check(count == size && hash.digest().joinToString("") { "%02x".format(it) } == sha256) { "Встроенная модель повреждена. Переустановите полный APK" }
    }
}
