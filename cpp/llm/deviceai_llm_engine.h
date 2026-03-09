/**
 * deviceai_llm_engine.h
 *
 * Unified C API for LLM inference (llama.cpp backend).
 * Single implementation compiled once — consumed by:
 *
 *   Kotlin/Android  →  JNI wrapper calls dai_llm_*
 *   Swift/iOS       →  C interop via .def file calls dai_llm_*
 *   Flutter         →  dart:ffi DynamicLibrary.lookup('dai_llm_*')
 *   React Native    →  JSI/FFI bridge calls dai_llm_*
 *
 * Design rules:
 *   - Pure C API (extern "C") — callable from any FFI
 *   - Opaque state — no caller-visible C++ types
 *   - Callbacks with void* user_data — safe across thread/language boundaries
 *   - Caller owns strings returned by dai_llm_generate (free with dai_llm_free_string)
 *   - No platform imports in this header
 */

#ifndef DEVICEAI_LLM_ENGINE_H
#define DEVICEAI_LLM_ENGINE_H

#ifdef __cplusplus
extern "C" {
#endif

// ─── Callbacks ───────────────────────────────────────────────────────────────

/** Called for each generated token piece during streaming. */
typedef void (*dai_llm_token_cb)(const char *token, void *user_data);

/** Called on error during generation. */
typedef void (*dai_llm_error_cb)(const char *error, void *user_data);

// ─── Lifecycle ───────────────────────────────────────────────────────────────

/**
 * Load a GGUF model and initialize the inference context.
 *
 * @param model_path   Absolute path to the .gguf file
 * @param context_size KV-cache context window in tokens (e.g. 4096)
 * @param max_threads  CPU threads for inference
 * @param use_gpu      1 = use GPU (Metal on iOS, OpenCL/Vulkan on Android); 0 = CPU only
 * @return 1 on success, 0 on failure
 */
int dai_llm_init(
    const char *model_path,
    int         context_size,
    int         max_threads,
    int         use_gpu
);

/**
 * Unload the model and release all resources.
 * Safe to call even if not initialized.
 */
void dai_llm_shutdown(void);

// ─── Generation ──────────────────────────────────────────────────────────────

/**
 * Generate a response for a conversation (blocking).
 *
 * Roles and contents are parallel arrays of length message_count.
 * Standard role values: "system", "user", "assistant".
 * The model's embedded chat template (ChatML, Llama-3, Gemma, Mistral, etc.)
 * is applied automatically via llama_chat_apply_template.
 *
 * @param roles         Array of role strings
 * @param contents      Array of message content strings
 * @param message_count Number of messages
 * @param max_tokens    Maximum tokens to generate
 * @param temperature   Sampling temperature (0.0 = greedy, 1.0 = creative)
 * @param top_p         Nucleus sampling threshold (e.g. 0.9)
 * @param top_k         Top-K limit (e.g. 40)
 * @param repeat_penalty Repetition penalty (e.g. 1.1)
 * @return Heap-allocated generated text. Caller MUST free with dai_llm_free_string().
 *         Returns NULL on error or if not initialized.
 */
char *dai_llm_generate(
    const char **roles,
    const char **contents,
    int          message_count,
    int          max_tokens,
    float        temperature,
    float        top_p,
    int          top_k,
    float        repeat_penalty
);

/**
 * Stream a response token-by-token (blocking until complete or cancelled).
 *
 * on_token is called from the same thread for each piece.
 * The stream completes when the function returns — no on_complete callback needed.
 *
 * @param roles         Array of role strings
 * @param contents      Array of message content strings
 * @param message_count Number of messages
 * @param max_tokens    Maximum tokens to generate
 * @param temperature   Sampling temperature
 * @param top_p         Nucleus sampling threshold
 * @param top_k         Top-K limit
 * @param repeat_penalty Repetition penalty
 * @param on_token      Called for each generated piece; return ignored
 * @param on_error      Called on error (may be NULL)
 * @param user_data     Passed unchanged to all callbacks
 */
void dai_llm_generate_stream(
    const char     **roles,
    const char     **contents,
    int              message_count,
    int              max_tokens,
    float            temperature,
    float            top_p,
    int              top_k,
    float            repeat_penalty,
    dai_llm_token_cb on_token,
    dai_llm_error_cb on_error,
    void            *user_data
);

/**
 * Cancel an in-progress generate or generate_stream call.
 * Safe to call from any thread.
 */
void dai_llm_cancel(void);

// ─── Memory management ───────────────────────────────────────────────────────

/**
 * Free a string returned by dai_llm_generate.
 * Must be called exactly once per returned pointer.
 */
void dai_llm_free_string(char *ptr);

#ifdef __cplusplus
}
#endif

#endif // DEVICEAI_LLM_ENGINE_H
