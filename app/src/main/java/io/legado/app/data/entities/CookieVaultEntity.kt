package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cookie_vault",
    indices = [
        Index(value = ["scopeKey"]),
        Index(value = ["domain"]),
        Index(value = ["expiresAt"]),
        Index(value = ["scopeKey", "name"]),
    ],
)
data class CookieVaultEntity(
    @PrimaryKey val id: String,
    val scopeKey: String,
    val domain: String,
    val path: String,
    val name: String,
    val valueCiphertext: String,
    val origin: String,
    val expiresAt: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: String? = null,
    val hostOnly: Boolean = false,
    val persistent: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
