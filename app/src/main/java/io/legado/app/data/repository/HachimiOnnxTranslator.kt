package io.legado.app.data.repository

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.app.ActivityManager
import android.content.Context
import io.legado.app.model.translation.HachimiOnnxModelRegistry
import io.legado.app.model.translation.HachimiOnnxRuntimeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.Normalizer

data class HachimiLexicalConstraint(
    /** One or more source forms which activate this target only for the current segment. */
    val sourceKeys: List<String>,
    val target: String,
    /** Project Name/VietPhrase locks are required; learned pronoun/register hints stay soft. */
    val required: Boolean = true,
    val startBias: Float = if (required) 1.25f else 0.55f,
    /** Names must keep their canonical spelling at every explicit source mention. */
    val matchEverySourceOccurrence: Boolean = false,
    /** Replace source Name glyphs with the project canonical form before model tokenization. */
    val canonicalizeSourceName: Boolean = false
) {
    fun appliesTo(source: String): Boolean =
        sourceKeys.any { key -> key.isNotBlank() && source.contains(key, ignoreCase = true) }

    fun requiredOccurrencesIn(source: String): Int = when {
        !required -> 0
        !matchEverySourceOccurrence -> 1
        else -> sourceKeys.sumOf { key -> source.countNonOverlapping(key) }.coerceAtLeast(1)
    }
}

data class HachimiDecodePolicy(
    /** Source-conditioned canonical terms. Constraints from unrelated segments are never applied. */
    val lexicalConstraints: List<HachimiLexicalConstraint> = emptyList(),
    val maxNewTokens: Int = 240,
    val repetitionPenalty: Float = 1.2f,
    val noRepeatNgramSize: Int = 2,
    /** Retry only segments which lost a required project term, using stricter lexical decoding. */
    val retryMissingRequiredTerms: Boolean = true,
    /**
     * Small source windows reduce time-to-first-output and keep a glossary retry local. A 480-token
     * window can require two full 240-token autoregressive passes on Android before reporting any
     * progress, and can truncate Vietnamese because it expands relative to Chinese.
     */
    val maxSourceTokens: Int = DEFAULT_SOURCE_TOKEN_BUDGET,
    /** Hard character ceiling for each source segment before tokenization. */
    val maxSourceChars: Int = DEFAULT_SOURCE_CHAR_BUDGET,
    /** Source-side model prefix. This is intentionally independent from the AI system prompt. */
    val sourcePrompt: String = "",
) {
    companion object {
        const val DEFAULT_SOURCE_TOKEN_BUDGET = 96
        const val DEFAULT_SOURCE_CHAR_BUDGET = 1000
    }
}

data class HachimiTranslationResult(
    val text: String,
    val sourceSegments: Int,
    val generatedTokens: Int,
    val missingRequiredTerms: List<String> = emptyList(),
    val attribution: String = HachimiOnnxTranslator.ATTRIBUTION
)

/**
 * Marian encoder-decoder runtime for the imported HachimiMT ONNX INT8 graphs.
 *
 * Sessions are lazy and CPU-only. Models are memory-mapped from app-private storage after the user
 * downloads and imports the package from the external catalog.
 */
