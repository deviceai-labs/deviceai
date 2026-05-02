# STT feature engine — wraps whisper.cpp with VAD + params
add_library(dai_stt STATIC stt_engine.cpp)

target_include_directories(dai_stt
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
)

if(DAI_WHISPER_FOUND AND TARGET whisper)
    target_include_directories(dai_stt PRIVATE
        ${whisper_cpp_SOURCE_DIR}/include
        ${whisper_cpp_SOURCE_DIR}
    )
    target_link_libraries(dai_stt PUBLIC whisper)
endif()

target_compile_options(dai_stt PRIVATE -fPIC -O3)
