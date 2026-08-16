package io.legado.app.web

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.domain.webservice.WebServicePolicy
import io.legado.app.domain.webservice.WebServicePolicyPatchRequest
import io.legado.app.domain.webservice.WebServicePolicyPatchResult
import io.legado.app.domain.webservice.WebServicePolicyRevision
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefStringSync

object WebServicePolicyStore {

    @Synchronized
    fun read(context: Context): WebServicePolicy =
        context.getPrefString(PreferKey.webServicePolicy)
            ?.let { raw -> runCatching { GSON.fromJson(raw, WebServicePolicy::class.java) }.getOrNull() }
            ?.takeIf { it.revision >= 1L }
            ?.sanitized()
            ?: WebServicePolicy()

    @Synchronized
    fun patch(
        context: Context,
        request: WebServicePolicyPatchRequest,
        ifMatch: String?,
        now: Long = System.currentTimeMillis(),
    ): WebServicePolicyPatchResult {
        val current = read(context)
        return when (val result = WebServicePolicyRevision.applyPatch(current, request, ifMatch, now)) {
            is WebServicePolicyPatchResult.Success -> {
                save(context, result.policy)
                result
            }

            is WebServicePolicyPatchResult.Conflict,
            WebServicePolicyPatchResult.PreconditionRequired -> result
        }
    }

    @Synchronized
    fun reset(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): WebServicePolicy {
        val policy = WebServicePolicy(
            autoTranslationEnabled = true,
            revision = read(context).revision + 1L,
            updatedAt = now,
        )
        save(context, policy)
        return policy
    }

    private fun save(
        context: Context,
        policy: WebServicePolicy,
    ) {
        context.putPrefStringSync(PreferKey.webServicePolicy, GSON.toJson(policy.sanitized()))
    }
}
