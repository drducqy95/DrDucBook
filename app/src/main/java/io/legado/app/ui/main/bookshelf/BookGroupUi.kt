package io.legado.app.ui.main.bookshelf

import android.content.Context
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.BookGroup

@Stable
data class BookGroupUi(
    val groupId: Long,
    val groupName: String,
    val cover: String?,
    val order: Int,
    val enableRefresh: Boolean,
    val show: Boolean,
    val bookSort: Int,
    val isPrivate: Boolean
)

fun BookGroup.toBookGroupUi(context: Context? = null): BookGroupUi {
    val displayName = if (context != null && groupId < 0) {
        getManageName(context).suffix ?: groupName
    } else {
        groupName
    }
    return BookGroupUi(
        groupId = groupId,
        groupName = displayName,
        cover = cover,
        order = order,
        enableRefresh = enableRefresh,
        show = show,
        bookSort = bookSort,
        isPrivate = isPrivate
    )
}