class HachimiOnnxTranslator(
    private val context: Context
) {
    @Volatile private var runtime: Runtime? = null
    @Volatile private var runtimeGeneration: Long = Long.MIN_VALUE
    private var idleUnloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = scope.launch {
            delay(5 * 60 * 1000L) // 5 minutes
            close()
        }
    }

    suspend fun translate(
        text: String,
        policy: HachimiDecodePolicy = HachimiDecodePolicy(),
        onProgress: suspend (
            completedSegments: Int,
            totalSegments: Int,
            mixedText: String,
        ) -> Unit = { _, _, _ -> }
    ): HachimiTranslationResult = withContext(Dispatchers.Default) {
        HachimiOnnxRuntimeCoordinator.accessMutex.withLock {
            idleUnloadJob?.cancel()
            if (text.isBlank()) return@withLock HachimiTranslationResult(text, 0, 0)
            val safePolicy = policy.copy(
                // A single Android process cannot safely sustain the desktop-sized decode limits
                // accepted by old settings. Keep a bounded native tensor footprint per segment.
                maxNewTokens = policy.maxNewTokens.coerceIn(1, MAX_SAFE_NEW_TOKENS),
                maxSourceTokens = policy.maxSourceTokens.coerceIn(MIN_SOURCE_TOKENS, MAX_SAFE_SOURCE_TOKENS),
            )
            val modelGeneration = HachimiOnnxRuntimeCoordinator.currentGeneration()
            val loaded = try {
                runtime?.takeIf { runtimeGeneration == modelGeneration } ?: run {
                    runtime?.close()
                    Runtime.load(context).also {
                        runtime = it
                        runtimeGeneration = modelGeneration
                    }
                }
            } catch (error: OutOfMemoryError) {
                throw releaseRuntimeAfterMemoryFailure(error)
            }
            val sourcePrompt = safePolicy.sourcePrompt.trim()
            val promptTokens = sourcePrompt.takeIf(String::isNotEmpty)
                ?.let(loaded::sourceTokenCount)
                ?: 0
            require(promptTokens <= safePolicy.maxSourceTokens - MIN_SEGMENT_SOURCE_TOKENS) {
                "NMT source prompt is too long for the selected source-token budget"
            }
            val segmentTokenBudget = (safePolicy.maxSourceTokens - promptTokens)
                .coerceAtLeast(MIN_SEGMENT_SOURCE_TOKENS)
            val segments = loaded.segment(
                text = text,
                requestedTokenBudget = segmentTokenBudget,
                requestedCharBudget = safePolicy.maxSourceChars,
            )
            val constraints = loaded.encodeConstraints(policy.lexicalConstraints)
            var generatedTokens = 0
            val translated = StringBuilder(text.length + text.length / 3)
            val missingRequiredTerms = linkedSetOf<String>()
            segments.forEachIndexed { index, segment ->
                val relevantConstraints = constraints.asSequence()
                    .filter { it.appliesTo(segment.source) }
                    .map { it.forSource(segment.source) }
                    .toList()
                val canonicalSource = canonicalizeHachimiNameSources(
                    source = segment.source,
                    constraints = policy.lexicalConstraints
                )
                val sourceForModel = if (sourcePrompt.isEmpty()) {
                    canonicalSource
                } else {
                    "$sourcePrompt $canonicalSource"
                }
                val result = try {
                    loaded.translateSegment(sourceForModel, safePolicy, relevantConstraints)
                } catch (error: OutOfMemoryError) {
                    throw releaseRuntimeAfterMemoryFailure(error)
                }
                generatedTokens += result.tokens.size
                missingRequiredTerms += result.missingRequiredTargets
                val canonicalOutput = canonicalizeHachimiNameOutputs(
                    source = segment.source,
                    translated = result.text,
                    constraints = policy.lexicalConstraints
                )
                translated.append(segment.prefixBefore)
                translated.append(restoreHachimiQuoteSkeleton(segment.source, canonicalOutput))
                translated.append(segment.separatorAfter)
                val mixedText = buildString(text.length + text.length / 3) {
                    append(translated)
                    segments.subList(index + 1, segments.size).forEach { remaining ->
                        append(remaining.prefixBefore)
                        append(remaining.source)
                        append(remaining.separatorAfter)
                    }
                }
                onProgress(index + 1, segments.size, mixedText)
            }
            val chapterOutput = canonicalizeHachimiNameOutputs(
                source = text,
                translated = translated.toString(),
                constraints = policy.lexicalConstraints
            )
            val result = HachimiTranslationResult(
                text = QuickTranslationTextPostProcessor.capitalizeSentenceStarts(
                    balanceHachimiQuotes(chapterOutput)
                ),
                sourceSegments = segments.size,
                generatedTokens = generatedTokens,
                missingRequiredTerms = missingRequiredTerms.toList()
            )
            scheduleIdleUnload()
            result
        }
    }

    fun isLoaded(): Boolean = runtime != null

    private fun releaseRuntimeAfterMemoryFailure(error: OutOfMemoryError): IOException {
        val failedRuntime = runtime
        runtime = null
        runtimeGeneration = Long.MIN_VALUE
        try {
            failedRuntime?.close()
        } catch (_: Throwable) {
            // Preserve the allocation failure; Android may still terminate the isolated process.
        }
        return IOException(
            "Not enough memory to run NMT; reduce the source-token or output-token budget",
            error,
        )
    }

    suspend fun close() = HachimiOnnxRuntimeCoordinator.accessMutex.withLock {
        idleUnloadJob?.cancel()
        idleUnloadJob = null
        runtime?.close()
        runtime = null
        runtimeGeneration = Long.MIN_VALUE
    }

    private class Runtime(
        private val environment: OrtEnvironment,
        private val sourceTokenizer: OrtSession,
        private val targetTokenizer: OrtSession,
        private val detokenizer: OrtSession,
        private val encoder: OrtSession,
        private val decoder: OrtSession,
    ) : AutoCloseable {
        data class SourceSegment(
            val source: String,
            val separatorAfter: String,
            val prefixBefore: String = "",
        )
        data class EncodedConstraint(
            val sourceKeys: List<String>,
            val target: String,
            val tokens: IntArray,
            val required: Boolean,
            val startBias: Float,
            val matchEverySourceOccurrence: Boolean,
            val requiredOccurrences: Int = 1
        ) {
            fun appliesTo(source: String): Boolean =
                sourceKeys.any { key -> source.contains(key, ignoreCase = true) }

            fun forSource(source: String): EncodedConstraint = copy(
                requiredOccurrences = when {
                    !matchEverySourceOccurrence -> 1
                    else -> sourceKeys.sumOf { key -> source.countNonOverlapping(key) }.coerceAtLeast(1)
                }
            )
        }

        data class SegmentResult(
            val text: String,
            val tokens: List<Int>,
            val missingRequiredTargets: List<String> = emptyList()
        )

        fun encodeConstraints(constraints: List<HachimiLexicalConstraint>): List<EncodedConstraint> =
            constraints.asSequence()
                .filter { it.sourceKeys.any(String::isNotBlank) && it.target.isNotBlank() }
                .distinctBy { it.target.trim().lowercase() to it.sourceKeys.map(String::trim) }
                .take(MAX_CONSTRAINTS)
                .mapNotNull { constraint ->
                    val target = constraint.target.trim()
                    runTokenizer(targetTokenizer, target).use { tokenized ->
                        val ids = tokenized.ids.longValues()
                            .map(Long::toInt)
                            .filterNot { it in SPECIAL_TOKEN_IDS }
                            .toIntArray()
                        ids.takeIf { it.isNotEmpty() && it.size <= MAX_CONSTRAINT_TOKENS }
                            ?.let { tokens ->
                                EncodedConstraint(
                                    sourceKeys = constraint.sourceKeys.map(String::trim)
                                        .filter(String::isNotBlank)
                                        .distinct(),
                                    target = target,
                                    tokens = tokens,
                                    required = constraint.required,
                                    startBias = constraint.startBias.coerceIn(0f, MAX_START_BIAS),
                                    matchEverySourceOccurrence = constraint.matchEverySourceOccurrence
                                )
                            }
                    }
                }
                .toList()

        fun segment(
            text: String,
            requestedTokenBudget: Int,
            requestedCharBudget: Int,
        ): List<SourceSegment> {
            val normalized = text.replace("\r\n", "\n")
            if (normalized.isEmpty()) return emptyList()
            val tokenBudget = requestedTokenBudget.coerceIn(
                MIN_SOURCE_TOKENS,
                MAX_SOURCE_TOKENS
            )
            val charBudget = requestedCharBudget.coerceIn(10, 10_000)
            val output = mutableListOf<SourceSegment>()
            var pendingPrefix = ""
            var cursor = 0
            PARAGRAPH_BREAK.findAll(normalized).forEach { boundary ->
                pendingPrefix = appendParagraphSegments(
                    normalized.substring(cursor, boundary.range.first),
                    boundary.value,
                    output,
                    tokenBudget,
                    charBudget,
                    pendingPrefix,
                )
                cursor = boundary.range.last + 1
            }
            pendingPrefix = appendParagraphSegments(
                normalized.substring(cursor),
                "",
                output,
                tokenBudget,
                charBudget,
                pendingPrefix,
            )
            if (pendingPrefix.isNotEmpty() && output.isNotEmpty()) {
                val last = output.removeAt(output.lastIndex)
                output += last.copy(separatorAfter = last.separatorAfter + pendingPrefix)
            }
            return output.ifEmpty { listOf(SourceSegment(normalized, "")) }
        }

        fun translateSegment(
            source: String,
            policy: HachimiDecodePolicy,
            constraints: List<EncodedConstraint>
        ): SegmentResult {
            runTokenizer(sourceTokenizer, source).use { tokenized ->
                encoder.run(
                    mapOf(
                        "input_ids" to tokenized.ids,
                        "attention_mask" to tokenized.attentionMask
                    )
                ).use { encoderResult ->
                    val hidden = encoderResult.tensor("last_hidden_state", fallbackIndex = 0)
                    val first = decode(
                        attentionMask = tokenized.attentionMask,
                        encoderHidden = hidden,
                        policy = policy,
                        constraints = constraints,
                        strictConstraints = false
                    )
                    return if (
                        policy.retryMissingRequiredTerms &&
                        first.missingRequiredTargets.isNotEmpty()
                    ) {
                        decode(
                            attentionMask = tokenized.attentionMask,
                            encoderHidden = hidden,
                            policy = policy,
                            constraints = constraints,
                            strictConstraints = true
                        )
                    } else {
                        first
                    }
                }
            }
        }

        private fun decode(
            attentionMask: OnnxTensor,
            encoderHidden: OnnxTensor,
            policy: HachimiDecodePolicy,
            constraints: List<EncodedConstraint>,
            strictConstraints: Boolean
        ): SegmentResult {
            val generated = mutableListOf(DECODER_START_TOKEN_ID)
            var currentResult: OrtSession.Result? = null
            var initialResult: OrtSession.Result? = null
            var crossCache: Map<String, OnnxTensor> = emptyMap()
            var selfCache: Map<String, OnnxTensor> = emptyMap()
            var tokenSelector: HachimiGreedySelector? = null
            val biasedTokens = IntArray(MAX_CONSTRAINTS)
            val biasedScores = FloatArray(MAX_CONSTRAINTS)
            try {
                for (step in 0 until policy.maxNewTokens.coerceIn(1, MAX_NEW_TOKENS)) {
                    OnnxTensor.createTensor(
                        environment,
                        arrayOf(longArrayOf(generated.last().toLong()))
                    ).use { currentToken ->
                        OnnxTensor.createTensor(
                            environment,
                            booleanArrayOf(step > 0)
                        ).use { cacheBranch ->
                            val ownedEmptyCache = if (step == 0) createEmptyCache() else emptyMap()
                            try {
                                val feeds = linkedMapOf<String, OnnxTensorLike>(
                                    "encoder_attention_mask" to attentionMask,
                                    "input_ids" to currentToken,
                                    "encoder_hidden_states" to encoderHidden,
                                    "use_cache_branch" to cacheBranch
                                )
                                if (step == 0) {
                                    feeds.putAll(ownedEmptyCache)
                                } else {
                                    feeds.putAll(selfCache)
                                    feeds.putAll(crossCache)
                                }
                                val nextResult = decoder.run(feeds)
                                val logits = nextResult.tensor("logits").floatBuffer
                                val selector = tokenSelector ?: HachimiGreedySelector(
                                    vocabularySize = logits.remaining(),
                                    initialTokens = generated
                                ).also { tokenSelector = it }
                                val nextToken = chooseToken(
                                    logits = logits,
                                    generated = generated,
                                    constraints = constraints,
                                    policy = policy,
                                    strictConstraints = strictConstraints,
                                    selector = selector,
                                    biasedTokens = biasedTokens,
                                    biasedScores = biasedScores
                                )
                                if (step == 0) {
                                    initialResult = nextResult
                                    crossCache = nextResult.cacheValues(".encoder.")
                                } else {
                                    currentResult?.close()
                                    currentResult = nextResult
                                }
                                selfCache = nextResult.cacheValues(".decoder.")
                                selector.record(nextToken)
                                generated += nextToken
                            } finally {
                                ownedEmptyCache.values.forEach(OnnxTensor::close)
                            }
                        }
                    }
                    if (generated.last() == EOS_TOKEN_ID) break
                }
                val visible = generated.drop(1).filterNot(SPECIAL_TOKEN_IDS::contains)
                val missing = constraints.asSequence()
                    .filter(EncodedConstraint::required)
                    .filterNot { isSatisfied(generated, it.tokens, it.requiredOccurrences) }
                    .map(EncodedConstraint::target)
                    .toList()
                return SegmentResult(detokenize(visible), visible, missing)
            } finally {
                currentResult?.close()
                initialResult?.close()
            }
        }

        private fun chooseToken(
            logits: FloatBuffer,
            generated: List<Int>,
            constraints: List<EncodedConstraint>,
            policy: HachimiDecodePolicy,
            strictConstraints: Boolean,
            selector: HachimiGreedySelector,
            biasedTokens: IntArray,
            biasedScores: FloatArray
        ): Int {
            var forcedToken = -1
            var forcedRequired = false
            var forcedStartBias = Float.NEGATIVE_INFINITY
            var biasedCount = 0
            var hasMissingRequired = false
            constraints.forEach { constraint ->
                if (isSatisfied(generated, constraint.tokens, constraint.requiredOccurrences)) return@forEach
                val matchedPrefix = matchingSuffixLength(generated, constraint.tokens)
                if (strictConstraints && constraint.required) hasMissingRequired = true
                if (matchedPrefix in 1 until constraint.tokens.size) {
                    val shouldReplace = forcedToken < 0 ||
                        constraint.required && !forcedRequired ||
                        constraint.required == forcedRequired &&
                        constraint.startBias > forcedStartBias
                    if (shouldReplace) {
                        forcedToken = constraint.tokens[matchedPrefix]
                        forcedRequired = constraint.required
                        forcedStartBias = constraint.startBias
                    }
                    return@forEach
                }
                if (matchedPrefix != 0) return@forEach

                val token = constraint.tokens[0]
                val score = constraint.startBias * if (
                    strictConstraints && constraint.required
                ) STRICT_START_BIAS_MULTIPLIER else 1f
                var existingIndex = -1
                for (index in 0 until biasedCount) {
                    if (biasedTokens[index] == token) {
                        existingIndex = index
                        break
                    }
                }
                if (existingIndex >= 0) {
                    biasedScores[existingIndex] = maxOf(biasedScores[existingIndex], score)
                } else if (biasedCount < biasedTokens.size) {
                    biasedTokens[biasedCount] = token
                    biasedScores[biasedCount] = score
                    biasedCount++
                }
            }
            if (forcedToken >= 0) return forcedToken

            return selector.select(
                logits = logits,
                generated = generated,
                repetitionPenalty = policy.repetitionPenalty,
                noRepeatNgramSize = policy.noRepeatNgramSize,
                eosToken = EOS_TOKEN_ID,
                eosPenalty = if (hasMissingRequired) STRICT_EOS_PENALTY else 0f,
                biasedTokens = biasedTokens,
                biasedScores = biasedScores,
                biasedCount = biasedCount
            )
        }

        private fun appendParagraphSegments(
            paragraph: String,
            paragraphSeparator: String,
            output: MutableList<SourceSegment>,
            tokenBudget: Int,
            charBudget: Int,
            pendingPrefix: String,
        ): String {
            val firstContent = paragraph.indexOfFirst { !it.isWhitespace() }
            val lastContent = paragraph.indexOfLast { !it.isWhitespace() }
            val clean = if (firstContent < 0) {
                ""
            } else {
                paragraph.substring(firstContent, lastContent + 1)
            }
            if (clean.isEmpty()) {
                return pendingPrefix + paragraph + paragraphSeparator
            }
            val prefix = pendingPrefix + paragraph.substring(0, firstContent)
            val suffix = paragraph.substring(lastContent + 1) + paragraphSeparator
            val units = SENTENCE.findAll(clean)
                .map(MatchResult::value)
                .map(String::trim)
                .filter(String::isNotBlank)
                .toList()
                .ifEmpty { listOf(clean) }
            val paragraphSegments = mutableListOf<String>()
            val current = StringBuilder()
            units.forEach { unit ->
                val candidate = if (current.isEmpty()) unit else "$current $unit"
                if (candidate.length <= charBudget && tokenCount(candidate) <= tokenBudget) {
                    current.clear()
                    current.append(candidate)
                } else {
                    if (current.isNotEmpty()) {
                        paragraphSegments += current.toString()
                        current.clear()
                    }
                    splitOversizedUnit(unit, tokenBudget, charBudget)
                        .forEach(paragraphSegments::add)
                }
            }
            if (current.isNotEmpty()) paragraphSegments += current.toString()
            paragraphSegments.forEachIndexed { index, source ->
                output += SourceSegment(
                    source = source,
                    separatorAfter = when {
                        index < paragraphSegments.lastIndex -> " "
                        else -> suffix
                    },
                    prefixBefore = if (index == 0) prefix else "",
                )
            }
            return ""
        }

        private fun splitOversizedUnit(
            unit: String,
            tokenBudget: Int,
            charBudget: Int,
        ): List<String> {
            val output = mutableListOf<String>()
            var remaining = unit.trim()
            while (remaining.isNotEmpty()) {
                if (remaining.length <= charBudget && tokenCount(remaining) <= tokenBudget) {
                    output += remaining
                    break
                }
                var low = 1
                var high = remaining.length.coerceAtMost(charBudget)
                var best = 1
                while (low <= high) {
                    val middle = (low + high) ushr 1
                    if (tokenCount(remaining.take(middle)) <= tokenBudget) {
                        best = middle
                        low = middle + 1
                    } else {
                        high = middle - 1
                    }
                }
                val preferredBreak = remaining
                    .take(best)
                    .indexOfLast { it in SOFT_BREAK_CHARACTERS }
                    .takeIf { it >= best / 2 }
                    ?.plus(1)
                    ?: best
                output += remaining.take(preferredBreak).trim()
                remaining = remaining.drop(preferredBreak).trim()
            }
            return output.filter(String::isNotBlank)
        }

        private fun tokenCount(text: String): Int =
            runTokenizer(sourceTokenizer, text).use { tokenized ->
                tokenized.ids.longValues().size
            }

        fun sourceTokenCount(text: String): Int = tokenCount(text)

        private fun detokenize(tokens: List<Int>): String {
            if (tokens.isEmpty()) return ""
            OnnxTensor.createTensor(
                environment,
                tokens.map(Int::toLong).toLongArray()
            ).use { ids ->
                detokenizer.run(mapOf("ids" to ids)).use { result ->
                    val value = result.tensor("text", fallbackIndex = 0).value
                    return when (value) {
                        is Array<*> -> value.firstOrNull()?.toString().orEmpty()
                        else -> value.toString()
                    }.trim()
                }
            }
        }

        private fun runTokenizer(session: OrtSession, text: String): Tokenized {
            val textTensor = OnnxTensor.createTensor(environment, arrayOf(text))
            val result = session.run(mapOf("text" to textTensor))
            return Tokenized(
                textTensor = textTensor,
                result = result,
                ids = result.tensor("input_ids", fallbackIndex = 0),
                attentionMask = result.tensor("attention_mask", fallbackIndex = 1)
            )
        }

        private fun createEmptyCache(): Map<String, OnnxTensor> =
            (0 until DECODER_LAYERS).flatMap { layer ->
                listOf("decoder", "encoder").flatMap { attention ->
                    listOf("key", "value").map { kind ->
                        val name = "past_key_values.$layer.$attention.$kind"
                        val buffer = ByteBuffer.allocateDirect(0)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer()
                        name to OnnxTensor.createTensor(
                            environment,
                            buffer,
                            longArrayOf(1, ATTENTION_HEADS, 0, HEAD_DIMENSION)
                        )
                    }
                }
            }.toMap()

        override fun close() {
            decoder.close()
            encoder.close()
            detokenizer.close()
            targetTokenizer.close()
            sourceTokenizer.close()
        }

        companion object {
            fun load(context: Context): Runtime {
                val environment = OrtEnvironment.getEnvironment()
                val modelDirectory = HachimiOnnxModelRegistry(context).installedDirectory()
                val modelPaths = MODEL_FILES.associateWith { fileName ->
                    modelFilePath(File(modelDirectory, fileName))
                }
                val installedModelBytes = modelDirectory.walkTopDown()
                    .filter(File::isFile)
                    .sumOf(File::length)
                ensureMemoryAvailable(context, installedModelBytes)
                val lowRamDevice = isLowRamDevice(context)
                val extensionOptions = OrtSession.SessionOptions().apply {
                    registerCustomOpLibrary(OrtxPackage.getLibraryPath())
                    applyMemoryProfile(lowRamDevice)
                    setOptimizationLevel(
                        if (lowRamDevice) {
                            OrtSession.SessionOptions.OptLevel.BASIC_OPT
                        } else {
                            OrtSession.SessionOptions.OptLevel.ALL_OPT
                        }
                    )
                }
                val modelOptions = OrtSession.SessionOptions().apply {
                    applyMemoryProfile(lowRamDevice)
                    setOptimizationLevel(
                        if (lowRamDevice) {
                            OrtSession.SessionOptions.OptLevel.BASIC_OPT
                        } else {
                            OrtSession.SessionOptions.OptLevel.ALL_OPT
                        }
                    )
                    setIntraOpNumThreads(
                        inferenceThreadCount(context)
                    )
                }
                val openedSessions = ArrayList<OrtSession>(MODEL_FILES.size)
                try {
                    val sourceTokenizer = environment.createSession(
                        modelPaths.getValue(TOKENIZER_FILE),
                        extensionOptions
                    ).also(openedSessions::add)
                    val targetTokenizer = environment.createSession(
                        modelPaths.getValue(TARGET_TOKENIZER_FILE),
                        extensionOptions
                    ).also(openedSessions::add)
                    val detokenizer = environment.createSession(
                        modelPaths.getValue(DETOKENIZER_FILE),
                        extensionOptions
                    ).also(openedSessions::add)
                    val encoder = environment.createSession(
                        modelPaths.getValue(ENCODER_FILE),
                        modelOptions
                    ).also(openedSessions::add)
                    val decoder = environment.createSession(
                        modelPaths.getValue(DECODER_FILE),
                        modelOptions
                    ).also(openedSessions::add)
                    return Runtime(
                        environment = environment,
                        sourceTokenizer = sourceTokenizer,
                        targetTokenizer = targetTokenizer,
                        detokenizer = detokenizer,
                        encoder = encoder,
                        decoder = decoder,
                    )
                } catch (error: Throwable) {
                    openedSessions.asReversed().forEach { session ->
                        runCatching { session.close() }
                    }
                    throw error
                } finally {
                    extensionOptions.close()
                    modelOptions.close()
                }
            }

            private fun modelFilePath(file: File): String {
                if (!file.isFile) throw IOException("Missing NMT model file: ${file.name}")
                return file.absolutePath
            }

            private fun inferenceThreadCount(context: Context): Int {
                val lowRamDevice = isLowRamDevice(context)
                return if (lowRamDevice) {
                    LOW_RAM_INTRA_OP_THREADS
                } else {
                    java.lang.Runtime.getRuntime().availableProcessors()
                        .coerceIn(1, MAX_INTRA_OP_THREADS)
                }
            }

            private fun isLowRamDevice(context: Context): Boolean {
                val activityManager = context.getSystemService(ActivityManager::class.java)
                return shouldUseLowMemoryNmtProfile(
                    systemReportsLowRam = activityManager?.isLowRamDevice == true,
                    memoryClassMb = activityManager?.memoryClass ?: Int.MAX_VALUE,
                )
            }

            private fun OrtSession.SessionOptions.applyMemoryProfile(lowRamDevice: Boolean) {
                if (!lowRamDevice) return
                // Variable tokenizer/decoder shapes do not benefit enough from retaining native
                // arenas on constrained Android devices. Releasing them after each run avoids a
                // high resident-memory watermark while the model sessions remain loaded.
                setMemoryPatternOptimization(false)
                setCPUArenaAllocator(false)
                setInterOpNumThreads(1)
            }

            private fun ensureMemoryAvailable(context: Context, modelBytes: Long) {
                val activityManager = context.getSystemService(ActivityManager::class.java)
                    ?: return
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val requiredBytes = requiredNmtSystemHeadroomBytes(
                    modelBytes = modelBytes,
                    systemLowMemoryThresholdBytes = memoryInfo.threshold,
                )
                if (!hasEnoughNmtSystemMemory(
                        availableBytes = memoryInfo.availMem,
                        systemReportsLowMemory = memoryInfo.lowMemory,
                        requiredHeadroomBytes = requiredBytes,
                    )
                ) {
                    throw IOException(
                        "Not enough memory to load NMT model " +
                            "(available=${memoryInfo.availMem}, required=$requiredBytes)"
                    )
                }
            }
        }
    }

    private class Tokenized(
        private val textTensor: OnnxTensor,
        private val result: OrtSession.Result,
        val ids: OnnxTensor,
        val attentionMask: OnnxTensor
    ) : AutoCloseable {
        override fun close() {
            result.close()
            textTensor.close()
        }
    }

    companion object {
        const val ATTRIBUTION = "HachimiMT-60 by ngocdang83 · CC-BY-4.0"
        private const val MIN_SEGMENT_SOURCE_TOKENS = 16
        private const val ENCODER_FILE = HachimiOnnxModelRegistry.ENCODER_FILE
        private const val DECODER_FILE = HachimiOnnxModelRegistry.DECODER_FILE
        private const val TOKENIZER_FILE = HachimiOnnxModelRegistry.TOKENIZER_FILE
        private const val TARGET_TOKENIZER_FILE = HachimiOnnxModelRegistry.TARGET_TOKENIZER_FILE
        private const val DETOKENIZER_FILE = HachimiOnnxModelRegistry.DETOKENIZER_FILE
        private val MODEL_FILES = listOf(
            ENCODER_FILE,
            DECODER_FILE,
            TOKENIZER_FILE,
            TARGET_TOKENIZER_FILE,
            DETOKENIZER_FILE
        )
        private const val DECODER_START_TOKEN_ID = 1
        private const val EOS_TOKEN_ID = 2
        private val SPECIAL_TOKEN_IDS = setOf(0, 1, 2, 3)
        private const val DECODER_LAYERS = 2
        private const val ATTENTION_HEADS = 8L
        private const val HEAD_DIMENSION = 64L
        private const val MAX_NEW_TOKENS = 384
        private const val MAX_SAFE_NEW_TOKENS = 256
        private const val MAX_CONSTRAINTS = 64
        private const val MAX_CONSTRAINT_TOKENS = 16
        private const val MAX_INTRA_OP_THREADS = 4
        private const val LOW_RAM_INTRA_OP_THREADS = 1
        private const val MAX_START_BIAS = 6f
        private const val STRICT_START_BIAS_MULTIPLIER = 4f
        private const val STRICT_EOS_PENALTY = 12f
        private const val MIN_SOURCE_TOKENS = 32
        private const val MAX_SOURCE_TOKENS = 480
        private const val MAX_SAFE_SOURCE_TOKENS = 320
        private val SENTENCE = Regex("[^。！？!?…\\n]+(?:[。！？!?…]+[”’」』\\\"]*|$)")
        // Preserve every source line boundary. Treating only two newlines as a paragraph made
        // NMT's post-processing join EPUB lines with spaces and changed the reader layout.
        private val PARAGRAPH_BREAK = Regex("\\r?\\n")
        private val SOFT_BREAK_CHARACTERS = setOf('，', ',', '；', ';', '：', ':', '、', ' ')
    }
}

