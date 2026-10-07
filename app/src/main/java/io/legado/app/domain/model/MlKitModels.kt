package io.legado.app.domain.model

import androidx.compose.runtime.Stable
import com.drducbook.app.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class MlKitLanguagePair(
    val id: String,
    val sourceLang: String,
    val targetLang: String,
    val labelRes: Int,
    val shortTag: String,
) {
    companion object {
        val PRESETS: ImmutableList<MlKitLanguagePair> = persistentListOf(
            MlKitLanguagePair("zh-ja", "zh", "ja", R.string.mlkit_pair_zh_ja, "JA"),
            MlKitLanguagePair("zh-en", "zh", "en", R.string.mlkit_pair_zh_en, "EN"),
            MlKitLanguagePair("zh-ko", "zh", "ko", R.string.mlkit_pair_zh_ko, "KO"),
            MlKitLanguagePair("zh-vi", "zh", "vi", R.string.mlkit_pair_zh_vi, "VI"),
        )
    }
}
