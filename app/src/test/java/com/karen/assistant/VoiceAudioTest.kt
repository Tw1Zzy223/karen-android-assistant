package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceAudioTest {
    @Test fun resampleKeepsDurationAndConstantLevel() {
        val result = VoiceAudio.resample(FloatArray(48000) { 0.2f }, 48000)
        assertEquals(24000, result.size)
        assertTrue(result.all { kotlin.math.abs(it - 0.2f) < 0.00001f })
    }
    @Test fun resampleInterpolatesWithoutReadingPastEnd() {
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 1f), VoiceAudio.resample(floatArrayOf(0f, 1f), 8000, 16000), 0.00001f)
    }
    @Test fun wavHasCorrectPcmHeaderAndLength() {
        val stream = ByteArrayOutputStream()
        VoiceAudio.wav(stream, floatArrayOf(-1f, 0f, 1f))
        val bytes = stream.toByteArray()
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(50, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(24000, view.getInt(24))
        assertEquals(6, view.getInt(40))
        assertEquals((-32767).toShort(), view.getShort(44))
        assertEquals(32767.toShort(), view.getShort(48))
    }
    @Test(expected = IllegalArgumentException::class) fun silenceIsRejected() { VoiceAudio.normalize(FloatArray(72000)) }
    @Test(expected = IllegalArgumentException::class) fun shortReferenceIsRejected() { VoiceAudio.normalize(FloatArray(1000) { 0.1f }) }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteReferenceIsRejected() { VoiceAudio.normalize(FloatArray(72000) { Float.NaN }) }
    @Test fun normalizationIsBounded() {
        val normalized = VoiceAudio.normalize(FloatArray(72000) { if (it % 2 == 0) 0.5f else -0.5f })
        assertTrue(normalized.all { it.isFinite() && it in -1f..1f })
        assertEquals(0.12f, normalized[0], 0.0001f)
    }
}