private const val MIN_NMT_RUNTIME_HEADROOM_BYTES = 64L * 1024L * 1024L

internal fun shouldUseLowMemoryNmtProfile(
    systemReportsLowRam: Boolean,
    memoryClassMb: Int,
): Boolean = systemReportsLowRam || memoryClassMb <= 256

internal fun requiredNmtSystemHeadroomBytes(
    modelBytes: Long,
    systemLowMemoryThresholdBytes: Long,
): Long = maxOf(
    modelBytes.coerceAtLeast(0L) + MIN_NMT_RUNTIME_HEADROOM_BYTES,
    systemLowMemoryThresholdBytes.coerceAtLeast(0L) * 2L,
)

internal fun hasEnoughNmtSystemMemory(
    availableBytes: Long,
    systemReportsLowMemory: Boolean,
    requiredHeadroomBytes: Long,
): Boolean = !systemReportsLowMemory && availableBytes >= requiredHeadroomBytes

/**
 * Allocation-free greedy selection for the autoregressive decoder hot path.
 *
 * ONNX exposes the logits through a direct [FloatBuffer]. The selector scans that buffer once,
 * retains repetition state between steps, and evaluates the small set of glossary-biased tokens
 * separately. This avoids copying the whole vocabulary and checking every lexical constraint for
 * every vocabulary item.
 */
