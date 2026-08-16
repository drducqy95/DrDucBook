package io.legado.app.data.repository

import io.legado.app.domain.model.AccountPermission
import io.legado.app.domain.model.AccountRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SupabaseAccountAccessCompatibilityTest {

    @Test
    fun detectsMissingTimedRoleColumnsFromPostgresAndPostgrestErrors() {
        assertTrue(
            isMissingTimedAccountAccessColumns(
                "42703: column account_access.role_expires_at does not exist"
            )
        )
        assertTrue(
            isMissingTimedAccountAccessColumns(
                "PGRST204: Could not find the role_starts_at column"
            )
        )
    }

    @Test
    fun doesNotTreatUnrelatedBackendErrorsAsLegacySchema() {
        assertFalse(isMissingTimedAccountAccessColumns("PGRST301: invalid JWT"))
        assertFalse(isMissingTimedAccountAccessColumns("network timeout"))
    }

    @Test
    fun permanentRoleUpdateFallsBackToLegacyRpcSignature() = runBlocking {
        val rest = LegacyAccountAccessRestClient()
        val repository = SupabaseAccountAccessRepository(rest)

        val updated = repository.updateAccess(
            userId = "user-1",
            role = AccountRole.PREMIUM,
            permissions = AccountRole.PREMIUM.defaultPermissions,
            roleStartsAtEpochMillis = null,
            roleExpiresAtEpochMillis = null,
        )

        assertEquals(AccountRole.PREMIUM, updated.role)
        assertEquals(2, rest.postBodies.size)
        assertTrue(rest.postBodies.first().contains("p_role_starts_at"))
        assertFalse(rest.postBodies.last().contains("p_role_starts_at"))
        assertFalse(rest.postBodies.last().contains("p_role_expires_at"))
    }

    @Test
    fun timedRoleUpdateDoesNotSilentlyDropItsExpiry() = runBlocking {
        val rest = LegacyAccountAccessRestClient()
        val repository = SupabaseAccountAccessRepository(rest)

        val error = runCatching {
            repository.updateAccess(
                userId = "user-1",
                role = AccountRole.PREMIUM,
                permissions = setOf(AccountPermission.WEB_SERVICE),
                roleStartsAtEpochMillis = 1_000L,
                roleExpiresAtEpochMillis = 2_000L,
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(1, rest.postBodies.size)
    }

    private class LegacyAccountAccessRestClient : AccountAccessRestClient {
        override val configured: Boolean = true
        val postBodies = mutableListOf<String>()

        override suspend fun get(path: String, query: Map<String, String>): String = "[]"

        override suspend fun post(
            path: String,
            body: String,
            query: Map<String, String>,
            prefer: String?,
        ): String {
            postBodies += body
            if (body.contains("p_role_starts_at")) {
                throw IOException(
                    "Supabase HTTP 404: PGRST202 Could not find the function " +
                        "public.admin_update_account_access(p_permissions, p_role, " +
                        "p_role_expires_at, p_role_starts_at, p_user_id)"
                )
            }
            return """[{"user_id":"user-1","email":"new@example.com","role":"premium","permissions":["cloud_backup","web_service"],"updated_at":"2026-08-12T00:00:00Z"}]"""
        }
    }
}
