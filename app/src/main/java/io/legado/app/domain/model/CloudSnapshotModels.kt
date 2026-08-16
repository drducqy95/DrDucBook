package io.legado.app.domain.model

enum class CloudSnapshotDataset(
    val storageKey: String,
    val includedByDefault: Boolean,
) {
    BOOK_SOURCES("book_sources", true),
    RSS_SOURCES("rss_sources", true),
    READING_PROGRESS("reading_progress", true),
    AUTHORING_PROJECTS("authoring_projects", true),
    AGENT_STATE("agent_state", true),
    MANUAL_BOOKMARKS("manual_bookmarks", true),
    APPEARANCE("appearance", true),
    WEB_SERVICE_POLICY("web_service_policy", true),
    SOURCE_HEALTH_SUMMARY("source_health_summary", true),
    SETTINGS("settings", true),
    COOKIES("cookies", false),
    AUTH_SESSIONS("auth_sessions", false),
    CACHE("cache", false),
    MODEL_PACKAGES("model_packages", false),
    MEDIA_DOWNLOADS("media_downloads", false);

    companion object {
        fun included(): Set<CloudSnapshotDataset> =
            entries.filterTo(linkedSetOf(), CloudSnapshotDataset::includedByDefault)

        fun excluded(): Set<CloudSnapshotDataset> =
            entries.filterTo(linkedSetOf()) { !it.includedByDefault }
    }
}

data class CloudSnapshotEntry(
    val dataset: CloudSnapshotDataset,
    val objectPath: String,
    val sha256: String,
    val sizeBytes: Long,
    val recordCount: Int,
)

data class CloudSnapshotManifest(
    val schemaVersion: Int,
    val snapshotId: String,
    val revision: String,
    val deviceId: String,
    val createdAtEpochMillis: Long,
    val entries: List<CloudSnapshotEntry>,
    val excludedDatasets: Set<CloudSnapshotDataset>,
)

data class CloudSnapshotHead(
    val target: CloudSyncTarget,
    val revision: String?,
    val snapshotId: String?,
    val contentSha256: String?,
    val updatedAtEpochMillis: Long,
)

enum class CloudSnapshotConflictKind {
    NO_CHANGE,
    LOCAL_ONLY,
    TARGET_ONLY,
    CONFLICT,
    TARGETS_DIVERGED,
    INVALID_TARGET,
}

data class CloudSnapshotConflictState(
    val kind: CloudSnapshotConflictKind,
    val baseRevision: String?,
    val localRevision: String?,
    val targetRevision: String?,
    val requiresUserChoice: Boolean,
)

data class CloudSnapshotRestorePlan(
    val manifest: CloudSnapshotManifest,
    val verifyBeforeCommit: Boolean,
    val transactional: Boolean,
    val excludedDatasets: Set<CloudSnapshotDataset>,
)

enum class CloudSnapshotConflictChoice {
    KEEP_LOCAL_AS_NEW_REVISION,
    RESTORE_TARGET,
    SAVE_LOCAL_AS_CLOUD_COPY,
}

data class CloudSnapshotResolutionPlan(
    val conflictState: CloudSnapshotConflictState,
    val choice: CloudSnapshotConflictChoice?,
    val selectedTarget: CloudSyncTarget?,
    val uploadLocalSnapshot: Boolean,
    val restoreTargetSnapshot: Boolean,
    val saveLocalAsCloudCopy: Boolean,
    val requiresNewRevision: Boolean,
    val userChoiceRequired: Boolean,
)

data class CloudSnapshotHeadWritePlan(
    val target: CloudSyncTarget,
    val expectedRevision: String?,
    val expectedSnapshotId: String?,
    val expectedContentSha256: String?,
    val nextRevision: String,
    val nextSnapshotId: String,
    val nextContentSha256: String,
    val updatedAtEpochMillis: Long,
)