internal class HachimiGreedySelector(
    private val vocabularySize: Int,
    initialTokens: List<Int>
) {
    private val seen = BooleanArray(vocabularySize)
    private val blockedAtStep = IntArray(vocabularySize)
    private var blockStep = 0

    init {
        initialTokens.forEach(::record)
    }

    fun record(token: Int) {
        if (token in seen.indices) seen[token] = true
    }

    fun select(
        logits: FloatBuffer,
        generated: List<Int>,
        repetitionPenalty: Float,
        noRepeatNgramSize: Int,
        eosToken: Int,
        eosPenalty: Float,
        biasedTokens: IntArray,
        biasedScores: FloatArray,
        biasedCount: Int
    ): Int {
        require(logits.remaining() == vocabularySize) {
            "Logit vocabulary changed from $vocabularySize to ${logits.remaining()}"
        }
        prepareBlockedBigrams(generated, noRepeatNgramSize)
        val offset = logits.position()
        var winner = eosToken
        var winnerScore = Float.NEGATIVE_INFINITY
        for (token in 0 until vocabularySize) {
            val score = adjustedScore(
                token = token,
                rawScore = logits.get(offset + token),
                repetitionPenalty = repetitionPenalty,
                eosToken = eosToken,
                eosPenalty = eosPenalty,
                extraBias = 0f
            )
            if (score > winnerScore) {
                winnerScore = score
                winner = token
            }
        }
        for (index in 0 until biasedCount.coerceAtMost(biasedTokens.size)) {
            val token = biasedTokens[index]
            if (token !in 0 until vocabularySize) continue
            val score = adjustedScore(
                token = token,
                rawScore = logits.get(offset + token),
                repetitionPenalty = repetitionPenalty,
                eosToken = eosToken,
                eosPenalty = eosPenalty,
                extraBias = biasedScores[index]
            )
            if (score > winnerScore) {
                winnerScore = score
                winner = token
            }
        }
        return winner
    }

    private fun adjustedScore(
        token: Int,
        rawScore: Float,
        repetitionPenalty: Float,
        eosToken: Int,
        eosPenalty: Float,
        extraBias: Float
    ): Float {
        if (blockedAtStep[token] == blockStep) return Float.NEGATIVE_INFINITY
        var score = rawScore
        if (seen[token]) {
            score = if (score < 0f) {
                score * repetitionPenalty
            } else {
                score / repetitionPenalty
            }
        }
        if (token == eosToken) score -= eosPenalty
        return score + extraBias
    }

    private fun prepareBlockedBigrams(generated: List<Int>, noRepeatNgramSize: Int) {
        blockStep++
        if (noRepeatNgramSize != 2 || generated.isEmpty()) return
        val prefix = generated.last()
        for (index in 0 until generated.lastIndex) {
            if (generated[index] != prefix) continue
            val blocked = generated[index + 1]
            if (blocked in blockedAtStep.indices) blockedAtStep[blocked] = blockStep
        }
    }
}

