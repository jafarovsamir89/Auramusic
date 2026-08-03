#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include <unistd.h>

#include "whisper.h"

namespace {
constexpr const char * kTag = "AuraWhisper";

struct AuraWhisperContext {
    whisper_context * context = nullptr;
};

void throw_illegal_state(JNIEnv * env, const char * message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_az_simplesoft_aura_assistant_WhisperCppEngine_nativeCreate(
        JNIEnv * env,
        jobject,
        jstring model_path) {
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    auto * holder = new AuraWhisperContext();
    holder->context = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(model_path, path);
    if (holder->context == nullptr) {
        delete holder;
        throw_illegal_state(env, "Whisper model could not be loaded");
        return 0;
    }
    return reinterpret_cast<jlong>(holder);
}

extern "C" JNIEXPORT jstring JNICALL
Java_az_simplesoft_aura_assistant_WhisperCppEngine_nativeTranscribe(
        JNIEnv * env,
        jobject,
        jlong handle,
        jfloatArray samples,
        jstring language) {
    auto * holder = reinterpret_cast<AuraWhisperContext *>(handle);
    if (holder == nullptr || holder->context == nullptr) {
        throw_illegal_state(env, "Whisper context is not initialized");
        return nullptr;
    }
    const jsize count = env->GetArrayLength(samples);
    if (count == 0) return env->NewStringUTF("");
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    const char * requested_language = env->GetStringUTFChars(language, nullptr);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    const long cores = sysconf(_SC_NPROCESSORS_ONLN);
    params.n_threads = static_cast<int>(std::max(1L, std::min(4L, cores > 1 ? cores - 1 : 1L)));
    params.detect_language = std::string(requested_language) == "auto";
    params.language = params.detect_language ? "en" : requested_language;
    const int status = whisper_full(holder->context, params, pcm.data(), pcm.size());
    env->ReleaseStringUTFChars(language, requested_language);
    if (status != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "whisper_full returned %d", status);
        throw_illegal_state(env, "Whisper could not transcribe audio");
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(holder->context);
    for (int index = 0; index < segments; ++index) {
        text += whisper_full_get_segment_text(holder->context, index);
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_az_simplesoft_aura_assistant_WhisperCppEngine_nativeDestroy(
        JNIEnv *,
        jobject,
        jlong handle) {
    auto * holder = reinterpret_cast<AuraWhisperContext *>(handle);
    if (holder == nullptr) return;
    if (holder->context != nullptr) whisper_free(holder->context);
    delete holder;
}
