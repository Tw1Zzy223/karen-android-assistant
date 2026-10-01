package com.karen.assistant

// Kept unminified: JNI resolves this class and onProgress by name.
class NativeClone {
    init { System.loadLibrary("karen_voice") }
    external fun create(): Long
    external fun free(ptr: Long)
    external fun cancel(ptr: Long)
    external fun load(ptr: Long, directory: ByteArray)
    external fun generate(ptr: Long, text: ByteArray, reference: ByteArray, listener: Progress): FloatArray
    fun interface Progress { fun onProgress(tokens: Int) }
}