private fun OrtSession.Result.tensor(name: String, fallbackIndex: Int = -1): OnnxTensor {
    val value = get(name).orElseGet {
        require(fallbackIndex >= 0) { "Missing ONNX output: $name" }
        get(fallbackIndex)
    }
    return value as? OnnxTensor ?: error("ONNX output $name is not a tensor")
}

private fun OrtSession.Result.cacheValues(part: String): Map<String, OnnxTensor> =
    asSequence()
        .filter { (name, _) -> name.startsWith("present.") && part in name }
        .associate { (name, value) ->
            name.replace("present.", "past_key_values.") to
                (value as? OnnxTensor ?: error("Cache output $name is not a tensor"))
        }

private fun OnnxTensor.longValues(): LongArray {
    val buffer = longBuffer
    val output = LongArray(buffer.remaining())
    buffer.get(output)
    return output
}

/**
 * Source-side canonicalization prevents the model from inventing another transliteration before
 * the decoder constraint can start. Only required Name constraints opt into this behavior; normal
 * VietPhrase locks and learned soft hints keep their original Chinese context.
 */
internal fun canonicalizeHachimiNameSources(
    source: String,
    constraints: List<HachimiLexicalConstraint>
): String = constraints.asSequence()
    .filter { it.required && it.canonicalizeSourceName && it.appliesTo(source) }
    .sortedByDescending { constraint -> constraint.sourceKeys.maxOfOrNull(String::length) ?: 0 }
    .fold(source) { current, constraint ->
        constraint.sourceKeys.asSequence()
            .filter(String::isNotBlank)
            .sortedByDescending(String::length)
            .fold(current) { value, key ->
                value.replace(key, " ${constraint.target.trim()} ", ignoreCase = true)
            }
    }
    .replace(Regex("[ \\t]+"), " ")
    .trim()

