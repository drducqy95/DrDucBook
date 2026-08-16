package io.legado.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAccessTest {

    @Test
    fun freeAccountHasDailyLimitedFeaturesButCannotAdministerAccounts() {
        val access = AccountAccess.defaultFor("user-1")

        assertTrue(access.allows(AccountPermission.CLOUD_BACKUP))
        assertTrue(access.allows(AccountPermission.DOWNLOAD_CONTENT))
        assertTrue(access.allows(AccountPermission.EXPORT_EBOOK))
        assertTrue(access.allows(AccountPermission.AUTHORING_CHAPTER))
        assertTrue(access.allows(AccountPermission.EDIT_EBOOK_CHAPTER))
        assertFalse(access.allows(AccountPermission.WEB_SERVICE))
        assertFalse(access.allows(AccountPermission.MANAGE_ACCOUNTS))
    }

    @Test
    fun adminAlwaysHasEveryKnownPermission() {
        val access = AccountAccess(
            userId = "admin-1",
            email = "admin@example.com",
            role = AccountRole.ADMIN,
            permissions = emptySet(),
        )

        AccountPermission.entries.forEach { permission ->
            assertTrue(access.allows(permission))
        }
    }

    @Test
    fun premiumPresetUnlocksFeaturesButNotAccountAdministration() {
        val permissions = AccountRole.PREMIUM.defaultPermissions

        assertTrue(permissions.contains(AccountPermission.DOWNLOAD_CONTENT))
        assertTrue(permissions.contains(AccountPermission.EXPORT_EBOOK))
        assertTrue(permissions.contains(AccountPermission.AUTHORING_CHAPTER))
        assertTrue(permissions.contains(AccountPermission.EDIT_EBOOK_CHAPTER))
        assertTrue(permissions.contains(AccountPermission.WEB_SERVICE))
        assertFalse(permissions.contains(AccountPermission.MANAGE_ACCOUNTS))
    }

    @Test
    fun premiumRoleKeepsWebServiceWhenLegacyServerPermissionsAreStale() {
        val access = AccountAccess(
            userId = "premium-1",
            email = "premium@example.com",
            role = AccountRole.PREMIUM,
            permissions = emptySet(),
        )

        assertTrue(access.allows(AccountPermission.WEB_SERVICE))
        assertFalse(access.allows(AccountPermission.MANAGE_ACCOUNTS))
    }

    @Test
    fun expiredPremiumAccountFallsBackToFreePermissions() {
        val access = AccountAccess(
            userId = "trial-1",
            email = "trial@example.com",
            role = AccountRole.PREMIUM,
            permissions = AccountRole.PREMIUM.defaultPermissions,
            roleStartsAtEpochMillis = 1_000L,
            roleExpiresAtEpochMillis = 2_000L,
        )

        assertTrue(access.allows(AccountPermission.WEB_SERVICE, nowEpochMillis = 1_999L))
        assertFalse(access.allows(AccountPermission.WEB_SERVICE, nowEpochMillis = 2_000L))
        assertTrue(access.allows(AccountPermission.DOWNLOAD_CONTENT, nowEpochMillis = 2_000L))
    }

    @Test
    fun freeDailyLimitsMatchProductRules() {
        assertTrue(AccountQuotaKind.DOWNLOAD_CONTENT.freeDailyLimit == 5)
        assertTrue(AccountQuotaKind.EXPORT_EBOOK.freeDailyLimit == 1)
        assertTrue(AccountQuotaKind.AUTHORING_CHAPTER.freeDailyLimit == 3)
        assertTrue(AccountQuotaKind.EDIT_EBOOK_CHAPTER.freeDailyLimit == 3)
    }

    @Test
    fun freeTtsLimitsMatchProductRules() {
        val limits = AccountAccess.defaultFor("user-1").featureLimits

        assertTrue(limits.maxActiveTtsModels == 3)
        assertTrue(limits.maxInstalledLocalTtsModels == 1)
        assertTrue(AccountFeatureLimits.forRole(AccountRole.PREMIUM).maxActiveTtsModels == null)
    }
}
