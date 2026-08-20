package io.legado.app.ui.account

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import com.drducbook.app.auth.GoogleCredentialBridge
import com.drducbook.app.auth.GoogleDriveAuthorizationBridge
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.AccountRole

@Composable
fun AccountRouteScreen(
    onBackClick: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val context by rememberUpdatedState(LocalContext.current)
    val googleDriveCancelledMessage = stringResource(R.string.account_google_drive_cancelled)
    val googleDriveAuthorizationFailedMessage =
        stringResource(R.string.account_google_drive_authorization_failed)
    val googleDriveNoAccessMessage = stringResource(R.string.account_google_drive_no_access)
    val safPermissionFailedMessage = stringResource(R.string.account_saf_permission_failed)
    var pendingGoogleDriveAction by rememberSaveable {
        mutableStateOf<GoogleDriveAction?>(null)
    }
    val googleDriveAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingGoogleDriveAction
        pendingGoogleDriveAction = null
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null || action == null) {
            viewModel.onIntent(
                AccountIntent.GoogleDriveAuthorizationFailed(googleDriveCancelledMessage)
            )
            return@rememberLauncherForActivityResult
        }
        GoogleDriveAuthorizationBridge.completeAuthorization(context, data)
            .onSuccess { token ->
                viewModel.onIntent(AccountIntent.SubmitGoogleDriveAccessToken(token, action))
            }
            .onFailure { error ->
                viewModel.onIntent(
                    AccountIntent.GoogleDriveAuthorizationFailed(
                        error.message ?: googleDriveAuthorizationFailedMessage
                    )
                )
                        }
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        if (persisted) {
            viewModel.onIntent(AccountIntent.SubmitSafFolder(uri.toString()))
        } else {
            android.widget.Toast.makeText(
                context,
                safPermissionFailedMessage,
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AccountEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is AccountEffect.RequestGoogleCredential -> {
                    GoogleCredentialBridge.requestIdToken(
                        context = context,
                        serverClientId = effect.clientId,
                        nonce = effect.nonce,
                    ).onSuccess { token ->
                        viewModel.onIntent(AccountIntent.SubmitGoogleToken(token.idToken, token.nonce))
                    }.onFailure { error ->
                        if (error !is kotlinx.coroutines.CancellationException) {
                            GoogleCredentialBridge.openBrowserFallback(context)
                                .onSuccess {
                                    snackbarHostState.showSnackbar("Đã chuyển sang trình duyệt để đăng nhập Google")
                                }
                                .onFailure {
                                    snackbarHostState.showSnackbar(GoogleCredentialBridge.userMessage(error))
                                }
                        }
                    }
                }
                is AccountEffect.RequestGoogleDriveAuthorization -> {
                    GoogleDriveAuthorizationBridge.authorize(context)
                        .onSuccess { authorization ->
                            val token = authorization.accessToken
                            val resolution = authorization.resolution
                            when {
                                !token.isNullOrBlank() -> viewModel.onIntent(
                                    AccountIntent.SubmitGoogleDriveAccessToken(token, effect.action)
                                )
                                resolution != null -> {
                                    pendingGoogleDriveAction = effect.action
                                    googleDriveAuthorizationLauncher.launch(
                                        IntentSenderRequest.Builder(resolution).build()
                                    )
                                }
                                else -> viewModel.onIntent(
                                    AccountIntent.GoogleDriveAuthorizationFailed(
                                        googleDriveNoAccessMessage
                                    )
                                )
                            }
                        }
                        .onFailure { error ->
                            viewModel.onIntent(
                                AccountIntent.GoogleDriveAuthorizationFailed(
                                    error.message ?: googleDriveAuthorizationFailedMessage
                                )
                            )
                        }
                }
                AccountEffect.RequestSafFolder -> safFolderLauncher.launch(null)
            }
        }
    }

    AccountScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (AccountIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.account_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AccountStatusSection(state = state, onIntent = onIntent)
            }
            if (state.session != null) {
                item { AccountEntitlementSection(state = state) }
            }
            item { AccountCloudBackupSection(state = state, onIntent = onIntent) }
            if (state.session != null) {
                item { AccountPasswordSection(state = state, onIntent = onIntent) }
            }
            if (state.access?.isAdmin == true) item {
                AccountAdminSection(state = state, onIntent = onIntent)
            }
        }
    }

    AppAlertDialog(
        show = state.restoreConfirmationVisible,
        onDismissRequest = { onIntent(AccountIntent.DismissRestoreCloudBackup) },
        title = stringResource(R.string.account_restore_cloud_title),
        text = stringResource(R.string.account_restore_cloud_message),
        confirmText = stringResource(R.string.restore),
        onConfirm = { onIntent(AccountIntent.ConfirmRestoreCloudBackup) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AccountIntent.DismissRestoreCloudBackup) },
    )

    AppAlertDialog(
        show = state.googleDriveRestoreConfirmationVisible,
        onDismissRequest = { onIntent(AccountIntent.DismissRestoreGoogleDriveBackup) },
        title = stringResource(R.string.account_restore_google_title),
        text = stringResource(R.string.account_restore_google_message),
        confirmText = stringResource(R.string.restore),
        onConfirm = { onIntent(AccountIntent.ConfirmRestoreGoogleDriveBackup) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AccountIntent.DismissRestoreGoogleDriveBackup) },
    )

    AppAlertDialog(
        show = state.safRestoreConfirmationVisible,
        onDismissRequest = { onIntent(AccountIntent.DismissRestoreSafBackup) },
        title = stringResource(R.string.account_restore_saf_title),
        text = stringResource(R.string.account_restore_saf_message),
        confirmText = stringResource(R.string.restore),
        onConfirm = { onIntent(AccountIntent.ConfirmRestoreSafBackup) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AccountIntent.DismissRestoreSafBackup) },
    )

    AccountRoleEditorDialog(state = state, onIntent = onIntent)

}