/**
 * Hachimi can mix curly, CJK and straight quotes or omit one side. Reusing the source quote
 * skeleton keeps dialogue boundaries deterministic while leaving the translated prose untouched.
 */
internal fun restoreHachimiQuoteSkeleton(source: String, translated: String): String {
    val expected = source.filter(Char::isHachimiQuote).map(Char::normalizedHachimiQuote)
    if (expected.isEmpty()) return translated.filterNot(Char::isHachimiQuote)

    val sourceTrimmed = source.trim()
    var value = translated.trim()
    if (sourceTrimmed.firstOrNull()?.isHachimiQuote() == true && value.firstOrNull()?.isHachimiQuote() != true) {
        value = expected.first() + value
    }
    if (sourceTrimmed.lastOrNull()?.isHachimiQuote() == true && value.lastOrNull()?.isHachimiQuote() != true) {
        value += expected.last()
    }
    var quoteIndex = 0
    return buildString(value.length) {
        value.forEach { char ->
            if (char.isHachimiQuote()) {
                append(expected.getOrNull(quoteIndex) ?: char.normalizedHachimiQuote())
                quoteIndex++
            } else {
                append(char)
            }
        }
    }
}

/**
 * The canonical Latin name is already injected into the model source, so remaining spelling drift
 * is bounded to copied forms such as `Nor Wen` or `Nowan`. Normalize those local variants and
 * collapse only an immediately duplicated canonical name produced by constraint completion.
 */
