#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <android/log.h>
#include <thread>
#include <chrono>
#include "llama.h"

#define LOG_TAG "LlamaAndroidJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaContextWrapper {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    std::string path;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_arivai_data_model_LlamaEngine_nativeLoadModel(
        JNIEnv *env, jobject thiz, jstring jpath, jint nThreads) {

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    LOGI("nativeLoadModel starting for path: %s with nThreads: %d", path, nThreads);

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();

    llama_model* model = llama_model_load_from_file(path, model_params);
    if (!model) {
        LOGE("llama_model_load_from_file failed for %s", path);
        env->ReleaseStringUTFChars(jpath, path);
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("llama_init_from_model failed");
        llama_model_free(model);
        env->ReleaseStringUTFChars(jpath, path);
        return 0;
    }

    const llama_vocab* vocab = llama_model_get_vocab(model);

    auto wrapper = new LlamaContextWrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->vocab = vocab;
    wrapper->path = path;

    LOGI("llama.cpp GGUF model loaded successfully!");
    env->ReleaseStringUTFChars(jpath, path);
    return reinterpret_cast<jlong>(wrapper);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_arivai_data_model_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject thiz, jlong handle, jstring jprompt, jint maxTokens) {

    if (handle == 0) return env->NewStringUTF("Error: Invalid model handle.");

    auto wrapper = reinterpret_cast<LlamaContextWrapper*>(handle);
    const char *promptCStr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(promptCStr);
    env->ReleaseStringUTFChars(jprompt, promptCStr);

    llama_synchronize(wrapper->ctx);

    std::vector<llama_token> tokens(2048);
    int n_tokens = llama_tokenize(wrapper->vocab, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(wrapper->vocab, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);

    if (n_tokens == 0) return env->NewStringUTF("");

    // NOTE: llama_batch_get_one() returns a batch with logits == nullptr.
    // Do NOT manually index into batch.logits here — it is a null pointer,
    // and writing through it is undefined behavior (it will not always crash
    // immediately; it can silently corrupt memory and surface later as NaNs
    // deep inside ggml's compute graph, e.g. in swiglu). llama_decode()
    // already computes logits for the last token by default for a batch
    // built this way, matching upstream llama.cpp's own examples.
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    if (llama_decode(wrapper->ctx, batch) != 0) {
        return env->NewStringUTF("Error in llama_decode");
    }

    auto sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    // Without a repetition penalty, small models (0.6B especially) are much
    // more prone to degenerating into copying/echoing recently-seen tokens
    // -- including the prompt itself. penalty_last_n=64 looks at the last 64
    // tokens; penalty_repeat=1.1 mildly discourages repeating any of them.
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(wrapper->vocab), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string resultText;
    int n_decode = 0;

    while (n_decode < maxTokens) {
        llama_token new_token_id = llama_sampler_sample(sampler, wrapper->ctx, -1);
        if (llama_vocab_is_eog(wrapper->vocab, new_token_id)) break;

        char pieceBuf[256] = {0};
        int pieceLen = llama_token_to_piece(wrapper->vocab, new_token_id, pieceBuf, sizeof(pieceBuf), 0, true);
        if (pieceLen > 0) {
            resultText.append(pieceBuf, pieceLen);
        }

        batch = llama_batch_get_one(&new_token_id, 1);
        if (llama_decode(wrapper->ctx, batch) != 0) break;

        n_decode++;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(resultText.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_arivai_data_model_LlamaEngine_nativeGenerateStream(
        JNIEnv *env, jobject thiz, jlong handle, jstring jprompt, jint maxTokens, jobject callback) {

    if (handle == 0 || callback == nullptr) return;

    auto wrapper = reinterpret_cast<LlamaContextWrapper*>(handle);
    const char *promptCStr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(promptCStr);
    env->ReleaseStringUTFChars(jprompt, promptCStr);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "()V");

    if (onTokenMethod == nullptr || onCompleteMethod == nullptr) return;

    llama_synchronize(wrapper->ctx);

    std::vector<llama_token> tokens(2048);
    int n_tokens = llama_tokenize(wrapper->vocab, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(wrapper->vocab, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);

    if (n_tokens == 0) {
        env->CallVoidMethod(callback, onCompleteMethod);
        return;
    }

    // See note in nativeGenerate: do not manually write to batch.logits here.
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("llama_decode failed on prompt");
        env->CallVoidMethod(callback, onCompleteMethod);
        return;
    }

    int n_decode = 0;

    auto sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(wrapper->vocab), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    while (n_decode < maxTokens) {
        llama_token new_token_id = llama_sampler_sample(sampler, wrapper->ctx, -1);
        if (llama_vocab_is_eog(wrapper->vocab, new_token_id)) break;

        char pieceBuf[256] = {0};
        int pieceLen = llama_token_to_piece(wrapper->vocab, new_token_id, pieceBuf, sizeof(pieceBuf), 0, true);
        if (pieceLen > 0) {
            std::string pieceStr(pieceBuf, pieceLen);
            jstring jpiece = env->NewStringUTF(pieceStr.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        batch = llama_batch_get_one(&new_token_id, 1);
        if (llama_decode(wrapper->ctx, batch) != 0) break;

        n_decode++;
    }

    llama_sampler_free(sampler);
    env->CallVoidMethod(callback, onCompleteMethod);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_arivai_data_model_LlamaEngine_nativeUnload(
        JNIEnv *env, jobject thiz, jlong handle) {

    if (handle != 0) {
        auto wrapper = reinterpret_cast<LlamaContextWrapper*>(handle);
        if (wrapper->ctx) llama_free(wrapper->ctx);
        if (wrapper->model) llama_model_free(wrapper->model);
        delete wrapper;
    }
}