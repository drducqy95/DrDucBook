package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotConflictKind
import io.legado.app.domain.model.CloudSnapshotConflictChoice
import io.legado.app.domain.model.CloudSnapshotDataset
import io.legado.app.domain.model.CloudSnapshotEntry
import io.legado.app.domain.model.CloudSnapshotHead
import io.legado.app.domain.model.CloudSyncTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSnapshotPolicyTest {

    @Test
    fun manifestIncludesSupportedDatasetsAndExcludesSensitiveRuntimeState() {
        val manifest = CloudSnapshotPolicy.createManifest(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            entries = listOf(
                entry(CloudSnapshotDataset.BOOK_SOURCES),
                entry(CloudSnapshotDataset.READING_PROGRESS),
                entry(CloudSnapshotDataset.AUTHORING_PROJECTS),
                entry(CloudSnapshotDataset.AGENT_STATE),
                entry(CloudSnapshotDataset.APPEARANCE),
                entry(CloudSnapshotDataset.WEB_SERVICE_POLICY),
                entry(CloudSnapshotDataset.SOURCE_HEALTH_SUMMARY),
            ),
        )

        assertTrue(CloudSnapshotDataset.COOKIES in manifest.excludedDatasets)
        assertTrue(CloudSnapshotDataset.AUTH_SESSIONS in manifest.excludedDatasets)
        assertTrue(CloudSnapshotDataset.CACHE in manifest.excludedDatasets)
        assertTrue(CloudSnapshotDataset.MODEL_PACKAGES in manifest.excludedDatasets)
        assertTrue(CloudSnapshotDataset.MEDIA_DOWNLOADS in manifest.excludedDatasets)
        assertFalse(manifest.entries.any { it.dataset in manifest.excludedDatasets })
    }

    @Test(expected = IllegalArgumentException::class)
    fun manifestRejectsCookieDataset() {
        CloudSnapshotPolicy.createManifest(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            entries = listOf(entry(CloudSnapshotDataset.COOKIES)),
        )
    }

    @Test
    fun bothChangedFromBaseRequiresUserChoiceAndDoesNotAutoMerge() {
        val state = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-local",
            targetRevision = "rev-cloud",
        )

        assertEquals(CloudSnapshotConflictKind.CONFLICT, state.kind)
        assertTrue(state.requiresUserChoice)
    }

    @Test
    fun localOnlyAndTargetOnlyCanBeFastForwarded() {
        val localOnly = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-local",
            targetRevision = "rev-base",
        )
        val targetOnly = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-base",
            targetRevision = "rev-cloud",
        )

        assertEquals(CloudSnapshotConflictKind.LOCAL_ONLY, localOnly.kind)
        assertFalse(localOnly.requiresUserChoice)
        assertEquals(CloudSnapshotConflictKind.TARGET_ONLY, targetOnly.kind)
        assertFalse(targetOnly.requiresUserChoice)
    }

    @Test
    fun supabaseAndDriveDivergenceRequiresUserChoice() {
        val state = CloudSnapshotPolicy.classifyTargetDivergence(
            supabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64)),
            driveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-drive", "b".repeat(64)),
        )

        assertEquals(CloudSnapshotConflictKind.TARGETS_DIVERGED, state.kind)
        assertTrue(state.requiresUserChoice)
    }

    @Test
    fun restorePlanAlwaysVerifiesHashBeforeTransactionalCommit() {
        val manifest = CloudSnapshotPolicy.createManifest(
            snapshotId = "snapshot-1",
            revision = "rev-restore",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            entries = listOf(entry(CloudSnapshotDataset.SETTINGS)),
        )
        val restorePlan = CloudSnapshotPolicy.restorePlan(manifest)

        assertTrue(restorePlan.verifyBeforeCommit)
        assertTrue(restorePlan.transactional)
        assertEquals(manifest.excludedDatasets, restorePlan.excludedDatasets)
    }

    @Test
    fun automaticResolutionIsOnlyAllowedForFastForwardStates() {
        val localOnly = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-local",
            targetRevision = "rev-base",
        )
        val plan = CloudSnapshotPolicy.automaticResolutionPlan(localOnly)

        assertTrue(plan.uploadLocalSnapshot)
        assertFalse(plan.restoreTargetSnapshot)
        assertTrue(plan.requiresNewRevision)
        assertFalse(plan.userChoiceRequired)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bothChangedCannotAutoResolve() {
        val conflict = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-local",
            targetRevision = "rev-cloud",
        )

        CloudSnapshotPolicy.automaticResolutionPlan(conflict)
    }

    @Test
    fun explicitConflictChoiceCanRestoreTargetOrKeepLocal() {
        val conflict = CloudSnapshotPolicy.classifyRevisionState(
            baseRevision = "rev-base",
            localRevision = "rev-local",
            targetRevision = "rev-cloud",
        )

        val restore = CloudSnapshotPolicy.userResolutionPlan(
            state = conflict,
            choice = CloudSnapshotConflictChoice.RESTORE_TARGET,
        )
        val keepLocal = CloudSnapshotPolicy.userResolutionPlan(
            state = conflict,
            choice = CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION,
        )

        assertTrue(restore.restoreTargetSnapshot)
        assertFalse(restore.uploadLocalSnapshot)
        assertTrue(keepLocal.uploadLocalSnapshot)
        assertTrue(keepLocal.requiresNewRevision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun divergedTargetsRequireSelectedRestoreTarget() {
        val diverged = CloudSnapshotPolicy.classifyTargetDivergence(
            supabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64)),
            driveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-drive", "b".repeat(64)),
        )

        CloudSnapshotPolicy.userResolutionPlan(
            state = diverged,
            choice = CloudSnapshotConflictChoice.RESTORE_TARGET,
        )
    }

    @Test
    fun divergedTargetsCanSaveLocalAsSeparateCloudCopy() {
        val diverged = CloudSnapshotPolicy.classifyTargetDivergence(
            supabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64)),
            driveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-drive", "b".repeat(64)),
        )

        val plan = CloudSnapshotPolicy.userResolutionPlan(
            state = diverged,
            choice = CloudSnapshotConflictChoice.SAVE_LOCAL_AS_CLOUD_COPY,
        )

        assertTrue(plan.saveLocalAsCloudCopy)
        assertFalse(plan.restoreTargetSnapshot)
        assertTrue(plan.requiresNewRevision)
    }

    @Test
    fun bothTargetHeadWritesCreateIndependentCompareAndSetPlans() {
        val plans = CloudSnapshotPolicy.headWritePlans(
            targetMode = CloudSyncTarget.BOTH,
            observedSupabaseHead = head(CloudSyncTarget.SUPABASE, "rev-base", "a".repeat(64)),
            observedDriveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-base", "a".repeat(64)),
            nextRevision = "rev-next",
            nextSnapshotId = "snapshot-next",
            nextContentSha256 = "c".repeat(64),
            updatedAtEpochMillis = 2L,
        )

        assertEquals(listOf(CloudSyncTarget.SUPABASE, CloudSyncTarget.GOOGLE_DRIVE), plans.map { it.target })
        assertTrue(plans.all { it.expectedRevision == "rev-base" })
        assertTrue(plans.all { it.nextRevision == "rev-next" })
    }

    @Test
    fun headCompareAndSetRejectsConcurrentTargetChange() {
        val observed = head(CloudSyncTarget.SUPABASE, "rev-base", "a".repeat(64))
        val plan = CloudSnapshotPolicy.headWritePlans(
            targetMode = CloudSyncTarget.SUPABASE,
            observedSupabaseHead = observed,
            observedDriveHead = null,
            nextRevision = "rev-next",
            nextSnapshotId = "snapshot-next",
            nextContentSha256 = "c".repeat(64),
            updatedAtEpochMillis = 2L,
        ).single()

        assertTrue(CloudSnapshotPolicy.verifyHeadCompareAndSet(plan, observed).isSuccess)
        assertTrue(
            CloudSnapshotPolicy.verifyHeadCompareAndSet(
                plan,
                head(CloudSyncTarget.SUPABASE, "rev-other", "b".repeat(64)),
            ).isFailure
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun headWritePlanRejectsInvalidObservedTarget() {
        CloudSnapshotPolicy.headWritePlans(
            targetMode = CloudSyncTarget.SUPABASE,
            observedSupabaseHead = head(CloudSyncTarget.BOTH, "rev-base", "a".repeat(64)),
            observedDriveHead = null,
            nextRevision = "rev-next",
            nextSnapshotId = "snapshot-next",
            nextContentSha256 = "c".repeat(64),
            updatedAtEpochMillis = 2L,
        )
    }

    private fun entry(dataset: CloudSnapshotDataset) = CloudSnapshotEntry(
        dataset = dataset,
        objectPath = "entries/${dataset.storageKey}.json",
        sha256 = "a".repeat(64),
        sizeBytes = 10L,
        recordCount = 1,
    )

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
