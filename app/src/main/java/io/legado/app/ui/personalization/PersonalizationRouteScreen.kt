package io.legado.app.ui.personalization

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.domain.model.AppearanceAssetKind
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun PersonalizationRouteScreen(
    onBack: () -> Unit,
    viewModel: PersonalizationViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val context = LocalContext.current
    val assetPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(PersonalizationIntent.AssetPicked(it.toString())) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PersonalizationEffect.PickAsset -> {
                    val mimeTypes = when (effect.request.kind) {
                        AppearanceAssetKind.ICON ->
                            arrayOf("image/png", "image/webp", "image/svg+xml")
                        AppearanceAssetKind.WALLPAPER ->
                            arrayOf("image/png", "image/jpeg", "image/webp")
                        AppearanceAssetKind.FONT ->
                            arrayOf("font/ttf", "font/otf")
                    }
                    assetPicker.launch(mimeTypes)
                }
                is PersonalizationEffect.ShowMessage -> context.toastOnUi(effect.message)
                PersonalizationEffect.Close -> currentOnBack()
            }
        }
    }

    BackHandler { viewModel.onIntent(PersonalizationIntent.BackPressed) }
    PersonalizationScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { viewModel.onIntent(PersonalizationIntent.BackPressed) },
    )
}
