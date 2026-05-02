# TTS feature engine — wraps sherpa-onnx with config + WAV writer
add_library(dai_tts STATIC tts_engine.cpp)

target_include_directories(dai_tts
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
)

if(DAI_SHERPA_FOUND)
    target_compile_definitions(dai_tts PRIVATE HAVE_SHERPA_ONNX=1)
    target_include_directories(dai_tts PRIVATE ${SHERPA_ONNX_HEADER_DIR})
    target_link_libraries(dai_tts PUBLIC sherpa-onnx-c-api)
endif()

target_compile_options(dai_tts PRIVATE -fPIC -O3)
