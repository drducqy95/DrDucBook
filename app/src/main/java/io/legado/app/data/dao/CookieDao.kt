package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.CookieVaultEntity

@Dao
interface CookieDao {

    @Query("SELECT * FROM cookies Where url = :url")
    fun get(url: String): Cookie?

    @Query("select * from cookies where url like '%|%'")
    fun getOkHttpCookies(): List<Cookie>

    @Query("SELECT * FROM cookies")
    fun getAllLegacyCookies(): List<Cookie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg cookie: Cookie)

    @Update
    fun update(vararg cookie: Cookie)

    @Query("delete from cookies where url = :url")
    fun delete(url: String)

    @Query("delete from cookies where url like '%|%'")
    fun deleteOkHttp()

    @Query("SELECT * FROM cookie_vault WHERE scopeKey = :scopeKey ORDER BY updatedAt DESC, name COLLATE LOCALIZED")
    fun getVaultCookiesByScopeKey(scopeKey: String): List<CookieVaultEntity>

    @Query("SELECT * FROM cookie_vault WHERE domain IN (:domains) ORDER BY updatedAt DESC, name COLLATE LOCALIZED")
    fun getVaultCookiesByDomains(domains: List<String>): List<CookieVaultEntity>

    @Query("SELECT * FROM cookie_vault WHERE id = :id LIMIT 1")
    fun getVaultCookieById(id: String): CookieVaultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertVaultCookie(vararg cookie: CookieVaultEntity)

    @Query("DELETE FROM cookie_vault WHERE id = :id")
    fun deleteVaultCookieById(id: String)

    @Query("DELETE FROM cookie_vault WHERE scopeKey = :scopeKey AND name = :name")
    fun deleteVaultCookie(scopeKey: String, name: String)

    @Query("DELETE FROM cookie_vault WHERE scopeKey = :scopeKey")
    fun deleteVaultCookiesByScopeKey(scopeKey: String)

    @Query("DELETE FROM cookie_vault WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    fun deleteExpiredVaultCookies(now: Long)

    @Query("DELETE FROM cookie_vault")
    fun deleteAllVaultCookies()

    @Query("DELETE FROM cookies")
    fun deleteAllLegacyCookies()
}
