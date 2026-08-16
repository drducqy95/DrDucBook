package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotConflictKind
import io.legado.app.domain.model.CloudSnapshotConflictChoice
import io.legado.app.domain.model.CloudSnapshotConflictState
import io.legado.app.domain.model.CloudSnapshotDataset
import io.legado.app.domain.model.CloudSnapshotEntry
import io.legado.app.domain.model.CloudSnapshotHead
import io.legado.app.domain.model.CloudSnapshotHeadWritePlan
import io.legado.app.domain.model.CloudSnapshotManifest
import io.legado.app.domain.model.CloudSnapshotResolutionPlan
import io.legado.app.domain.model.CloudSnapshotRestorePlan
import io.legado.app.domain.model.CloudSyncTarget

object CloudSnapshotPolicy {

    const val SCHEMA_VERSION = 1

    val includedDatasets: Set<CloudSnapshotDataset> = CloudSnapshotDataset.included()
    val excludedDatasets: Set<CloudSnapshotDataset> = CloudSnapshotDataset.excluded()

    fun createManifest(
        snapshotId: String,
        revision: String,
        deviceId: String,
        createdAtEpochMillis: Long,
        entries: List<CloudSnapshotEntry>,
    ): CloudSnapshotManifest {
        require(entries.isNotEmpty()) { "Snapshot must contain at least one entry" }
        entries.forEach(::validateEntry)
        require(entries.map { it.dataset }.distinct().size == entries.size) {
            "Snapshot must contain at most one entry per dataset"
        }
        return CloudSnapshotManifest(
            schemaVersion = SCHEMA_VERSION,
            snapshotId = snapshotId,
            revision = revision,
            deviceId = deviceId,
            createdAtEpochMillis = createdAtEpochMillis,
            entries = entries.sortedBy { it.dataset.storageKey },
            excludedDatasets = excludedDatasets,
        )
    }

    fun validateEntry(entry: CloudSnapshotEntry) {
        require(entry.dataset in includedDatasets) {
            "Dataset ${entry.dataset.storageKey} must not be included in cloud snapshots"
        }
        require(entry.objectPath.isNotBlank() && !entry.objectPath.contains("..")) {
            "Snapshot object path is invalid"
        }
        require(sha256Regex.matches(entry.sha256)) { "Snapshot entry SHA-256 is invalid" }
        require(entry.sizeBytes >= 0) { "Snapshot entry size must not be negative" }
        require(entry.recordCount >= 0) { "Snapshot entry record count must not be negative" }
    }

    fun classifyRevisionState(
        baseRevision: String?,
        localRevision: String?,
        targetRevision: String?,
    ): CloudSnapshotConflictState {
        val kind = when {
            localRevision == targetRevision -> CloudSnapshotConflictKind.NO_CHANGE
            targetRevision == null -> CloudSnapshotConflictKind.LOCAL_ONLY
            localRevision == null -> CloudSnapshotConflictKind.TARGET_ONLY
            baseRevision == null -> CloudSnapshotConflictKind.CONFLICT
            localRevision == baseRevision -> CloudSnapshotConflictKind.TARGET_ONLY
            targetRevision == baseRevision -> CloudSnapshotConflictKind.LOCAL_ONLY
            else -> CloudSnapshotConflictKind.CONFLICT
        }
        return CloudSnapshotConflictState(
            kind = kind,
            baseRevision = baseRevision,
            localRevision = localRevision,
            targetRevision = targetRevision,
            requiresUserChoice = kind.requiresUserChoice,
        )
    }

    fun classifyTargetDivergence(
        supabaseHead: CloudSnapshotHead?,
        driveHead: CloudSnapshotHead?,
    ): CloudSnapshotConflictState {
        val invalid = listOfNotNull(supabaseHead, driveHead).firstOrNull {
            it.revision.isNullOrBlank() != it.snapshotId.isNullOrBlank() ||
                it.revision.isNullOrBlank() != it.contentSha256.isNullOrBlank()
        }
        if (invalid != null) {
            return CloudSnapshotConflictState(
                kind = CloudSnapshotConflictKind.INVALID_TARGET,
                baseRevision = null,
                localRevision = null,
                targetRevision = invalid.revision,
                requiresUserChoice = true,
            )
        }
        if (supabaseHead == null || driveHead == null) {
            return CloudSnapshotConflictState(
                kind = CloudSnapshotConflictKind.NO_CHANGE,
                baseRevision = null,
                localRevision = supabaseHead?.revision,
                targetRevision = driveHead?.revision,
                requiresUserChoice = false,
            )
        }
        val diverged = supabaseHead.revision != driveHead.revision ||
            supabaseHead.snapshotId != driveHead.snapshotId ||
            supabaseHead.contentSha256 != driveHead.contentSha256
        return CloudSnapshotConflictState(
            kind = if (diverged) {
                CloudSnapshotConflictKind.TARGETS_DIVERGED
            } else {
                CloudSnapshotConflictKind.NO_CHANGE
            },
            baseRevision = null,
            localRevision = supabaseHead.revision,
            targetRevision = driveHead.revision,
            requiresUserChoice = diverged,
        )
    }

