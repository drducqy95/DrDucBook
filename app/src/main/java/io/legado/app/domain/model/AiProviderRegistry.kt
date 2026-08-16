package io.legado.app.domain.model

import androidx.annotation.Keep

object AiServiceKind {
    const val LLM = "llm"
    const val EMBEDDING = "embedding"
    const val IMAGE = "image"
    const val IMAGE_TO_TEXT = "imageToText"
    const val WEB_SEARCH = "webSearch"
    const val WEB_FETCH = "webFetch"
    const val TTS = "tts"
    const val STT = "stt"
    const val VIDEO = "video"
}

object AiRegistryAuthType {
    const val NONE = "none"
    const val API_KEY = "apikey"
    const val OAUTH = "oauth"
    const val COOKIE = "cookie"
    const val LOCAL = "local"
}

@Keep
data class AiRegistryEndpoint(
    val kind: String,
    val url: String,
    val format: String = "",
    val authType: String = "",
    val authHeader: String = "",
)

@Keep
data class AiRegistryModel(
    val id: String,
    val name: String,
    val kind: String = AiServiceKind.LLM,
)

@Keep
data class AiRegistryOAuth(
    val flow: String,
    val clientId: String = "",
    val authorizeUrl: String = "",
    val deviceCodeUrl: String = "",
    val tokenUrl: String = "",
    val refreshUrl: String = "",
    val scope: String = "",
)

/**
 * Lossless-enough, offline provider inventory generated from 9router's registry. It deliberately
 * keeps non-chat capabilities so Ebook Editor and media modules can reuse the same provider IDs.
 * Protocol executors remain explicit: presence here does not imply that an adapter is implemented.
 */
@Keep
data class AiProviderRegistryEntry(
    val id: String,
    val name: String,
    val alias: String = "",
    val category: String,
    val authModes: Set<String>,
    val serviceKinds: Set<String>,
    val noAuth: Boolean = false,
    val hidden: Boolean = false,
    val hasFree: Boolean = false,
    val deprecated: Boolean = false,
    val endpoints: List<AiRegistryEndpoint> = emptyList(),
    val models: List<AiRegistryModel> = emptyList(),
    val modelsUrl: String? = null,
    val modelsFetcherType: String? = null,
    val oauth: AiRegistryOAuth? = null,
    val sourcePath: String,
)

object AiProviderRegistry {
    const val UPSTREAM_REPOSITORY = "https://github.com/decolua/9router"
    const val UPSTREAM_VERSION = "0.4.63"
    const val SYNC_DATE = "2026-07-19"

    val entries: List<AiProviderRegistryEntry> = AiProviderRegistryGenerated.entries

    val textProviders: List<AiProviderRegistryEntry> by lazy {
        entries.filter { AiServiceKind.LLM in it.serviceKinds }
    }

    /** Providers that can power translation, editing, chatbot and writing workflows. */
    val languageProviders: List<AiProviderRegistryEntry> by lazy {
        entries.filter { AiServiceKind.LLM in it.serviceKinds }
    }

    /** Providers that expose vector embeddings for semantic search/entity memory. */
    val embeddingProviders: List<AiProviderRegistryEntry> by lazy {
        entries.filter { AiServiceKind.EMBEDDING in it.serviceKinds }
    }

    /** Providers retained for Ebook Editor media insertion and export workflows. */
    val mediaProviders: List<AiProviderRegistryEntry> by lazy {
        entries.filter {
            it.serviceKinds.any { kind ->
                kind == AiServiceKind.IMAGE ||
                    kind == AiServiceKind.IMAGE_TO_TEXT ||
                    kind == AiServiceKind.TTS ||
                    kind == AiServiceKind.STT ||
                    kind == AiServiceKind.VIDEO
            }
        }
    }

    val freeProviders: List<AiProviderRegistryEntry> by lazy {
        entries.filter { it.noAuth || it.hasFree || it.category == "free" || it.category == "freeTier" }
    }

    val apiKeyProviders: List<AiProviderRegistryEntry> by lazy {
        entries.filter { AiRegistryAuthType.API_KEY in it.authModes }
    }

    val oauthProviders: List<AiProviderRegistryEntry> by lazy {
        entries.filter { AiRegistryAuthType.OAUTH in it.authModes }
    }

    fun providersFor(serviceKind: String): List<AiProviderRegistryEntry> =
        entries.filter { serviceKind in it.serviceKinds }

    fun byId(id: String): AiProviderRegistryEntry? = entries.firstOrNull { it.id == id }
}
