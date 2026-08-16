package io.legado.app.ui.config.backupConfig

import androidx.annotation.StringRes
import com.drducbook.app.R
import io.legado.app.domain.model.CloudSnapshotConflictChoice
import io.legado.app.domain.model.CloudSnapshotConflictKind
import io.legado.app.domain.model.CloudSnapshotResolutionPlan
import io.legado.app.domain.model.CloudSyncTarget
import io.legado.app.domain.usecase.CloudSnapshotConflictOption
import io.legado.app.domain.usecase.CloudSnapshotConflictPrompt
import io.legado.app.domain.usecase.CloudSnapshotConflictResolver

data class CloudSnapshotConflictOptionItem(
    val option: CloudSnapshotConflictOption,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: CloudSnapshotConflictOptionIcon,
)

enum class CloudSnapshotConflictOptionIcon {
    DEVICE,
    CLOUD_RESTORE,
    CLOUD_COPY,
}

object CloudSnapshotConflictUiMapper {

    @StringRes
    fun titleRes(prompt: CloudSnapshotConflictPrompt): Int = when (prompt.state.kind) {
        CloudSnapshotConflictKind.CONFLICT -> R.string.cloud_snapshot_conflict_title
        CloudSnapshotConflictKind.TARGETS_DIVERGED -> R.string.cloud_snapshot_targets_diverged_title
        CloudSnapshotConflictKind.INVALID_TARGET -> R.string.cloud_snapshot_invalid_target_title
        CloudSnapshotConflictKind.NO_CHANGE -> R.string.cloud_snapshot_up_to_date_title
        CloudSnapshotConflictKind.LOCAL_ONLY -> R.string.cloud_snapshot_local_newer_title
        CloudSnapshotConflictKind.TARGET_ONLY -> R.string.cloud_snapshot_cloud_newer_title
    }

    @StringRes
    fun messageRes(prompt: CloudSnapshotConflictPrompt): Int = when (prompt.state.kind) {
        CloudSnapshotConflictKind.CONFLICT -> R.string.cloud_snapshot_conflict_message
        CloudSnapshotConflictKind.TARGETS_DIVERGED -> R.string.cloud_snapshot_targets_diverged_message
        CloudSnapshotConflictKind.INVALID_TARGET -> R.string.cloud_snapshot_invalid_target_message
        CloudSnapshotConflictKind.NO_CHANGE,
        CloudSnapshotConflictKind.LOCAL_ONLY,
        CloudSnapshotConflictKind.TARGET_ONLY -> R.string.cloud_snapshot_no_manual_choice_message
    }

    fun optionItems(prompt: CloudSnapshotConflictPrompt): List<CloudSnapshotConflictOptionItem> =
        prompt.options.map { option ->
            CloudSnapshotConflictOptionItem(
                option = option,
                titleRes = titleRes(option),
                descriptionRes = descriptionRes(option),
                icon = iconFor(option),
            )
        }

    fun resolutionPlan(
        prompt: CloudSnapshotConflictPrompt,
        item: CloudSnapshotConflictOptionItem,
    ): CloudSnapshotResolutionPlan =
        CloudSnapshotConflictResolver.userPlanFor(prompt.state, item.option)

    @StringRes
    private fun titleRes(option: CloudSnapshotConflictOption): Int = when (option.choice) {
        CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION ->
            R.string.cloud_snapshot_keep_local_title
        CloudSnapshotConflictChoice.SAVE_LOCAL_AS_CLOUD_COPY ->
            R.string.cloud_snapshot_save_local_copy_title
        CloudSnapshotConflictChoice.RESTORE_TARGET -> when (option.selectedTarget) {
            CloudSyncTarget.SUPABASE -> R.string.cloud_snapshot_restore_supabase_title
            CloudSyncTarget.GOOGLE_DRIVE -> R.string.cloud_snapshot_restore_drive_title
            CloudSyncTarget.BOTH,
            null -> R.string.cloud_snapshot_restore_cloud_title
        }
    }

    @StringRes
    private fun descriptionRes(option: CloudSnapshotConflictOption): Int = when (option.choice) {
        CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION ->
            R.string.cloud_snapshot_keep_local_description
        CloudSnapshotConflictChoice.SAVE_LOCAL_AS_CLOUD_COPY ->
            R.string.cloud_snapshot_save_local_copy_description
        CloudSnapshotConflictChoice.RESTORE_TARGET -> when (option.selectedTarget) {
            CloudSyncTarget.SUPABASE -> R.string.cloud_snapshot_restore_supabase_description
            CloudSyncTarget.GOOGLE_DRIVE -> R.string.cloud_snapshot_restore_drive_description
            CloudSyncTarget.BOTH,
            null -> R.string.cloud_snapshot_restore_cloud_description
        }
    }

    private fun iconFor(option: CloudSnapshotConflictOption): CloudSnapshotConflictOptionIcon =
        when (option.choice) {
            CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION ->
                CloudSnapshotConflictOptionIcon.DEVICE
            CloudSnapshotConflictChoice.RESTORE_TARGET ->
                CloudSnapshotConflictOptionIcon.CLOUD_RESTORE
            CloudSnapshotConflictChoice.SAVE_LOCAL_AS_CLOUD_COPY ->
                CloudSnapshotConflictOptionIcon.CLOUD_COPY
        }
}