    fun restorePlan(manifest: CloudSnapshotManifest): CloudSnapshotRestorePlan {
        require(manifest.schemaVersion == SCHEMA_VERSION) { "Unsupported snapshot schema version" }
        manifest.entries.forEach(::validateEntry)
        return CloudSnapshotRestorePlan(
            manifest = manifest,
            verifyBeforeCommit = true,
            transactional = true,
            excludedDatasets = manifest.excludedDatasets,
        )
    }

    fun automaticResolutionPlan(
        state: CloudSnapshotConflictState,
    ): CloudSnapshotResolutionPlan {
        require(!state.requiresUserChoice) {
            "Snapshot conflict requires an explicit user choice"
        }
        return when (state.kind) {
            CloudSnapshotConflictKind.NO_CHANGE -> CloudSnapshotResolutionPlan(
                conflictState = state,
                choice = null,
                selectedTarget = null,
                uploadLocalSnapshot = false,
                restoreTargetSnapshot = false,
                saveLocalAsCloudCopy = false,
                requiresNewRevision = false,
                userChoiceRequired = false,
            )

            CloudSnapshotConflictKind.LOCAL_ONLY -> CloudSnapshotResolutionPlan(
                conflictState = state,
                choice = null,
                selectedTarget = null,
                uploadLocalSnapshot = true,
                restoreTargetSnapshot = false,
                saveLocalAsCloudCopy = false,
                requiresNewRevision = true,
                userChoiceRequired = false,
            )

            CloudSnapshotConflictKind.TARGET_ONLY -> CloudSnapshotResolutionPlan(
                conflictState = state,
                choice = null,
                selectedTarget = null,
                uploadLocalSnapshot = false,
                restoreTargetSnapshot = true,
                saveLocalAsCloudCopy = false,
                requiresNewRevision = false,
                userChoiceRequired = false,
            )

            CloudSnapshotConflictKind.CONFLICT,
            CloudSnapshotConflictKind.TARGETS_DIVERGED,
            CloudSnapshotConflictKind.INVALID_TARGET -> error("Unreachable user-choice conflict")
        }
    }

    fun userResolutionPlan(
        state: CloudSnapshotConflictState,
        choice: CloudSnapshotConflictChoice,
        selectedTarget: CloudSyncTarget? = null,
    ): CloudSnapshotResolutionPlan {
        require(state.requiresUserChoice) {
            "Snapshot state does not require an explicit user choice"
        }
        if (state.kind == CloudSnapshotConflictKind.TARGETS_DIVERGED &&
            choice == CloudSnapshotConflictChoice.RESTORE_TARGET
        ) {
            require(selectedTarget == CloudSyncTarget.SUPABASE ||
                selectedTarget == CloudSyncTarget.GOOGLE_DRIVE
            ) {
                "Diverged cloud targets require a selected restore target"
            }
        }
        require(state.kind != CloudSnapshotConflictKind.INVALID_TARGET ||
            choice != CloudSnapshotConflictChoice.RESTORE_TARGET
        ) {
            "Invalid cloud targets cannot be restored"
        }
        return when (choice) {
            CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION -> CloudSnapshotResolutionPlan(
                conflictState = state,
                choice = choice,
                selectedTarget = null,
                uploadLocalSnapshot = true,
                restoreTargetSnapshot = false,
                saveLocalAsCloudCopy = false,
                requiresNewRevision = true,
                userChoiceRequired = true,
            )

            CloudSnapshotConflictChoice.RESTORE_TARGET -> CloudSnapshotResolutionPlan(
                conflictState = state,
                choice = choice,
                selectedTarget = selectedTarget,
                uploadLocalSnapshot = false,
                restoreTargetSnapshot = true,
                saveLocalAsCloudCopy = false,
                requiresNewRevision = false,
                userChoiceRequired = true,
            )

            CloudSnapshotConflictChoice.SAVE_LOCAL_AS_CLOUD_COPY -> CloudSnapshotResolutionPlan(
                conflictState = state,
                choice = choice,
                selectedTarget = null,
                uploadLocalSnapshot = false,
                restoreTargetSnapshot = false,
                saveLocalAsCloudCopy = true,
                requiresNewRevision = true,
                userChoiceRequired = true,
            )
        }
    }

