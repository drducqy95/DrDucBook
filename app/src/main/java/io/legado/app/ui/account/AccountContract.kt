package io.legado.app.ui.account

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.AccountSignOutMode
import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountPermission
import io.legado.app.domain.model.AccountQuotaUsage
import io.legado.app.domain.model.AccountRole
import io.legado.app.domain.model.CloudBackupReceipt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Stable
data class AccountUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val configured: Boolean = true,
    val googleSignInAvailable: Boolean = false,
    val googleDriveAvailable: Boolean = false,
    val googleDriveAuthorized: Boolean = false,
    val safBackupConfigured: Boolean = false,
    val safScheduleIntervalHours: Long? = null,
    val session: AccountSessionUi? = null,
    val access: AccountAccessUi? = null,
    val quotaUsage: ImmutableList<AccountQuotaUi> = persistentListOf(),
    val adminAccounts: ImmutableList<AccountAdminUi> = persistentListOf(),
    val adminSearchQuery: String = "",
    val adminRoleFilter: AccountRole? = null,
    val adminSortOption: AccountAdminSort = AccountAdminSort.LAST_SIGN_IN_DESC,
    val adminActivityFilter: AccountAdminActivityFilter = AccountAdminActivityFilter.ALL,
    val editingAccount: AccountAdminUi? = null,
    val restoreConfirmationVisible: Boolean = false,
    val googleDriveRestoreConfirmationVisible: Boolean = false,
    val safRestoreConfirmationVisible: Boolean = false,
    val lastCloudBackup: CloudBackupUi? = null,
    val lastGoogleDriveBackup: CloudBackupUi? = null,
)

enum class AccountAdminSort(val label: String) {
    LAST_SIGN_IN_DESC("Đăng nhập mới nhất"),
    LAST_SIGN_IN_ASC("Đăng nhập cũ nhất"),
    CREATED_AT_DESC("Đăng ký mới nhất"),
    CREATED_AT_ASC("Đăng ký cũ nhất"),
    EMAIL_ASC("Email A-Z"),
}

enum class AccountAdminActivityFilter(val label: String) {
    ALL("Tất cả hoạt động"),
    ACTIVE_30_DAYS("30 ngày gần đây"),
    NEVER_SIGNED_IN("Chưa đăng nhập"),
}

@Stable
data class AccountSessionUi(
    val userId: String,
    val email: String,
    val emailVerified: Boolean,
    val providers: String,
    val expiresAtEpochMillis: Long?,
)

@Stable
data class AccountAccessUi(
    val userId: String,
    val email: String,
    val role: AccountRole,
    val permissions: ImmutableList<AccountPermission>,
    val roleStartsAtEpochMillis: Long? = null,
    val roleExpiresAtEpochMillis: Long? = null,
) {
    val isAdmin: Boolean
        get() = role == AccountRole.ADMIN
}

@Stable
data class AccountQuotaUi(
    val kind: io.legado.app.domain.model.AccountQuotaKind,
    val used: Int,
    val limit: Int?,
)

@Stable
data class AccountAdminUi(
    val userId: String,
    val email: String,
    val role: AccountRole,
    val roleStartsAtEpochMillis: Long? = null,
    val roleExpiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long? = null,
    val lastSignInAtEpochMillis: Long? = null,
    val selectedRole: AccountRole = role,
    val selectedDurationDays: String = "",
    val selectedPermanent: Boolean = role != AccountRole.FREE && roleExpiresAtEpochMillis == null,
)

@Stable
data class CloudBackupUi(
    val revision: String,
    val sizeBytes: Long,
    val completedAtEpochMillis: Long,
)

