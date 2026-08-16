package io.legado.app.constant

import io.legado.app.utils.getPrefBoolean
import splitties.init.appCtx

object FeatureFlags {

    val aiRouterV2: Boolean
        get() = enabled(PreferKey.featureAiRouterV2, true)

    val agentMutation: Boolean
        get() = enabled(PreferKey.featureAgentMutation, true)

    val agentSkill: Boolean
        get() = enabled(PreferKey.featureAgentSkill, true)

    val agentPlugin: Boolean
        get() = enabled(PreferKey.featureAgentPlugin, true)

    val chatBubble: Boolean
        get() = enabled(PreferKey.featureChatBubble, true)

    val mangaTranslation: Boolean
        get() = enabled(PreferKey.featureMangaTranslation, false)

    val browserPageTranslation: Boolean
        get() = enabled(PreferKey.featureBrowserPageTranslation, true)

    val sourceDailyHealth: Boolean
        // Health probes can hit anti-bot endpoints. Keep this opt-in so unused sources never
        // trigger network traffic or foreground CAPTCHA prompts until the user enables it.
        get() = enabled(PreferKey.featureSourceDailyHealth, false)

    val mediaDownload: Boolean
        get() = enabled(PreferKey.featureMediaDownload, true)

    val ebookFixedLayout: Boolean
        get() = enabled(PreferKey.featureEbookFixedLayout, true)

    private fun enabled(key: String, defaultValue: Boolean): Boolean =
        appCtx.getPrefBoolean(key, defaultValue)
}
