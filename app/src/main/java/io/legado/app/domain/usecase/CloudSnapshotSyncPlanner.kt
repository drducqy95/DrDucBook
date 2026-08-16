package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotConflictKind
import io.legado.app.domain.model.CloudSnapshotConflictState
import io.legado.app.domain.model.CloudSnapshotHead
import io.legado.app.domain.model.CloudSnapshotHeadWritePlan
import io.legado.app.domain.model.CloudSnapshotResolutionPlan
import io.legado.app.domain.model.CloudSyncTarget

data class CloudSnapshotSyncState(
    val targetMode: CloudSyncTarget,
    val baseRevision: String?,
    val localRevision: String?,
    val observedSupabaseHead: CloudSnapshotHead?,
    val observedDriveHead: CloudSnapshotHead?,
)

sealed interface CloudSnapshotSyncDecision {
    val syncState: CloudSnapshotSyncState

    data class Automatic(
        override val syncState: CloudSnapshotSyncState,
        val plan: CloudSnapshotResolutionPlan,
    ) : CloudSnapshotSyncDecision

    data class UserChoiceRequired(
        override val syncState: CloudSnapshotSyncState,
        val prompt: CloudSnapshotConflictPrompt,
    ) : CloudSnapshotSyncDecision
}

object CloudSnapshotSyncPlanner {

    fun decide(syncState: CloudSnapshotSyncState): CloudSnapshotSyncDecision {
        val invalidOrDiverged = classifyTargetState(syncState)
        if (invalidOrDiverged.requiresUserChoice) {
            return CloudSnapshotSyncDecision.UserChoiceRequired(
                syncState = syncState,
                prompt = CloudSnapshotConflictResolver.promptFor(
                    state = invalidOrDiverged,
                    preferredTarget = syncState.targetMode,
                ),
            )
        }
        val revisionState = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = syncState.baseRevision,
            localRevision = syncState.localRevision,
            targetRevision = selectedTargetRevision(syncState),
        )
        return if (revisionState.requiresUserChoice) {
            CloudSnapshotSyncDecision.UserChoiceRequired(
                syncState = syncState,
                prompt = CloudSnapshotConflictResolver.promptFor(
                    state = revisionState,
                    preferredTarget = syncState.targetMode,
                ),
            )
        } else {
            CloudSnapshotSyncDecision.Automatic(
                syncState = syncState,
                plan = CloudSnapshotConflictResolver.automaticPlanFor(revisionState),
            )
        }
    }

    fun uploadHeadWritePlans(
        syncState: CloudSnapshotSyncState,
        resolutionPlan: CloudSnapshotResolutionPlan,
        nextRevision: String,
        nextSnapshotId: String,
        nextContentSha256: String,
        updatedAtEpochMillis: Long,
    ): List<CloudSnapshotHeadWritePlan> {
        require(resolutionPlan.uploadLocalSnapshot || resolutionPlan.saveLocalAsCloudCopy) {
            "Snapshot head writes are only valid for upload or cloud-copy plans"
        }
        return CloudSnapshotPolicy.headWritePlans(
            targetMode = syncState.targetMode,
            observedSupabaseHead = syncState.observedSupabaseHead,
            observedDriveHead = syncState.observedDriveHead,
            nextRevision = nextRevision,
            nextSnapshotId = nextSnapshotId,
            nextContentSha256 = nextContentSha256,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    private fun classifyTargetState(syncState: CloudSnapshotSyncState): CloudSnapshotConflictState =
        when (syncState.targetMode) {
            CloudSyncTarget.SUPABASE -> CloudSnapshotPolicy.classifyTargetDivergence(
                supabaseHead = syncState.observedSupabaseHead,
                driveHead = null,
            )
            CloudSyncTarget.GOOGLE_DRIVE -> CloudSnapshotPolicy.classifyTargetDivergence(
                supabaseHead = null,
                driveHead = syncState.observedDriveHead,
            )
            CloudSyncTarget.BOTH -> CloudSnapshotPolicy.classifyTargetDivergence(
                supabaseHead = syncState.observedSupabaseHead,
                driveHead = syncState.observedDriveHead,
            )
        }

    private fun selectedTargetRevision(syncState: CloudSnapshotSyncState): String? =
        when (syncState.targetMode) {
            CloudSyncTarget.SUPABASE -> syncState.observedSupabaseHead?.revision
            CloudSyncTarget.GOOGLE_DRIVE -> syncState.observedDriveHead?.revision
            CloudSyncTarget.BOTH ->
                syncState.observedSupabaseHead?.revision ?: syncState.observedDriveHead?.revision
        }

    val CloudSnapshotSyncDecision.requiresUserChoice: Boolean
        get() = when (this) {
            is CloudSnapshotSyncDecision.Automatic -> false
            is CloudSnapshotSyncDecision.UserChoiceRequired -> true
        }

    val CloudSnapshotSyncDecision.conflictKind: CloudSnapshotConflictKind
        get() = when (this) {
            is CloudSnapshotSyncDecision.Automatic -> plan.conflictState.kind
            is CloudSnapshotSyncDecision.UserChoiceRequired -> prompt.state.kind
        }
}
