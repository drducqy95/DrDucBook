package io.legado.app.help.storage

/** Controls whether an archive contains only logical app data or downloaded content too. */
enum class BackupContentProfile {
    METADATA,
    FULL,
}

data class BackupManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val profile: BackupContentProfile = BackupContentProfile.FULL,
    val generatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

object BackupArchivePolicy {
    val fullDirectories = setOf(
        "book_cache",
        "epub",
        "media_downloads",
        "covers",
        "bg",
        "font",
        "ruleData",
    )

    fun includesDownloadedContent(profile: BackupContentProfile): Boolean =
        profile == BackupContentProfile.FULL
}
