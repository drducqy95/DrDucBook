package io.legado.app.ui.rss.read

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.drducbook.app.R

enum class RedirectPolicy {
    ALLOW_ALL,
    ASK_ALWAYS,
    ASK_CROSS_ORIGIN,
    BLOCK_CROSS_ORIGIN,
    BLOCK_ALL,
    ASK_SAME_DOMAIN_BLOCK_CROSS;

    companion object {
        fun fromString(value: String?): RedirectPolicy {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: ALLOW_ALL
        }
    }
}

@Composable
fun RedirectPolicy.title(): String {
    return when (this) {
        RedirectPolicy.ALLOW_ALL -> stringResource(R.string.redirect_allow_all)
        RedirectPolicy.ASK_ALWAYS -> stringResource(R.string.redirect_ask_always)
        RedirectPolicy.ASK_CROSS_ORIGIN -> stringResource(R.string.redirect_ask_cross_origin)
        RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS -> stringResource(
            R.string.redirect_ask_same_block_cross
        )
        RedirectPolicy.BLOCK_CROSS_ORIGIN -> stringResource(R.string.redirect_block_cross_origin)
        RedirectPolicy.BLOCK_ALL -> stringResource(R.string.redirect_block_all)
    }
}
