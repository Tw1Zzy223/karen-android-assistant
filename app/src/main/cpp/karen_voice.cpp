#include <jni.h>
#include <atomic>
#include <chrono>
#include <string>
#include "qwen3_tts.h"
#include "qwen3_tts_c.h"

struct VoiceContext {
    qwen3_tts::Qwen3TTS engine;
    std::atomic<bool> cancelled{false};
};
static void fail(JNIEnv* env, const std::string& message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}
// UTF-16 is converted by Kotlin to standard UTF-8; JNI modified UTF-8 corrupts emoji.
static std::string bytes(JNIEnv* env, jbyteArray value) {
    std::string s(env->GetArrayLength(value), '\0');
    env->GetByteArrayRegion(value, 0, s.size(), reinterpret_cast<jbyte*>(s.data()));
    return s;
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_karen_assistant_NativeClone_create(JNIEnv* env, jobject) {
    try { return reinterpret_cast<jlong>(new VoiceContext()); }
    catch (const std::exception& e) { fail(env, e.what()); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_karen_assistant_NativeClone_free(JNIEnv*, jobject, jlong ptr) {
    delete reinterpret_cast<VoiceContext*>(ptr);
}
extern "C" JNIEXPORT void JNICALL
Java_com_karen_assistant_NativeClone_cancel(JNIEnv*, jobject, jlong ptr) {
    if (ptr) reinterpret_cast<VoiceContext*>(ptr)->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_com_karen_assistant_NativeClone_load(JNIEnv* env, jobject, jlong ptr, jbyteArray directory) {
    try {
        auto* c = reinterpret_cast<VoiceContext*>(ptr);
        qwen3_tts_set_backend_preference(1); // CPU: MediaTek phone, no Qualcomm-only backend.
        qwen3_tts_set_cpu_threads(4);
        if (!c->engine.load_models(bytes(env, directory), "qwen-talker-0.6b-base-Q4_K_M.gguf"))
            fail(env, c->engine.get_error());
        else if (!c->engine.get_model_capabilities().supports_voice_clone)
            fail(env, "The selected model does not support voice cloning");
    } catch (const std::exception& e) { fail(env, e.what()); }
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_karen_assistant_NativeClone_generate(JNIEnv* env, jobject, jlong ptr,
    jbyteArray text, jbyteArray reference, jobject listener) {
    auto* c = reinterpret_cast<VoiceContext*>(ptr);
    if (c->cancelled.load()) { fail(env, "Cancelled"); return nullptr; }
    auto start = std::chrono::steady_clock::now();
    auto expired = [&]() {
        return c->cancelled.load() || std::chrono::steady_clock::now() - start > std::chrono::seconds(120);
    };
    jclass callbackClass = env->GetObjectClass(listener);
    jmethodID progress = env->GetMethodID(callbackClass, "onProgress", "(I)V");
    env->DeleteLocalRef(callbackClass);
    if (!progress) return nullptr;
    c->engine.set_progress_callback([&](int tokens, int) {
        if (tokens % 12 == 0) {
            env->CallVoidMethod(listener, progress, tokens);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
    });
    try {
        qwen3_tts::tts_streaming_params params;
        params.generation.language_id = 2069; // Russian.
        params.generation.max_audio_tokens = 256;
        params.generation.n_threads = 4;
        params.generation.print_timing = false;
        params.chunk_sec = 0.5f;
        params.collect_audio = true;
        auto result = c->engine.synthesize_with_voice_streaming(
            bytes(env, text), bytes(env, reference),
            [&](const qwen3_tts::tts_audio_chunk&) { return !expired(); }, params);
        c->engine.set_progress_callback(nullptr);
        if (expired()) { fail(env, "Cancelled or synthesis timeout (120 seconds)"); return nullptr; }
        if (!result.success || result.audio.empty() || result.sample_rate != 24000) {
            fail(env, result.error_msg.empty() ? "No speech generated" : result.error_msg); return nullptr;
        }
        jfloatArray audio = env->NewFloatArray(result.audio.size());
        if (audio) env->SetFloatArrayRegion(audio, 0, result.audio.size(), result.audio.data());
        return audio;
    } catch (const std::exception& e) {
        c->engine.set_progress_callback(nullptr); fail(env, e.what()); return nullptr;
    }
}
