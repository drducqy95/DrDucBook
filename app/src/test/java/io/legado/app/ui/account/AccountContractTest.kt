package io.legado.app.ui.account

import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountContractTest {

    @Test
    fun permanentRoleMapsToExplicitPermanentSelection() {
        val ui = AccountAccess(
            userId = "admin",
            email = "admin@example.com",
            role = AccountRole.ADMIN,
            permissions = AccountRole.ADMIN.defaultPermissions,
            roleStartsAtEpochMillis = null,
            roleExpiresAtEpochMillis = null,
        ).toAdminUi()

        assertTrue(ui.selectedPermanent)
        assertTrue(ui.selectedDurationDays.isBlank())
    }

    @Test
    fun expiringRoleMapsToTimedSelection() {
        val ui = AccountAccess(
            userId = "premium",
            email = "premium@example.com",
            role = AccountRole.PREMIUM,
            permissions = AccountRole.PREMIUM.defaultPermissions,
            roleStartsAtEpochMillis = System.currentTimeMillis(),
            roleExpiresAtEpochMillis = System.currentTimeMillis() + 86_400_000L,
        ).toAdminUi()

        assertFalse(ui.selectedPermanent)
        assertTrue(ui.selectedDurationDays.isNotBlank())
    }

    @Test
    fun adminSearchAndRoleFilterWorkTogether() {
        val accounts = listOf(
            AccountAdminUi("admin-id", "owner@example.com", AccountRole.ADMIN),
            AccountAdminUi("premium-id", "reader@example.com", AccountRole.PREMIUM),
            AccountAdminUi("free-id", "free@example.com", AccountRole.FREE),
        )

        assertEquals(
            listOf("premium-id"),
            filterAdminAccounts(accounts, "READER@", AccountRole.PREMIUM)
                .map(AccountAdminUi::userId),
        )
        assertEquals(
            listOf("admin-id"),
            filterAdminAccounts(accounts, "admin-id", null)
                .map(AccountAdminUi::userId),
        )
        assertTrue(filterAdminAccounts(accounts, "missing", null).isEmpty())
    }
}
