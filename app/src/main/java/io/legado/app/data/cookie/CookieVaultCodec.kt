package io.legado.app.data.cookie

interface CookieVaultCodec {

    fun encrypt(value: String): String

    fun decrypt(value: String): String?
}
