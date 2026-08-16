package io.legado.app.domain.gateway

interface AiSecretStore {
    /** Stores [secret], replacing [secretRef] when supplied, and returns the opaque reference. */
    fun put(secret: String, secretRef: String? = null): String
    fun get(secretRef: String): String?
    fun delete(secretRef: String)
}

