package io.legado.app.ui.config.backupConfig

import com.drducbook.app.R
import io.legado.app.domain.model.CloudSnapshotConflictChoice
import io.legado.app.domain.model.CloudSyncTarget
import io.legado.app.domain.usecase.CloudSnapshotConflictResolver
import io.legado.app.domain.usecase.CloudSnapshotPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSnapshotConflictUiMapperTest {

    @Test
    fun conflictPromptMapsToThreeUserChoices() {
        val prompt = CloudSnapshotConflictResolver.promptFor(
            state = CloudSnapshotPolicy.classifyRevisionState(
                baseRevision = "rev-base",
                localRevision = "rev-local",
                targetRevision = "rev-cloud",
            ),
            preferredTarget = CloudSyncTarget.SUPABASE,
        )

        val items = CloudSnapshotConflictUiMapper.optionItems(prompt)

        assertEquals(R.string.cloud_snapshot_conflict_title, CloudSnapshotConflictUiMapper.titleRes(prompt))
        assertEquals(
            listOf(
                R.string.cloud_snapshot_keep_local_title,
                R.string.cloud_snapshot_restore_supabase_title,
                R.string.cloud_snapshot_save_local_copy_title,
            ),
            items.map { it.titleRes },
        )
        assertTrue(items.single { it.option.choice == CloudSnapshotConflictChoice.RESTORE_TARGET }
            .option.destructiveRestore)
    }

    @Test
    fun divergedTargetsMapToSeparateRestorePlans() {
        val prompt = CloudSnapshotConflictResolver.promptFor(
            state = CloudSnapshotPolicy.classifyTargetDivergence(
                supabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64)),
                driveHead = head(CloudSyncTarget.GOOGLE_DRIVE, "rev-drive", "b".repeat(64)),
            ),
            preferredTarget = CloudSyncTarget.BOTH,
        )

        val items = CloudSnapshotConflictUiMapper.optionItems(prompt)
        val supabaseItem = items.single { it.titleRes == R.string.cloud_snapshot_restore_supabase_title }
        val driveItem = items.single { it.titleRes == R.string.cloud_snapshot_restore_drive_title }
        val supabasePlan = CloudSnapshotConflictUiMapper.resolutionPlan(prompt, supabaseItem)
        val drivePlan = CloudSnapshotConflictUiMapper.resolutionPlan(prompt, driveItem)

        assertEquals(CloudSyncTarget.SUPABASE, supabasePlan.selectedTarget)
        assertEquals(CloudSyncTarget.GOOGLE_DRIVE, drivePlan.selectedTarget)
        assertTrue(supabasePlan.restoreTargetSnapshot)
        assertTrue(drivePlan.restoreTargetSnapshot)
    }

    @Test
    fun invalidTargetMapperDoesNotExposeRestoreCard() {
        val prompt = CloudSnapshotConflictResolver.promptFor(
            state = CloudSnapshotPolicy.classifyTargetDivergence(
                supabaseHead = head(CloudSyncTarget.SUPABASE, "rev-supabase", "a".repeat(64))
                    .copy(snapshotId = null),
                driveHead = null,
            ),
            preferredTarget = CloudSyncTarget.SUPABASE,
        )

        val items = CloudSnapshotConflictUiMapper.optionItems(prompt)

        assertEquals(R.string.cloud_snapshot_invalid_target_title, CloudSnapshotConflictUiMapper.titleRes(prompt))
        assertFalse(items.any { it.option.choice == CloudSnapshotConflictChoice.RESTORE_TARGET })
        assertTrue(items.any { it.titleRes == R.string.cloud_snapshot_keep_local_title })
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
