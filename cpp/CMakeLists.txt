cmake_minimum_required(VERSION 3.22)
project(deviceai_engines)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── Options ─────────────────────────────────────────────────────────────────

option(DEVICEAI_ENABLE_LLM    "Build LLM engine (llama.cpp)"           ON)
option(DEVICEAI_ENABLE_SPEECH "Build Speech engine (whisper + piper)"  ON)
option(DEVICEAI_ENABLE_TTS    "Include TTS in Speech engine (piper)"   OFF)
option(DEVICEAI_BUILD_SHARED  "Build shared libs instead of static"    OFF)

# ─── Paths ───────────────────────────────────────────────────────────────────

set(ROOT_DIR    "${CMAKE_SOURCE_DIR}/..")
set(LLAMA_DIR   "${ROOT_DIR}/llama.cpp")
set(WHISPER_DIR "${ROOT_DIR}/whisper.cpp")
set(PIPER_DIR   "${ROOT_DIR}/piper")
set(ONNX_DIR    "${ROOT_DIR}/onnxruntime")
set(ESPEAK_DIR  "${ROOT_DIR}/espeak-ng")

if(DEVICEAI_BUILD_SHARED)
    set(LIB_TYPE SHARED)
else()
    set(LIB_TYPE STATIC)
endif()

# ─── LLM Engine ──────────────────────────────────────────────────────────────

if(DEVICEAI_ENABLE_LLM)
    set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

    if(APPLE)
        set(GGML_METAL ON CACHE BOOL "" FORCE)
    else()
        set(GGML_METAL OFF CACHE BOOL "" FORCE)
    endif()

    if(EXISTS ${LLAMA_DIR}/CMakeLists.txt)
        set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
        add_subdirectory(${LLAMA_DIR} llama-build EXCLUDE_FROM_ALL)
    else()
        message(FATAL_ERROR "llama.cpp not found at ${LLAMA_DIR}")
    endif()

    add_library(deviceai_llm ${LIB_TYPE}
        llm/deviceai_llm_engine.cpp
    )

    target_include_directories(deviceai_llm PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/llm
        ${LLAMA_DIR}/include
        ${LLAMA_DIR}
    )

    target_link_libraries(deviceai_llm llama)

    if(ANDROID)
        find_library(log-lib log)
        target_link_libraries(deviceai_llm ${log-lib})
    endif()

    target_compile_options(deviceai_llm PRIVATE -fPIC -O3)
endif()

# ─── Speech Engine ────────────────────────────────────────────────────────────

if(DEVICEAI_ENABLE_SPEECH)
    set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

    if(APPLE)
        set(GGML_METAL         ON CACHE BOOL "" FORCE)
        set(GGML_ACCELERATE    ON CACHE BOOL "" FORCE)
        set(WHISPER_COREML     ON CACHE BOOL "" FORCE)
        set(WHISPER_COREML_ALLOW_FALLBACK ON CACHE BOOL "" FORCE)
    else()
        set(GGML_METAL OFF CACHE BOOL "" FORCE)
    endif()

    if(EXISTS ${WHISPER_DIR}/CMakeLists.txt)
        set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
        add_subdirectory(${WHISPER_DIR} whisper-build EXCLUDE_FROM_ALL)
    else()
        message(FATAL_ERROR "whisper.cpp not found at ${WHISPER_DIR}")
    endif()

    set(SPEECH_SOURCES speech/deviceai_speech_engine.cpp)
    set(SPEECH_DEFINES)
    set(SPEECH_INCLUDES
        ${CMAKE_CURRENT_SOURCE_DIR}/speech
        ${WHISPER_DIR}/include
        ${WHISPER_DIR}
    )
    set(SPEECH_LIBS whisper)

    if(DEVICEAI_ENABLE_TTS)
        # espeak-ng
        set(USE_ASYNC OFF CACHE BOOL "" FORCE)
        set(USE_MBROLA OFF CACHE BOOL "" FORCE)
        set(USE_LIBPCAUDIO OFF CACHE BOOL "" FORCE)
        set(USE_KLATT OFF CACHE BOOL "" FORCE)
        set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)
        set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

        if(EXISTS ${ESPEAK_DIR}/CMakeLists.txt)
            add_subdirectory(${ESPEAK_DIR} espeak-build EXCLUDE_FROM_ALL)
            list(APPEND SPEECH_INCLUDES ${ESPEAK_DIR}/src/include)
            list(APPEND SPEECH_LIBS espeak-ng)
        endif()

        # ONNX Runtime for Piper
        if(ANDROID)
            set(ONNX_LIB "${ONNX_DIR}/jni/${ANDROID_ABI}/libonnxruntime.so")
            list(APPEND SPEECH_INCLUDES "${ONNX_DIR}/headers")
        else()
            find_library(ONNX_LIB onnxruntime HINTS ${ONNX_DIR}/lib)
            list(APPEND SPEECH_INCLUDES "${ONNX_DIR}/include")
        endif()

        # Piper
        if(EXISTS ${PIPER_DIR}/src/cpp/piper.cpp)
            add_library(piper STATIC ${PIPER_DIR}/src/cpp/piper.cpp)
            target_include_directories(piper PRIVATE
                ${PIPER_DIR}/src/cpp
                ${ONNX_DIR}/include
                ${ESPEAK_DIR}/src/include
            )
            target_link_libraries(piper espeak-ng ${ONNX_LIB})
            list(APPEND SPEECH_INCLUDES ${PIPER_DIR}/src/cpp)
            list(APPEND SPEECH_LIBS piper)
        endif()
    else()
        list(APPEND SPEECH_DEFINES DAI_SPEECH_STT_ONLY=1)
    endif()

    add_library(deviceai_speech ${LIB_TYPE} ${SPEECH_SOURCES})

    target_include_directories(deviceai_speech PUBLIC ${SPEECH_INCLUDES})
    target_compile_definitions(deviceai_speech PRIVATE ${SPEECH_DEFINES})
    target_link_libraries(deviceai_speech ${SPEECH_LIBS})

    if(ANDROID)
        find_library(log-lib log)
        target_link_libraries(deviceai_speech ${log-lib})
    endif()

    target_compile_options(deviceai_speech PRIVATE -fPIC -O3)
endif()
