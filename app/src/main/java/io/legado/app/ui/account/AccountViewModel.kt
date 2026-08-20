package io.legado.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.drducbook.app.auth.GoogleAuthNonce
import com.drducbook.app.auth.AuthCallbackStateStore
import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountAuthResult
import io.legado.app.domain.model.AccountRole
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.usecase.AccountAccessUseCase
import io.legado.app.domain.usecase.AccountAuthUseCase
import io.legado.app.domain.usecase.AccountCloudBackupUseCase
import io.legado.app.domain.usecase.GoogleDriveBackupUseCase
import io.legado.app.domain.usecase.SafBackupUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AccountViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val accountAuthUseCase: AccountAuthUseCase,
    private val accountAccessUseCase: AccountAccessUseCase,
    private val accountCloudBackupUseCase: AccountCloudBackupUseCase,
    private val googleDriveBackupUseCase: GoogleDriveBackupUseCase,
    private val safBackupUseCase: SafBackupUseCase,
    private val publicConfig: SupabasePublicConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountUiState(
            configured = publicConfig.isConfigured,
            googleSignInAvailable = publicConfig.googleAuthClientId.isNotBlank(),
            googleDriveAvailable = googleDriveBackupUseCase.configured,
            safBackupConfigured = safBackupUseCase.configured,
            safScheduleIntervalHours = safBackupUseCase.scheduleIntervalHours,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AccountEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var pendingGoogleNonce: String?
        get() = savedStateHandle[KEY_PENDING_GOOGLE_NONCE]
        set(value) { savedStateHandle[KEY_PENDING_GOOGLE_NONCE] = value }
    private var pendingGoogleLink: Boolean
        get() = savedStateHandle[KEY_PENDING_GOOGLE_LINK] ?: false
        set(value) { savedStateHandle[KEY_PENDING_GOOGLE_LINK] = value }
    private var activeSession: AccountSession? = null
    private var activeAccess: AccountAccess? = null
    private var roleExpiryJob: Job? = null
    private var pendingBackupPassword: String = ""
    private var pendingGoogleDrivePassword: String = ""

    init {
        AuthCallbackStateStore.consumeError(appCtx)?.let { message ->
            _effects.tryEmit(AccountEffect.ShowMessage(message))
        }
        viewModelScope.launch {
            accountAuthUseCase.observeSession()
                .catch { error ->
                    _uiState.update { it.copy(loading = false) }
                    _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage()))
                }
                .collectLatest { session ->
                    activeSession = session
                    activeAccess = null
                    roleExpiryJob?.cancel()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            configured = publicConfig.isConfigured,
                            googleSignInAvailable = publicConfig.googleAuthClientId.isNotBlank(),
                            googleDriveAvailable = googleDriveBackupUseCase.configured,
                            safBackupConfigured = safBackupUseCase.configured,
                            safScheduleIntervalHours = safBackupUseCase.scheduleIntervalHours,
                            googleDriveAuthorized = if (session == null) {
                                false
                            } else {
                                it.googleDriveAuthorized
                            },
                            session = session?.toUi(),
                            access = null,
                            quotaUsage = kotlinx.collections.immutable.persistentListOf(),
                            adminAccounts = kotlinx.collections.immutable.persistentListOf(),
                            editingAccount = null,
                            restoreConfirmationVisible = false,
                            googleDriveRestoreConfirmationVisible = false,
                        )
                    }
                    if (session != null) loadAccountData(session, showError = true)
                }
        }
    }

    fun onIntent(intent: AccountIntent) {
        when (intent) {
            AccountIntent.Refresh -> refresh()
            is AccountIntent.SignInEmail -> signInEmail(intent.email, intent.password)
            is AccountIntent.SignUpEmail -> signUpEmail(intent.email, intent.password)
            is AccountIntent.SendPasswordReset -> sendPasswordReset(intent.email)
            is AccountIntent.ChangePassword -> changePassword(
                intent.currentPassword,
                intent.newPassword,
            )
            AccountIntent.RequestGoogleSignIn -> requestGoogleSignIn()
            is AccountIntent.SubmitGoogleToken -> submitGoogleToken(intent.idToken, intent.nonce)
            AccountIntent.Reauthenticate -> reauthenticate()
            AccountIntent.SignOutLocal -> signOut(AccountIntent.SignOutLocal.signOutMode())
            AccountIntent.SignOutEverywhere -> signOut(AccountIntent.SignOutEverywhere.signOutMode())
            is AccountIntent.UploadCloudBackup -> uploadCloudBackup(intent.password)
            is AccountIntent.RequestRestoreCloudBackup -> _uiState.update {
                pendingBackupPassword = intent.password
                it.copy(restoreConfirmationVisible = true)
            }
            AccountIntent.DismissRestoreCloudBackup -> _uiState.update {
                it.copy(restoreConfirmationVisible = false)
            }
            AccountIntent.ConfirmRestoreCloudBackup -> restoreCloudBackup(pendingBackupPassword)
            is AccountIntent.UploadGoogleDriveBackup -> requestGoogleDriveAuthorization(
                GoogleDriveAction.UPLOAD,
                intent.password,
            )
            is AccountIntent.RequestRestoreGoogleDriveBackup -> _uiState.update {
                pendingBackupPassword = intent.password
                it.copy(googleDriveRestoreConfirmationVisible = true)
            }
            AccountIntent.ConfirmRestoreGoogleDriveBackup -> {
                _uiState.update { it.copy(googleDriveRestoreConfirmationVisible = false) }
                requestGoogleDriveAuthorization(GoogleDriveAction.RESTORE, pendingBackupPassword)
            }
            AccountIntent.DismissRestoreGoogleDriveBackup -> _uiState.update {
                it.copy(googleDriveRestoreConfirmationVisible = false)
            }
            AccountIntent.RequestSafFolder -> _effects.tryEmit(AccountEffect.RequestSafFolder)
            is AccountIntent.SubmitSafFolder -> submitSafFolder(intent.uri)
            AccountIntent.ClearSafFolder -> clearSafFolder()
            is AccountIntent.UploadSafBackup -> uploadSafBackup(intent.password)
            is AccountIntent.EnableSafSchedule -> enableSafSchedule(intent.intervalHours, intent.password)
            AccountIntent.DisableSafSchedule -> disableSafSchedule()
            is AccountIntent.RequestRestoreSafBackup -> {
                pendingBackupPassword = intent.password
                _uiState.update { it.copy(safRestoreConfirmationVisible = true) }
            }
            AccountIntent.ConfirmRestoreSafBackup -> restoreSafBackup(pendingBackupPassword)
            AccountIntent.DismissRestoreSafBackup -> _uiState.update {
                it.copy(safRestoreConfirmationVisible = false)
            }
            is AccountIntent.SubmitGoogleDriveAccessToken -> submitGoogleDriveAccessToken(
                intent.accessToken,
                intent.action,
            )
            is AccountIntent.GoogleDriveAuthorizationFailed -> {
                _uiState.update { it.copy(busy = false) }
                _effects.tryEmit(AccountEffect.ShowMessage(intent.message))
            }
            AccountIntent.LoadAdminAccounts -> loadAdminAccounts()
            is AccountIntent.UpdateAdminSearch -> _uiState.update {
                it.copy(adminSearchQuery = intent.query)
            }
            is AccountIntent.FilterAdminRole -> _uiState.update {
                it.copy(adminRoleFilter = intent.role)
            }
            is AccountIntent.EditAccount -> editAccount(intent.userId)
            is AccountIntent.SelectAccountRole -> selectAccountRole(intent.role)
            is AccountIntent.SetAccountRoleDurationDays -> setAccountRoleDurationDays(intent.value)
            is AccountIntent.SetAccountRolePermanent -> setAccountRolePermanent(intent.permanent)
            AccountIntent.SaveAccountRole -> saveAccountRole()
            AccountIntent.DismissAccountEditor -> _uiState.update { it.copy(editingAccount = null) }
        }
    }

    private fun refresh() = launchAuthAction {
        accountAuthUseCase.refreshSession()
        activeSession?.let { loadAccountData(it, showError = false) }
        AccountEffect.ShowMessage("Đã làm mới phiên đăng nhập")
    }

    private fun signInEmail(email: String, password: String) = launchAuthAction {
        accountAuthUseCase.signInWithEmail(email, password)
        AccountEffect.ShowMessage("Đăng nhập thành công")
    }

    private fun signUpEmail(email: String, password: String) = launchAuthAction {
        when (val result = accountAuthUseCase.signUpWithEmail(email, password)) {
            is AccountAuthResult.EmailVerificationRequired ->
                AccountEffect.ShowMessage("Hãy kiểm tra ${result.email} để xác minh tài khoản")
            is AccountAuthResult.SignedIn ->
                AccountEffect.ShowMessage("Đã tạo tài khoản Premium dùng thử 7 ngày")
            AccountAuthResult.SignedOut ->
                AccountEffect.ShowMessage("Đã đăng xuất")
        }
    }

    private fun sendPasswordReset(email: String) = launchAuthAction {
        accountAuthUseCase.sendPasswordReset(email)
        AccountEffect.ShowMessage("Đã gửi email đặt lại mật khẩu")
    }

    private fun changePassword(currentPassword: String, newPassword: String) = launchAuthAction {
        accountAuthUseCase.changePassword(currentPassword, newPassword)
        AccountEffect.ShowMessage("Đã đổi mật khẩu")
    }

    private fun requestGoogleSignIn() {
        if (!publicConfig.isConfigured) {
            _effects.tryEmit(AccountEffect.ShowMessage("Bản dựng chưa cấu hình Supabase"))
            return
        }
        if (publicConfig.googleAuthClientId.isBlank()) {
            _effects.tryEmit(AccountEffect.ShowMessage("Chưa cấu hình đăng nhập Google"))
            return
        }
        val nonce = GoogleAuthNonce.generate()
        pendingGoogleNonce = nonce
        pendingGoogleLink = activeSession != null
        _effects.tryEmit(
            AccountEffect.RequestGoogleCredential(
                clientId = publicConfig.googleAuthClientId,
                nonce = nonce,
            )
        )
    }

    private fun submitGoogleToken(idToken: String, nonce: String) = launchAuthAction {
        val expectedNonce = pendingGoogleNonce
        require(expectedNonce != null && expectedNonce == nonce) {
            "Phiên đăng nhập Google không khớp"
        }
        pendingGoogleNonce = null
        accountAuthUseCase.signInOrLinkGoogle(idToken, nonce)
        val message = if (pendingGoogleLink) {
            "Đã liên kết tài khoản Google"
        } else {
            "Đã đăng nhập bằng Google"
        }
        pendingGoogleLink = false
        AccountEffect.ShowMessage(message)
    }

    private fun reauthenticate() = launchAuthAction {
        accountAuthUseCase.reauthenticate()
        AccountEffect.ShowMessage("Đã gửi mã xác thực lại")
    }

    private fun signOut(mode: io.legado.app.domain.model.AccountSignOutMode) = launchAuthAction {
        accountAuthUseCase.signOut(mode)
        AccountEffect.ShowMessage("Đã đăng xuất")
    }

    private fun uploadCloudBackup(password: String) = launchAuthAction {
        val session = requireSession()
        val access = requireAccess()
        val receipt = accountCloudBackupUseCase.uploadLatest(session, access, password)
        _uiState.update { it.copy(lastCloudBackup = receipt.toUi()) }
        AccountEffect.ShowMessage("Đã sao lưu dữ liệu lên đám mây")
    }

    private fun restoreCloudBackup(password: String) = launchAuthAction {
        _uiState.update { it.copy(restoreConfirmationVisible = false) }
        val receipt = accountCloudBackupUseCase.restoreLatest(
            requireSession(),
            requireAccess(),
            password,
        )
        if (receipt == null) {
            AccountEffect.ShowMessage("Tài khoản chưa có bản sao lưu đám mây")
        } else {
            _uiState.update { it.copy(lastCloudBackup = receipt.toUi()) }
            AccountEffect.ShowMessage("Đã khôi phục bản sao lưu và kiểm tra SHA-256")
        }
    }

    private fun submitSafFolder(rawUri: String) = viewModelScope.launch {
        runCatching { safBackupUseCase.setTreeUri(Uri.parse(rawUri)) }
            .onSuccess {
                _uiState.update { it.copy(safBackupConfigured = true) }
                _effects.tryEmit(AccountEffect.ShowMessage("Đã chọn thư mục SAF sao lưu"))
            }
            .onFailure { error -> _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage())) }
    }

    private fun clearSafFolder() = viewModelScope.launch {
        runCatching { safBackupUseCase.clearTreeUri() }
            .onSuccess {
                _uiState.update {
                    it.copy(safBackupConfigured = false, safScheduleIntervalHours = null)
                }
                _effects.tryEmit(AccountEffect.ShowMessage("Đã bỏ thư mục SAF và lịch sao lưu"))
            }
            .onFailure { error -> _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage())) }
    }

    private fun uploadSafBackup(password: String) = viewModelScope.launch {
        _uiState.update { it.copy(busy = true) }
        runCatching { safBackupUseCase.uploadLatest(password) }
            .onSuccess { size ->
                _effects.tryEmit(AccountEffect.ShowMessage("Đã sao lưu SAF (${size} bytes)"))
            }
            .onFailure { error -> _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage())) }
        _uiState.update { it.copy(busy = false, safBackupConfigured = safBackupUseCase.configured) }
    }

    private fun enableSafSchedule(intervalHours: Long, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(busy = true) }
        runCatching { safBackupUseCase.setSchedule(intervalHours, password) }
            .onSuccess {
                _uiState.update { it.copy(safScheduleIntervalHours = intervalHours) }
                _effects.tryEmit(AccountEffect.ShowMessage("Đã bật sao lưu SAF định kỳ"))
            }
            .onFailure { error -> _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage())) }
        _uiState.update { it.copy(busy = false) }
    }

    private fun disableSafSchedule() = viewModelScope.launch {
        runCatching { safBackupUseCase.clearSchedule() }
            .onSuccess {
                _uiState.update { it.copy(safScheduleIntervalHours = null) }
                _effects.tryEmit(AccountEffect.ShowMessage("Đã tắt sao lưu SAF định kỳ"))
            }
            .onFailure { error -> _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage())) }
    }

    private fun restoreSafBackup(password: String) = viewModelScope.launch {
        _uiState.update { it.copy(busy = true, safRestoreConfirmationVisible = false) }
        runCatching { safBackupUseCase.restoreLatest(password) }
            .onSuccess { restored ->
                _effects.tryEmit(
                    AccountEffect.ShowMessage(
                        if (restored) "Đã khôi phục bản sao SAF" else "Chưa có bản sao SAF",
                    ),
                )
            }
            .onFailure { error -> _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage())) }
        _uiState.update { it.copy(busy = false) }
    }

    private fun requestGoogleDriveAuthorization(action: GoogleDriveAction, password: String) {
        runCatching {
            requireSession()
            requireAccess()
            check(googleDriveBackupUseCase.configured) { "Google Drive chưa sẵn sàng" }
        }.onFailure { error ->
            _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage()))
            return
        }
        _uiState.update { it.copy(busy = true) }
        pendingGoogleDrivePassword = password
        _effects.tryEmit(AccountEffect.RequestGoogleDriveAuthorization(action))
    }

    private fun submitGoogleDriveAccessToken(
        accessToken: String,
        action: GoogleDriveAction,
    ) {
        viewModelScope.launch {
            runCatching {
                val receipt = when (action) {
                    GoogleDriveAction.UPLOAD -> googleDriveBackupUseCase.uploadLatest(
                        accessToken = accessToken,
                        session = requireSession(),
                        access = requireAccess(),
                        password = pendingGoogleDrivePassword,
                    )
                    GoogleDriveAction.RESTORE -> googleDriveBackupUseCase.restoreLatest(
                        accessToken = accessToken,
                        session = requireSession(),
                        access = requireAccess(),
                        password = pendingGoogleDrivePassword,
                    )
                }
                _uiState.update {
                    it.copy(
                        googleDriveAuthorized = true,
                        lastGoogleDriveBackup = receipt?.toUi() ?: it.lastGoogleDriveBackup,
                    )
                }
                when {
                    action == GoogleDriveAction.UPLOAD -> "Đã sao lưu dữ liệu lên Google Drive"
                    receipt == null -> "Google Drive chưa có bản sao lưu"
                    else -> "Đã khôi phục bản sao Google Drive và kiểm tra SHA-256"
                }
            }.onSuccess { message ->
                _effects.tryEmit(AccountEffect.ShowMessage(message))
            }.onFailure { error ->
                _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage()))
            }
            _uiState.update { it.copy(busy = false) }
            pendingGoogleDrivePassword = ""
        }
    }

    private fun loadAdminAccounts() = launchAuthAction {
        val requester = requireAccess()
        val accounts = accountAccessUseCase.listAccounts(requester)
            .map(AccountAccess::toAdminUi)
            .toImmutableList()
        _uiState.update { it.copy(adminAccounts = accounts) }
        AccountEffect.ShowMessage("Đã tải danh sách tài khoản")
    }

    private fun editAccount(userId: String) {
        if (activeAccess?.effectiveRole() != AccountRole.ADMIN) return
        val account = _uiState.value.adminAccounts.firstOrNull { it.userId == userId } ?: return
        _uiState.update { it.copy(editingAccount = account) }
    }

    private fun selectAccountRole(role: AccountRole) {
        if (activeAccess?.effectiveRole() != AccountRole.ADMIN) return
        _uiState.update { state ->
            val editing = state.editingAccount ?: return@update state
            state.copy(
                editingAccount = editing.copy(
                    selectedRole = role,
                    selectedDurationDays = if (role == AccountRole.FREE) {
                        ""
                    } else {
                        editing.selectedDurationDays
                    },
                    selectedPermanent = when {
                        role == AccountRole.FREE -> false
                        editing.selectedRole == AccountRole.FREE -> true
                        else -> editing.selectedPermanent
                    },
                ),
            )
        }
    }

    private fun setAccountRoleDurationDays(value: String) {
        if (activeAccess?.effectiveRole() != AccountRole.ADMIN) return
        if (value.any { !it.isDigit() }) return
        _uiState.update { state ->
            state.copy(
                editingAccount = state.editingAccount?.copy(
                    selectedDurationDays = value.take(5),
                    selectedPermanent = false,
                ),
            )
        }
    }

    private fun setAccountRolePermanent(permanent: Boolean) {
        if (activeAccess?.effectiveRole() != AccountRole.ADMIN) return
        _uiState.update { state ->
            val editing = state.editingAccount ?: return@update state
            if (editing.selectedRole == AccountRole.FREE) return@update state
            state.copy(
                editingAccount = editing.copy(selectedPermanent = permanent),
            )
        }
    }

    private fun saveAccountRole() = launchAuthAction {
        val requester = requireAccess()
        val editing = requireNotNull(_uiState.value.editingAccount)
        val durationDays = editing.selectedDurationDays
            .takeIf(String::isNotBlank)
            ?.toLongOrNull()
        require(
            editing.selectedRole == AccountRole.FREE ||
                editing.selectedPermanent ||
                durationDays != null && durationDays > 0L
        ) {
            "Số ngày hiệu lực phải lớn hơn 0"
        }
        val now = System.currentTimeMillis()
        val startsAt = if (
            editing.selectedRole == AccountRole.FREE || editing.selectedPermanent
        ) {
            null
        } else {
            now
        }
        val expiresAt = if (
            editing.selectedRole == AccountRole.FREE || editing.selectedPermanent
        ) {
            null
        } else {
            Math.addExact(now, Math.multiplyExact(requireNotNull(durationDays), MILLIS_PER_DAY))
        }
        val updated = accountAccessUseCase.updateAccess(
            requester = requester,
            userId = editing.userId,
            role = editing.selectedRole,
            roleStartsAtEpochMillis = startsAt,
            roleExpiresAtEpochMillis = expiresAt,
        )
        _uiState.update { state ->
            state.copy(
                editingAccount = null,
                adminAccounts = state.adminAccounts.map { account ->
                    if (account.userId == updated.userId) updated.toAdminUi() else account
                }.toImmutableList(),
            )
        }
        if (updated.userId == requester.userId) {
            activeSession?.let { loadAccountData(it, showError = false) }
        }
        AccountEffect.ShowMessage("Đã cập nhật hạng tài khoản")
    }

    private suspend fun loadAccountData(session: AccountSession, showError: Boolean) {
        val accessResult = runCatching {
            accountAccessUseCase.getAccess(session.userId)
        }
        accessResult.onSuccess { access ->
            activeAccess = access
            _uiState.update { it.copy(access = access.toUi()) }
            roleExpiryJob?.cancel()
            access.roleExpiresAtEpochMillis?.let { expiresAt ->
                val remainingMillis = expiresAt - System.currentTimeMillis()
                if (remainingMillis > 0L) {
                    roleExpiryJob = viewModelScope.launch {
                        delay(remainingMillis)
                        _uiState.update { state -> state.copy(access = access.toUi()) }
                    }
                }
            }
        }.onFailure { error ->
            if (showError) {
                _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage()))
            }
        }

        runCatching {
            accountAccessUseCase.getDailyQuotaUsage()
        }.onSuccess { quota ->
            _uiState.update {
                it.copy(quotaUsage = quota.map { usage -> usage.toUi() }.toImmutableList())
            }
        }.onFailure { error ->
            if (showError) {
                _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage()))
            }
        }
    }

    private fun requireSession(): AccountSession = requireNotNull(activeSession) {
        "Cần đăng nhập để sử dụng tính năng này"
    }

    private fun requireAccess(): AccountAccess = requireNotNull(activeAccess) {
        "Chưa tải được quyền tài khoản"
    }

    private fun launchAuthAction(block: suspend () -> AccountEffect.ShowMessage) {
        if (!publicConfig.isConfigured) {
            _effects.tryEmit(AccountEffect.ShowMessage("Bản dựng chưa cấu hình Supabase"))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            runCatching { block() }
                .onSuccess { effect -> _effects.tryEmit(effect) }
                .onFailure { error -> _effects.tryEmit(AccountEffect.ShowMessage(error.userMessage())) }
            _uiState.update { it.copy(busy = false) }
        }
    }

    private fun Throwable.userMessage(): String {
        val raw = message?.takeIf(String::isNotBlank) ?: return "Không thể xác thực tài khoản"
        return when {
            "Invalid login credentials" in raw -> "Email hoặc mật khẩu không đúng"
            "Email not confirmed" in raw -> "Email chưa được xác minh"
            raw.contains("access_denied", ignoreCase = true) ||
                raw.contains("not completed verification", ignoreCase = true) ->
                "Google đang chặn DrDucBook vì ứng dụng đang ở chế độ kiểm thử. Hãy thêm email vào Test users hoặc hoàn tất xác minh OAuth."
            raw.contains("DEVELOPER_ERROR", ignoreCase = true) || raw.contains("status code: 10") ->
                "Cấu hình Google chưa khớp với bản đang cài. Hãy kiểm tra OAuth client, package com.drducbook.app và SHA-1."
            "PGRST205" in raw && "account_access" in raw ->
                "Supabase chưa áp dụng migration account_access; ứng dụng đang dùng quyền Free tạm thời"
            ("role_starts_at" in raw || "role_expires_at" in raw) &&
                raw.contains("PGRST", ignoreCase = true) ->
                "Supabase chưa áp dụng migration quyền có thời hạn"
            raw.contains("admin_update_account_access", ignoreCase = true) &&
                raw.contains("PGRST202", ignoreCase = true) ->
                "Supabase chưa cập nhật hàm phân quyền có thời hạn"
            raw.contains("default administrator must remain permanent", ignoreCase = true) ->
                "Tài khoản quản trị mặc định phải giữ quyền Admin vĩnh viễn"
            "PGRST205" in raw && ("sync_" in raw || "storage" in raw) ->
                "Supabase chưa áp dụng migration đồng bộ/sao lưu"
            "PGRST202" in raw && "quota" in raw.lowercase() ->
                "Supabase chưa deploy hàm hạn mức tài khoản"
            "Requested function was not found" in raw ->
                "Supabase chưa deploy Edge Function cần thiết"
            "daily_quota_exceeded" in raw -> "Đã hết hạn mức sử dụng trong ngày"
            else -> raw
        }
    }

    private companion object {
        const val KEY_PENDING_GOOGLE_NONCE = "account.pendingGoogleNonce"
        const val KEY_PENDING_GOOGLE_LINK = "account.pendingGoogleLink"
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}
