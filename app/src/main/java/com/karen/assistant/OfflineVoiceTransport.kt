package com.karen.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

class OfflineVoiceTransport(private val context: Context, private val events: SpeechEvents, private val enroll: ((FloatArray) -> Unit)? = null) : SpeechTransport {
    @Volatile private var cancelled = false
    @Volatile private var recorder: AudioRecord? = null
    private val main = Handler(Looper.getMainLooper())
    private fun post(block: () -> Unit) { main.post { if (!cancelled) block() } }
    @SuppressLint("MissingPermission")
    override fun start() {
        Thread({
            var recognizer: org.vosk.Recognizer? = null
            var audio: AudioRecord? = null
            try {
                recognizer = OfflineModels.recognizer(context)
                if (cancelled) return@Thread
                val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
                audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
                check(audio.state == AudioRecord.STATE_INITIALIZED)
                recorder = audio
                if (cancelled) return@Thread
                audio.startRecording()
                post { events.ready() }
                val buffer = ShortArray(size / 2)
                var heard = false
                while (!cancelled) {
                    val count = audio.read(buffer, 0, buffer.size)
                    if (count <= 0) { if (!cancelled) post { events.error(3) }; break }
                    val complete = recognizer.acceptWaveForm(buffer, count)
                    val json = JSONObject(if (complete) recognizer.result else recognizer.partialResult)
                    if (complete) {
                        val text = json.optString("text")
                        if (text.isBlank()) continue
                        post { events.ended() }
                        val vector = json.optJSONArray("spk")
                        val embedding = vector?.let { FloatArray(it.length()) { i -> it.getDouble(i).toFloat() } }
                        if (embedding == null || json.optInt("spk_frames") < 80) { post { events.error(1002) }; break }
                        if (enroll != null) post { enroll.invoke(embedding); events.results(listOf(text)) }
                        else if (OwnerVoice(context).accepts(embedding)) post { events.results(listOf(text)) }
                        else post { events.error(1001) }
                        break
                    } else {
                        val text = json.optString("partial")
                        if (text.isNotBlank()) {
                            if (!heard) { heard = true; post { events.beginning() } }
                            post { events.partial(listOf(text)) }
                        }
                    }
                }
            } catch (_: SecurityException) { post { events.error(9) } }
            catch (_: Exception) { post { events.error(1003) } }
            catch (_: LinkageError) { post { events.error(1003) } }
            finally {
                try { audio?.stop() } catch (_: Exception) {}
                audio?.release(); recorder = null; recognizer?.close()
            }
        }, "karen-owner-speech").start()
    }
    override fun close() { cancelled = true; try { recorder?.stop() } catch (_: Exception) {} }
}
