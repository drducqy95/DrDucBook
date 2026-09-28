package io.legado.app.ui.drive

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.auth.GoogleDriveAuthorizationBridge
import io.legado.app.ui.drive.components.DriveBookPreviewSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun DriveLibraryRouteScreen(
    onOpenBookInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriveLibraryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val tokenRes = GoogleDriveAuthorizationBridge.completeAuthorization(context, result.data!!)
            tokenRes.onSuccess { token ->
                context.toastOnUi("Đăng nhập Google Drive thành công!")
                state.activeSource?.let { src ->
                    viewModel.onIntent(DriveLibraryIntent.ConnectSource(src))
                }
            }.onFailure { error ->
                context.toastOnUi("Xác thực Google thất bại: ${error.message}")
            }
        }
    }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is DriveLibraryEffect.ShowToast -> {
                    context.toastOnUi(effect.message)
                }
                is DriveLibraryEffect.OpenBookInfo -> {
                    onOpenBookInfo(effect.bookUrl)
                }
                is DriveLibraryEffect.RequestGoogleAuth -> {
                    val authRes = GoogleDriveAuthorizationBridge.authorizeDriveFile(context)
                    authRes.onSuccess { auth ->
                        if (auth.resolution != null) {
                            val request = IntentSenderRequest.Builder(auth.resolution.intentSender).build()
                            googleAuthLauncher.launch(request)
                        } else if (auth.accessToken != null) {
                            context.toastOnUi("Đã có quyền Google Drive!")
                        }
                    }.onFailure { error ->
                        context.toastOnUi("Lỗi đăng nhập: ${error.message}")
                    }
                }
            }
        }
    }

    DriveLibrarySection(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier
    )

    // Bottom Sheet: Add Source
    if (state.activeSheet is DriveLibrarySheet.AddSource) {
        AddDriveSourceSheet(
            onDismiss = { viewModel.onIntent(DriveLibraryIntent.DismissSheet) },
            onAddPublicLink = { url, name ->
                viewModel.onIntent(DriveLibraryIntent.AddPublicLink(url, name))
            },
            onRequestGoogleAuth = {
                viewModel.onIntent(DriveLibraryIntent.DismissSheet)
                viewModel.onIntent(DriveLibraryIntent.RequestGoogleAuth)
            },
            onAddGoogleAccount = { email, folderId, name ->
                viewModel.onIntent(DriveLibraryIntent.AddGoogleAccount(email, folderId, name))
            }
        )
    }

    // Bottom Sheet: Book Preview
    val activeSheet = state.activeSheet
    if (activeSheet is DriveLibrarySheet.BookPreview) {
        val previewItem = activeSheet.item
        DriveBookPreviewSheet(
            item = previewItem,
            isDownloading = state.downloadingPaths.contains(previewItem.path),
            isImported = previewItem.isImported || state.importedPaths.contains(previewItem.path),
            onDismissRequest = { viewModel.onIntent(DriveLibraryIntent.DismissBookPreview) },
            onDownloadAndImport = { item ->
                viewModel.onIntent(DriveLibraryIntent.DownloadAndImport(item))
            },
            onOpenBook = { item ->
                viewModel.onIntent(DriveLibraryIntent.OpenBook(item))
            }
        )
    }

    // Dialog: Confirm Delete
    state.activeDialog?.let { dialog ->
        when (dialog) {
            is DriveLibraryDialog.ConfirmDelete -> {
                AppAlertDialog(
                    show = true,
                    onDismissRequest = { viewModel.onIntent(DriveLibraryIntent.DismissDialog) },
                    title = "Xóa nguồn thư viện",
                    text = "Bạn có chắc chắn muốn xóa nguồn \"${dialog.source.name}\"? Sách đã tải về kệ sẽ không bị ảnh hưởng.",
                    confirmText = "Xóa",
                    dismissText = "Hủy",
                    onConfirm = {
                        viewModel.onIntent(DriveLibraryIntent.ConfirmDeleteSource(dialog.source))
                    }
                )
            }
        }
    }
}