internal fun canonicalizeHachimiNameOutputs(
    source: String,
    translated: String,
    constraints: List<HachimiLexicalConstraint>
): String = constraints.asSequence()
    .filter { it.required && it.canonicalizeSourceName && it.appliesTo(source) }
    .fold(translated) { current, constraint ->
        val canonical = constraint.target.trim()
        val letters = canonical.filter(Char::isLetterOrDigit)
        if (letters.isEmpty()) return@fold current
        val flexiblePattern = letters.map { Regex.escape(it.toString()) }
            .joinToString("[\\s\\-·]*")
        var value = Regex(
            "(?<!\\p{L})$flexiblePattern(?!\\p{L})",
            RegexOption.IGNORE_CASE
        ).replace(current, canonical)
        val normalizedCanonical = canonical.normalizedNameKey()
        if (normalizedCanonical.length >= MIN_FUZZY_NAME_LENGTH) {
            value = NAME_CANDIDATE.replace(value) { match ->
                val candidate = match.value
                val normalizedCandidate = candidate.normalizedNameKey()
                val splitCandidateWithOneEdit = candidate.any { it == ' ' || it == '-' || it == '·' } &&
                    levenshteinDistance(normalizedCandidate, normalizedCanonical) <= 1
                if (
                    (normalizedCandidate.firstOrNull() == normalizedCanonical.firstOrNull() ||
                        splitCandidateWithOneEdit) &&
                    kotlin.math.abs(normalizedCandidate.length - normalizedCanonical.length) <= 2 &&
                    isLikelyNameVariant(normalizedCandidate, normalizedCanonical)
                ) canonical else candidate
            }
        }
        val duplicate = Regex(
            "(?<!\\p{L})${Regex.escape(canonical)}\\s+${Regex.escape(canonical)}(?!\\p{L})",
            RegexOption.IGNORE_CASE
        )
        while (true) {
            val repeated = duplicate.find(value) ?: break
            value = value.replaceRange(repeated.range, canonical)
        }
        value
    }

