package com.karen.assistant

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VoiceAudio {
    fun resample(samples: FloatArray, sourceRate: Int, targetRate: Int = 24000): FloatArray {
        require(sourceRate in 8000..192000 && targetRate > 0 && samples.isNotEmpty())
        val count = (samples.size.toLong() * targetRate / sourceRate).toInt()
        require(count > 0)
        return FloatArray(count) { i ->
            val p = i.toDouble() * sourceRate / targetRate
            val lo = p.toInt().coerceAtMost(samples.lastIndex)
            val hi = (lo + 1).coerceAtMost(samples.lastIndex)
            (samples[lo] * (1 - (p - lo)) + samples[hi] * (p - lo)).toFloat()
        }
    }
    fun normalize(samples: FloatArray): FloatArray {
        require(samples.size >= 3 * 24000) { "Нужно хотя бы 3 секунды речи" }
        require(samples.all { it.isFinite() }) { "Некорректный звук" }
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        require(rms > 0.002) { "В образце почти тишина. Выберите фрагмент с речью" }
        val gain = (0.12 / rms).coerceIn(0.2, 8.0).toFloat()
        return FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }
    }
    fun wav(output: OutputStream, samples: FloatArray) {
        val size = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(24000).putInt(48000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(size)
        output.write(header.array())
        val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            buffer.putShort((sample.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            if (!buffer.hasRemaining()) { output.write(buffer.array()); buffer.clear() }
        }
        if (buffer.position() > 0) output.write(buffer.array(), 0, buffer.position())
    }
}
