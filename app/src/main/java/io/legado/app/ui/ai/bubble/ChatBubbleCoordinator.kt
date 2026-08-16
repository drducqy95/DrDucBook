package io.legado.app.ui.ai.bubble

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AiChatBubbleConfig
import io.legado.app.ui.ai.context.AiScreenContextRegistry
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainRouteAiChat
import io.legado.app.utils.defaultSharedPreferences

object ChatBubbleCoordinator : Application.ActivityLifecycleCallbacks,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var application: Application? = null
    private var resumedActivity: Activity? = null
    private var hiddenSessionActivity: Activity? = null
    private var host: ChatBubbleHost? = null

    fun install(application: Application) {
        if (this.application != null) return
        this.application = application
        application.registerActivityLifecycleCallbacks(this)
        application.defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun refresh() {
        syncHost()
    }

    override fun onActivityResumed(activity: Activity) {
        if (hiddenSessionActivity !== activity) {
            hiddenSessionActivity = null
        }
        resumedActivity = activity
        syncHost()
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) {
            resumedActivity = null
            detachHost(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumedActivity === activity) {
            resumedActivity = null
        }
        detachHost(activity)
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        if (key == PreferKey.aiChatBubbleEnabled) {
            syncHost()
        }
    }

    private fun syncHost() {
        val activity = resumedActivity ?: return
        if (
            !AiChatBubbleConfig.enabled ||
            hiddenSessionActivity === activity ||
            activity.isBubbleExcluded() ||
            isChatBubbleExcludedScreen(AiScreenContextRegistry.current.value?.screen) ||
            AiScreenContextRegistry.current.value?.sensitive == true
        ) {
            detachHost()
            return
        }
        if (host?.activity === activity) return
        detachHost()
        host = ChatBubbleHost.attach(
            activity = activity,
            onOpenChat = {
                activity.startActivity(MainActivity.createAiChatIntent(activity))
            },
            onHideSession = {
                hiddenSessionActivity = activity
                detachHost(activity)
            },
            onDisableBubble = {
                AiChatBubbleConfig.enabled = false
                detachHost(activity)
            },
        )
    }

    private fun detachHost(activity: Activity? = null) {
        val currentHost = host ?: return
        if (activity == null || currentHost.activity === activity) {
            currentHost.detach()
            host = null
        }
    }

    private fun Activity.isBubbleExcluded(): Boolean {
        val name = javaClass.name.lowercase()
        return sensitiveActivityNameParts.any { it in name }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private val sensitiveActivityNameParts = listOf(
        "login",
        "oauth",
        "password",
        "secret",
        "provideredit",
        "modeledit",
    )
}

internal fun isChatBubbleExcludedScreen(screen: String?): Boolean {
    return screen == MainRouteAiChat::class.simpleName
}
