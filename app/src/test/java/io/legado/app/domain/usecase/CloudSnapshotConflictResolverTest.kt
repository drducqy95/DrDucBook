package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotConflictChoice
import io.legado.app.domain.model.CloudSnapshotConflictKind
import io.legado.app.domain.model.CloudSyncTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSnapshotConflictResolverTest {

    @Test
    fun conflictPromptNeverAutoMergesBothChangedState() {
        val state = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-local",
            targetRevision = "rev-cloud",
        )

        val prompt = CloudSnapshotConflictResolver.promptFor(
            state = state,
            preferredTarget = CloudSyncTarget.SUPABASE,
        )

        assertEquals(CloudSnapshotConflictKind.CONFLICT, prompt.state.kind)
        assertFalse(prompt.canAutoResolve)
        assertEquals(
            listOf(
                CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION,
                CloudSnapshotConflictChoice.RESTORE_TARGET,
                CloudSnapshotConflictChoice.SAVE_LOCAL_AS_CLOUD_COPY,
            ),
            prompt.options.map { it.choice },
        )
        assertTrue(prompt.options.single { it.choice == CloudSnapshotConflictChoice.RESTORE_TARGET }
            .destructiveRestore)
    }

    @Test
    fun divergedTargetsExposeSeparateRestoreChoices() {
        val state = CloudSnapshotPolicy.classifyTargetDivergence(
            supabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64)),
            driveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-drive", "b".repeat(64)),
        )

        val prompt = CloudSnapshotConflictResolver.promptFor(
            state = state,
            preferredTarget = CloudSyncTarget.BOTH,
        )
        val restoreTargets = prompt.options
            .filter { it.choice == CloudSnapshotConflictChoice.RESTORE_TARGET }
            .map { it.selectedTarget }

        assertEquals(listOf(CloudSyncTarget.SUPABASE, CloudSyncTarget.GOOGLE_DRIVE), restoreTargets)
        val supabaseRestore = prompt.options.first { it.selectedTarget == CloudSyncTarget.SUPABASE }
        val plan = CloudSnapshotConflictResolver.userPlanFor(state, supabaseRestore)
        assertTrue(plan.restoreTargetSnapshot)
        assertEquals(CloudSyncTarget.SUPABASE, plan.selectedTarget)
    }

    @Test
    fun invalidTargetCannotBeRestoredFromPrompt() {
        val state = CloudSnapshotPolicy.classifyTargetDivergence(
            supabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64))
                .copy(snapshotId = null),
            driveHead = null,
        )

        val prompt = CloudSnapshotConflictResolver.promptFor(
            state = state,
            preferredTarget = CloudSyncTarget.SUPABASE,
        )

        assertEquals(CloudSnapshotConflictKind.INVALID_TARGET, prompt.state.kind)
        assertTrue(prompt.options.none { it.choice == CloudSnapshotConflictChoice.RESTORE_TARGET })
        assertTrue(
            prompt.options.any { it.choice == CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION }
        )
    }

    @Test
    fun fastForwardStateProducesAutomaticPlan() {
        val state = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-local",
            targetRevision = "rev-base",
        )

        val prompt = CloudSnapshotConflictResolver.promptFor(
            state = state,
            preferredTarget = CloudSyncTarget.SUPABASE,
        )
        val plan = CloudSnapshotConflictResolver.automaticPlanFor(state)

        assertTrue(prompt.canAutoResolve)
        assertTrue(prompt.options.isEmpty())
        assertTrue(plan.uploadLocalSnapshot)
        assertFalse(plan.userChoiceRequired)
    }

    private fun head(
        target: CloudSyncTarget,
        revision: String,
        hash: String,
    ) = io.legado.app.domain.model.CloudSnapshotHead(
        target = target,
        revision = revision,
        snapshotId = "snapshot-$revision",
        contentSha256 = hash,
        updatedAtEpochMillis = 1L,
    )
}
