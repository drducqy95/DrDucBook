package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotConflictChoice
import io.legado.app.domain.model.CloudSnapshotConflictKind
import io.legado.app.domain.model.CloudSnapshotConflictState
import io.legado.app.domain.model.CloudSnapshotResolutionPlan
import io.legado.app.domain.model.CloudSyncTarget

data class CloudSnapshotConflictPrompt(
    val state: CloudSnapshotConflictState,
    val title: String,
    val message: String,
    val options: List<CloudSnapshotConflictOption>,
    val canAutoResolve: Boolean,
)

data class CloudSnapshotConflictOption(
    val choice: CloudSnapshotConflictChoice,
    val selectedTarget: CloudSyncTarget? = null,
    val title: String,
    val description: String,
    val destructiveRestore: Boolean = false,
)

object CloudSnapshotConflictResolver {

    fun promptFor(
        state: CloudSnapshotConflictState,
        preferredTarget: CloudSyncTarget,
    ): CloudSnapshotConflictPrompt {
        if (!state.requiresUserChoice) {
            return CloudSnapshotConflictPrompt(
                state = state,
                title = titleFor(state.kind),
                message = "No manual choice is required.",
                options = emptyList(),
                canAutoResolve = true,
            )
        }
        val restoreOptions = restoreOptionsFor(state.kind, preferredTarget)
        return CloudSnapshotConflictPrompt(
            state = state,
            title = titleFor(state.kind),
            message = messageFor(state),
            options = buildList {
                add(
                    CloudSnapshotConflictOption(
                        choice = CloudSnapshotConflictChoice.KEEP_LOCAL_AS_NEW_REVISION,
                        title = "Keep this device",
                        description = "Upload local data as a new cloud revision after compare-and-set.",
                    )
                )
                addAll(restoreOptions)
                add(
                    CloudSnapshotConflictOption(
                        choice = CloudSnapshotConflictChoice.SAVE_LOCAL_AS_CLOUD_COPY,
                        title = "Save local copy separately",
                        description = "Keep the current cloud head and save local data as a separate cloud copy.",
                    )
                )
            },
            canAutoResolve = false,
        )
    }

    fun automaticPlanFor(state: CloudSnapshotConflictState): CloudSnapshotResolutionPlan =
        CloudSnapshotPolicy.automaticResolutionPlan(state)

    fun userPlanFor(
        state: CloudSnapshotConflictState,
        option: CloudSnapshotConflictOption,
    ): CloudSnapshotResolutionPlan =
        CloudSnapshotPolicy.userResolutionPlan(
            state = state,
            choice = option.choice,
            selectedTarget = option.selectedTarget,
        )

    private fun restoreOptionsFor(
        kind: CloudSnapshotConflictKind,
        preferredTarget: CloudSyncTarget,
    ): List<CloudSnapshotConflictOption> = when (kind) {
        CloudSnapshotConflictKind.TARGETS_DIVERGED -> listOf(
            restoreOption(CloudSyncTarget.SUPABASE),
            restoreOption(CloudSyncTarget.GOOGLE_DRIVE),
        )
        CloudSnapshotConflictKind.INVALID_TARGET -> emptyList()
        CloudSnapshotConflictKind.CONFLICT -> listOf(
            CloudSnapshotConflictOption(
                choice = CloudSnapshotConflictChoice.RESTORE_TARGET,
                selectedTarget = preferredTarget.takeIf { it != CloudSyncTarget.BOTH },
                title = "Restore cloud version",
                description = "Replace local data with the selected cloud snapshot after hash verification.",
                destructiveRestore = true,
            )
        )
        CloudSnapshotConflictKind.NO_CHANGE,
        CloudSnapshotConflictKind.LOCAL_ONLY,
        CloudSnapshotConflictKind.TARGET_ONLY -> emptyList()
    }

    private fun restoreOption(target: CloudSyncTarget): CloudSnapshotConflictOption =
        CloudSnapshotConflictOption(
            choice = CloudSnapshotConflictChoice.RESTORE_TARGET,
            selectedTarget = target,
            title = "Restore ${target.displayName}",
            description = "Use the ${target.displayName} snapshot and leave the other target unchanged.",
            destructiveRestore = true,
        )

    private fun titleFor(kind: CloudSnapshotConflictKind): String = when (kind) {
        CloudSnapshotConflictKind.NO_CHANGE -> "Cloud backup is up to date"
        CloudSnapshotConflictKind.LOCAL_ONLY -> "Local backup is newer"
        CloudSnapshotConflictKind.TARGET_ONLY -> "Cloud backup is newer"
        CloudSnapshotConflictKind.CONFLICT -> "Backup conflict"
        CloudSnapshotConflictKind.TARGETS_DIVERGED -> "Cloud targets differ"
        CloudSnapshotConflictKind.INVALID_TARGET -> "Cloud backup is incomplete"
    }

    private fun messageFor(state: CloudSnapshotConflictState): String = when (state.kind) {
        CloudSnapshotConflictKind.CONFLICT ->
            "Both this device and the cloud changed since the last synced revision."
        CloudSnapshotConflictKind.TARGETS_DIVERGED ->
            "Supabase and Google Drive point to different snapshot heads. Choose one target explicitly."
        CloudSnapshotConflictKind.INVALID_TARGET ->
            "A cloud target head is incomplete, so it cannot be restored. Keep local data or save a separate copy."
        CloudSnapshotConflictKind.NO_CHANGE,
        CloudSnapshotConflictKind.LOCAL_ONLY,
        CloudSnapshotConflictKind.TARGET_ONLY ->
            "No manual choice is required."
    }

    private val CloudSyncTarget.displayName: String
        get() = when (this) {
            CloudSyncTarget.SUPABASE -> "Supabase"
            CloudSyncTarget.GOOGLE_DRIVE -> "Google Drive"
            CloudSyncTarget.BOTH -> "both targets"
        }
}
