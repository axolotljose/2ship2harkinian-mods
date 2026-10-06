#include "runtime_interface.h"
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <GLES2/gl2.h>
#include <EGL/egl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#define LOG_TAG "PS2RuntimePlaceholder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static char g_game_data_path[1024] = {0};
static PS2InputState g_last_input_state = {0};
static float g_anim_time = 0.0f;
static bool g_initialized = false;

/* EGL State */
static EGLDisplay g_egl_display = EGL_NO_DISPLAY;
static EGLSurface g_egl_surface = EGL_NO_SURFACE;
static EGLContext g_egl_context = EGL_NO_CONTEXT;
static ANativeWindow* g_native_window = nullptr;
static int g_surface_width = 0;
static int g_surface_height = 0;

int PS2Runtime_Init(const char* game_data_path) {
    if (game_data_path) {
        strncpy(g_game_data_path, game_data_path, sizeof(g_game_data_path) - 1);
    } else {
        g_game_data_path[0] = '\0';
    }
    g_initialized = true;
    LOGI("PS2Runtime_Init initialized with data path: %s", g_game_data_path);
    return 0;
}

void PS2Runtime_Shutdown(void) {
    LOGI("PS2Runtime_Shutdown called");
    PS2Runtime_OnSurfaceDestroyed();
    g_initialized = false;
}

void PS2Runtime_ReceiveInput(const PS2InputState* input_state) {
    if (!input_state) return;

    /* Log changes or continuous input debug info */
    if (memcmp(&g_last_input_state, input_state, sizeof(PS2InputState)) != 0) {
        LOGI("Input update: Buttons 0x%04X, LStick (%.2f, %.2f), RStick (%.2f, %.2f), Triggers L2=%d R2=%d",
             input_state->buttons,
             input_state->lstick_x, input_state->lstick_y,
             input_state->rstick_x, input_state->rstick_y,
             input_state->l2_pressure, input_state->r2_pressure);
        g_last_input_state = *input_state;
    }
}

int PS2Runtime_ProvideSurface(void* native_window, int width, int height, PS2GraphicsBackend backend) {
    LOGI("PS2Runtime_ProvideSurface width=%d height=%d backend=%d", width, height, (int)backend);
    g_native_window = (ANativeWindow*)native_window;
    g_surface_width = width;
    g_surface_height = height;

    if (!g_native_window) return -1;

    /* Initialize EGL and GLES2 if window provided */
    g_egl_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_egl_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return -1;
    }

    if (!eglInitialize(g_egl_display, nullptr, nullptr)) {
        LOGE("eglInitialize failed");
        return -1;
    }

    const EGLint attribs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    eglChooseConfig(g_egl_display, attribs, &config, 1, &numConfigs);

    g_egl_surface = eglCreateWindowSurface(g_egl_display, config, g_native_window, nullptr);
    if (g_egl_surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed");
        return -1;
    }

    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };

    g_egl_context = eglCreateContext(g_egl_display, config, EGL_NO_CONTEXT, contextAttribs);
    if (g_egl_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return -1;
    }

    if (!eglMakeCurrent(g_egl_display, g_egl_surface, g_egl_surface, g_egl_context)) {
        LOGE("eglMakeCurrent failed");
        return -1;
    }

    glViewport(0, 0, width, height);
    return 0;
}

void PS2Runtime_SurfaceChanged(int width, int height) {
    g_surface_width = width;
    g_surface_height = height;
    if (g_egl_display != EGL_NO_DISPLAY && g_egl_context != EGL_NO_CONTEXT) {
        glViewport(0, 0, width, height);
    }
}

void PS2Runtime_OnSurfaceDestroyed(void) {
    if (g_egl_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_egl_context != EGL_NO_CONTEXT) {
            eglDestroyContext(g_egl_display, g_egl_context);
            g_egl_context = EGL_NO_CONTEXT;
        }
        if (g_egl_surface != EGL_NO_SURFACE) {
            eglDestroySurface(g_egl_display, g_egl_surface);
            g_egl_surface = EGL_NO_SURFACE;
        }
        eglTerminate(g_egl_display);
        g_egl_display = EGL_NO_DISPLAY;
    }
    g_native_window = nullptr;
    LOGI("PS2Runtime surface destroyed");
}

void PS2Runtime_RunFrame(void) {
    g_anim_time += 0.016f;

    /* If EGL context is valid, render an animated test screen */
    if (g_egl_display != EGL_NO_DISPLAY && g_egl_surface != EGL_NO_SURFACE) {
        /* Shift clear color based on animation time and left stick input */
        float r = (sinf(g_anim_time) + 1.0f) * 0.25f + (g_last_input_state.lstick_x + 1.0f) * 0.25f;
        float g = (cosf(g_anim_time * 1.3f) + 1.0f) * 0.25f + (g_last_input_state.lstick_y + 1.0f) * 0.25f;
        float b = 0.4f;

        /* Turn background brighter if any button is pressed */
        if (g_last_input_state.buttons != 0) {
            r += 0.2f;
            g += 0.2f;
            b += 0.2f;
        }

        glClearColor(r, g, b, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        eglSwapBuffers(g_egl_display, g_egl_surface);
    }
}

int PS2Runtime_ProvideAudioBuffer(int16_t* buffer, size_t num_samples) {
    if (!buffer) return 0;
    /* Generate a test 440Hz sine wave audio tone */
    static float phase = 0.0f;
    float phase_increment = 2.0f * 3.14159265f * 440.0f / 44100.0f;

    for (size_t i = 0; i < num_samples; i++) {
        /* If CROSS is pressed, generate tone; otherwise generate silence */
        if (g_last_input_state.buttons & PS2_BUTTON_CROSS) {
            buffer[i] = (int16_t)(sinf(phase) * 8000.0f);
            phase += phase_increment;
            if (phase >= 2.0f * 3.14159265f) phase -= 2.0f * 3.14159265f;
        } else {
            buffer[i] = 0;
        }
    }
    return (int)num_samples;
}

void* PS2Runtime_ReadFileFromPath(const char* relative_path, size_t* out_size) {
    if (!relative_path || !out_size) return nullptr;
    *out_size = 0;

    char full_path[2048];
    if (strlen(g_game_data_path) > 0) {
        snprintf(full_path, sizeof(full_path), "%s/%s", g_game_data_path, relative_path);
    } else {
        snprintf(full_path, sizeof(full_path), "%s", relative_path);
    }

    FILE* f = fopen(full_path, "rb");
    if (!f) {
        LOGE("Failed to open file: %s", full_path);
        return nullptr;
    }

    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);

    if (sz < 0) {
        fclose(f);
        return nullptr;
    }

    void* buf = malloc((size_t)sz);
    if (!buf) {
        fclose(f);
        return nullptr;
    }

    size_t read_bytes = fread(buf, 1, (size_t)sz, f);
    fclose(f);

    *out_size = read_bytes;
    LOGI("Successfully read %zu bytes from %s", read_bytes, full_path);
    return buf;
}

void PS2Runtime_FreeFileBuffer(void* buffer) {
    if (buffer) {
        free(buffer);
    }
}
