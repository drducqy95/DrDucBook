package io.legado.app.domain.model

import androidx.annotation.Keep

object AiProviderCategory {
    const val API_KEY = "apikey"
    const val FREE = "free"
    const val FREE_TIER = "free_tier"
    const val SUBSCRIPTION_KEY = "subscription_key"
    const val LOCAL = "local"
}

@Keep
data class AiCatalogModel(
    val id: String,
    val name: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
)

@Keep
data class AiProviderCatalogEntry(
    val id: String,
    val name: String,
    val category: String,
    val protocol: String = AiProtocol.OPENAI_CHAT_COMPLETIONS,
    val baseUrl: String,
    val modelsUrl: String? = null,
    val authType: String = AiProviderAuthType.BEARER,
    val headers: Map<String, String> = emptyMap(),
    val customHeaders: Map<String, String> = emptyMap(),
    val chatPath: String = "/chat/completions",
    val responsesPath: String = "/responses",
    val messagesPath: String = "/v1/messages",
    val modelsPath: String? = null,
    val serviceKinds: Set<String> = setOf(AiServiceKind.LLM),
    val models: List<AiCatalogModel> = emptyList(),
    val notice: String = "",
)

/** Text providers that have a protocol adapter usable by chat and translation today. */
object AiProviderCatalog {
    private val curatedEntries: List<AiProviderCatalogEntry> = listOf(
        AiProviderCatalogEntry(
            id = "local_gguf",
            name = "Local GGUF",
            category = AiProviderCategory.LOCAL,
            protocol = AiProtocol.LOCAL_GGUF,
            baseUrl = "",
            authType = AiProviderAuthType.NONE,
            models = LocalAiModelCatalog.all.map { model ->
                AiCatalogModel(
                    model.fileName,
                    model.fileName,
                    model.contextWindow,
                    model.defaultParams.maxOutputTokens ?: model.contextWindow,
                )
            },
            notice = "Chạy model GGUF cục bộ; cần chọn file model và test sinh thử trước khi dùng.",
        ),
        AiProviderCatalogEntry(
            id = "opencode_free",
            name = "OpenCode Free",
            category = AiProviderCategory.FREE,
            baseUrl = "https://console.opencode.ai/inference/openai/v1",
            modelsUrl = "https://console.opencode.ai/inference/openai/v1/models",
            authType = AiProviderAuthType.NONE,
            models = listOf(
                AiCatalogModel("big-pickle", "Big Pickle"),
                AiCatalogModel("mimo-v2.5-free", "MiMo V2.5 Free", 200_000, 32_000),
            ),
            notice = "Không cần API key; model miễn phí được lấy trực tiếp từ OpenCode Zen.",
        ),
        AiProviderCatalogEntry(
            id = "mimo_free",
            name = "MiMo Code Free",
            category = AiProviderCategory.FREE,
            baseUrl = "https://api.xiaomimimo.com/api/free-ai/openai",
            authType = AiProviderAuthType.NONE,
            chatPath = "/chat",
            models = listOf(
                AiCatalogModel("mimo-auto", "MiMo Auto"),
            ),
            notice = "Endpoint free chính thức của Xiaomi; model MiMo Auto, không cần đăng nhập/API key.",
        ),
        AiProviderCatalogEntry(
            id = "xiaomi_mimo",
            name = "Xiaomi MiMo",
            category = AiProviderCategory.API_KEY,
            baseUrl = "https://api.xiaomimimo.com/v1",
            modelsUrl = "https://api.xiaomimimo.com/v1/models",
            models = listOf(
                AiCatalogModel("mimo-v2.5-pro", "MiMo V2.5 Pro", 1_000_000, 128_000),
                AiCatalogModel("mimo-v2.5", "MiMo V2.5", 1_000_000, 128_000),
            ),
            notice = "Nhận cả API key pay-as-you-go (sk-) và key do Xiaomi cấp.",
        ),
        AiProviderCatalogEntry(
            id = "xiaomi_mimo_token_plan_sgp",
            name = "Xiaomi MiMo Token Plan · Singapore",
            category = AiProviderCategory.SUBSCRIPTION_KEY,
            baseUrl = "https://token-plan-sgp.xiaomimimo.com/v1",
            modelsUrl = "https://token-plan-sgp.xiaomimimo.com/v1/models",
            models = listOf(
                AiCatalogModel("mimo-v2.5-pro", "MiMo V2.5 Pro", 1_000_000, 128_000),
                AiCatalogModel("mimo-v2.5", "MiMo V2.5", 1_000_000, 128_000),
            ),
            notice = "Dùng key Token Plan dạng tp-; endpoint phải đúng vùng đăng ký.",
        ),
        AiProviderCatalogEntry(
            id = "opencode_go",
            name = "OpenCode Go",
            category = AiProviderCategory.SUBSCRIPTION_KEY,
            baseUrl = "https://opencode.ai/zen/go/v1",
            models = listOf(
                AiCatalogModel("glm-5.2", "GLM 5.2"),
                AiCatalogModel("kimi-k2.7-code", "Kimi K2.7 Code"),
                AiCatalogModel("deepseek-v4-pro", "DeepSeek V4 Pro"),
                AiCatalogModel("mimo-v2.5-pro", "MiMo V2.5 Pro"),
                AiCatalogModel("minimax-m3", "MiniMax M3"),
            ),
            notice = "Dùng API key của gói OpenCode Go.",
        ),
        AiProviderCatalogEntry(
            id = "openrouter",
            name = "OpenRouter",
            category = AiProviderCategory.FREE_TIER,
            baseUrl = "https://openrouter.ai/api/v1",
            modelsUrl = "https://openrouter.ai/api/v1/models",
            headers = mapOf(
                "HTTP-Referer" to "https://legado.local",
                "X-Title" to "Legado AI Router",
            ),
            models = listOf(
                AiCatalogModel("openrouter/free", "OpenRouter Free Router"),
            ),
            notice = "Hỗ trợ model :free; cần API key OpenRouter.",
        ),
        AiProviderCatalogEntry(
            id = "groq",
            name = "Groq",
            category = AiProviderCategory.FREE_TIER,
            baseUrl = "https://api.groq.com/openai/v1",
            modelsUrl = "https://api.groq.com/openai/v1/models",
            models = listOf(
                AiCatalogModel("llama-3.3-70b-versatile", "Llama 3.3 70B"),
                AiCatalogModel("qwen/qwen3-32b", "Qwen3 32B"),
                AiCatalogModel("openai/gpt-oss-120b", "GPT-OSS 120B"),
            ),
            notice = "Free tier có giới hạn tốc độ; cần API key Groq.",
        ),
        AiProviderCatalogEntry(
            id = "deepseek",
            name = "DeepSeek",
            category = AiProviderCategory.API_KEY,
            baseUrl = "https://api.deepseek.com",
            modelsUrl = "https://api.deepseek.com/models",
            models = listOf(
                AiCatalogModel("deepseek-v4-pro", "DeepSeek V4 Pro"),
                AiCatalogModel("deepseek-v4-flash", "DeepSeek V4 Flash"),
                AiCatalogModel("deepseek-chat", "DeepSeek Chat"),
                AiCatalogModel("deepseek-reasoner", "DeepSeek Reasoner"),
            ),
        ),
        AiProviderCatalogEntry(
            id = "openai",
            name = "OpenAI API",
            category = AiProviderCategory.API_KEY,
            protocol = AiProtocol.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            modelsUrl = "https://api.openai.com/v1/models",
            models = listOf(
                AiCatalogModel("gpt-5.4-mini", "GPT 5.4 Mini", 400_000, 128_000),
                AiCatalogModel("gpt-5.4", "GPT 5.4", 400_000, 128_000),
            ),
        ),
        AiProviderCatalogEntry(
            id = "anthropic",
            name = "Anthropic API",
            category = AiProviderCategory.API_KEY,
            protocol = AiProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://api.anthropic.com",
            modelsUrl = "https://api.anthropic.com/v1/models",
            authType = AiProviderAuthType.HEADER,
            customHeaders = mapOf("x-api-key" to "{apiKey}"),
            models = listOf(
                AiCatalogModel("claude-sonnet-4-6", "Claude Sonnet 4.6", 200_000, 64_000),
                AiCatalogModel("claude-opus-4-6", "Claude Opus 4.6", 200_000, 64_000),
            ),
        ),
        AiProviderCatalogEntry(
            id = "gemini",
            name = "Google Gemini API",
            category = AiProviderCategory.FREE_TIER,
            protocol = AiProtocol.GEMINI_GENERATE_CONTENT,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            modelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
            models = listOf(
                AiCatalogModel("gemini-3.6-flash", "Gemini 3.6 Flash", 1_000_000, 64_000),
                AiCatalogModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", 1_000_000, 64_000),
                AiCatalogModel("gemini-3.5-flash", "Gemini 3.5 Flash", 1_000_000, 64_000),
                AiCatalogModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", 1_000_000, 64_000),
                AiCatalogModel("gemini-3.1-flash", "Gemini 3.1 Flash", 1_000_000, 64_000),
                AiCatalogModel("gemini-3.1-pro", "Gemini 3.1 Pro", 1_000_000, 64_000),
            ),
        ),
    )