sealed interface AccountIntent {
    data object Refresh : AccountIntent
    data object RequestGoogleSignIn : AccountIntent
    data object Reauthenticate : AccountIntent
    data object SignOutLocal : AccountIntent
    data object SignOutEverywhere : AccountIntent
    class UploadCloudBackup(val password: String) : AccountIntent {
        override fun toString(): String = "UploadCloudBackup(password=<redacted>)"
    }
    class RequestRestoreCloudBackup(val password: String) : AccountIntent {
        override fun toString(): String = "RequestRestoreCloudBackup(password=<redacted>)"
    }
    data object ConfirmRestoreCloudBackup : AccountIntent
    data object DismissRestoreCloudBackup : AccountIntent
    class UploadGoogleDriveBackup(val password: String) : AccountIntent {
        override fun toString(): String = "UploadGoogleDriveBackup(password=<redacted>)"
    }
    data object RequestSafFolder : AccountIntent
    data class SubmitSafFolder(val uri: String) : AccountIntent
    data object ClearSafFolder : AccountIntent
    class UploadSafBackup(val password: String) : AccountIntent {
        override fun toString(): String = "UploadSafBackup(password=<redacted>)"
    }
    class EnableSafSchedule(val intervalHours: Long, val password: String) : AccountIntent {
        override fun toString(): String =
            "EnableSafSchedule(intervalHours=$intervalHours, password=<redacted>)"
    }
    data object DisableSafSchedule : AccountIntent
    class RequestRestoreSafBackup(val password: String) : AccountIntent {
        override fun toString(): String = "RequestRestoreSafBackup(password=<redacted>)"
    }
    data object ConfirmRestoreSafBackup : AccountIntent
    data object DismissRestoreSafBackup : AccountIntent
    class RequestRestoreGoogleDriveBackup(val password: String) : AccountIntent {
        override fun toString(): String = "RequestRestoreGoogleDriveBackup(password=<redacted>)"
    }
    data object ConfirmRestoreGoogleDriveBackup : AccountIntent
    data object DismissRestoreGoogleDriveBackup : AccountIntent
    class SubmitGoogleDriveAccessToken(
        val accessToken: String,
        val action: GoogleDriveAction,
    ) : AccountIntent {
        override fun toString(): String =
            "SubmitGoogleDriveAccessToken(accessToken=<redacted>, action=$action)"
    }
    data class GoogleDriveAuthorizationFailed(val message: String) : AccountIntent
    data object LoadAdminAccounts : AccountIntent
    data class UpdateAdminSearch(val query: String) : AccountIntent
    data class FilterAdminRole(val role: AccountRole?) : AccountIntent
    data class SelectAdminSort(val sort: AccountAdminSort) : AccountIntent
    data class SelectAdminActivityFilter(val filter: AccountAdminActivityFilter) : AccountIntent
    data class EditAccount(val userId: String) : AccountIntent
    data class SelectAccountRole(val role: AccountRole) : AccountIntent
    data class SetAccountRoleDurationDays(val value: String) : AccountIntent
    data class SetAccountRolePermanent(val permanent: Boolean) : AccountIntent
    data object SaveAccountRole : AccountIntent
    data object DismissAccountEditor : AccountIntent

    class SignInEmail(
        val email: String,
        val password: String,
    ) : AccountIntent {
        override fun toString(): String = "SignInEmail(email=$email, password=<redacted>)"
    }

    class SignUpEmail(
        val email: String,
        val password: String,
    ) : AccountIntent {
        override fun toString(): String = "SignUpEmail(email=$email, password=<redacted>)"
    }

    data class SendPasswordReset(val email: String) : AccountIntent

    class ChangePassword(
        val currentPassword: String,
        val newPassword: String,
    ) : AccountIntent {
        override fun toString(): String =
            "ChangePassword(currentPassword=<redacted>, newPassword=<redacted>)"
    }

    class SubmitGoogleToken(
        val idToken: String,
        val nonce: String,
    ) : AccountIntent {
        override fun toString(): String = "SubmitGoogleToken(idToken=<redacted>, nonce=<redacted>)"
    }
}

sealed interface AccountEffect {
    data class ShowMessage(val message: String) : AccountEffect

    class RequestGoogleCredential(
        val clientId: String,
        val nonce: String,
    ) : AccountEffect {
        override fun toString(): String = "RequestGoogleCredential(clientId=$clientId, nonce=<redacted>)"
    }

    data class RequestGoogleDriveAuthorization(
        val action: GoogleDriveAction,
    ) : AccountEffect
    data object RequestSafFolder : AccountEffect
}

enum class GoogleDriveAction {
    UPLOAD,
    RESTORE,
}

internal fun AccountSession.toUi(): AccountSessionUi = AccountSessionUi(
    userId = userId,
    email = email.orEmpty(),
    emailVerified = emailVerified,
    providers = providerIds
        .ifEmpty { setOf("email") }
        .sorted()
        .joinToString(", "),
    expiresAtEpochMillis = expiresAtEpochMillis,
)

