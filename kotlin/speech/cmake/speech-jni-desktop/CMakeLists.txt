cmake_minimum_required(VERSION 3.18)
project(speech_jni_desktop)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ═══════════════════════════════════════════════════════════════
#                        PATHS
# ═══════════════════════════════════════════════════════════════

set(WHISPER_DIR "${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp")
set(SHERPA_DIR  "${CMAKE_SOURCE_DIR}/../../../../third_party/sherpa-onnx")
set(JNI_CPP_DIR "${PROJECT_SOURCE_DIR}/../../src/commonMain/cpp")

# ═══════════════════════════════════════════════════════════════
#                      WHISPER.CPP
# ═══════════════════════════════════════════════════════════════

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

if(APPLE)
    set(GGML_METAL             ON  CACHE BOOL "" FORCE)
    set(GGML_ACCELERATE        ON  CACHE BOOL "" FORCE)
    set(WHISPER_COREML                  ON  CACHE BOOL "" FORCE)
    set(WHISPER_COREML_ALLOW_FALLBACK   ON  CACHE BOOL "" FORCE)
else()
    set(GGML_METAL      OFF CACHE BOOL "" FORCE)
    set(WHISPER_COREML  OFF CACHE BOOL "" FORCE)
endif()

if(EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    add_subdirectory(${WHISPER_DIR} whisper-build EXCLUDE_FROM_ALL)
else()
    message(FATAL_ERROR "whisper.cpp not found at ${WHISPER_DIR}")
endif()

# ═══════════════════════════════════════════════════════════════
#                    TTS (SHERPA-ONNX)
# ═══════════════════════════════════════════════════════════════

if(EXISTS ${SHERPA_DIR}/CMakeLists.txt)
    set(SHERPA_ONNX_ENABLE_PYTHON       OFF CACHE BOOL "" FORCE)
    set(SHERPA_ONNX_ENABLE_TESTS        OFF CACHE BOOL "" FORCE)
    set(SHERPA_ONNX_ENABLE_CHECK        OFF CACHE BOOL "" FORCE)
    set(SHERPA_ONNX_ENABLE_PORTAUDIO    OFF CACHE BOOL "" FORCE)
    set(SHERPA_ONNX_ENABLE_JNI          OFF CACHE BOOL "" FORCE)
    set(SHERPA_ONNX_ENABLE_C_API        ON  CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS               OFF CACHE BOOL "" FORCE)

    add_subdirectory(${SHERPA_DIR} sherpa-build EXCLUDE_FROM_ALL)
    set(SHERPA_FOUND TRUE)
    message(STATUS "sherpa-onnx found — TTS enabled")
else()
    message(STATUS "sherpa-onnx not found at ${SHERPA_DIR} — TTS will use no-op stubs")
    set(SHERPA_FOUND FALSE)
endif()

# ═══════════════════════════════════════════════════════════════
#                       JNI SETUP
# ═══════════════════════════════════════════════════════════════

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# ═══════════════════════════════════════════════════════════════
#                    JNI SHARED LIBRARY
# ═══════════════════════════════════════════════════════════════

add_library(speech_jni SHARED
    ${JNI_CPP_DIR}/deviceai_whisper_jni.cpp
    ${JNI_CPP_DIR}/deviceai_tts_jni.cpp
)

target_include_directories(speech_jni PRIVATE
    ${JNI_CPP_DIR}
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}
)

target_link_libraries(speech_jni whisper)

if(SHERPA_FOUND)
    target_compile_definitions(speech_jni PRIVATE HAVE_SHERPA_ONNX=1)
    target_include_directories(speech_jni PRIVATE ${SHERPA_DIR})
    target_link_libraries(speech_jni sherpa-onnx-c-api)
endif()

if(APPLE)
    target_link_libraries(speech_jni
        "-framework Accelerate"
        "-framework Metal"
        "-framework MetalKit"
        "-framework CoreML"
        "-framework Foundation"
    )
endif()

find_package(Threads REQUIRED)
target_link_libraries(speech_jni Threads::Threads)

set_target_properties(speech_jni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"
)

# ═══════════════════════════════════════════════════════════════
#                    COMPILE FLAGS
# ═══════════════════════════════════════════════════════════════

target_compile_options(speech_jni PRIVATE
    -fPIC
    -O3
)

if(APPLE)
    target_compile_options(speech_jni PRIVATE
        -fvisibility=hidden
    )
endif()
