package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotConflictChoice
import io.legado.app.domain.model.CloudSnapshotConflictKind
import io.legado.app.domain.model.CloudSnapshotHead
import io.legado.app.domain.model.CloudSyncTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSnapshotSyncPlannerTest {

    @Test
    fun bothTargetsDivergedRequiresUserChoiceBeforeRevisionMerge() {
        val decision = CloudSnapshotSyncPlanner.decide(
            CloudSnapshotSyncState(
                targetMode = CloudSyncTarget.BOTH,
                baseRevision = "rev-base",
                localRevision = "rev-local",
                observedSupabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64)),
                observedDriveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-drive", "b".repeat(64)),
            )
        )

        assertTrue(decision is CloudSnapshotSyncDecision.UserChoiceRequired)
        val prompt = (decision as CloudSnapshotSyncDecision.UserChoiceRequired).prompt
        assertEquals(CloudSnapshotConflictKind.TARGETS_DIVERGED, prompt.state.kind)
        assertEquals(
            listOf(CloudSyncTarget.SUPABASE, CloudSyncTarget.GOOGLE_DRIVE),
            prompt.options
                .filter { it.choice == CloudSnapshotConflictChoice.RESTORE_TARGET }
                .map { it.selectedTarget },
        )
    }

    @Test
    fun localNewerBothTargetsCreatesIndependentCasPlans() {
        val syncState = CloudSnapshotSyncState(
            targetMode = CloudSyncTarget.BOTH,
            baseRevision = "rev-base",
            localRevision = "rev-local",
            observedSupabaseHead = head(CloudSyncTarget.SUPABASE, "rev-base", "a".repeat(64)),
            observedDriveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-base", "a".repeat(64)),
        )

        val decision = CloudSnapshotSyncPlanner.decide(syncState)

        assertTrue(decision is CloudSnapshotSyncDecision.Automatic)
        val plan = (decision as CloudSnapshotSyncDecision.Automatic).plan
        assertTrue(plan.uploadLocalSnapshot)
        assertFalse(plan.userChoiceRequired)
        val writes = CloudSnapshotSyncPlanner.uploadHeadWritePlans(
            syncState = syncState,
            resolutionPlan = plan,
            nextRevision = "rev-next",
            nextSnapshotId = "snapshot-next",
            nextContentSha256 = "c".repeat(64),
            updatedAtEpochMillis = 2L,
        )
        assertEquals(listOf(CloudSyncTarget.SUPABASE, CloudSyncTarget.GOOGLE_DRIVE), writes.map { it.target })
        assertEquals(listOf("rev-base", "rev-base"), writes.map { it.expectedRevision })
    }

    @Test
    fun targetNewerSingleTargetRestoresAutomaticallyWithoutHeadWrite() {
        val decision = CloudSnapshotSyncPlanner.decide(
            CloudSnapshotSyncState(
                targetMode = CloudSyncTarget.SUPABASE,
                baseRevision = "rev-base",
                localRevision = "rev-base",
                observedSupabaseHead = head(CloudSyncTarget.SUPABASE, "rev-cloud", "b".repeat(64)),
                observedDriveHead = null,
            )
        )

        assertTrue(decision is CloudSnapshotSyncDecision.Automatic)
        val plan = (decision as CloudSnapshotSyncDecision.Automatic).plan
        assertTrue(plan.restoreTargetSnapshot)
        assertFalse(plan.uploadLocalSnapshot)
    }

    @Test
    fun invalidSingleTargetHeadRequiresUserChoiceButDoesNotExposeRestore() {
        val decision = CloudSnapshotSyncPlanner.decide(
            CloudSnapshotSyncState(
                targetMode = CloudSyncTarget.SUPABASE,
                baseRevision = "rev-base",
                localRevision = "rev-local",
                observedSupabaseHead = head(CloudSyncTarget.SUPABASE, "rev-cloud", "b".repeat(64))
                    .copy(snapshotId = null),
                observedDriveHead = null,
            )
        )

        assertTrue(decision is CloudSnapshotSyncDecision.UserChoiceRequired)
        val prompt = (decision as CloudSnapshotSyncDecision.UserChoiceRequired).prompt
        assertEquals(CloudSnapshotConflictKind.INVALID_TARGET, prompt.state.kind)
        assertTrue(prompt.options.none { it.choice == CloudSnapshotConflictChoice.RESTORE_TARGET })
    }

    @Test(expected = IllegalArgumentException::class)
    fun restorePlanCannotCreateHeadWritePlans() {
        val syncState = CloudSnapshotSyncState(
            targetMode = CloudSyncTarget.SUPABASE,
            baseRevision = "rev-base",
            localRevision = "rev-base",
            observedSupabaseHead = head(CloudSyncTarget.SUPABASE, "rev-cloud", "b".repeat(64)),
            observedDriveHead = null,
        )
        val decision = CloudSnapshotSyncPlanner.decide(syncState) as CloudSnapshotSyncDecision.Automatic

        CloudSnapshotSyncPlanner.uploadHeadWritePlans(
            syncState = syncState,
            resolutionPlan = decision.plan,
            nextRevision = "rev-next",
            nextSnapshotId = "snapshot-next",
            nextContentSha256 = "c".repeat(64),
            updatedAtEpochMillis = 2L,
        )
    }

    private fun head(
        target: CloudSyncTarget,
        revision: String,
        hash: String,
    ) = CloudSnapshotHead(
        target = target,
        revision = revision,
        snapshotId = "snapshot-$revision",
        contentSha256 = hash,
        updatedAtEpochMillis = 1L,
    )
}
