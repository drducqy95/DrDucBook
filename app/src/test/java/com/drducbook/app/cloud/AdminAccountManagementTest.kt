package com.drducbook.app.cloud

import io.legado.app.domain.model.AccountRole
import io.legado.app.ui.account.AccountAdminActivityFilter
import io.legado.app.ui.account.AccountAdminSort
import io.legado.app.ui.account.AccountAdminUi
import io.legado.app.ui.account.filterAdminAccounts
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminAccountManagementTest {

    private val now = System.currentTimeMillis()
    private val oneDayAgo = now - 86400000L
    private val fortyDaysAgo = now - (40L * 86400000L)

    private val accountA = AccountAdminUi(
        userId = "user-1",
        email = "alice@example.com",
        role = AccountRole.ADMIN,
        createdAtEpochMillis = oneDayAgo,
        lastSignInAtEpochMillis = now
    )

    private val accountB = AccountAdminUi(
        userId = "user-2",
        email = "bob@example.com",
        role = AccountRole.FREE,
        createdAtEpochMillis = fortyDaysAgo,
        lastSignInAtEpochMillis = fortyDaysAgo
    )

    private val accountC = AccountAdminUi(
        userId = "user-3",
        email = "charlie@example.com",
        role = AccountRole.FREE,
        createdAtEpochMillis = now,
        lastSignInAtEpochMillis = null // Never signed in
    )

    private val allAccounts = listOf(accountA, accountB, accountC)

    @Test
    fun filterByActivity_Active30Days() {
        val result = filterAdminAccounts(
            accounts = allAccounts,
            query = "",
            role = null,
            activityFilter = AccountAdminActivityFilter.ACTIVE_30_DAYS,
            sortOption = AccountAdminSort.EMAIL_ASC
        )
        // Only accountA signed in within 30 days
        assertEquals(1, result.size)
        assertEquals("alice@example.com", result[0].email)
    }

    @Test
    fun filterByActivity_NeverSignedIn() {
        val result = filterAdminAccounts(
            accounts = allAccounts,
            query = "",
            role = null,
            activityFilter = AccountAdminActivityFilter.NEVER_SIGNED_IN,
            sortOption = AccountAdminSort.EMAIL_ASC
        )
        // Only accountC has null lastSignInAtEpochMillis
        assertEquals(1, result.size)
        assertEquals("charlie@example.com", result[0].email)
    }

    @Test
    fun sortByLastSignInDesc() {
        val result = filterAdminAccounts(
            accounts = allAccounts,
            query = "",
            role = null,
            activityFilter = AccountAdminActivityFilter.ALL,
            sortOption = AccountAdminSort.LAST_SIGN_IN_DESC
        )
        // Expected order: accountA (now), accountB (40 days ago), accountC (null = MinValue)
        assertEquals(listOf("alice@example.com", "bob@example.com", "charlie@example.com"), result.map { it.email })
    }

    @Test
    fun sortByCreatedAtDesc() {
        val result = filterAdminAccounts(
            accounts = allAccounts,
            query = "",
            role = null,
            activityFilter = AccountAdminActivityFilter.ALL,
            sortOption = AccountAdminSort.CREATED_AT_DESC
        )
        // Expected order: accountC (now), accountA (1 day ago), accountB (40 days ago)
        assertEquals(listOf("charlie@example.com", "alice@example.com", "bob@example.com"), result.map { it.email })
    }

    @Test
    fun filterByRoleAndSortByEmail() {
        val result = filterAdminAccounts(
            accounts = allAccounts,
            query = "",
            role = AccountRole.FREE,
            activityFilter = AccountAdminActivityFilter.ALL,
            sortOption = AccountAdminSort.EMAIL_ASC
        )
        // Expected: bob, charlie
        assertEquals(2, result.size)
        assertEquals("bob@example.com", result[0].email)
        assertEquals("charlie@example.com", result[1].email)
    }
}
