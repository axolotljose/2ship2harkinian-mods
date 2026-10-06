#include <jni.h>
#include <android/native_window_jni.h>
#include <string>
#include <vector>
#include "runtime_interface.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_com_ps2_shell_NativeEngine_init(JNIEnv *env, jobject thiz, jstring data_path) {
    const char *path = nullptr;
    if (data_path != nullptr) {
        path = env->GetStringUTFChars(data_path, nullptr);
    }
    int result = PS2Runtime_Init(path ? path : "");
    if (path != nullptr) {
        env->ReleaseStringUTFChars(data_path, path);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_ps2_shell_NativeEngine_shutdown(JNIEnv *env, jobject thiz) {
    PS2Runtime_Shutdown();
}

JNIEXPORT void JNICALL
Java_com_ps2_shell_NativeEngine_runFrame(JNIEnv *env, jobject thiz) {
    PS2Runtime_RunFrame();
}

JNIEXPORT void JNICALL
Java_com_ps2_shell_NativeEngine_sendInput(JNIEnv *env, jobject thiz,
                                           jint buttons,
                                           jfloat lstick_x, jfloat lstick_y,
                                           jfloat rstick_x, jfloat rstick_y,
                                           jint l2_pressure, jint r2_pressure) {
    PS2InputState input_state;
    input_state.buttons = static_cast<uint32_t>(buttons);
    input_state.lstick_x = lstick_x;
    input_state.lstick_y = lstick_y;
    input_state.rstick_x = rstick_x;
    input_state.rstick_y = rstick_y;
    input_state.l2_pressure = static_cast<uint8_t>(l2_pressure);
    input_state.r2_pressure = static_cast<uint8_t>(r2_pressure);

    PS2Runtime_ReceiveInput(&input_state);
}

JNIEXPORT jint JNICALL
Java_com_ps2_shell_NativeEngine_onSurfaceCreated(JNIEnv *env, jobject thiz,
                                                 jobject surface,
                                                 jint width, jint height,
                                                 jint backend) {
    ANativeWindow *window = nullptr;
    if (surface != nullptr) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    return PS2Runtime_ProvideSurface(window, width, height, static_cast<PS2GraphicsBackend>(backend));
}

JNIEXPORT void JNICALL
Java_com_ps2_shell_NativeEngine_onSurfaceChanged(JNIEnv *env, jobject thiz, jint width, jint height) {
    PS2Runtime_SurfaceChanged(width, height);
}

JNIEXPORT void JNICALL
Java_com_ps2_shell_NativeEngine_onSurfaceDestroyed(JNIEnv *env, jobject thiz) {
    PS2Runtime_OnSurfaceDestroyed();
}

JNIEXPORT jint JNICALL
Java_com_ps2_shell_NativeEngine_fillAudioBuffer(JNIEnv *env, jobject thiz,
                                                jshortArray buffer, jint num_samples) {
    if (buffer == nullptr || num_samples <= 0) return 0;

    jshort *audio_data = env->GetShortArrayElements(buffer, nullptr);
    int samples_written = PS2Runtime_ProvideAudioBuffer(reinterpret_cast<int16_t *>(audio_data), static_cast<size_t>(num_samples));
    env->ReleaseShortArrayElements(buffer, audio_data, 0);
    return samples_written;
}

JNIEXPORT jbyteArray JNICALL
Java_com_ps2_shell_NativeEngine_readFile(JNIEnv *env, jobject thiz, jstring relative_path) {
    if (relative_path == nullptr) return nullptr;

    const char *path = env->GetStringUTFChars(relative_path, nullptr);
    size_t size = 0;
    void *buf = PS2Runtime_ReadFileFromPath(path, &size);
    env->ReleaseStringUTFChars(relative_path, path);

    if (buf == nullptr || size == 0) return nullptr;

    jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(size));
    if (byteArray != nullptr) {
        env->SetByteArrayRegion(byteArray, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte *>(buf));
    }

    PS2Runtime_FreeFileBuffer(buf);
    return byteArray;
}

}