    fun headWritePlans(
        targetMode: CloudSyncTarget,
        observedSupabaseHead: CloudSnapshotHead?,
        observedDriveHead: CloudSnapshotHead?,
        nextRevision: String,
        nextSnapshotId: String,
        nextContentSha256: String,
        updatedAtEpochMillis: Long,
    ): List<CloudSnapshotHeadWritePlan> {
        validateNextHeadParts(nextRevision, nextSnapshotId, nextContentSha256)
        require(updatedAtEpochMillis > 0L) { "Snapshot head update time is invalid" }
        return targetMode.concreteTargets.map { target ->
            val observedHead = when (target) {
                CloudSyncTarget.SUPABASE -> observedSupabaseHead
                CloudSyncTarget.GOOGLE_DRIVE -> observedDriveHead
                CloudSyncTarget.BOTH -> error("BOTH is not a concrete snapshot target")
            }
            validateObservedHead(target, observedHead)
            CloudSnapshotHeadWritePlan(
                target = target,
                expectedRevision = observedHead?.revision,
                expectedSnapshotId = observedHead?.snapshotId,
                expectedContentSha256 = observedHead?.contentSha256,
                nextRevision = nextRevision,
                nextSnapshotId = nextSnapshotId,
                nextContentSha256 = nextContentSha256,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        }
    }

    fun verifyHeadCompareAndSet(
        plan: CloudSnapshotHeadWritePlan,
        actualHead: CloudSnapshotHead?,
    ): Result<Unit> = runCatching {
        require(plan.target != CloudSyncTarget.BOTH) {
            "Snapshot head compare-and-set requires a concrete target"
        }
        validateNextHeadParts(
            revision = plan.nextRevision,
            snapshotId = plan.nextSnapshotId,
            contentSha256 = plan.nextContentSha256,
        )
        validateObservedHead(plan.target, actualHead)
        require(actualHead?.revision == plan.expectedRevision &&
            actualHead?.snapshotId == plan.expectedSnapshotId &&
            actualHead?.contentSha256 == plan.expectedContentSha256
        ) {
            "Snapshot target head changed before write"
        }
    }

    private val CloudSnapshotConflictKind.requiresUserChoice: Boolean
        get() = when (this) {
            CloudSnapshotConflictKind.CONFLICT,
            CloudSnapshotConflictKind.TARGETS_DIVERGED,
            CloudSnapshotConflictKind.INVALID_TARGET -> true
            CloudSnapshotConflictKind.NO_CHANGE,
            CloudSnapshotConflictKind.LOCAL_ONLY,
            CloudSnapshotConflictKind.TARGET_ONLY -> false
        }

    private val CloudSyncTarget.concreteTargets: List<CloudSyncTarget>
        get() = when (this) {
            CloudSyncTarget.SUPABASE -> listOf(CloudSyncTarget.SUPABASE)
            CloudSyncTarget.GOOGLE_DRIVE -> listOf(CloudSyncTarget.GOOGLE_DRIVE)
            CloudSyncTarget.BOTH -> listOf(CloudSyncTarget.SUPABASE, CloudSyncTarget.GOOGLE_DRIVE)
        }

    private fun validateObservedHead(
        expectedTarget: CloudSyncTarget,
        head: CloudSnapshotHead?,
    ) {
        if (head == null) return
        require(head.target == expectedTarget && head.target != CloudSyncTarget.BOTH) {
            "Snapshot head target is invalid"
        }
        val empty = head.revision.isNullOrBlank() &&
            head.snapshotId.isNullOrBlank() &&
            head.contentSha256.isNullOrBlank()
        if (empty) return
        require(!head.revision.isNullOrBlank() &&
            !head.snapshotId.isNullOrBlank() &&
            !head.contentSha256.isNullOrBlank()
        ) {
            "Snapshot head is incomplete"
        }
        validateHeadToken("revision", head.revision.orEmpty())
        validateHeadToken("snapshotId", head.snapshotId.orEmpty())
        require(sha256Regex.matches(head.contentSha256.orEmpty())) {
            "Snapshot head SHA-256 is invalid"
        }
    }

    private fun validateNextHeadParts(
        revision: String,
        snapshotId: String,
        contentSha256: String,
    ) {
        validateHeadToken("revision", revision)
        validateHeadToken("snapshotId", snapshotId)
        require(sha256Regex.matches(contentSha256)) {
            "Snapshot head SHA-256 is invalid"
        }
    }

    private fun validateHeadToken(
        fieldName: String,
        value: String,
    ) {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) { "Snapshot head $fieldName is required" }
        require(!trimmed.contains("..") && !trimmed.contains('/') && !trimmed.contains('\\')) {
            "Snapshot head $fieldName is invalid"
        }
    }

    private val sha256Regex = Regex("^[a-f0-9]{64}$")
}
