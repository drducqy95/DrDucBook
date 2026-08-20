package io.legado.app.web

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.domain.webservice.newWebServiceInstanceId
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefStringSync

object WebServiceIdentityStore {

    private const val DEFAULT_NAME = "DrDucBook"

    fun getOrCreateInstanceId(context: Context): String {
        context.getPrefString(PreferKey.webServiceInstanceId)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return newWebServiceInstanceId().also {
            context.putPrefStringSync(PreferKey.webServiceInstanceId, it)
        }
    }

    fun getServiceName(context: Context): String =
        context.getPrefString(PreferKey.webServiceName)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_NAME

    fun setServiceName(context: Context, name: String): String {
        val normalized = name.trim().replace(Regex("\\s+"), " ").take(80).ifBlank { DEFAULT_NAME }
        context.putPrefStringSync(PreferKey.webServiceName, normalized)
        return normalized
    }
}
