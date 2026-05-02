# LLM feature engine — wraps llama.cpp with chat template + sampler chain
add_library(dai_llm STATIC llm_engine.cpp)

target_include_directories(dai_llm
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
)

if(DAI_LLAMA_FOUND AND TARGET llama)
    target_include_directories(dai_llm PRIVATE
        ${llama_cpp_SOURCE_DIR}/include
        ${llama_cpp_SOURCE_DIR}
    )
    target_link_libraries(dai_llm PUBLIC llama)
endif()

target_compile_options(dai_llm PRIVATE -fPIC -O3)
