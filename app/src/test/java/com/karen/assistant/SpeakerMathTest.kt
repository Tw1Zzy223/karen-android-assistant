package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test

class SpeakerMathTest {
    @Test fun requiresEnrollmentAndRejectsWrongVoice() {
        val a = floatArrayOf(1f, 0f, 0f)
        assertFalse(SpeakerMath.accepts(listOf(a), a))
        assertTrue(SpeakerMath.accepts(listOf(a, a, a), a))
        assertFalse(SpeakerMath.accepts(listOf(a, a, a), floatArrayOf(0f, 1f, 0f)))
    }
    @Test fun invalidVectorsCannotPass() {
        val a = floatArrayOf(1f, 0f)
        assertFalse(SpeakerMath.accepts(listOf(a, a, a), floatArrayOf(Float.NaN, 0f)))
        assertFalse(SpeakerMath.accepts(listOf(a, a, a), floatArrayOf(0f, 0f)))
        assertFalse(SpeakerMath.accepts(listOf(a, a, a), floatArrayOf(1f)))
    }
    @Test fun needsTwoMatchingSamples() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertFalse(SpeakerMath.accepts(listOf(a, b, b), a))
        assertTrue(SpeakerMath.accepts(listOf(a, a, b), a))
    }
}
