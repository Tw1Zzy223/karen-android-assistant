package com.karen.assistant

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.nio.ByteOrder

object VideoVoiceImporter {
    // Decode only the selected 3–15 s audio fragment. Never persist the entire video.
    fun decode(context: Context, uri: Uri, startSeconds: Int, seconds: Int): FloatArray {
        require(uri.scheme == "content") { "Передайте сам файл через «Поделиться», не ссылку на сообщение" }
        require(startSeconds in 0..3600 && seconds in 3..15)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("В видео нет звуковой дорожки")
            val format = extractor.getTrackFormat(track)
            val startUs = startSeconds * 1_000_000L
            val endUs = startUs + seconds * 1_000_000L
            extractor.selectTrack(track)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            require(rate in 8000..192000 && channels in 1..8)
            val mono = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (!outputDone) {
                check(SystemClock.elapsedRealtime() < deadline) { "Извлечение звука заняло слишком долго" }
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time >= endUs) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, time, 0); extractor.advance()
                        }
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val decoded = decoder.outputFormat
                        val nextRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        check(mono.isEmpty() || nextRate == rate) { "Частота звука меняется внутри видео" }
                        rate = nextRate; channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        require(rate in 8000..192000 && channels in 1..8)
                        encoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "Неподдерживаемый формат PCM" }
                    }
                    else -> if (index >= 0) {
                        try {
                            if (info.size > 0) {
                            val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            var frame = 0L
                            while (buffer.remaining() >= bytes * channels) {
                                var sample = 0f
                                repeat(channels) { sample += if (bytes == 4) buffer.float else buffer.short / 32768f }
                                val time = info.presentationTimeUs + frame++ * 1_000_000 / rate
                                if (time >= startUs && time < endUs && mono.size < seconds * rate) mono.add(sample / channels)
                            }
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || mono.size >= seconds * rate
                        } finally { decoder.releaseOutputBuffer(index, false) }
                    }
                }
            }
            check(mono.isNotEmpty()) { "В выбранном фрагменте нет звука" }
            return VoiceAudio.normalize(VoiceAudio.resample(mono.toFloatArray(), rate))
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            codec?.release(); extractor.release()
        }
    }
}
