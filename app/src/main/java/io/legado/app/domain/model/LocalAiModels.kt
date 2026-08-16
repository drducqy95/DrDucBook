package io.legado.app.domain.model

import kotlin.math.ceil

/** Runtime facts used to tune local inference without coupling the domain layer to Android APIs. */
data class LocalAiDeviceInfo(
    val primaryAbi: String,
    val supportedAbis: Set<String>,
    val availableProcessors: Int,
    val totalMemoryMb: Long,
    val manufacturer: String,
    val model: String,
)

data class LocalAiRuntimeProfile(
    val threads: Int,
    val batchThreads: Int,
    val batchSize: Int,
    val microBatchSize: Int,
    val contextWindow: Int,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val gpuLayers: Int = 0,
    val preferredChunkChars: Int,
    val adjacentContextChars: Int,
)

data class LocalAiTranslationBudget(
    val maxSourceChars: Int,
    val maxOutputTokens: Int,
    val adjacentContextChars: Int,
    val safetyTokens: Int,
)

/**
 * Picks conservative CPU-first defaults. llama.cpp still performs its own runtime ISA detection;
 * this profile only controls memory pressure and scheduling policy.
 */
object LocalAiRuntimePlanner {

    fun plan(
        device: LocalAiDeviceInfo,
        requestedContextWindow: Int,
    ): LocalAiRuntimeProfile {
        val abi = device.primaryAbi.lowercase()
        require(abi == "arm64-v8a" || abi == "x86_64") {
            "Local GGUF inference requires a 64-bit ARM or x86 runtime"
        }
        val processors = device.availableProcessors.coerceAtLeast(1)
        val isHuaweiPuraClass = device.manufacturer.contains("huawei", ignoreCase = true) &&
            (device.model.contains("pura", ignoreCase = true) || device.totalMemoryMb >= 8_000)
        val lowMemory = device.totalMemoryMb in 1..5_999
        val contextWindow = requestedContextWindow
            .takeIf { it > 0 }
            ?.coerceIn(1_024, if (lowMemory) 2_048 else 4_096)
            ?: if (lowMemory) 2_048 else 4_096

        return when {
            isHuaweiPuraClass -> LocalAiRuntimeProfile(
                threads = processors.coerceIn(4, 6),
                batchThreads = processors.coerceIn(4, 7),
                batchSize = 256,
                microBatchSize = 64,
                contextWindow = contextWindow,
                preferredChunkChars = 768,
                adjacentContextChars = 160,
            )
            abi == "arm64-v8a" -> LocalAiRuntimeProfile(
                threads = processors.coerceIn(3, 5),
                batchThreads = processors.coerceIn(3, 6),
                batchSize = if (lowMemory) 128 else 192,
                microBatchSize = if (lowMemory) 32 else 64,
                contextWindow = contextWindow,
                preferredChunkChars = if (lowMemory) 480 else 640,
                adjacentContextChars = if (lowMemory) 96 else 128,
            )
            else -> LocalAiRuntimeProfile(
                threads = processors.coerceIn(2, 6),
                batchThreads = processors.coerceIn(2, 8),
                batchSize = 256,
                microBatchSize = 64,
                contextWindow = contextWindow,
                preferredChunkChars = 640,
                adjacentContextChars = 128,
            )
        }
    }
}

/**
 * Budgets translation chunks inside one shared context window.
 *
 * Local inference cannot copy the online-provider policy of granting every chunk thousands of
 * output tokens: prompt, terminology, adjacent context, source, result, and KV safety reserve all
 * occupy the same window. The estimates deliberately over-reserve Vietnamese output so a chunk is
 * reduced before native tokenization, then the native engine performs a final exact-token guard.
 */
object LocalAiTranslationBudgetPlanner {

    private const val MIN_CHUNK_CHARS = 10
    private const val PROMPT_CHARS_PER_TOKEN = 2.7
    private const val SOURCE_TOKENS_PER_CHAR = 1.15
    private const val OUTPUT_TOKENS_PER_CHAR = 1.9
    private const val OUTPUT_FIXED_RESERVE = 96

    fun plan(
        contextWindow: Int,
        providerMaxOutputTokens: Int,
        configuredMaxOutputTokens: Int?,
        configuredMaxSourceChars: Int,
        preferredChunkChars: Int,
        adjacentContextChars: Int,
        fixedPromptChars: Int,
        sourceChars: Int = configuredMaxSourceChars,
    ): LocalAiTranslationBudget {
        val context = contextWindow.takeIf { it > 0 }?.coerceAtLeast(1_024) ?: 4_096
        val safety = (context * 0.10).toInt().coerceAtLeast(256)
        val promptTokens = ceil(fixedPromptChars.coerceAtLeast(0) / PROMPT_CHARS_PER_TOKEN).toInt()
        val contextTokens = ceil(
            adjacentContextChars.coerceAtLeast(0) * 2 * SOURCE_TOKENS_PER_CHAR
        ).toInt()
        val usableForSourceAndOutput = (
            context - safety - promptTokens - contextTokens
        ).coerceAtLeast(256)
        val safeChars = (
            usableForSourceAndOutput / (SOURCE_TOKENS_PER_CHAR + OUTPUT_TOKENS_PER_CHAR)
        ).toInt().coerceAtLeast(MIN_CHUNK_CHARS)
        val chunkChars = minOf(
            configuredMaxSourceChars.coerceAtLeast(MIN_CHUNK_CHARS),
            preferredChunkChars.coerceAtLeast(MIN_CHUNK_CHARS),
            safeChars,
        )
        val requestedOutput = ceil(
            sourceChars.coerceIn(0, chunkChars) * OUTPUT_TOKENS_PER_CHAR
        ).toInt().plus(OUTPUT_FIXED_RESERVE).coerceAtLeast(256)
        val hardOutputLimit = listOfNotNull(
            providerMaxOutputTokens.takeIf { it > 0 },
            configuredMaxOutputTokens?.takeIf { it > 0 },
            (context - safety - promptTokens).takeIf { it > 0 },
        ).minOrNull() ?: (context - safety - promptTokens).coerceAtLeast(256)

        return LocalAiTranslationBudget(
            maxSourceChars = chunkChars,
            maxOutputTokens = requestedOutput.coerceAtMost(hardOutputLimit).coerceAtLeast(1),
            adjacentContextChars = adjacentContextChars.coerceAtMost(chunkChars / 2),
            safetyTokens = safety,
        )
    }
}
