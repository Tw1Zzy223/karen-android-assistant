package com.karen.assistant

import kotlin.math.sqrt

object SpeakerMath {
    fun similarity(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || a.size != b.size || a.any { !it.isFinite() } || b.any { !it.isFinite() }) return -1.0
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
        return if (aa == 0.0 || bb == 0.0) -1.0 else (dot / sqrt(aa * bb)).coerceIn(-1.0, 1.0)
    }
    fun accepts(samples: List<FloatArray>, voice: FloatArray, threshold: Double = 0.55): Boolean {
        if (samples.size < 3) return false
        return samples.count { similarity(it, voice) >= threshold } >= 2
    }
}
