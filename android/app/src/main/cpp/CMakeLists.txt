cmake_minimum_required(VERSION 3.22.1)

project("ps2runtime")

# Find Android NDK system libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles2-lib GLESv2)

# Source files for native placeholder runtime and JNI bridge
set(SOURCE_FILES
    jni_bridge.cpp
    placeholder_runtime.cpp
)

# Build shared library ps2runtime
add_library(ps2runtime SHARED ${SOURCE_FILES})

target_include_directories(ps2runtime PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(
    ps2runtime
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles2-lib}
)
