/**
 * deviceai_llm_engine.cpp
 *
 * Unified LLM inference engine — pure C++ core, platform-agnostic.
 * Exposes a plain C API (dai_llm_*) consumed by all platform wrappers:
 *   - kotlin/llm: thin JNI wrapper
 *   - swift/llm:  thin C interop wrapper
 *   - flutter/llm: dart:ffi wrapper
 *   - react-native/llm: JSI/FFI wrapper
 *
 * This file has zero JNI, zero Swift, zero Flutter imports.
 * Platform-specific glue lives in each SDK's wrapper — not here.
 */

#include "deviceai_llm_engine.h"
#include "llama.h"

#include <string>
#include <vector>
#include <atomic>
#include <functional>
#include <cstring>
#include <cstdlib>
#include <cstdio>

// ─── Logging ─────────────────────────────────────────────────────────────────
// Android: __android_log_print routes to logcat.
// All other platforms (iOS, desktop, Flutter host): fprintf to stderr/stdout.
// The logging macro is the ONLY platform conditional in this file.

#ifdef __ANDROID__
#  include <android/log.h>
#  define DAI_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "DeviceAI-LLM", __VA_ARGS__)
#  define DAI_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "DeviceAI-LLM", __VA_ARGS__)
#  define DAI_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "DeviceAI-LLM", __VA_ARGS__)
#else
#  define DAI_LOGI(...) fprintf(stdout, "[DeviceAI-LLM] "       __VA_ARGS__); fputc('\n', stdout)
#  define DAI_LOGE(...) fprintf(stderr, "[DeviceAI-LLM ERROR] " __VA_ARGS__); fputc('\n', stderr)
#  define DAI_LOGD(...) fprintf(stdout, "[DeviceAI-LLM DEBUG] " __VA_ARGS__); fputc('\n', stdout)
#endif

// ─── Global state ─────────────────────────────────────────────────────────────
// Single-instance engine. For multi-instance support in a future version,
// wrap these in a struct and expose an opaque handle.

static llama_model   *g_model   = nullptr;
static llama_context *g_ctx     = nullptr;
static std::atomic<bool> g_cancel{false};

// ─── Internal helpers ─────────────────────────────────────────────────────────

static void cleanup() {
    if (g_ctx)   { llama_free(g_ctx);        g_ctx   = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

/**
 * Build a sampler chain: top-k → top-p → temperature → repeat-penalty → dist.
 * Caller is responsible for freeing via llama_sampler_free().
 */
static llama_sampler *build_sampler(
    float temperature,
    float top_p,
    int   top_k,
    float repeat_penalty
) {
    auto *chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_penalties(
        64,             // last_n penalty window
        repeat_penalty,
        0.0f,           // frequency penalty
        0.0f            // presence penalty
    ));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return chain;
}

/**
 * Apply the model's embedded chat template to a conversation.
 *
 * Handles ChatML, Llama-3, Gemma, Mistral, Phi-3, etc. automatically via
 * llama_chat_apply_template — no hard-coded template strings.
 *
 * IMPORTANT: storage must be reserved before the loop. Any reallocation
 * invalidates existing .c_str() pointers stored in msgs[], causing UB.
 */
static std::string build_prompt(
    const char **roles,
    const char **contents,
    int          count
) {
    if (!g_model || count <= 0) return "";

    std::vector<std::string>        storage;
    std::vector<llama_chat_message> msgs;
    storage.reserve(count * 2);

    for (int i = 0; i < count; i++) {
        storage.push_back(roles[i]    ? roles[i]    : "");
        storage.push_back(contents[i] ? contents[i] : "");
        msgs.push_back({
            storage[storage.size() - 2].c_str(),
            storage.back().c_str()
        });
    }

    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    int32_t sz = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, nullptr, 0);
    if (sz <= 0) return "";

    std::string out(sz, '\0');
    llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, out.data(), sz);
    return out;
}

/**
 * Core token generation loop.
 *
 * Tokenizes the prompt, decodes it in one batch, then samples token-by-token.
 * on_token is called for each piece; returning false stops generation early.
 * KV-cache is cleared at the start of each call for stateless behaviour.
 */
