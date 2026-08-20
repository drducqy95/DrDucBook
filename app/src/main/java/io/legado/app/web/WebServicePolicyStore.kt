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

    fun addWebDiscoverySources(context: Context, sourceUrls: List<String>) {
        val current = read(context)
        val urls = (current.webDiscoverySourceUrls + sourceUrls)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        save(
            context,
            current.copy(
                webDiscoverySourceUrls = urls,
                revision = current.revision + 1L,
                updatedAt = System.currentTimeMillis(),
            )
        )
        WebServiceDiscoveryController.clearCache()
    }

    fun setWebDiscoverySources(context: Context, sourceUrls: List<String>): WebServicePolicy {
        val current = read(context)
        val updated = current.copy(
            webDiscoverySourceUrls = sourceUrls,
            revision = current.revision + 1L,
            updatedAt = System.currentTimeMillis(),
        )
        save(context, updated)
        WebServiceDiscoveryController.clearCache()
        return updated
    }

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
                if (result.policy.webDiscoverySourceUrls != current.webDiscoverySourceUrls) {
                    WebServiceDiscoveryController.clearCache()
                }
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
        WebServiceDiscoveryController.clearCache()
        return policy
    }

    private fun save(
        context: Context,
        policy: WebServicePolicy,
    ) {
        context.putPrefStringSync(PreferKey.webServicePolicy, GSON.toJson(policy.sanitized()))
    }
}
