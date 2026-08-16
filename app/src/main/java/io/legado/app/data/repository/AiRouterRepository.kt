package io.legado.app.data.repository

import io.legado.app.data.dao.AiRouterDao
import io.legado.app.data.entities.AiCredentialEntity
import io.legado.app.data.entities.AiRouteAttemptEntity
import io.legado.app.data.entities.AiRouteProfileEntity
import io.legado.app.data.entities.AiRouteTargetEntity
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiOAuthGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiCredentialConfig
import io.legado.app.domain.model.AiCredentialDraft
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiFailureKind
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiModelRegistry
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderException
import io.legado.app.domain.model.AiProviderFailureClassifier
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiRouteAttemptConfig
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteProfileDraft
import io.legado.app.domain.model.AiRouteUnavailableException
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiRouteTargetConfig
import io.legado.app.domain.model.AiRouteTargetDraft
import io.legado.app.domain.model.AiRouterSnapshot
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTranslationTokenBudget
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.usecase.AiRouteSelector
import io.legado.app.domain.usecase.AiRouterPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Management repository and runtime routing decorator. Protocol details stay in [delegate];
 * this class only resolves targets, credentials, health and failover.
 */
class AiRouterRepository(
    private val dao: AiRouterDao,
    private val profileGateway: AiProfileGateway,
    private val secretStore: AiSecretStore,
    private val oauthGateway: AiOAuthGateway,
    private val delegate: AiTextGateway,
    private val clock: Clock,
    private val selector: AiRouteSelector = AiRouteSelector(),
) : AiRouterGateway, AiTextGateway {

    private val targetSemaphores = ConcurrentHashMap<String, TargetSemaphore>()
    private val credentialCursors = ConcurrentHashMap<String, AtomicLong>()

    override fun observeSnapshot(): Flow<AiRouterSnapshot> = combine(
        dao.observeCredentials(),
        dao.observeRoutes(),
        dao.observeTargets(),
        dao.observeRecentAttempts(),
    ) { credentials, routes, targets, attempts ->
        AiRouterSnapshot(
            credentials = credentials.map { it.toConfig(secretStore.get(it.secretRef) != null) },
            routes = routes.map(AiRouteProfileEntity::toConfig),
            targets = targets.map(AiRouteTargetEntity::toConfig),
            attempts = attempts.map(AiRouteAttemptEntity::toConfig),
        )
    }

    override suspend fun saveCredential(draft: AiCredentialDraft): AiCredentialConfig {
        require(draft.providerId.isNotBlank()) { "Provider is required" }
        require(draft.label.isNotBlank()) { "Credential name is required" }
        val existing = draft.id?.let { dao.getCredential(it) }
        val secretRef = when {
            draft.secret.isNotBlank() -> secretStore.put(draft.secret, existing?.secretRef)
            existing != null -> existing.secretRef
            else -> error("API key or access token is required")
        }
        val now = clock.millis()
        val entity = AiCredentialEntity(
            id = existing?.id
                ?: draft.id?.takeIf(String::isNotBlank)
                ?: newId("credential"),
            providerId = draft.providerId,
            label = draft.label.trim(),
            kind = draft.kind,
            secretRef = secretRef,
            enabled = draft.enabled,
            sortNumber = draft.sortNumber,
            cooldownUntil = existing?.cooldownUntil ?: 0,
            consecutiveFailures = existing?.consecutiveFailures ?: 0,
            lastFailureKind = existing?.lastFailureKind,
            lastUsedAt = existing?.lastUsedAt,
            lastSuccessAt = existing?.lastSuccessAt,
            lastFailureAt = existing?.lastFailureAt,
            oauthProvider = existing?.oauthProvider,
            refreshTokenRef = existing?.refreshTokenRef,
            idTokenRef = existing?.idTokenRef,
            accountId = existing?.accountId,
            accountLabel = existing?.accountLabel,
            expiresAt = existing?.expiresAt,
            scopes = existing?.scopes,
            status = existing?.status ?: io.legado.app.domain.model.AiCredentialStatus.ACTIVE,
            providerDataJson = existing?.providerDataJson,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsertCredential(entity)
        return entity.toConfig(hasSecret = true)
    }

    override suspend fun resolveCredentialSecret(id: String): String {
        val credential = dao.getCredential(id) ?: error("Credential không tồn tại")
        return secretStore.get(credential.secretRef)
            ?.takeIf(String::isNotBlank)
            ?: error("Không đọc được API key/token đã lưu")
    }

    override suspend fun deleteCredential(id: String) {
        val entity = dao.getCredential(id) ?: return
        dao.removeCredential(id)
        secretStore.delete(entity.secretRef)
        entity.refreshTokenRef?.let { secretStore.delete(it) }
        entity.idTokenRef?.let { secretStore.delete(it) }
    }

    override suspend fun saveRoute(draft: AiRouteProfileDraft): AiRouteProfileConfig {
        require(draft.name.isNotBlank()) { "Route name is required" }
        require(draft.taskType.isNotBlank()) { "AI task is required" }
        require(draft.strategy in AiRouteStrategy.all) { "Unsupported route strategy" }
        val existing = draft.id?.let { dao.getRoute(it) }
        val now = clock.millis()
        val entity = AiRouteProfileEntity(
            id = existing?.id ?: newId("route"),
            name = draft.name.trim(),
            taskType = draft.taskType,
            strategy = draft.strategy,
            maxAttempts = draft.maxAttempts.coerceIn(1, MAX_ROUTE_ATTEMPTS),
            stickySession = draft.stickySession,
            enabled = draft.enabled,
            isDefault = draft.makeDefault || existing?.isDefault == true,
            sortNumber = draft.sortNumber,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (entity.isDefault) dao.clearDefaultRoutes(entity.taskType)
        dao.upsertRoute(entity)
        return entity.toConfig()
    }

    override suspend fun deleteRoute(id: String) {
        dao.deleteRoute(id)
    }

    override suspend fun saveTarget(draft: AiRouteTargetDraft): AiRouteTargetConfig {
        require(dao.getRoute(draft.routeProfileId) != null) { "Route is required" }
        val model = profileGateway.getModelConfig(draft.modelProfileId)
            ?: error("Model is required")
        draft.credentialId?.let { credentialId ->
            val credential = dao.getCredential(credentialId)
                ?: error("Credential is required")
            require(credential.providerId == model.provider.id) {
                "Credential and model must belong to the same provider"
            }
        }
        val existing = draft.id?.let { dao.getTarget(it) }
        val now = clock.millis()
        val entity = AiRouteTargetEntity(
            id = existing?.id ?: newId("target"),
            routeProfileId = draft.routeProfileId,
            modelProfileId = draft.modelProfileId,
            credentialId = draft.credentialId,
            priority = draft.priority,
            weight = draft.weight.coerceIn(1, 100),
            maxConcurrency = draft.maxConcurrency.coerceIn(0, 32),
            enabled = draft.enabled,
            sortNumber = draft.sortNumber,
            cooldownUntil = existing?.cooldownUntil ?: 0,
            consecutiveFailures = existing?.consecutiveFailures ?: 0,
            lastFailureKind = existing?.lastFailureKind,
            lastUsedAt = existing?.lastUsedAt,
            lastSuccessAt = existing?.lastSuccessAt,
            lastFailureAt = existing?.lastFailureAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsertTarget(entity)
        targetSemaphores.remove(entity.id)
        return entity.toConfig()
    }

    override suspend fun deleteTarget(id: String) {
        dao.deleteTarget(id)
        targetSemaphores.remove(id)
    }

    override suspend fun resetHealth(targetId: String?, credentialId: String?) {
        val now = clock.millis()
        when {
            targetId == null && credentialId == null -> {
                dao.resetTargetHealth(null, now)
                dao.resetCredentialHealth(null, now)
            }
            targetId != null -> dao.resetTargetHealth(targetId, now)
            credentialId != null -> dao.resetCredentialHealth(credentialId, now)
        }
        credentialId?.let { id ->
            dao.getCredential(id)
                ?.takeIf { credential ->
                    credential.kind == io.legado.app.domain.model.AiCredentialKind.OAUTH_ACCESS_TOKEN &&
                        credential.status in setOf(
                            AiCredentialStatus.VERIFYING,
                            AiCredentialStatus.VERIFICATION_FAILED,
                        )
                }
                ?.let {
                    dao.updateCredentialStatus(id, AiCredentialStatus.ACTIVE, now)
                }
        }
    }

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
        var routeContext = try {
            resolveRoute(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return Result.failure(classify(error, request))
        } ?: return delegate.generate(request)
        var lastError: Throwable? = null
        for (routePass in 0..1) {
            var attemptedCandidate = false
            routeContext.candidates.forEachIndexed { index, candidate ->
                val startedAt = clock.millis()
                val routedRequest = request.withCandidate(candidate)
                val result = runCatching {
                    withTargetPermit(candidate.target) {
                        ensureCandidateCurrentlyEligible(candidate)
                        attemptedCandidate = true
                        delegate.generate(routedRequest).getOrThrow().also { response ->
                            if (response.text.isBlank()) {
                                throw IllegalStateException("Empty AI response")
                            }
                        }
                    }
                }
                if (result.exceptionOrNull() is RouteCandidateUnavailableException) {
                    return@forEachIndexed
                }
                result.onSuccess { response ->
                    recordSuccess(routeContext.profile, candidate, startedAt, null)
                    return Result.success(response)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    val failure = classify(error, routedRequest, index)
                    lastError = failure
                    recordFailure(routeContext.profile, candidate, failure, startedAt, null)
                    val canTryNext = index < routeContext.candidates.lastIndex &&
                        AiRouterPolicy.mayFallback(failure.failure.kind, outputStarted = false)
                    if (!canTryNext) return Result.failure(failure)
                    selector.forgetSticky(routeContext.profile.id, request.routeSessionKey)
                }
            }
            if (attemptedCandidate || routePass == 1) break
            selector.forgetSticky(routeContext.profile.id, request.routeSessionKey)
            routeContext = try {
                resolveRoute(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                return Result.failure(classify(error, request))
            } ?: return delegate.generate(request)
        }
        return Result.failure(
            lastError ?: classify(routeUnavailable(routeContext.profile), request)
        )
    }

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        var routeContext = try {
            resolveRoute(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw classify(error, request)
        } ?: run {
            delegate.generateStream(request).collect { emit(it) }
            return@flow
        }
        var lastError: Throwable? = null
        for (routePass in 0..1) {
            var attemptedCandidate = false
            routeContext.candidates.forEachIndexed { index, candidate ->
                val startedAt = clock.millis()
                var firstEventAt: Long? = null
                val routedRequest = request.withCandidate(candidate)
                var hasContent = false
                val bufferForFailover = request.taskType == AiTaskType.TRANSLATE_CHAPTER &&
                    index < routeContext.candidates.lastIndex
                val bufferedEvents = if (bufferForFailover) {
                    mutableListOf<AiStreamEvent>()
                } else {
                    null
                }
                try {
                    withTargetPermit(candidate.target) {
                        ensureCandidateCurrentlyEligible(candidate)
                        attemptedCandidate = true
                        delegate.generateStream(routedRequest).collect { event ->
                            if (event is AiStreamEvent.Content &&
                                event.text.isNotBlank() &&
                                firstEventAt == null
                            ) {
                                firstEventAt = clock.millis()
                                hasContent = true
                            }
                            if (bufferedEvents != null) {
                                bufferedEvents += event
                            } else {
                                emit(event)
                            }
                        }
                    }
                    if (!hasContent) {
                        throw IllegalStateException("Empty AI response")
                    }
                    recordSuccess(routeContext.profile, candidate, startedAt, firstEventAt)
                    bufferedEvents?.forEach { emit(it) }
                    return@flow
                } catch (error: CancellationException) {
                    throw error
                } catch (_: RouteCandidateUnavailableException) {
                    return@forEachIndexed
                } catch (error: Throwable) {
                    val failure = classify(error, routedRequest, index)
                    lastError = failure
                    recordFailure(routeContext.profile, candidate, failure, startedAt, firstEventAt)
                    val canTryNext = index < routeContext.candidates.lastIndex &&
                        AiRouterPolicy.mayFallback(
                            failure.failure.kind,
                            outputStarted = firstEventAt != null && !bufferForFailover,
                        )
                    if (!canTryNext) throw failure
                    selector.forgetSticky(routeContext.profile.id, request.routeSessionKey)
                }
            }
            if (attemptedCandidate || routePass == 1) break
            selector.forgetSticky(routeContext.profile.id, request.routeSessionKey)
            routeContext = try {
                resolveRoute(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw classify(error, request)
            } ?: run {
                delegate.generateStream(request).collect { emit(it) }
                return@flow
            }
        }
        throw lastError ?: classify(routeUnavailable(routeContext.profile), request)
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        delegate.fetchModels(provider)

    private suspend fun resolveRoute(request: AiGenerateRequest): RouteContext? {
        val taskType = request.taskType?.takeIf(String::isNotBlank) ?: return null
        // Null uses the active default route; blank explicitly bypasses the router.
        if (request.routeProfileId != null && request.routeProfileId.isBlank()) return null
        val explicitRouteId = request.routeProfileId?.takeIf(String::isNotBlank)
        val profile = if (explicitRouteId != null) {
            val route = dao.getRoute(explicitRouteId)
                ?: error("AI route not found")
            require(route.enabled) { "AI route is disabled" }
            require(route.taskType == taskType) { "AI route does not match task $taskType" }
            route.toConfig()
        } else {
            dao.getActiveRoute(taskType)?.toConfig() ?: return null
        }
        val now = clock.millis()
        val entities = normalizeLegacyGeneratedTranslationTargets(
            profile = profile,
            targets = dao.getEnabledTargets(profile.id),
            now = now,
        )
        if (entities.isEmpty()) error("AI route has no enabled targets")
        val credentials = entities.mapNotNull(AiRouteTargetEntity::credentialId)
            .distinct()
            .associateWith { dao.getCredential(it) }
        val eligibleTargets = entities.filter { entity ->
            val credential = entity.credentialId?.let(credentials::get)
            entity.cooldownUntil <= now &&
                (credential == null || (
                    credential.enabled &&
                        credential.cooldownUntil <= now &&
                        AiCredentialStatus.isRouterEligible(credential.status)
                    ))
        }
        val ordered = selector.order(
            profile = profile,
            targets = eligibleTargets.map(AiRouteTargetEntity::toConfig),
            now = now,
            sessionKey = request.routeSessionKey,
        )
        if (ordered.isEmpty()) {
            throw routeUnavailable(profile, entities, routeCredentials(entities), now)
        }
        val entityById = entities.associateBy(AiRouteTargetEntity::id)
        val allCandidates = ordered.flatMap { selectedTarget ->
            val entity = entityById[selectedTarget.id] ?: return@flatMap emptyList()
            val model = profileGateway.getModelConfig(selectedTarget.modelProfileId)
                ?: return@flatMap emptyList()
            val credential = entity.credentialId?.let(credentials::get)
            resolveCandidatesForTarget(
                target = selectedTarget,
                model = model,
                explicitCredential = credential,
                now = now,
            )
        }
        if (allCandidates.isEmpty()) {
            throw routeUnavailable(profile, entities, routeCredentials(entities), now)
        }
        val retryOffset = request.routeRetryOffset.coerceAtLeast(0)
        request.routeSemanticFailureKind
            ?.takeIf { retryOffset > 0 }
            ?.let { failureKind ->
                allCandidates.distinctBy { it.target.id }.getOrNull(retryOffset - 1)?.let { failedCandidate ->
                    recordSemanticFailure(profile, failedCandidate, failureKind)
                }
            }
        val skippedTargetIds = allCandidates
            .map { it.target.id }
            .distinct()
            .take(retryOffset)
            .toSet()
        val candidates = allCandidates
            .filterNot { it.target.id in skippedTargetIds }
            .ifEmpty { allCandidates.takeLast(1) }
            .takeTargetAttempts(profile.maxAttempts)
        return RouteContext(profile, candidates)
    }

    private suspend fun resolveCandidatesForTarget(
        target: AiRouteTargetConfig,
        model: io.legado.app.domain.model.AiModelConfig,
        explicitCredential: AiCredentialEntity?,
        now: Long,
    ): List<RouteCandidate> {
        if (explicitCredential != null) {
            val secret = resolveCredentialSecret(explicitCredential)
            if (secret.isNullOrBlank()) return emptyList()
            return listOf(
                RouteCandidate(
                    target = target,
                    model = model.withCredential(secret, explicitCredential.runtimeMetadata()),
                    credential = explicitCredential,
                    usesProviderCredentialPool = false,
                )
            )
        }
        val directCandidate = if (
            model.provider.authType == AiProviderAuthType.NONE ||
            model.provider.apiKey.isNotBlank()
        ) {
            RouteCandidate(
                target = target,
                model = model,
                credential = null,
                usesProviderCredentialPool = false,
            )
        } else {
            null
        }
        if (model.provider.authType == AiProviderAuthType.NONE) {
            return listOfNotNull(directCandidate)
        }
        val credentials = dao.getCredentialsForProvider(model.provider.id)
            .filter { credential ->
                credential.enabled &&
                    credential.cooldownUntil <= now &&
                    AiCredentialStatus.isRouterEligible(credential.status)
            }
        val credentialCandidates = orderProviderCredentials(
            providerId = model.provider.id,
            targetId = target.id,
            credentials = credentials,
        ).mapNotNull { credential ->
            val secret = try {
                resolveCredentialSecret(credential)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (credential.kind == AiCredentialKind.OAUTH_ACCESS_TOKEN) {
                    dao.markCredentialFailure(
                        credential.id,
                        AiFailureKind.AUTHENTICATION.name,
                        now + AiRouterPolicy.cooldownMillis(AiFailureKind.AUTHENTICATION, 1),
                        now,
                    )
                }
                null
            }?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            RouteCandidate(
                target = target,
                model = model.withCredential(secret, credential.runtimeMetadata()),
                credential = credential,
                usesProviderCredentialPool = true,
            )
        }
        return if (credentialCandidates.isEmpty()) {
            listOfNotNull(directCandidate)
        } else {
            credentialCandidates + listOfNotNull(directCandidate)
        }
    }

    private suspend fun resolveCredentialSecret(credential: AiCredentialEntity): String? =
        if (credential.kind == AiCredentialKind.OAUTH_ACCESS_TOKEN) {
            oauthGateway.resolveAccessToken(credential.id)
        } else {
            secretStore.get(credential.secretRef)
        }

    private fun io.legado.app.domain.model.AiModelConfig.withCredential(
        secret: String,
        runtimeMetadata: Map<String, String>,
    ): io.legado.app.domain.model.AiModelConfig {
        val runtimeBaseUrl = runtimeMetadata["resourceUrl"]
            ?.trimEnd('/')
            ?.takeIf(String::isNotBlank)
            ?.let { "$it/v1" }
        return copy(
            provider = provider.copy(
                apiKey = secret,
                baseUrl = runtimeBaseUrl ?: provider.baseUrl,
                runtimeMetadata = runtimeMetadata,
            )
        )
    }

    private fun orderProviderCredentials(
        providerId: String,
        targetId: String,
        credentials: List<AiCredentialEntity>,
    ): List<AiCredentialEntity> {
        val ordered = credentials.sortedWith(
            compareBy<AiCredentialEntity> { it.sortNumber }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
        if (ordered.size <= 1) return ordered
        val cursorKey = "$providerId:$targetId"
        val offset = Math.floorMod(
            credentialCursors.getOrPut(cursorKey) { AtomicLong() }.get(),
            ordered.size.toLong(),
        ).toInt()
        return ordered.drop(offset) + ordered.take(offset)
    }

    private fun advanceProviderCredentialCursor(candidate: RouteCandidate) {
        if (!candidate.usesProviderCredentialPool || candidate.credential == null) return
        val cursorKey = "${candidate.model.provider.id}:${candidate.target.id}"
        credentialCursors.getOrPut(cursorKey) { AtomicLong() }.incrementAndGet()
    }

    private fun List<RouteCandidate>.takeTargetAttempts(maxAttempts: Int): List<RouteCandidate> {
        val allowedTargetIds = map { it.target.id }
            .distinct()
            .take(maxAttempts.coerceAtLeast(1))
            .toSet()
        return filter { it.target.id in allowedTargetIds }
    }

    private suspend fun normalizeLegacyGeneratedTranslationTargets(
        profile: AiRouteProfileConfig,
        targets: List<AiRouteTargetEntity>,
        now: Long,
    ): List<AiRouteTargetEntity> {
        if (profile.taskType != io.legado.app.domain.model.AiTaskType.TRANSLATE_CHAPTER ||
            profile.name != GENERATED_FREE_TRANSLATION_ROUTE_NAME
        ) {
            return targets
        }
        return targets.map { target ->
            if (target.maxConcurrency == GENERATED_TRANSLATION_MAX_CONCURRENCY) {
                target
            } else {
                target.copy(
                    maxConcurrency = GENERATED_TRANSLATION_MAX_CONCURRENCY,
                    updatedAt = now,
                ).also { normalized ->
                    dao.upsertTarget(normalized)
                    targetSemaphores.remove(normalized.id)
                }
            }
        }
    }

    private suspend fun recordSemanticFailure(
        profile: AiRouteProfileConfig,
        candidate: RouteCandidate,
        failureKind: AiFailureKind,
    ) {
        // A structurally valid provider response can still fail chapter-specific parsing or
        // quality checks. Keep this diagnostic on the attempt without globally quarantining the
        // model for other chunks that may translate successfully.
        dao.markLatestAttemptSemanticFailure(
            routeProfileId = profile.id,
            targetId = candidate.target.id,
            failureKind = failureKind.name,
        )
    }

    private suspend fun routeUnavailable(profile: AiRouteProfileConfig): AiRouteUnavailableException {
        val entities = dao.getEnabledTargets(profile.id)
        val credentials = routeCredentials(entities)
        return routeUnavailable(profile, entities, credentials, clock.millis())
    }

    private suspend fun routeCredentials(
        entities: List<AiRouteTargetEntity>,
    ): Map<String, AiCredentialEntity?> {
        val credentials = linkedMapOf<String, AiCredentialEntity?>()
        entities.mapNotNull(AiRouteTargetEntity::credentialId)
            .distinct()
            .forEach { credentialId -> credentials[credentialId] = dao.getCredential(credentialId) }
        entities
            .filter { it.credentialId == null }
            .forEach { entity ->
                profileGateway.getModelConfig(entity.modelProfileId)
                    ?.provider
                    ?.id
                    ?.let { providerId ->
                        dao.getCredentialsForProvider(providerId).forEach { credential ->
                            credentials.putIfAbsent(credential.id, credential)
                        }
                    }
            }
        return credentials
    }

    private suspend fun routeUnavailable(
        profile: AiRouteProfileConfig,
        entities: List<AiRouteTargetEntity>,
        credentials: Map<String, AiCredentialEntity?>,
        now: Long,
    ): AiRouteUnavailableException {
        val nextAvailableTimes = mutableListOf<Long>()
        val summaryParts = ArrayList<String>(MAX_UNAVAILABLE_SUMMARY_TARGETS)
        for (entity in entities.take(MAX_UNAVAILABLE_SUMMARY_TARGETS)) {
            val model = profileGateway.getModelConfig(entity.modelProfileId)
            val providerCredentials = when {
                entity.credentialId != null -> listOfNotNull(credentials[entity.credentialId])
                model != null -> credentials.values
                    .filterNotNull()
                    .filter { it.providerId == model.provider.id }
                else -> emptyList()
            }
            val credential = providerCredentials.firstOrNull {
                it.lastFailureKind != null || !AiCredentialStatus.isRouterEligible(it.status)
            } ?: providerCredentials.firstOrNull()
            val credentialCooldown = providerCredentials
                .mapNotNull { it.cooldownUntil.takeIf { cooldown -> cooldown > now } }
                .minOrNull()
                ?: 0L
            maxOf(entity.cooldownUntil, credentialCooldown)
                .takeIf { it > now }
                ?.let(nextAvailableTimes::add)
            val label = model?.let { "${it.provider.name} · ${it.displayName}" }
                ?: entity.modelProfileId
            val status = entity.lastFailureKind
                ?: credential?.lastFailureKind
                ?: credential?.status?.takeUnless(AiCredentialStatus::isRouterEligible)
                ?: if (entity.cooldownUntil > now || (credential?.cooldownUntil ?: 0L) > now) {
                    "COOLDOWN"
                } else {
                    "UNAVAILABLE"
            }
            summaryParts += "$label=$status"
        }
        val nextAvailableAt = nextAvailableTimes.minOrNull()
        val summary = summaryParts.joinToString("; ")
        return AiRouteUnavailableException(
            taskType = profile.taskType,
            routeName = profile.name,
            retryAfterMillis = nextAvailableAt?.minus(now),
            targetSummary = summary,
        )
    }

    private suspend fun recordSuccess(
        profile: AiRouteProfileConfig,
        candidate: RouteCandidate,
        startedAt: Long,
        firstEventAt: Long?,
    ) {
        val now = clock.millis()
        dao.markTargetSuccess(candidate.target.id, now)
        candidate.credential?.let { dao.markCredentialSuccess(it.id, now) }
        recordAttempt(profile, candidate, true, null, startedAt, firstEventAt, now)
    }

    private suspend fun recordFailure(
        profile: AiRouteProfileConfig,
        candidate: RouteCandidate,
        failure: AiProviderException,
        startedAt: Long,
        firstEventAt: Long?,
    ) {
        val now = clock.millis()
        val cooldown = now + AiRouterPolicy.cooldownMillis(
            failure.failure.kind,
            candidate.target.consecutiveFailures + 1,
        )
        val credentialOnlyFailure = candidate.usesProviderCredentialPool &&
            AiRouterPolicy.affectsCredential(failure.failure.kind)
        if (!credentialOnlyFailure) {
            dao.markTargetFailure(candidate.target.id, failure.failure.kind.name, cooldown, now)
        }
        candidate.credential
            ?.takeIf { AiRouterPolicy.affectsCredential(failure.failure.kind) }
            ?.let {
                dao.markCredentialFailure(it.id, failure.failure.kind.name, cooldown, now)
            }
        recordAttempt(
            profile,
            candidate,
            false,
            failure.failure.kind,
            startedAt,
            firstEventAt,
            now,
        )
    }

    private suspend fun recordAttempt(
        profile: AiRouteProfileConfig,
        candidate: RouteCandidate,
        success: Boolean,
        failureKind: AiFailureKind?,
        startedAt: Long,
        firstEventAt: Long?,
        now: Long,
    ) {
        dao.insertAttempt(
            AiRouteAttemptEntity(
                routeProfileId = profile.id,
                targetId = candidate.target.id,
                providerName = candidate.model.provider.name,
                modelName = candidate.model.displayName,
                credentialLabel = candidate.credential?.label,
                success = success,
                failureKind = failureKind?.name,
                latencyMs = (now - startedAt).coerceAtLeast(0),
                firstEventMs = firstEventAt?.let { (it - startedAt).coerceAtLeast(0) },
                createdAt = now,
            )
        )
        advanceProviderCredentialCursor(candidate)
        dao.trimAttempts()
    }

    private fun AiGenerateRequest.withCandidate(candidate: RouteCandidate): AiGenerateRequest {
        val targetModel = candidate.model
        val routedParams = if (taskType != null &&
            taskType != io.legado.app.domain.model.AiTaskType.CHAT &&
            targetModel.provider.protocol != AiProtocol.LOCAL_GGUF
        ) {
            val reasoningModel = AiCapability.REASONING in targetModel.capabilities ||
                AiCapability.REASONING in AiModelRegistry.inferCapabilities(targetModel.modelId)
            params.copy(
                maxOutputTokens = AiTranslationTokenBudget.forRouteTarget(
                    requestedLimit = params.maxOutputTokens,
                    providerLimit = targetModel.maxOutputTokens,
                    reasoningModel = reasoningModel,
                )
            )
        } else {
            params
        }
        return copy(model = targetModel, params = routedParams)
    }

    private fun classify(
        error: Throwable,
        request: AiGenerateRequest,
        attemptOffset: Int = 0,
    ): AiProviderException =
        if (error is AiProviderException) error else AiProviderFailureClassifier.classify(
            error = error,
            provider = request.model.provider.name,
            model = request.model.modelId,
            attemptOffset = attemptOffset,
        )

    private suspend fun <T> withTargetPermit(
        target: AiRouteTargetConfig,
        block: suspend () -> T,
    ): T {
        if (target.maxConcurrency <= 0) return block()
        val holder = targetSemaphores.compute(target.id) { _, current ->
            if (current == null || current.permits != target.maxConcurrency) {
                TargetSemaphore(target.maxConcurrency, Semaphore(target.maxConcurrency))
            } else {
                current
            }
        } ?: error("Failed to create target limiter")
        return holder.semaphore.withPermit { block() }
    }

    /**
     * Route resolution happens before a request waits for the target semaphore. Another chunk
     * may quarantine or replace that target while this request is queued, so health must be
     * checked again immediately before the provider call.
     */
    private suspend fun ensureCandidateCurrentlyEligible(candidate: RouteCandidate) {
        val now = clock.millis()
        val target = dao.getTarget(candidate.target.id)
            ?: throw RouteCandidateUnavailableException()
        if (
            !target.enabled ||
            target.cooldownUntil > now ||
            target.modelProfileId != candidate.target.modelProfileId ||
            target.credentialId != candidate.target.credentialId
        ) {
            throw RouteCandidateUnavailableException()
        }
        target.credentialId?.let { credentialId ->
            val credential = dao.getCredential(credentialId)
                ?: throw RouteCandidateUnavailableException()
            if (
                !credential.enabled ||
                credential.cooldownUntil > now ||
                !AiCredentialStatus.isRouterEligible(credential.status)
            ) {
                throw RouteCandidateUnavailableException()
            }
        }
        candidate.credential?.let { candidateCredential ->
            val credential = dao.getCredential(candidateCredential.id)
                ?: throw RouteCandidateUnavailableException()
            if (
                credential.providerId != candidate.model.provider.id ||
                !credential.enabled ||
                credential.cooldownUntil > now ||
                !AiCredentialStatus.isRouterEligible(credential.status)
            ) {
                throw RouteCandidateUnavailableException()
            }
        }
    }

    private data class RouteContext(
        val profile: AiRouteProfileConfig,
        val candidates: List<RouteCandidate>,
    )

    private data class RouteCandidate(
        val target: AiRouteTargetConfig,
        val model: io.legado.app.domain.model.AiModelConfig,
        val credential: AiCredentialEntity?,
        val usesProviderCredentialPool: Boolean,
    )

    private data class TargetSemaphore(
        val permits: Int,
        val semaphore: Semaphore,
    )

    private class RouteCandidateUnavailableException : RuntimeException()

    private companion object {
        const val MAX_ROUTE_ATTEMPTS = 20
        const val MAX_UNAVAILABLE_SUMMARY_TARGETS = 6
        fun newId(prefix: String) =
            "${prefix}_${UUID.randomUUID().toString().replace("-", "")}" 
    }
}

private fun AiCredentialEntity.toConfig(hasSecret: Boolean) = AiCredentialConfig(
    id = id,
    providerId = providerId,
    label = label,
    kind = kind,
    enabled = enabled,
    sortNumber = sortNumber,
    cooldownUntil = cooldownUntil,
    consecutiveFailures = consecutiveFailures,
    lastFailureKind = lastFailureKind,
    lastUsedAt = lastUsedAt,
    lastSuccessAt = lastSuccessAt,
    lastFailureAt = lastFailureAt,
    hasSecret = hasSecret,
    oauthProvider = oauthProvider,
    accountId = accountId,
    accountLabel = accountLabel,
    expiresAt = expiresAt,
    scopes = scopes,
    status = status,
    hasRefreshToken = refreshTokenRef != null,
)

private fun AiCredentialEntity.runtimeMetadata(): Map<String, String> = buildMap {
    accountId?.let { put("accountId", it) }
    providerDataJson?.let { raw ->
        runCatching {
            @Suppress("UNCHECKED_CAST")
            io.legado.app.utils.GSON.fromJson(raw, Map::class.java) as? Map<String, Any?>
        }.getOrNull()?.forEach { (key, value) ->
            if (!key.isSensitiveProviderDataKey()) {
                value?.toString()?.let { put(key, it) }
            }
        }
    }
}

private fun String.isSensitiveProviderDataKey(): Boolean {
    val normalized = lowercase()
    return listOf(
        "token",
        "secret",
        "apikey",
        "api_key",
        "authorization",
        "password",
        "credential",
    ).any(normalized::contains)
}

private const val GENERATED_FREE_TRANSLATION_ROUTE_NAME =
    "D\u1ecbch AI \u00b7 Free fallback"
private const val GENERATED_TRANSLATION_MAX_CONCURRENCY = 1

private fun AiRouteProfileEntity.toConfig() = AiRouteProfileConfig(
    id = id,
    name = name,
    taskType = taskType,
    strategy = strategy,
    maxAttempts = maxAttempts,
    stickySession = stickySession,
    enabled = enabled,
    isDefault = isDefault,
    sortNumber = sortNumber,
)

private fun AiRouteTargetEntity.toConfig() = AiRouteTargetConfig(
    id = id,
    routeProfileId = routeProfileId,
    modelProfileId = modelProfileId,
    credentialId = credentialId,
    priority = priority,
    weight = weight,
    maxConcurrency = maxConcurrency,
    enabled = enabled,
    sortNumber = sortNumber,
    cooldownUntil = cooldownUntil,
    consecutiveFailures = consecutiveFailures,
    lastFailureKind = lastFailureKind,
    lastUsedAt = lastUsedAt,
    lastSuccessAt = lastSuccessAt,
    lastFailureAt = lastFailureAt,
)

private fun AiRouteAttemptEntity.toConfig() = AiRouteAttemptConfig(
    id = id,
    routeProfileId = routeProfileId,
    targetId = targetId,
    providerName = providerName,
    modelName = modelName,
    credentialLabel = credentialLabel,
    success = success,
    failureKind = failureKind,
    latencyMs = latencyMs,
    firstEventMs = firstEventMs,
    createdAt = createdAt,
)