    private val curatedUpstreamIds = setOf(
        "opencode",
        "mimo-free",
        "mmf",
        "xiaomi-mimo",
        "xiaomi-tokenplan",
        "opencode-go",
        "openrouter",
        "groq",
        "deepseek",
        "openai",
        "anthropic",
        "gemini",
    )

    private val compatibleRegistryEntries: List<AiProviderCatalogEntry> =
        AiProviderRegistry.textProviders.asSequence()
            .filterNot { it.hidden || it.id in curatedUpstreamIds }
            .filter { it.noAuth || AiRegistryAuthType.API_KEY in it.authModes }
            .mapNotNull(::toTextCatalogEntry)
            .toList()

    val entries: List<AiProviderCatalogEntry> =
        (curatedEntries + compatibleRegistryEntries).distinctBy(AiProviderCatalogEntry::id)

    fun byId(id: String): AiProviderCatalogEntry? = entries.firstOrNull { it.id == id }

    val autoInstallIds: Set<String> = setOf("opencode_free")
}

private fun toTextCatalogEntry(registry: AiProviderRegistryEntry): AiProviderCatalogEntry? {
    val endpoint = registry.endpoints.firstOrNull { it.kind == AiServiceKind.LLM } ?: return null
    val endpointParts = endpoint.toProtocolEndpoint() ?: return null
    val category = when {
        registry.noAuth -> AiProviderCategory.FREE
        registry.hasFree -> AiProviderCategory.FREE_TIER
        registry.category == "freeTier" -> AiProviderCategory.FREE_TIER
        else -> AiProviderCategory.API_KEY
    }
    val authType = when {
        registry.noAuth -> AiProviderAuthType.NONE
        endpointParts.protocol == AiProtocol.ANTHROPIC_MESSAGES -> AiProviderAuthType.HEADER
        else -> AiProviderAuthType.BEARER
    }
    val customHeaders = when {
        !registry.noAuth && endpointParts.protocol == AiProtocol.ANTHROPIC_MESSAGES ->
            mapOf("x-api-key" to "{apiKey}")
        registry.id == "commandcode" -> mapOf(
            // Command Code expects these CLI identity headers in addition to Bearer auth.
            "x-command-code-version" to "0.25.7",
            "x-cli-environment" to "cli",
        )
        else -> emptyMap()
    }
    return AiProviderCatalogEntry(
        id = registry.id.replace('-', '_'),
        name = registry.name,
        category = category,
        protocol = endpointParts.protocol,
        baseUrl = endpointParts.baseUrl,
        modelsUrl = registry.modelsUrl,
        authType = authType,
        customHeaders = customHeaders,
        chatPath = endpointParts.path.takeIf {
            endpointParts.protocol == AiProtocol.OPENAI_CHAT_COMPLETIONS
        } ?: "/chat/completions",
        responsesPath = endpointParts.path.takeIf {
            endpointParts.protocol == AiProtocol.OPENAI_RESPONSES
        } ?: "/responses",
        messagesPath = endpointParts.path.takeIf {
            endpointParts.protocol == AiProtocol.ANTHROPIC_MESSAGES
        } ?: "/v1/messages",
        serviceKinds = registry.serviceKinds,
        models = registry.models
            .filter { it.kind == AiServiceKind.LLM }
            .map { AiCatalogModel(it.id, it.name) },
        notice = "Đồng bộ từ 9router: ${registry.category}; ${registry.authModes.joinToString()}.",
    )
}

