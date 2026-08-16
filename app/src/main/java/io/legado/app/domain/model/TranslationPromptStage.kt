package io.legado.app.domain.model

enum class TranslationPromptStage(val storageKey: String) {
    PREPARE("prepare"),
    FILTER("filter"),
    DICTIONARY("dictionary"),
    TRANSLATE("translate"),
    RETRANSLATE("retranslate");

    val taskType: String
        get() = "$TASK_TYPE_PREFIX$storageKey"

    companion object {
        const val TASK_TYPE_PREFIX = "translation_prompt:"

        fun fromTaskType(taskType: String): TranslationPromptStage? {
            val key = taskType.removePrefix(TASK_TYPE_PREFIX)
            return entries.firstOrNull { it.storageKey == key }
        }
    }
}

internal fun activeTranslationPromptStages(
    includeRetranslateStage: Boolean,
): List<TranslationPromptStage> = if (includeRetranslateStage) {
    TranslationPromptStage.entries
} else {
    TranslationPromptStage.entries.filterNot { it == TranslationPromptStage.RETRANSLATE }
}
