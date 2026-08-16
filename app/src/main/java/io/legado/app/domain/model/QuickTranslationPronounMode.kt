package io.legado.app.domain.model

enum class QuickTranslationPronounMode(val value: String) {
    AUTO("auto"),
    ANCIENT("ancient"),
    MODERN("modern"),
    WESTERN("western"),
    OFF("off");

    companion object {
        val default = AUTO

        fun from(value: String?): QuickTranslationPronounMode =
            entries.firstOrNull { it.value == value } ?: default
    }
}
