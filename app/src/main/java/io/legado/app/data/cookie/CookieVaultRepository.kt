package io.legado.app.data.cookie

import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.CookieVaultEntity
import io.legado.app.domain.gateway.SourceCookieGateway
import io.legado.app.utils.NetworkUtils
import okhttp3.Cookie as OkHttpCookie
import java.net.URL
import java.util.concurrent.Callable

class CookieVaultRepository(
    private val appDatabase: AppDatabase,
    private val cookieDao: CookieDao,
    private val codec: CookieVaultCodec,
) : SourceCookieGateway {

    override fun saveResponse(url: String, cookies: List<OkHttpCookie>) {
        val persistentCookies = cookies.filter { it.persistent }
        if (persistentCookies.isEmpty()) {
            return
        }
        val scopeKey = normalizeScopeKey(url)
        val now = System.currentTimeMillis()
        appDatabase.inTransaction {
            persistentCookies.forEach { cookie ->
                upsertEntity(
                    scopeKey = scopeKey,
                    origin = url,
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain.ifBlank { scopeDomain(url) },
                    path = cookie.path.ifBlank { "/" },
                    expiresAt = cookie.expiresAt.takeIf { cookie.persistent },
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    sameSite = null,
                    hostOnly = cookie.hostOnly,
                    persistent = cookie.persistent,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            pruneExpiredLocked(now)
            deleteLegacyRows(url, scopeKey)
        }
    }

    override fun setCookie(url: String, cookie: String?) {
        val scopeKey = normalizeScopeKey(url)
        val cookieMap = CookieHeaderCodec.cookieToMap(cookie.orEmpty())
        if (cookieMap.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        appDatabase.inTransaction {
            deleteVaultByScopeKey(scopeKey)
            cookieMap.forEach { (name, value) ->
                upsertEntity(
                    scopeKey = scopeKey,
                    origin = url,
                    name = name,
                    value = value,
                    domain = scopeDomain(url),
                    path = "/",
                    expiresAt = null,
                    secure = false,
                    httpOnly = false,
                    sameSite = null,
                    hostOnly = false,
                    persistent = false,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            deleteLegacyRows(url, scopeKey)
        }
    }

    override fun replaceCookie(url: String, cookie: String) {
        val merged = CookieHeaderCodec.mergeCookies(getCookie(url), cookie) ?: cookie
        setCookie(url, merged)
    }

    override fun getCookie(url: String): String {
        val scopeKey = normalizeScopeKey(url)
        val now = System.currentTimeMillis()
        val entities = appDatabase.inTransaction {
            cleanupLegacyAndExpired(url, scopeKey, now)
            loadCandidateEntities(url, scopeKey)
        }
        if (entities.isEmpty()) {
            return ""
        }
        return CookieHeaderCodec.mapToCookie(
            entities.asSequence()
                .filter { it.valueCiphertext.isNotBlank() }
                .filter { entity ->
                    entity.expiresAt == null || entity.expiresAt > now
                }
                .filter { entity ->
                    CookieScopeResolver.matches(url, entity.domain, entity.path, entity.hostOnly)
                }
                .filter { entity ->
                    !entity.secure || url.startsWith("https://", ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<CookieVaultEntity> { it.path.length }
                        .thenByDescending { it.updatedAt }
                        .thenBy { it.name }
                )
                .fold(LinkedHashMap<String, String>()) { acc, entity ->
                    val value = codec.decrypt(entity.valueCiphertext)
                    if (value != null && acc.putIfAbsent(entity.name, value) == null) {
                        acc
                    } else {
                        if (value == null) {
                            deleteVaultById(entity.id)
                        }
                        acc
                    }
                }
        ) ?: ""
    }

    override fun removeCookie(url: String) {
        val scopeKey = normalizeScopeKey(url)
        appDatabase.inTransaction {
            deleteVaultByScopeKey(scopeKey)
            deleteLegacyRows(url, scopeKey)
        }
    }

    override fun removeCookie(url: String, key: String) {
        val scopeKey = normalizeScopeKey(url)
        appDatabase.inTransaction {
            deleteVaultCookie(scopeKey, key)
            deleteLegacyRows(url, scopeKey)
        }
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> =
        CookieHeaderCodec.cookieToMap(cookie)

    override fun mapToCookie(cookieMap: Map<String, String>?): String? =
        CookieHeaderCodec.mapToCookie(cookieMap)

    override fun migrateLegacyCookies(): Int {
        val legacyRows = cookieDao.getAllLegacyCookies()
        if (legacyRows.isEmpty()) {
            return 0
        }
        var migrated = 0
        appDatabase.inTransaction {
            legacyRows.forEach { row ->
                val scopeKey = normalizeScopeKey(row.url)
                val cookieMap = CookieHeaderCodec.cookieToMap(row.cookie)
                if (cookieMap.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    cookieMap.forEach { (name, value) ->
                        upsertEntity(
                            scopeKey = scopeKey,
                            origin = row.url,
                            name = name,
                            value = value,
                            domain = scopeDomain(row.url),
                            path = "/",
                            expiresAt = null,
                            secure = false,
                            httpOnly = false,
                            sameSite = null,
                            hostOnly = false,
                            persistent = false,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }
                    cookieDao.delete(row.url)
                    migrated++
                } else {
                    cookieDao.delete(row.url)
                }
            }
            pruneExpiredLocked(System.currentTimeMillis())
        }
        return migrated
    }

    override fun clear() {
        appDatabase.inTransaction {
            cookieDao.deleteAllLegacyCookies()
            deleteAllVaultCookies()
        }
    }

    fun cleanupExpiredCookies() {
        appDatabase.inTransaction {
            pruneExpiredLocked(System.currentTimeMillis())
        }
    }

    private fun loadCandidateEntities(url: String, scopeKey: String): List<CookieVaultEntity> {
        val entities = linkedMapOf<String, CookieVaultEntity>()
        cookieDao.getVaultCookiesByScopeKey(scopeKey).forEach { entities[it.id] = it }
        val candidateDomains = CookieScopeResolver.resolveLookupScopes(url)
        if (candidateDomains.isNotEmpty()) {
            cookieDao.getVaultCookiesByDomains(candidateDomains).forEach { entities[it.id] = it }
        }
        if (entities.isEmpty()) {
            migrateLegacyScope(url, scopeKey)
            cookieDao.getVaultCookiesByScopeKey(scopeKey).forEach { entities[it.id] = it }
            if (candidateDomains.isNotEmpty()) {
                cookieDao.getVaultCookiesByDomains(candidateDomains).forEach { entities[it.id] = it }
            }
        }
        return entities.values.toList()
    }

    private fun migrateLegacyScope(url: String, scopeKey: String): Int {
        val legacy = cookieDao.get(scopeKey) ?: cookieDao.get(url) ?: return 0
        val cookieMap = CookieHeaderCodec.cookieToMap(legacy.cookie)
        if (cookieMap.isEmpty()) {
            cookieDao.delete(scopeKey)
            cookieDao.delete(url)
            return 0
        }
        val now = System.currentTimeMillis()
        cookieMap.forEach { (name, value) ->
            upsertEntity(
                scopeKey = scopeKey,
                origin = legacy.url,
                name = name,
                value = value,
                domain = scopeDomain(legacy.url),
                path = "/",
                expiresAt = null,
                secure = false,
                httpOnly = false,
                sameSite = null,
                hostOnly = false,
                persistent = false,
                createdAt = now,
                updatedAt = now,
            )
        }
        cookieDao.delete(scopeKey)
        if (url != scopeKey) {
            cookieDao.delete(url)
        }
        return cookieMap.size
    }

    private fun cleanupLegacyAndExpired(url: String, scopeKey: String, now: Long) {
        pruneExpiredLocked(now)
        if (cookieDao.getVaultCookiesByScopeKey(scopeKey).isNotEmpty()) {
            deleteLegacyRows(url, scopeKey)
        }
    }

    private fun pruneExpiredLocked(now: Long) {
        cookieDao.deleteExpiredVaultCookies(now)
    }

    private fun deleteLegacyRows(url: String, scopeKey: String) {
        cookieDao.delete(scopeKey)
        if (url != scopeKey) {
            cookieDao.delete(url)
        }
    }

    private fun deleteVaultByScopeKey(scopeKey: String) {
        cookieDao.deleteVaultCookiesByScopeKey(scopeKey)
    }

    private fun deleteVaultCookie(scopeKey: String, name: String) {
        cookieDao.deleteVaultCookie(scopeKey, name)
    }

    private fun deleteVaultById(id: String) {
        cookieDao.deleteVaultCookieById(id)
    }

    private fun deleteAllVaultCookies() {
        cookieDao.deleteAllVaultCookies()
    }

    private fun upsertEntity(
        scopeKey: String,
        origin: String,
        name: String,
        value: String,
        domain: String,
        path: String,
        expiresAt: Long?,
        secure: Boolean,
        httpOnly: Boolean,
        sameSite: String?,
        hostOnly: Boolean,
        persistent: Boolean,
        createdAt: Long,
        updatedAt: Long,
    ) {
        val id = cookieId(scopeKey, domain, path, name)
        val existing = cookieDao.getVaultCookieById(id)
        cookieDao.upsertVaultCookie(
            CookieVaultEntity(
                id = id,
                scopeKey = scopeKey,
                domain = domain,
                path = path,
                name = name,
                valueCiphertext = codec.encrypt(value),
                origin = origin,
                expiresAt = expiresAt,
                secure = secure,
                httpOnly = httpOnly,
                sameSite = sameSite,
                hostOnly = hostOnly,
                persistent = persistent,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: createdAt,
                updatedAt = updatedAt,
            )
        )
    }

    private fun cookieId(scopeKey: String, domain: String, path: String, name: String): String {
        return buildString {
            append(scopeKey.trim().lowercase())
            append('|')
            append(domain.trim().lowercase())
            append('|')
            append(path.trim())
            append('|')
            append(name.trim())
        }
    }

    private fun normalizeScopeKey(url: String): String {
        return CookieScopeResolver.normalizeScopeKey(url)
    }

    private fun scopeDomain(url: String): String {
        return runCatching {
            val base = NetworkUtils.getBaseUrl(url)
            if (base.isNullOrBlank()) {
                normalizeScopeKey(url)
            } else {
                URL(base).host.lowercase()
            }
        }.getOrDefault(normalizeScopeKey(url))
    }

    private inline fun <T> AppDatabase.inTransaction(crossinline block: () -> T): T {
        return runInTransaction(Callable { block() })
    }
}
