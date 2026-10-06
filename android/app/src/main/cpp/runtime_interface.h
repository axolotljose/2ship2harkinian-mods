#ifndef PS2_RUNTIME_INTERFACE_H
#define PS2_RUNTIME_INTERFACE_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Button Bitmasks for DualShock 2 Controller */
#define PS2_BUTTON_SELECT   (1 << 0)
#define PS2_BUTTON_L3       (1 << 1)
#define PS2_BUTTON_R3       (1 << 2)
#define PS2_BUTTON_START    (1 << 3)
#define PS2_BUTTON_DPAD_UP  (1 << 4)
#define PS2_BUTTON_DPAD_RIGHT (1 << 5)
#define PS2_BUTTON_DPAD_DOWN  (1 << 6)
#define PS2_BUTTON_DPAD_LEFT  (1 << 7)
#define PS2_BUTTON_L2       (1 << 8)
#define PS2_BUTTON_R2       (1 << 9)
#define PS2_BUTTON_L1       (1 << 10)
#define PS2_BUTTON_R1       (1 << 11)
#define PS2_BUTTON_TRIANGLE (1 << 12)
#define PS2_BUTTON_CIRCLE   (1 << 13)
#define PS2_BUTTON_CROSS    (1 << 14)
#define PS2_BUTTON_SQUARE   (1 << 15)

/* Graphics Backend Types */
typedef enum {
    PS2_BACKEND_OPENGL_ES = 0,
    PS2_BACKEND_VULKAN = 1
} PS2GraphicsBackend;

/* DualShock 2 Input State Structure */
typedef struct {
    uint32_t buttons;    /* Bitmask of pressed PS2_BUTTON_* flags */
    float lstick_x;      /* Left analog stick X axis: [-1.0f, 1.0f] */
    float lstick_y;      /* Left analog stick Y axis: [-1.0f, 1.0f] */
    float rstick_x;      /* Right analog stick X axis: [-1.0f, 1.0f] */
    float rstick_y;      /* Right analog stick Y axis: [-1.0f, 1.0f] */
    uint8_t l2_pressure; /* Analog pressure for L2 trigger [0, 255] */
    uint8_t r2_pressure; /* Analog pressure for R2 trigger [0, 255] */
} PS2InputState;

/* C/C++ Interface functions to be implemented by the recompiled game runtime */

/**
 * Initializes the game runtime with the given root data directory path.
 * @param game_data_path Absolute file system path to the selected game folder.
 * @return 0 on success, non-zero error code on failure.
 */
int PS2Runtime_Init(const char* game_data_path);

/**
 * Releases and shuts down all resources used by the game runtime.
 */
void PS2Runtime_Shutdown(void);

/**
 * Advances the game simulation by one frame. Should be called periodically by the render loop.
 */
void PS2Runtime_RunFrame(void);

/**
 * Updates the current input state in the runtime.
 * @param input_state Pointer to the latest DualShock 2 input state.
 */
void PS2Runtime_ReceiveInput(const PS2InputState* input_state);

/**
 * Provides a native window surface for OpenGL ES or Vulkan rendering.
 * @param native_window Pointer to ANativeWindow (or SDL surface).
 * @param width Surface width in pixels.
 * @param height Surface height in pixels.
 * @param backend Graphics API choice (PS2_BACKEND_OPENGL_ES or PS2_BACKEND_VULKAN).
 * @return 0 on success, non-zero error code on failure.
 */
int PS2Runtime_ProvideSurface(void* native_window, int width, int height, PS2GraphicsBackend backend);

/**
 * Notifies the runtime that the native surface was resized.
 */
void PS2Runtime_SurfaceChanged(int width, int height);

/**
 * Notifies the runtime that the rendering surface was destroyed or app paused.
 */
void PS2Runtime_OnSurfaceDestroyed(void);

/**
 * Fills the provided audio buffer with PCM samples from the runtime.
 * @param buffer Pointer to memory buffer receiving 16-bit PCM audio samples.
 * @param num_samples Number of audio samples requested.
 * @return Number of samples actually written.
 */
int PS2Runtime_ProvideAudioBuffer(int16_t* buffer, size_t num_samples);

/**
 * Reads a file relative to the initialized game_data_path.
 * @param relative_path Relative file path within game directory.
 * @param out_size Pointer to store the size of the returned buffer in bytes.
 * @return Pointer to allocated file data buffer, or NULL on error. Caller must call PS2Runtime_FreeFileBuffer.
 */
void* PS2Runtime_ReadFileFromPath(const char* relative_path, size_t* out_size);

/**
 * Frees a memory buffer previously allocated by PS2Runtime_ReadFileFromPath.
 * @param buffer Pointer returned by PS2Runtime_ReadFileFromPath.
 */
void PS2Runtime_FreeFileBuffer(void* buffer);

#ifdef __cplusplus
}
#endif

#endif /* PS2_RUNTIME_INTERFACE_H */
