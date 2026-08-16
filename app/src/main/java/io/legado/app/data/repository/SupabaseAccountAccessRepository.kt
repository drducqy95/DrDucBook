package io.legado.app.data.repository

import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.domain.gateway.AccountAccessGateway
import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountPermission
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.AccountQuotaUsage
import io.legado.app.domain.model.AccountRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

class SupabaseAccountAccessRepository : AccountAccessGateway {

    private val rest: AccountAccessRestClient
    private val json: Json

    constructor(
        config: SupabasePublicConfig,
        accountAuthGateway: AccountAuthGateway,
        json: Json = Json { ignoreUnknownKeys = true },
    ) {
        rest = SupabaseAuthenticatedRestClient(config, accountAuthGateway)
        this.json = json
    }

    internal constructor(
        rest: AccountAccessRestClient,
        json: Json = Json { ignoreUnknownKeys = true },
    ) {
        this.rest = rest
        this.json = json
    }

    override val configured: Boolean
        get() = rest.configured

    override suspend fun getAccess(userId: String): AccountAccess {
        return runCatching {
            val response = getAccountRows(
                columns = ACCESS_COLUMNS,
                userId = userId,
            )
            parseAccountRows(response).firstOrNull()
                ?: AccountAccess.defaultFor(userId)
        }.getOrElse { error ->
            when {
                error.isMissingTimedRoleColumns() -> {
                    val response = getAccountRows(
                        columns = LEGACY_ACCESS_COLUMNS,
                        userId = userId,
                    )
                    parseAccountRows(response).firstOrNull()
                        ?: AccountAccess.defaultFor(userId)
                }
                error.isMissingAccountAccessTable() -> {
                    // Keep the account screen usable while an older Supabase project is being migrated.
                    // The fallback is deliberately Free and never grants admin permissions.
                    AccountAccess.defaultFor(userId)
                }
                else -> throw error
            }
        }
    }

    override suspend fun listAccounts(): List<AccountAccess> {
        return runCatching {
            val response = rest.get(
                path = "rest/v1/account_access",
                query = mapOf(
                    "select" to ACCESS_COLUMNS,
                    "order" to "email.asc,user_id.asc",
                    "limit" to "500",
                ),
            )
            parseAccountRows(response)
        }.getOrElse { error ->
            when {
                error.isMissingTimedRoleColumns() -> {
                    val response = rest.get(
                        path = "rest/v1/account_access",
                        query = mapOf(
                            "select" to LEGACY_ACCESS_COLUMNS,
                            "order" to "email.asc,user_id.asc",
                            "limit" to "500",
                        ),
                    )
                    parseAccountRows(response)
                }
                error.isMissingAccountAccessTable() -> emptyList()
                else -> throw error
            }
        }
    }

    override suspend fun updateAccess(
        userId: String,
        role: AccountRole,
        permissions: Set<AccountPermission>,
        roleStartsAtEpochMillis: Long?,
        roleExpiresAtEpochMillis: Long?,
    ): AccountAccess {
        val body = buildAccountAccessUpdateBody(
            userId = userId,
            role = role,
            permissions = permissions,
            roleStartsAtEpochMillis = roleStartsAtEpochMillis,
            roleExpiresAtEpochMillis = roleExpiresAtEpochMillis,
            includeRoleWindow = true,
        )
        val response = runCatching {
            rest.post(
                path = "rest/v1/rpc/admin_update_account_access",
                body = body,
            )
        }.getOrElse { error ->
            val isPermanentRole = roleStartsAtEpochMillis == null && roleExpiresAtEpochMillis == null
            if (!isPermanentRole || !isMissingTimedAccountAccessUpdateRpc(error.message.orEmpty())) {
                throw error
            }
            rest.post(
                path = "rest/v1/rpc/admin_update_account_access",
                body = buildAccountAccessUpdateBody(
                    userId = userId,
                    role = role,
                    permissions = permissions,
                    roleStartsAtEpochMillis = null,
                    roleExpiresAtEpochMillis = null,
                    includeRoleWindow = false,
                ),
            )
        }
        return parseSingleAccount(response)
    }

    private fun buildAccountAccessUpdateBody(
        userId: String,
        role: AccountRole,
        permissions: Set<AccountPermission>,
        roleStartsAtEpochMillis: Long?,
        roleExpiresAtEpochMillis: Long?,
        includeRoleWindow: Boolean,
    ): String = buildJsonObject {
        put("p_user_id", userId)
        put("p_role", role.storageValue)
        put("p_permissions", buildJsonArray {
            permissions.sortedBy(AccountPermission::storageValue).forEach { permission ->
                add(JsonPrimitive(permission.storageValue))
            }
        })
        if (includeRoleWindow) {
            if (roleStartsAtEpochMillis == null) {
                put("p_role_starts_at", JsonNull)
            } else {
                put("p_role_starts_at", Instant.ofEpochMilli(roleStartsAtEpochMillis).toString())
            }
            if (roleExpiresAtEpochMillis == null) {
                put("p_role_expires_at", JsonNull)
            } else {
                put("p_role_expires_at", Instant.ofEpochMilli(roleExpiresAtEpochMillis).toString())
            }
        }
    }.toString()