@Composable
private fun AccountEntitlementSection(state: AccountUiState) {
    val access = state.access ?: return
    SplicedColumnGroup(title = stringResource(R.string.account_entitlement_title)) {
        SettingItem(
            title = roleLabel(access.role),
            description = when (access.role) {
                AccountRole.FREE -> stringResource(R.string.account_role_free_summary)
                AccountRole.PREMIUM -> access.roleExpiresAtEpochMillis?.let { expiresAt ->
                    stringResource(R.string.account_role_premium_trial_until, formatDate(expiresAt))
                } ?: stringResource(R.string.account_role_premium_summary)
                AccountRole.ADMIN -> stringResource(R.string.account_role_admin_summary)
            },
        )
        state.quotaUsage.forEach { quota ->
            SettingItem(
                title = quotaLabel(quota.kind),
                description = quota.limit?.let { limit ->
                    stringResource(R.string.account_quota_used_today, quota.used, limit)
                } ?: stringResource(R.string.account_unlimited),
            )
        }
    }
}

@Composable
private fun AccountCloudBackupSection(
    state: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
) {
    var backupPassword by rememberSaveable { mutableStateOf("") }
    SplicedColumnGroup(title = stringResource(R.string.account_backup_sync_title)) {
        if (state.session != null) {
            state.lastCloudBackup?.let { backup ->
                SettingItem(
                    title = stringResource(R.string.account_last_cloud_backup),
                    description = "${DateFormat.getDateTimeInstance().format(Date(backup.completedAtEpochMillis))} · ${formatBytes(backup.sizeBytes)}",
                )
            }
            if (state.access == null) {
                SettingItem(
                    title = stringResource(R.string.account_access_loading),
                    description = stringResource(R.string.account_access_loading_summary),
                )
            }
            AccountActionRow {
                Button(
                    enabled = !state.busy && state.access != null && backupPassword.length >= 8,
                    onClick = { onIntent(AccountIntent.UploadCloudBackup(backupPassword)) },
                ) { AppText(stringResource(R.string.account_backup_cloud)) }
                OutlinedButton(
                    enabled = !state.busy && state.access != null && backupPassword.length >= 8,
                    onClick = { onIntent(AccountIntent.RequestRestoreCloudBackup(backupPassword)) },
                ) { AppText(stringResource(R.string.account_restore_latest)) }
            }
        }
        AppTextField(
            value = backupPassword,
            onValueChange = { backupPassword = it },
            enabled = !state.busy,
            label = stringResource(R.string.account_backup_password),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        if (state.session != null) {
            state.lastGoogleDriveBackup?.let { backup ->
                SettingItem(
                    title = stringResource(R.string.account_last_google_backup),
                    description = "${DateFormat.getDateTimeInstance().format(Date(backup.completedAtEpochMillis))} · ${formatBytes(backup.sizeBytes)}",
                )
            }
            SettingItem(
                title = "Google Drive",
                description = if (state.googleDriveAuthorized) {
                    stringResource(R.string.account_google_drive_authorized)
                } else {
                    stringResource(R.string.account_google_drive_summary)
                },
            )
            AccountActionRow {
                Button(
                    enabled = state.googleDriveAvailable && !state.busy && state.access != null && backupPassword.length >= 8,
                    onClick = { onIntent(AccountIntent.UploadGoogleDriveBackup(backupPassword)) },
                ) { AppText(stringResource(R.string.account_backup_google_drive)) }
                OutlinedButton(
                    enabled = state.googleDriveAvailable && !state.busy && state.access != null && backupPassword.length >= 8,
                    onClick = { onIntent(AccountIntent.RequestRestoreGoogleDriveBackup(backupPassword)) },
                ) { AppText(stringResource(R.string.account_restore_google_drive)) }
            }
        }
        SettingItem(
            title = stringResource(R.string.account_saf_title),
            description = if (state.safBackupConfigured) {
                stringResource(R.string.account_saf_selected)
            } else {
                stringResource(R.string.account_saf_summary)
            },
        )
        AccountActionRow {
            OutlinedButton(
                enabled = !state.busy,
                onClick = { onIntent(AccountIntent.RequestSafFolder) },
            ) { AppText(stringResource(R.string.account_saf_choose)) }
            Button(
                enabled = state.safBackupConfigured && !state.busy && backupPassword.length >= 8,
                onClick = { onIntent(AccountIntent.UploadSafBackup(backupPassword)) },
            ) { AppText(stringResource(R.string.account_saf_backup)) }
            OutlinedButton(
                enabled = state.safBackupConfigured && !state.busy && backupPassword.length >= 8,
                onClick = { onIntent(AccountIntent.RequestRestoreSafBackup(backupPassword)) },
            ) { AppText(stringResource(R.string.account_saf_restore)) }
            TextButton(
                enabled = state.safBackupConfigured && !state.busy,
                onClick = { onIntent(AccountIntent.ClearSafFolder) },
            ) { AppText(stringResource(R.string.remove)) }
        }
        SettingItem(
            title = stringResource(R.string.account_saf_schedule_title),
            description = state.safScheduleIntervalHours?.let { hours ->
                if (hours >= 168L) stringResource(R.string.account_saf_schedule_weekly)
                else stringResource(R.string.account_saf_schedule_daily)
            } ?: stringResource(R.string.account_saf_schedule_disabled),
        )
        AccountActionRow {
            OutlinedButton(
                enabled = state.safBackupConfigured && !state.busy && backupPassword.length >= 8,
                onClick = {
                    onIntent(AccountIntent.EnableSafSchedule(24L, backupPassword))
                },
            ) { AppText(stringResource(R.string.account_daily)) }
            OutlinedButton(
                enabled = state.safBackupConfigured && !state.busy && backupPassword.length >= 8,
                onClick = {
                    onIntent(AccountIntent.EnableSafSchedule(168L, backupPassword))
                },
            ) { AppText(stringResource(R.string.account_weekly)) }
            TextButton(
                enabled = state.safScheduleIntervalHours != null && !state.busy,
                onClick = { onIntent(AccountIntent.DisableSafSchedule) },
            ) { AppText(stringResource(R.string.disable)) }
        }
    }
}

@Composable
private fun AccountPasswordSection(
    state: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    SplicedColumnGroup(title = stringResource(R.string.account_change_password_title)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                enabled = !state.busy,
                label = stringResource(R.string.account_current_password),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                enabled = !state.busy,
                label = stringResource(R.string.account_new_password),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !state.busy && currentPassword.isNotBlank() && newPassword.length >= 8,
                onClick = {
                    onIntent(AccountIntent.ChangePassword(currentPassword, newPassword))
                    currentPassword = ""
                    newPassword = ""
                },
            ) { AppText(stringResource(R.string.account_change_password_title)) }
        }
    }
}

