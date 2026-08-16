#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <algorithm>
#include <mutex>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "sampling.h"

namespace {

constexpr const char * LOG_TAG = "LegadoLocalAI";
std::once_flag backend_once;
std::atomic<bool> cancelled{false};

struct Engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    int context_window = 4096;
    int batch_size = 256;
    llama_tokens cached_prompt_tokens;

    ~Engine() {
        if (context != nullptr) llama_free(context);
        if (model != nullptr) llama_model_free(model);
    }
};

void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

void throw_java(JNIEnv * env, const char * class_name, const std::string & message) {
    if (env->ExceptionCheck()) return;
    if (jclass clazz = env->FindClass(class_name)) {
        env->ThrowNew(clazz, message.c_str());
    }
}

std::string jstring_to_utf8(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string output(chars);
    env->ReleaseStringUTFChars(value, chars);
    return output;
}

std::vector<std::string> string_array(JNIEnv * env, jobjectArray array) {
    std::vector<std::string> values;
    if (array == nullptr) return values;
    const auto size = env->GetArrayLength(array);
    values.reserve(size);
    for (jsize i = 0; i < size; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        values.push_back(jstring_to_utf8(env, value));
        env->DeleteLocalRef(value);
    }
    return values;
}

std::string format_prompt(
    Engine * engine,
    const std::vector<std::string> & roles,
    const std::vector<std::string> & contents
) {
    if (roles.size() != contents.size() || roles.empty()) {
        throw std::invalid_argument("Local AI request has invalid messages");
    }
    auto templates = common_chat_templates_init(engine->model, "");
    common_chat_templates_inputs inputs;
    inputs.use_jinja = true;
    inputs.add_generation_prompt = true;
    inputs.enable_thinking = false;
    for (size_t i = 0; i < roles.size(); ++i) {
        common_chat_msg message;
        message.role = roles[i];
        message.content = contents[i];
        inputs.messages.push_back(std::move(message));
    }
    return common_chat_templates_apply(templates.get(), inputs).prompt;
}

bool valid_utf8(const std::string & value) {
    const auto * bytes = reinterpret_cast<const unsigned char *>(value.data());
    size_t i = 0;
    while (i < value.size()) {
        int length = 0;
        if ((bytes[i] & 0x80) == 0) length = 1;
        else if ((bytes[i] & 0xE0) == 0xC0) length = 2;
        else if ((bytes[i] & 0xF0) == 0xE0) length = 3;
        else if ((bytes[i] & 0xF8) == 0xF0) length = 4;
        else return false;
        if (i + length > value.size()) return false;
        for (int j = 1; j < length; ++j) {
            if ((bytes[i + j] & 0xC0) != 0x80) return false;
        }
        i += length;
    }
    return true;
}