    override suspend fun getDailyQuotaUsage(): List<AccountQuotaUsage> {
        return runCatching {
            val response = rest.post(
                path = "rest/v1/rpc/get_account_daily_quota_usage",
                body = "{}",
            )
            parseQuotaRows(response)
        }.getOrElse { error ->
            if (error.isMissingAccountAccessTable() || error.isMissingQuotaFunction()) {
                emptyList()
            } else {
                throw error
            }
        }
    }

    override suspend fun consumeDailyQuota(
        kind: AccountQuotaKind,
        operationKeys: Set<String>,
    ): AccountQuotaUsage {
        val body = buildJsonObject {
            put("p_quota_kind", kind.storageValue)
            put("p_operation_keys", buildJsonArray {
                operationKeys.sorted().forEach { operationKey ->
                    add(JsonPrimitive(operationKey))
                }
            })
        }.toString()
        val response = rest.post(
            path = "rest/v1/rpc/consume_account_daily_quota",
            body = body,
        )
        return parseQuotaElement(json.parseToJsonElement(response), kind)
    }

    private fun parseAccountRows(raw: String): List<AccountAccess> {
        val root = json.parseToJsonElement(raw)
        return when (root) {
            is JsonArray -> root.map(JsonElement::jsonObject).map(::parseAccount)
            is JsonObject -> listOf(parseAccount(root))
            else -> emptyList()
        }
    }

    private fun parseSingleAccount(raw: String): AccountAccess =
        parseAccountRows(raw).firstOrNull()
            ?: error("Supabase không trả về quyền tài khoản đã cập nhật")

    private fun parseAccount(row: JsonObject): AccountAccess {
        val role = AccountRole.fromStorageValue(row.string("role"))
        val permissions = row["permissions"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .mapNotNull(AccountPermission::fromStorageValue)
            .toSet()
            .ifEmpty { role.defaultPermissions }
        return AccountAccess(
            userId = requireNotNull(row.string("user_id")) { "Thiếu user_id" },
            email = row.string("email").orEmpty(),
            role = role,
            permissions = permissions,
            roleStartsAtEpochMillis = row.instantMillis("role_starts_at"),
            roleExpiresAtEpochMillis = row.instantMillis("role_expires_at"),
            updatedAt = row.string("updated_at"),
        )
    }

    private fun parseQuotaRows(raw: String): List<AccountQuotaUsage> {
        val root = json.parseToJsonElement(raw)
        val rows = when (root) {
            is JsonArray -> root
            is JsonObject -> JsonArray(listOf(root))
            else -> JsonArray(emptyList())
        }
        return rows.mapNotNull { element ->
            val kind = element.jsonObject.string("kind")
                ?.let(AccountQuotaKind::fromStorageValue)
                ?: return@mapNotNull null
            parseQuotaElement(element, kind)
        }
    }

    private fun parseQuotaElement(
        element: JsonElement,
        fallbackKind: AccountQuotaKind,
    ): AccountQuotaUsage {
        val row = element.jsonObject
        val limitElement = row["limit"]
        return AccountQuotaUsage(
            kind = row.string("kind")
                ?.let(AccountQuotaKind::fromStorageValue)
                ?: fallbackKind,
            used = row["used"]?.jsonPrimitive?.intOrNull ?: 0,
            limit = if (limitElement == null || limitElement is JsonNull) {
                null
            } else {
                limitElement.jsonPrimitive.intOrNull
            },
        )
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.instantMillis(name: String): Long? =
        string(name)?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }

    private suspend fun getAccountRows(columns: String, userId: String): String = rest.get(
        path = "rest/v1/account_access",
        query = mapOf(
            "user_id" to "eq.$userId",
            "select" to columns,
            "limit" to "1",
        ),
    )

    private fun Throwable.isMissingAccountAccessTable(): Boolean =
        message.orEmpty().contains("PGRST205") &&
            message.orEmpty().contains("account_access", ignoreCase = true)

    private fun Throwable.isMissingQuotaFunction(): Boolean =
        message.orEmpty().contains("get_account_daily_quota_usage", ignoreCase = true) &&
            (message.orEmpty().contains("PGRST202") || message.orEmpty().contains("PGRST205"))

    private fun Throwable.isMissingTimedRoleColumns(): Boolean {
        return isMissingTimedAccountAccessColumns(message.orEmpty())
    }

    private companion object {
        const val ACCESS_COLUMNS =
            "user_id,email,role,permissions,role_starts_at,role_expires_at,updated_at"
        const val LEGACY_ACCESS_COLUMNS = "user_id,email,role,permissions,updated_at"
    }
}

internal fun isMissingTimedAccountAccessColumns(raw: String): Boolean =
    (
        raw.contains("role_starts_at", ignoreCase = true) ||
            raw.contains("role_expires_at", ignoreCase = true)
        ) && (
            raw.contains("42703") ||
                raw.contains("PGRST", ignoreCase = true) ||
                raw.contains("does not exist", ignoreCase = true) ||
                raw.contains("could not find", ignoreCase = true)
        )

internal fun isMissingTimedAccountAccessUpdateRpc(raw: String): Boolean =
    raw.contains("admin_update_account_access", ignoreCase = true) &&
        raw.contains("PGRST202", ignoreCase = true) &&
        (
            raw.contains("p_role_starts_at", ignoreCase = true) ||
                raw.contains("p_role_expires_at", ignoreCase = true)
            )