/** Drop only unmatched quote glyphs; never invent a dialogue boundary at the chapter end. */
internal fun balanceHachimiQuotes(value: String): String {
    val output = StringBuilder(value.length)
    val unmatchedOpenings = ArrayDeque<Int>()
    value.forEach { char ->
        when (char) {
            '“' -> {
                unmatchedOpenings.addLast(output.length)
                output.append(char)
            }
            '”' -> if (unmatchedOpenings.isNotEmpty()) {
                unmatchedOpenings.removeLast()
                output.append(char)
            }
            else -> output.append(char)
        }
    }
    unmatchedOpenings.toList().asReversed().forEach(output::deleteCharAt)
    return output.toString()
}

private fun Char.isHachimiQuote(): Boolean = this in HACHIMI_QUOTES

private fun Char.normalizedHachimiQuote(): Char = when (this) {
    '“', '‘', '「', '『' -> '“'
    '”', '’', '」', '』' -> '”'
    else -> this
}

private fun String.normalizedNameKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    .filter(Char::isLetterOrDigit)
    .lowercase()

private fun String.nameConsonants(): String = filterNot { it in NAME_VOWELS }

private fun isLikelyNameVariant(candidate: String, canonical: String): Boolean {
    val distance = levenshteinDistance(candidate, canonical)
    if (distance <= BASE_FUZZY_NAME_DISTANCE) return true
    if (distance > EXTENDED_FUZZY_NAME_DISTANCE) return false
    val canonicalConsonants = canonical.nameConsonants().toSet()
    if (canonicalConsonants.size < MIN_DISTINCT_CONSONANTS_FOR_EXTENDED_FUZZY) return false
    val sharedConsonants = candidate.nameConsonants().toSet().intersect(canonicalConsonants).size
    return sharedConsonants >= canonicalConsonants.size - 1
}

private fun levenshteinDistance(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    left.forEachIndexed { leftIndex, leftChar ->
        val current = IntArray(right.length + 1)
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + if (leftChar == rightChar) 0 else 1
            )
        }
        previous = current
    }
    return previous[right.length]
}

private fun isSatisfied(
    values: List<Int>,
    sequence: IntArray,
    requiredOccurrences: Int
): Boolean = countSequence(values, sequence) >= requiredOccurrences

private fun countSequence(values: List<Int>, sequence: IntArray): Int {
    if (sequence.isEmpty() || sequence.size > values.size) return 0
    var matches = 0
    var start = 0
    while (start <= values.size - sequence.size) {
        var equal = true
        for (offset in sequence.indices) {
            if (values[start + offset] != sequence[offset]) {
                equal = false
                break
            }
        }
        if (equal) {
            matches++
            start += sequence.size
        } else {
            start++
        }
    }
    return matches
}

private val HACHIMI_QUOTES = setOf('“', '”', '‘', '’', '「', '」', '『', '』', '"')
private val NAME_CANDIDATE = Regex("\\p{Lu}[\\p{L}]{1,}(?:[\\s·-]+\\p{Lu}[\\p{L}]{1,})?")
private const val MIN_FUZZY_NAME_LENGTH = 5
private const val BASE_FUZZY_NAME_DISTANCE = 2
private const val EXTENDED_FUZZY_NAME_DISTANCE = 3
private const val MIN_DISTINCT_CONSONANTS_FOR_EXTENDED_FUZZY = 3
private const val NAME_VOWELS = "aeiouy"

private fun String.countNonOverlapping(needle: String): Int {
    if (needle.isBlank() || length < needle.length) return 0
    var matches = 0
    var offset = 0
    while (offset <= length - needle.length) {
        val found = indexOf(needle, offset, ignoreCase = true)
        if (found < 0) break
        matches++
        offset = found + needle.length
    }
    return matches
}

private fun matchingSuffixLength(values: List<Int>, sequence: IntArray): Int {
    val maximum = minOf(values.size, sequence.size - 1)
    for (length in maximum downTo 1) {
        var equal = true
        for (offset in 0 until length) {
            if (values[values.size - length + offset] != sequence[offset]) {
                equal = false
                break
            }
        }
        if (equal) return length
    }
    return 0
}