@Composable
private fun AccountAdminSection(
    state: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
) {
    SplicedColumnGroup(title = stringResource(R.string.account_admin_title)) {
        if (state.adminAccounts.isEmpty()) {
            ClickableSettingItem(
                title = stringResource(R.string.account_admin_load),
                description = stringResource(R.string.account_admin_summary),
                onClick = { onIntent(AccountIntent.LoadAdminAccounts) },
            )
        } else {
            SearchBar(
                query = state.adminSearchQuery,
                backgroundColor = io.legado.app.ui.theme.LegadoTheme.colorScheme.onSheetContent,
                onQueryChange = { onIntent(AccountIntent.UpdateAdminSearch(it)) },
                placeholder = stringResource(R.string.account_admin_search_hint),
                autoFocus = false,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.adminRoleFilter == null,
                    onClick = { onIntent(AccountIntent.FilterAdminRole(null)) },
                    label = {
                        AppText(
                            stringResource(
                                R.string.account_admin_role_all,
                                state.adminAccounts.size,
                            )
                        )
                    },
                )
                AccountRole.entries.forEach { role ->
                    val count = state.adminAccounts.count { it.role == role }
                    FilterChip(
                        selected = state.adminRoleFilter == role,
                        onClick = { onIntent(AccountIntent.FilterAdminRole(role)) },
                        label = { AppText("${roleLabel(role)} ($count)") },
                    )
                }
            }
            val filteredAccounts = filterAdminAccounts(
                accounts = state.adminAccounts,
                query = state.adminSearchQuery,
                role = state.adminRoleFilter,
            )
            if (filteredAccounts.isEmpty()) {
                SettingItem(title = stringResource(R.string.account_admin_no_results))
            } else {
                filteredAccounts
                    .groupBy(AccountAdminUi::role)
                    .toSortedMap(compareByDescending { role -> role.ordinal })
                    .forEach { (groupRole, accounts) ->
                        SettingItem(
                            title = stringResource(
                                R.string.account_admin_role_group,
                                roleLabel(groupRole),
                                accounts.size,
                            )
                        )
                        accounts.forEach { account ->
                            val role = roleLabel(account.role)
                            val description = account.roleExpiresAtEpochMillis?.let { expiresAt ->
                                stringResource(
                                    R.string.account_role_expires,
                                    role,
                                    formatDate(expiresAt),
                                )
                            } ?: role
                            ClickableSettingItem(
                                title = account.email.ifBlank { account.userId },
                                description = description,
                                onClick = { onIntent(AccountIntent.EditAccount(account.userId)) },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun AccountRoleEditorDialog(
    state: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
) {
    val editing = state.editingAccount
    AppAlertDialog(
        show = editing != null && state.access?.isAdmin == true,
        onDismissRequest = { onIntent(AccountIntent.DismissAccountEditor) },
        title = editing?.email?.ifBlank { editing.userId }
            ?: stringResource(R.string.account_title),
        content = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountRole.entries.forEach { role ->
                    FilterChip(
                        selected = editing?.selectedRole == role,
                        onClick = { onIntent(AccountIntent.SelectAccountRole(role)) },
                        label = { AppText(roleLabel(role)) },
                    )
                }
            }
            if (editing?.selectedRole != AccountRole.FREE) {
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = editing?.selectedPermanent == true,
                        onClick = { onIntent(AccountIntent.SetAccountRolePermanent(true)) },
                        label = { AppText(stringResource(R.string.account_role_permanent)) },
                    )
                    FilterChip(
                        selected = editing?.selectedPermanent == false,
                        onClick = { onIntent(AccountIntent.SetAccountRolePermanent(false)) },
                        label = { AppText(stringResource(R.string.account_role_timed)) },
                    )
                }
            }
            if (editing?.selectedRole != AccountRole.FREE && editing?.selectedPermanent == false) {
                AppTextField(
                    value = editing.selectedDurationDays,
                    onValueChange = { onIntent(AccountIntent.SetAccountRoleDurationDays(it)) },
                    label = stringResource(R.string.account_role_duration_days),
                    supportingText = { AppText(stringResource(R.string.account_role_duration_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmText = stringResource(R.string.account_save_role),
        onConfirm = { onIntent(AccountIntent.SaveAccountRole) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AccountIntent.DismissAccountEditor) },
    )
}

@Composable
private fun roleLabel(role: AccountRole): String = when (role) {
    AccountRole.FREE -> stringResource(R.string.account_role_free)
    AccountRole.PREMIUM -> stringResource(R.string.account_role_premium)
    AccountRole.ADMIN -> stringResource(R.string.account_role_admin)
}

@Composable
private fun quotaLabel(kind: AccountQuotaKind): String = when (kind) {
    AccountQuotaKind.DOWNLOAD_CONTENT -> stringResource(R.string.account_quota_download)
    AccountQuotaKind.EXPORT_EBOOK -> stringResource(R.string.account_quota_export)
    AccountQuotaKind.AUTHORING_CHAPTER -> stringResource(R.string.account_quota_authoring)
    AccountQuotaKind.EDIT_EBOOK_CHAPTER -> stringResource(R.string.account_quota_edit_ebook)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance().format(Date(epochMillis))

@Composable
private fun AccountStatusSection(
    state: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
) {
    SplicedColumnGroup(title = stringResource(R.string.account_title)) {
        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        val session = state.session
        when {
            !state.configured -> SettingItem(
                title = stringResource(R.string.account_not_configured),
                description = stringResource(R.string.account_summary),
            )
            session == null -> SettingItem(
                title = stringResource(R.string.account_signed_out),
                description = stringResource(R.string.account_summary),
            )
            else -> {
                SettingItem(
                    title = stringResource(
                        R.string.account_signed_in_as,
                        session.email.ifBlank { session.userId },
                    ),
                    description = if (session.emailVerified) {
                        stringResource(R.string.account_email_verified)
                    } else {
                        stringResource(R.string.account_email_not_verified)
                    },
                )
                SettingItem(
                    title = stringResource(R.string.account_providers),
                    description = session.providers,
                )
                SettingItem(
                    title = stringResource(R.string.account_user_id),
                    description = session.userId,
                )
                session.expiresAtEpochMillis?.let { expiresAt ->
                    SettingItem(
                        title = stringResource(
                            R.string.account_session_expires,
                            DateFormat.getDateTimeInstance().format(Date(expiresAt)),
                        ),
                    )
                }
            }
        }

        AccountActionRow {
            OutlinedButton(
                enabled = state.configured && !state.busy,
                onClick = { onIntent(AccountIntent.Refresh) },
            ) {
                AppText(stringResource(R.string.account_refresh_session))
            }
            if (session != null) {
                OutlinedButton(
                    enabled = !state.busy,
                    onClick = { onIntent(AccountIntent.Reauthenticate) },
                ) {
                    AppText(stringResource(R.string.account_reauthenticate))
                }
                OutlinedButton(
                    enabled = !state.busy,
                    onClick = { onIntent(AccountIntent.SignOutLocal) },
                ) {
                    AppText(stringResource(R.string.account_sign_out))
                }
                TextButton(
                    enabled = !state.busy,
                    onClick = { onIntent(AccountIntent.SignOutEverywhere) },
                ) {
                    AppText(stringResource(R.string.account_sign_out_everywhere))
                }
            }
        }
    }
}

@Composable
fun AccountAuthSection(
    state: AccountUiState,
    onIntent: (AccountIntent) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val signedIn = state.session != null

    SplicedColumnGroup(title = stringResource(R.string.account_email)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = email,
                onValueChange = { email = it },
                enabled = state.configured && !state.busy,
                label = stringResource(R.string.account_email),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = password,
                onValueChange = { password = it },
                enabled = state.configured && !state.busy,
                label = stringResource(R.string.account_password),
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        AppText(if (showPassword) stringResource(R.string.hide) else stringResource(R.string.show))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            AccountActionRow {
                Button(
                    enabled = state.configured && !state.busy,
                    onClick = { onIntent(AccountIntent.SignInEmail(email, password)) },
                ) {
                    AppText(stringResource(R.string.account_sign_in))
                }
                OutlinedButton(
                    enabled = state.configured && !state.busy,
                    onClick = { onIntent(AccountIntent.SignUpEmail(email, password)) },
                ) {
                    AppText(stringResource(R.string.account_sign_up))
                }
                TextButton(
                    enabled = state.configured && !state.busy,
                    onClick = { onIntent(AccountIntent.SendPasswordReset(email)) },
                ) {
                    AppText(stringResource(R.string.account_reset_password))
                }
            }

            if (state.googleSignInAvailable) {
                OutlinedButton(
                    enabled = state.configured && !state.busy,
                    onClick = { onIntent(AccountIntent.RequestGoogleSignIn) },
                ) {
                    AppText(
                        stringResource(
                            if (signedIn) R.string.account_link_google
                            else R.string.account_sign_in_google
                        )
                    )
                }
            } else {
                AppText(stringResource(R.string.account_google_unavailable))
            }
        }
    }
}

@Composable
private fun AccountActionRow(
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
