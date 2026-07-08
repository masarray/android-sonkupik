#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "arsonkupik_dsp.hpp"

#define ASK_LOG_TAG "ArSonKuPikDSP"
#define ASK_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, ASK_LOG_TAG, __VA_ARGS__)

namespace {

using arsonkupik::ArSonKuPikEngine;
using arsonkupik::RuntimeParams;
using arsonkupik::MeterState;
using arsonkupik::find_preset;

struct EngineHandle {
    ArSonKuPikEngine engine;
    RuntimeParams params;
    double sampleRate = 48000.0;
    int channels = 2;

    // Reused planar buffers for interleaved Android PCM float processing.
    // They are resized only when needed, never per-sample.
    std::vector<std::vector<float>> planarStorage;
    std::vector<float*> planes;

    void prepare(double sr, int ch) {
        sampleRate = sr > 0.0 ? sr : 48000.0;
        channels = std::clamp(ch, 1, arsonkupik::kMaxChannels);
        engine.prepare(sampleRate, static_cast<std::size_t>(channels));
        engine.set_runtime_params(params);
        planarStorage.resize(static_cast<std::size_t>(channels));
        planes.resize(static_cast<std::size_t>(channels));
    }

    void ensureFrames(int frames) {
        const auto f = static_cast<std::size_t>(std::max(0, frames));
        for (int ch = 0; ch < channels; ++ch) {
            if (planarStorage[static_cast<std::size_t>(ch)].size() < f) {
                planarStorage[static_cast<std::size_t>(ch)].resize(f);
            }
            planes[static_cast<std::size_t>(ch)] = planarStorage[static_cast<std::size_t>(ch)].data();
        }
    }
};

EngineHandle* fromHandle(jlong handle) {
    return reinterpret_cast<EngineHandle*>(handle);
}

jlong throwAndReturnZero(JNIEnv* env, const char* message) {
    jclass ex = env->FindClass("java/lang/IllegalStateException");
    if (ex) env->ThrowNew(ex, message);
    return 0;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeCreate(
        JNIEnv* env, jobject /*thiz*/, jint sampleRate, jint channels) {
    try {
        auto* h = new EngineHandle();
        h->prepare(static_cast<double>(sampleRate), static_cast<int>(channels));
        return reinterpret_cast<jlong>(h);
    } catch (...) {
        return throwAndReturnZero(env, "Failed to create ArSonKuPik native engine");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativePrepare(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint sampleRate, jint channels) {
    auto* h = fromHandle(handle);
    if (!h) return;
    h->prepare(static_cast<double>(sampleRate), static_cast<int>(channels));
}

extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeSetPreset(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring presetId) {
    auto* h = fromHandle(handle);
    if (!h || !presetId) return;

    const char* chars = env->GetStringUTFChars(presetId, nullptr);
    if (!chars) return;
    std::string id(chars);
    env->ReleaseStringUTFChars(presetId, chars);

    if (!find_preset(id)) {
        ASK_LOGE("Unknown preset id: %s", id.c_str());
        return;
    }

    h->params.preset_id = id;
    h->params.advanced_override = false;
    h->engine.set_runtime_params(h->params);
}

extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeSetMacros(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle,
        jfloat bass, jfloat vocal, jfloat width, jfloat air, jfloat loud) {
    auto* h = fromHandle(handle);
    if (!h) return;

    auto clamp100 = [](float v) { return std::clamp(static_cast<double>(v), 0.0, 100.0); };
    h->params.smart_bass = clamp100(bass);
    h->params.vocal_body = clamp100(vocal);
    h->params.stereo_magic = clamp100(width);
    h->params.smart_treble = clamp100(air);
    h->params.enhance = clamp100(loud);
    h->params.advanced_override = true;
    h->engine.set_runtime_params(h->params);
}

extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeSetBypass(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean bypass) {
    auto* h = fromHandle(handle);
    if (!h) return;
    h->params.bypass = bypass == JNI_TRUE;
    h->engine.set_runtime_params(h->params);
}

extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeSetSmartProtect(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean enabled) {
    auto* h = fromHandle(handle);
    if (!h) return;
    h->params.smart_protect = enabled == JNI_TRUE;
    h->engine.set_runtime_params(h->params);
}


extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeSetOutputTrimDb(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat trimDb) {
    auto* h = fromHandle(handle);
    if (!h) return;
    h->params.output_trim_db = std::clamp(static_cast<double>(trimDb), -12.0, 6.0);
    h->params.advanced_override = true;
    h->engine.set_runtime_params(h->params);
}

extern "C" JNIEXPORT void JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeProcessFloatInterleaved(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray buffer, jint frames, jint channels) {
    auto* h = fromHandle(handle);
    if (!h || !buffer || frames <= 0 || channels <= 0) return;

    const int chCount = std::min<int>(channels, h->channels);
    const jsize required = static_cast<jsize>(frames * channels);
    if (env->GetArrayLength(buffer) < required) return;

    h->ensureFrames(frames);

    jfloat* data = env->GetFloatArrayElements(buffer, nullptr);
    if (!data) return;

    for (int i = 0; i < frames; ++i) {
        for (int ch = 0; ch < chCount; ++ch) {
            h->planarStorage[static_cast<std::size_t>(ch)][static_cast<std::size_t>(i)] = data[i * channels + ch];
        }
    }

    h->engine.process(h->planes.data(), static_cast<std::size_t>(chCount), static_cast<std::size_t>(frames));

    for (int i = 0; i < frames; ++i) {
        for (int ch = 0; ch < chCount; ++ch) {
            data[i * channels + ch] = h->planarStorage[static_cast<std::size_t>(ch)][static_cast<std::size_t>(i)];
        }
    }

    env->ReleaseFloatArrayElements(buffer, data, 0);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_masari_arsonkupik_audio_ArSonKuPikNative_nativeGetMeters(
        JNIEnv* env, jobject /*thiz*/, jlong handle) {
    jfloatArray out = env->NewFloatArray(5);
    float values[5] = {-120.0f, -120.0f, 0.0f, 1.0f, 0.0f};
    auto* h = fromHandle(handle);
    if (h) {
        const MeterState& m = h->engine.meters();
        values[0] = static_cast<float>(m.input_peak_db);
        values[1] = static_cast<float>(m.output_peak_db);
        values[2] = static_cast<float>(m.gain_reduction_db);
        values[3] = static_cast<float>(m.correlation);
        values[4] = m.clipping ? 1.0f : 0.0f;
    }
    env->SetFloatArrayRegion(out, 0, 5, values);
    return out;
}
