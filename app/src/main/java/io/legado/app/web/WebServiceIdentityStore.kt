package io.legado.app.web

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.domain.webservice.newWebServiceInstanceId
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefStringSync

object WebServiceIdentityStore {

    fun getOrCreateInstanceId(context: Context): String {
        context.getPrefString(PreferKey.webServiceInstanceId)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return newWebServiceInstanceId().also {
            context.putPrefStringSync(PreferKey.webServiceInstanceId, it)
        }
    }
}
