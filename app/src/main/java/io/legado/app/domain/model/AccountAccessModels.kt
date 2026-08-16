package io.legado.app.domain.model

enum class AccountRole(
    val storageValue: String,
) {
    FREE("free"),
    PREMIUM("premium"),
    ADMIN("admin");

    val defaultPermissions: Set<AccountPermission>
        get() = when (this) {
            FREE -> setOf(
                AccountPermission.CLOUD_BACKUP,
                AccountPermission.DOWNLOAD_CONTENT,
                AccountPermission.EXPORT_EBOOK,
                AccountPermission.AUTHORING_CHAPTER,
                AccountPermission.EDIT_EBOOK_CHAPTER,
            )
            PREMIUM -> setOf(
                AccountPermission.CLOUD_BACKUP,
                AccountPermission.DOWNLOAD_CONTENT,
                AccountPermission.EXPORT_EBOOK,
                AccountPermission.AUTHORING_CHAPTER,
                AccountPermission.EDIT_EBOOK_CHAPTER,
                AccountPermission.WEB_SERVICE,
            )
            ADMIN -> AccountPermission.entries.toSet()
        }

    companion object {
        fun fromStorageValue(value: String?): AccountRole =
            entries.firstOrNull { it.storageValue == value } ?: FREE
    }
}

enum class AccountPermission(
    val storageValue: String,
) {
    CLOUD_BACKUP("cloud_backup"),
    DOWNLOAD_CONTENT("download_content"),
    EXPORT_EBOOK("export_ebook"),
    AUTHORING_CHAPTER("authoring_chapter"),
    EDIT_EBOOK_CHAPTER("edit_ebook_chapter"),
    WEB_SERVICE("web_service"),
    MANAGE_ACCOUNTS("manage_accounts");

    companion object {
        fun fromStorageValue(value: String): AccountPermission? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class AccountAccess(
    val userId: String,
    val email: String,
    val role: AccountRole,
    val permissions: Set<AccountPermission>,
    val roleStartsAtEpochMillis: Long? = null,
    val roleExpiresAtEpochMillis: Long? = null,
    val updatedAt: String? = null,
) {
    fun effectiveRole(nowEpochMillis: Long = System.currentTimeMillis()): AccountRole {
        val hasStarted = roleStartsAtEpochMillis?.let { nowEpochMillis >= it } ?: true
        val hasNotExpired = roleExpiresAtEpochMillis?.let { nowEpochMillis < it } ?: true
        return if (hasStarted && hasNotExpired) role else AccountRole.FREE
    }

    fun allows(
        permission: AccountPermission,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = permission in effectiveRole(nowEpochMillis).defaultPermissions

    val featureLimits: AccountFeatureLimits
        get() = AccountFeatureLimits.forRole(effectiveRole())

    companion object {
        fun defaultFor(userId: String, email: String = ""): AccountAccess = AccountAccess(
            userId = userId,
            email = email,
            role = AccountRole.FREE,
            permissions = AccountRole.FREE.defaultPermissions,
        )

        fun anonymous(): AccountAccess = defaultFor(userId = ANONYMOUS_USER_ID)

        const val ANONYMOUS_USER_ID = "anonymous"
    }
}

data class AccountFeatureLimits(
    val maxActiveTtsModels: Int?,
    val maxInstalledLocalTtsModels: Int?,
) {
    companion object {
        fun forRole(role: AccountRole): AccountFeatureLimits = when (role) {
            AccountRole.FREE -> AccountFeatureLimits(
                maxActiveTtsModels = 3,
                maxInstalledLocalTtsModels = 1,
            )
            AccountRole.PREMIUM,
            AccountRole.ADMIN -> AccountFeatureLimits(
                maxActiveTtsModels = null,
                maxInstalledLocalTtsModels = null,
            )
        }
    }
}

enum class AccountQuotaKind(
    val storageValue: String,
    val freeDailyLimit: Int,
) {
    DOWNLOAD_CONTENT("download_content", 5),
    EXPORT_EBOOK("export_ebook", 1),
    AUTHORING_CHAPTER("authoring_chapter", 3),
    EDIT_EBOOK_CHAPTER("edit_ebook_chapter", 3);

    companion object {
        fun fromStorageValue(value: String): AccountQuotaKind? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class AccountQuotaUsage(
    val kind: AccountQuotaKind,
    val used: Int,
    val limit: Int?,
) {
    val remaining: Int?
        get() = limit?.let { (it - used).coerceAtLeast(0) }
}

data class CloudBackupReceipt(
    val snapshotId: String,
    val revision: String,
    val sizeBytes: Long,
    val sha256: String,
    val completedAtEpochMillis: Long,
)