internal fun AccountAccess.toUi(): AccountAccessUi = AccountAccessUi(
    userId = userId,
    email = email,
    role = effectiveRole(),
    permissions = permissions.sortedBy(AccountPermission::storageValue).toImmutableList(),
    roleStartsAtEpochMillis = roleStartsAtEpochMillis,
    roleExpiresAtEpochMillis = roleExpiresAtEpochMillis,
)

internal fun AccountAccess.toAdminUi(): AccountAdminUi = AccountAdminUi(
    userId = userId,
    email = email,
    role = role,
    roleStartsAtEpochMillis = roleStartsAtEpochMillis,
    roleExpiresAtEpochMillis = roleExpiresAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    lastSignInAtEpochMillis = lastSignInAtEpochMillis,
    selectedDurationDays = roleExpiresAtEpochMillis
        ?.let { expiresAt ->
            val remainingMillis = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
            ((remainingMillis + MILLIS_PER_DAY - 1L) / MILLIS_PER_DAY).toString()
        }
        .orEmpty(),
    selectedPermanent = role != AccountRole.FREE && roleExpiresAtEpochMillis == null,
)

internal fun filterAdminAccounts(
    accounts: List<AccountAdminUi>,
    query: String,
    role: AccountRole?,
    activityFilter: AccountAdminActivityFilter = AccountAdminActivityFilter.ALL,
    sortOption: AccountAdminSort = AccountAdminSort.LAST_SIGN_IN_DESC,
): List<AccountAdminUi> {
    val normalizedQuery = query.trim().lowercase()
    val now = System.currentTimeMillis()
    val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1_000L

    val filtered = accounts.filter { account ->
        val matchesRole = role == null || account.role == role
        val matchesQuery = normalizedQuery.isBlank() ||
            account.email.lowercase().contains(normalizedQuery) ||
            account.userId.lowercase().contains(normalizedQuery)
        val matchesActivity = when (activityFilter) {
            AccountAdminActivityFilter.ALL -> true
            AccountAdminActivityFilter.ACTIVE_30_DAYS -> {
                val lastSignIn = account.lastSignInAtEpochMillis
                lastSignIn != null && (now - lastSignIn) <= thirtyDaysMillis
            }
            AccountAdminActivityFilter.NEVER_SIGNED_IN -> {
                account.lastSignInAtEpochMillis == null
            }
        }
        matchesRole && matchesQuery && matchesActivity
    }

    return when (sortOption) {
        AccountAdminSort.LAST_SIGN_IN_DESC -> filtered.sortedWith(
            compareByDescending<AccountAdminUi> { it.lastSignInAtEpochMillis ?: Long.MIN_VALUE }
                .thenByDescending { it.createdAtEpochMillis ?: Long.MIN_VALUE }
        )
        AccountAdminSort.LAST_SIGN_IN_ASC -> filtered.sortedWith(
            compareBy<AccountAdminUi> { it.lastSignInAtEpochMillis ?: Long.MAX_VALUE }
                .thenBy { it.createdAtEpochMillis ?: Long.MAX_VALUE }
        )
        AccountAdminSort.CREATED_AT_DESC -> filtered.sortedWith(
            compareByDescending<AccountAdminUi> { it.createdAtEpochMillis ?: Long.MIN_VALUE }
                .thenByDescending { it.lastSignInAtEpochMillis ?: Long.MIN_VALUE }
        )
        AccountAdminSort.CREATED_AT_ASC -> filtered.sortedWith(
            compareBy<AccountAdminUi> { it.createdAtEpochMillis ?: Long.MAX_VALUE }
                .thenBy { it.lastSignInAtEpochMillis ?: Long.MAX_VALUE }
        )
        AccountAdminSort.EMAIL_ASC -> filtered.sortedBy { it.email.lowercase() }
    }
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

internal fun AccountQuotaUsage.toUi(): AccountQuotaUi = AccountQuotaUi(
    kind = kind,
    used = used,
    limit = limit,
)

internal fun CloudBackupReceipt.toUi(): CloudBackupUi = CloudBackupUi(
    revision = revision,
    sizeBytes = sizeBytes,
    completedAtEpochMillis = completedAtEpochMillis,
)

internal fun AccountIntent.signOutMode(): AccountSignOutMode = when (this) {
    AccountIntent.SignOutEverywhere -> AccountSignOutMode.GLOBAL
    else -> AccountSignOutMode.LOCAL
}
