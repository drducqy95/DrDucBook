package io.legado.app.ui.config.labConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object LabConfig {

    var labEnabled by prefDelegate(
        PreferKey.labEnabled,
        false
    )

    var eInkDisplay by prefDelegate(
        PreferKey.labEInkDisplay,
        false
    )

    var eyeProtection by prefDelegate(
        PreferKey.labEyeProtection,
        false
    )

    var featureAiRouterV2 by prefDelegate(PreferKey.featureAiRouterV2, true)
    var featureAgentMutation by prefDelegate(PreferKey.featureAgentMutation, true)
    var featureAgentSkill by prefDelegate(PreferKey.featureAgentSkill, true)
    var featureAgentPlugin by prefDelegate(PreferKey.featureAgentPlugin, true)
    var featureChatBubble by prefDelegate(PreferKey.featureChatBubble, true)
    var featureMangaTranslation by prefDelegate(PreferKey.featureMangaTranslation, false)
    var featureBrowserPageTranslation by prefDelegate(PreferKey.featureBrowserPageTranslation, true)
    var featureSourceDailyHealth by prefDelegate(PreferKey.featureSourceDailyHealth, false)
    var featureMediaDownload by prefDelegate(PreferKey.featureMediaDownload, true)
    var featureEbookFixedLayout by prefDelegate(PreferKey.featureEbookFixedLayout, true)

}
