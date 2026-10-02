package io.legado.app.domain.model

import androidx.compose.runtime.Stable

@Stable
data class OnlineBookSourceItem(
    val id: String,
    val name: String,
    val originUrl: String,
    val author: String? = null,
    val updateTime: String? = null,
    val downloadCount: String? = null,
    val isVersion3: Boolean = true,
    val hasExplore: Boolean = false,
    val hasSearch: Boolean = false,
    val hasImage: Boolean = false,
    val hasAudio: Boolean = false,
    val downloadUrl: String,
    val sourceJson: String? = null,
)

@Stable
data class OnlineSourceCollectionItem(
    val title: String,
    val description: String,
    val sourceCount: Int,
    val updateTime: String,
    val downloadUrl: String,
    val author: String,
)
