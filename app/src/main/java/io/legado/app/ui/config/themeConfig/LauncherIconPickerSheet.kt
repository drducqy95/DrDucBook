package io.legado.app.ui.config.themeConfig

import android.content.ComponentName
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.drducbook.app.R
import io.legado.app.ui.main.Launcher0
import io.legado.app.ui.main.Launcher1
import io.legado.app.ui.main.Launcher2
import io.legado.app.ui.main.Launcher3
import io.legado.app.ui.main.Launcher4
import io.legado.app.ui.main.Launcher5
import io.legado.app.ui.main.Launcher6
import io.legado.app.ui.main.LauncherW
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.getCompatDrawable
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.main.MainActivity
import splitties.init.appCtx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherIconPickerSheet(
    show: Boolean,
    selectedValue: String,
    onDismissRequest: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val icons = LauncherIcons.list

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.change_icon)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(icons, key = { it.value }) { item ->
                    val isSelected = item.value == selectedValue || (selectedValue.isEmpty() && item.value == "ic_launcher")
                    val drawable = remember(item.resId) {
                        context.getCompatDrawable(item.resId)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .background(
                                if (isSelected)
                                    LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else
                                    LegadoTheme.colorScheme.surfaceContainer
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = LegadoTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.large
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                onValueChange(item.value)
                                onDismissRequest()
                            }
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                    }
                                },
                                update = { imageView ->
                                    imageView.setImageDrawable(drawable)
                                },
                                modifier = Modifier.size(52.dp)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(18.dp)
                                        .background(LegadoTheme.colorScheme.primary, MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = LegadoTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AppText(
                            text = stringResource(item.titleRes),
                            style = LegadoTheme.typography.labelMedium,
                            color = if (isSelected) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class LauncherIconItem(
    val value: String,
    @param:StringRes val titleRes: Int,
    val resId: Int,
    val component: ComponentName
)

object LauncherIcons {

    val list = listOf(
        LauncherIconItem(
            value = "ic_launcher",
            titleRes = R.string.launcher_icon_default,
            resId = R.mipmap.ic_launcher,
            component = ComponentName(appCtx, MainActivity::class.java)
        ),
        LauncherIconItem(
            value = "launcherw",
            titleRes = R.string.launcher_icon_white,
            resId = R.mipmap.launcherw,
            component = ComponentName(appCtx, LauncherW::class.java)
        ),
        LauncherIconItem(
            value = "launcher0",
            titleRes = R.string.launcher_icon_amoled,
            resId = R.mipmap.launcher0,
            component = ComponentName(appCtx, Launcher0::class.java)
        ),
        LauncherIconItem(
            value = "launcher1",
            titleRes = R.string.launcher_icon_gold,
            resId = R.mipmap.launcher1,
            component = ComponentName(appCtx, Launcher1::class.java)
        ),
        LauncherIconItem(
            value = "launcher2",
            titleRes = R.string.launcher_icon_jade,
            resId = R.mipmap.launcher2,
            component = ComponentName(appCtx, Launcher2::class.java)
        ),
        LauncherIconItem(
            value = "launcher3",
            titleRes = R.string.launcher_icon_violet,
            resId = R.mipmap.launcher3,
            component = ComponentName(appCtx, Launcher3::class.java)
        ),
        LauncherIconItem(
            value = "launcher4",
            titleRes = R.string.launcher_icon_sakura,
            resId = R.mipmap.launcher4,
            component = ComponentName(appCtx, Launcher4::class.java)
        ),
        LauncherIconItem(
            value = "launcher5",
            titleRes = R.string.launcher_icon_crimson,
            resId = R.mipmap.launcher5,
            component = ComponentName(appCtx, Launcher5::class.java)
        ),
        LauncherIconItem(
            value = "launcher6",
            titleRes = R.string.launcher_icon_amber,
            resId = R.mipmap.launcher6,
            component = ComponentName(appCtx, Launcher6::class.java)
        ),
    )

    fun find(value: String?): LauncherIconItem? {
        return list.find { it.value == value }
    }
}