void decode_prompt(Engine * engine, const llama_tokens & tokens) {
    llama_memory_t memory = llama_get_memory(engine->context);
    int reusable_tokens = 0;
    const int shared_tokens = std::min(
        static_cast<int>(engine->cached_prompt_tokens.size()),
        static_cast<int>(tokens.size())
    );
    while (reusable_tokens < shared_tokens &&
        engine->cached_prompt_tokens[reusable_tokens] == tokens[reusable_tokens]) {
        ++reusable_tokens;
    }
    // Re-evaluate at least the final prompt token so its logits are available for sampling.
    reusable_tokens = std::min(
        reusable_tokens,
        std::max(0, static_cast<int>(tokens.size()) - 1)
    );
    if (reusable_tokens == 0 ||
        !llama_memory_seq_rm(memory, 0, reusable_tokens, -1)) {
        llama_memory_clear(memory, false);
        reusable_tokens = 0;
    }

    llama_batch batch = llama_batch_init(engine->batch_size, 0, 1);
    for (int offset = reusable_tokens;
        offset < static_cast<int>(tokens.size());
        offset += engine->batch_size) {
        const int count = std::min(engine->batch_size, static_cast<int>(tokens.size()) - offset);
        common_batch_clear(batch);
        for (int i = 0; i < count; ++i) {
            common_batch_add(
                batch,
                tokens[offset + i],
                offset + i,
                {0},
                offset + i == static_cast<int>(tokens.size()) - 1
            );
        }
        if (llama_decode(engine->context, batch) != 0) {
            llama_batch_free(batch);
            throw std::runtime_error("llama_decode failed while processing the prompt");
        }
    }
    llama_batch_free(batch);
    engine->cached_prompt_tokens = tokens;
    __android_log_print(
        ANDROID_LOG_DEBUG,
        LOG_TAG,
        "Prompt KV reuse: %d/%zu tokens",
        reusable_tokens,
        tokens.size()
    );
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_legado_app_data_repository_LocalAiNativeBridge_load(
    JNIEnv * env,
    jobject,
    jstring native_lib_dir,
    jstring model_path,
    jint context_window,
    jint threads,
    jint batch_threads,
    jint batch_size,
    jint micro_batch_size,
    jboolean use_mmap,
    jboolean use_mlock,
    jint gpu_layers
) {
    try {
        const std::string lib_dir = jstring_to_utf8(env, native_lib_dir);
        std::call_once(backend_once, [&lib_dir] {
            llama_log_set([](ggml_log_level level, const char * text, void *) {
                const int priority = level == GGML_LOG_LEVEL_ERROR
                    ? ANDROID_LOG_ERROR
                    : ANDROID_LOG_DEBUG;
                __android_log_print(priority, LOG_TAG, "%s", text);
            }, nullptr);
            ggml_backend_load_all_from_path(lib_dir.c_str());
            llama_backend_init();
        });

        auto engine = std::make_unique<Engine>();
        llama_model_params model_params = llama_model_default_params();
        model_params.use_mmap = use_mmap;
        model_params.use_mlock = use_mlock;
        model_params.n_gpu_layers = gpu_layers;
        const std::string path = jstring_to_utf8(env, model_path);
        engine->model = llama_model_load_from_file(path.c_str(), model_params);
        if (engine->model == nullptr) throw std::runtime_error("Unable to load GGUF model");

        llama_context_params context_params = llama_context_default_params();
        engine->context_window = std::max(1024, static_cast<int>(context_window));
        engine->batch_size = std::max(32, static_cast<int>(batch_size));
        context_params.n_ctx = engine->context_window;
        context_params.n_batch = engine->batch_size;
        context_params.n_ubatch = std::max(16, static_cast<int>(micro_batch_size));
        context_params.n_threads = std::max(1, static_cast<int>(threads));
        context_params.n_threads_batch = std::max(1, static_cast<int>(batch_threads));
        context_params.no_perf = true;
        engine->context = llama_init_from_model(engine->model, context_params);
        if (engine->context == nullptr) throw std::runtime_error("Unable to allocate local AI context");
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception & error) {
        log_error(error.what());
        throw_java(env, "java/lang/IllegalStateException", error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_legado_app_data_repository_LocalAiNativeBridge_generate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jobjectArray roles_array,
    jobjectArray contents_array,
    jint max_output_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repetition_penalty,
    jobject callback
) {
    try {
        auto * engine = reinterpret_cast<Engine *>(handle);
        if (engine == nullptr || engine->model == nullptr || engine->context == nullptr) {
            throw std::invalid_argument("Local AI engine is not loaded");
        }
        const auto roles = string_array(env, roles_array);
        const auto contents = string_array(env, contents_array);
        const std::string prompt = format_prompt(engine, roles, contents);
        llama_tokens tokens = common_tokenize(engine->context, prompt, true, true);
        constexpr int safety_tokens = 64;
        const int available_output = engine->context_window - static_cast<int>(tokens.size()) - safety_tokens;
        if (available_output <= 0) {
            throw std::invalid_argument("Prompt exceeds the local model context window");
        }
        const int output_limit = std::min(
            std::max(1, static_cast<int>(max_output_tokens)),
            available_output
        );
        decode_prompt(engine, tokens);

        common_params_sampling sampling;
        sampling.temp = temperature;
        sampling.top_p = top_p;
        sampling.top_k = top_k;
        sampling.penalty_repeat = repetition_penalty;
        common_sampler * sampler = common_sampler_init(engine->model, sampling);
        if (sampler == nullptr) throw std::runtime_error("Unable to initialize local sampler");
        for (llama_token token : tokens) common_sampler_accept(sampler, token, false);

        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        jmethodID is_cancelled = env->GetMethodID(callback_class, "isCancelled", "()Z");
        if (on_token == nullptr || is_cancelled == nullptr) {
            common_sampler_free(sampler);
            throw std::runtime_error("Invalid local AI streaming callback");
        }

        cancelled.store(false);
        int position = static_cast<int>(tokens.size());
        std::string utf8_buffer;
        llama_batch batch = llama_batch_init(1, 0, 1);
        for (int generated = 0; generated < output_limit; ++generated) {
            if (cancelled.load() || env->CallBooleanMethod(callback, is_cancelled)) break;
            const llama_token token = common_sampler_sample(sampler, engine->context, -1);
            common_sampler_accept(sampler, token, true);
            if (llama_vocab_is_eog(llama_model_get_vocab(engine->model), token)) break;

            common_batch_clear(batch);
            common_batch_add(batch, token, position++, {0}, true);
            if (llama_decode(engine->context, batch) != 0) {
                throw std::runtime_error("llama_decode failed while generating output");
            }
            utf8_buffer += common_token_to_piece(engine->context, token);
            if (valid_utf8(utf8_buffer)) {
                jstring text = env->NewStringUTF(utf8_buffer.c_str());
                env->CallVoidMethod(callback, on_token, text);
                env->DeleteLocalRef(text);
                utf8_buffer.clear();
                if (env->ExceptionCheck()) break;
            }
        }
        llama_batch_free(batch);
        common_sampler_free(sampler);
        env->DeleteLocalRef(callback_class);
    } catch (const std::exception & error) {
        log_error(error.what());
        throw_java(env, "java/lang/IllegalStateException", error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_legado_app_data_repository_LocalAiNativeBridge_cancel(JNIEnv *, jobject) {
    cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_io_legado_app_data_repository_LocalAiNativeBridge_free(
    JNIEnv *,
    jobject,
    jlong handle
) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return;
    delete engine;
}