static std::string do_generate(
    const std::string                        &prompt,
    int                                       max_tokens,
    float                                     temperature,
    float                                     top_p,
    int                                       top_k,
    float                                     repeat_penalty,
    std::function<bool(const std::string &)>  on_token
) {
    if (!g_model || !g_ctx) return "";

    // Clear KV-cache so each call is fully stateless
    llama_memory_clear(llama_get_memory(g_ctx), /*data=*/false);
    llama_perf_context_reset(g_ctx);

    const llama_vocab *vocab = llama_model_get_vocab(g_model);

    // Tokenize prompt
    int n_ctx_max = llama_n_ctx(g_ctx);
    std::vector<llama_token> tokens(n_ctx_max);
    int n_tokens = llama_tokenize(
        vocab,
        prompt.c_str(),
        (int)prompt.size(),
        tokens.data(),
        n_ctx_max,
        /*add_special=*/true,
        /*parse_special=*/true
    );
    if (n_tokens < 0) {
        DAI_LOGE("Tokenization failed (prompt too long?)");
        return "";
    }
    tokens.resize(n_tokens);

    // Decode full prompt in one batch
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(g_ctx, batch)) {
        DAI_LOGE("Prompt decode failed");
        return "";
    }

    auto *sampler = build_sampler(temperature, top_p, top_k, repeat_penalty);

    std::string result;
    char        piece_buf[256];
    int         n_generated = 0;

    while (n_generated < max_tokens && !g_cancel.load()) {
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        llama_sampler_accept(sampler, token);

        if (llama_vocab_is_eog(vocab, token)) break;

        int n = llama_token_to_piece(vocab, token, piece_buf, sizeof(piece_buf), 0, true);
        if (n < 0) break;

        std::string piece(piece_buf, n);
        result += piece;
        n_generated++;

        if (!on_token(piece)) break;

        // Decode the newly generated token
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(g_ctx, next)) break;
    }

    llama_sampler_free(sampler);
    DAI_LOGD("Generated %d tokens", n_generated);
    return result;
}

// ─── Public C API ─────────────────────────────────────────────────────────────

extern "C" {

int dai_llm_init(
    const char *model_path,
    int         context_size,
    int         max_threads,
    int         use_gpu
) {
    cleanup();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = use_gpu ? 99 : 0;

    g_model = llama_model_load_from_file(model_path, mparams);
    if (!g_model) {
        DAI_LOGE("Failed to load model: %s", model_path);
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = context_size;
    cparams.n_threads = max_threads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        DAI_LOGE("Failed to create llama context");
        llama_model_free(g_model);
        g_model = nullptr;
        return 0;
    }

    DAI_LOGI("Initialized: %s (ctx=%d threads=%d gpu=%d)",
             model_path, context_size, max_threads, use_gpu);
    return 1;
}

void dai_llm_shutdown(void) {
    cleanup();
    DAI_LOGI("Shutdown complete");
}

char *dai_llm_generate(
    const char **roles,
    const char **contents,
    int          message_count,
    int          max_tokens,
    float        temperature,
    float        top_p,
    int          top_k,
    float        repeat_penalty
) {
    g_cancel = false;
    std::string prompt = build_prompt(roles, contents, message_count);
    std::string result = do_generate(
        prompt, max_tokens, temperature, top_p, top_k, repeat_penalty,
        [](const std::string &) { return true; }
    );

    char *out = static_cast<char *>(malloc(result.size() + 1));
    if (out) memcpy(out, result.c_str(), result.size() + 1);
    return out;
}

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
) {
    g_cancel = false;
    std::string prompt = build_prompt(roles, contents, message_count);

    do_generate(
        prompt, max_tokens, temperature, top_p, top_k, repeat_penalty,
        [&](const std::string &piece) -> bool {
            if (on_token) on_token(piece.c_str(), user_data);
            return !g_cancel.load();
        }
    );
    // Stream completes when this function returns — no on_complete needed.
    (void)on_error; // reserved for future error propagation during streaming
}

void dai_llm_cancel(void) {
    g_cancel = true;
}

void dai_llm_free_string(char *ptr) {
    free(ptr);
}

} // extern "C"