private data class ProtocolEndpoint(
    val protocol: String,
    val baseUrl: String,
    val path: String,
)

private fun AiRegistryEndpoint.toProtocolEndpoint(): ProtocolEndpoint? {
    val normalizedUrl = url.trimEnd('/')
    val candidates = when {
        format == "commandcode" -> listOf("") to AiProtocol.COMMAND_CODE
        format == "claude" -> listOf("/v1/messages", "/messages") to AiProtocol.ANTHROPIC_MESSAGES
        format == "gemini" -> listOf("/v1beta/models", "/models") to AiProtocol.GEMINI_GENERATE_CONTENT
        normalizedUrl.endsWith("/responses") -> listOf("/responses") to AiProtocol.OPENAI_RESPONSES
        normalizedUrl.endsWith("/chat/completions") ->
            listOf("/chat/completions") to AiProtocol.OPENAI_CHAT_COMPLETIONS
        normalizedUrl.endsWith("/v1/messages") ->
            listOf("/v1/messages") to AiProtocol.ANTHROPIC_MESSAGES
        else -> return null
    }
    val path = candidates.first.firstOrNull { it.isEmpty() || normalizedUrl.endsWith(it) } ?: return null
    return ProtocolEndpoint(
        protocol = candidates.second,
        baseUrl = normalizedUrl.removeSuffix(path),
        path = path,
    )
}
