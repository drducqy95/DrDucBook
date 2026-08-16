package io.legado.app.ui.ai.router

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.ui.ai.bubble.ChatBubbleCoordinator
import io.legado.app.ui.ai.context.AiScreenContextRegistry
import io.legado.app.ui.ai.context.AiScreenContextSnapshot
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.FilteredOpenDocumentContract
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AiRouterRouteScreen(
    onBackClick: () -> Unit,
    viewModel: AiRouterViewModel = koinViewModel(),
) {
    val localGgufPicker = rememberLauncherForActivityResult(
        contract = FilteredOpenDocumentContract(
            primaryMimeType = "application/octet-stream",
            persistableAccess = false,
        ),
    ) { uri ->
        uri?.let { viewModel.onIntent(AiRouterIntent.LocalGgufSelected(it.toString())) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            if (effect == AiRouterEffect.OpenLocalGgufPicker) {
                localGgufPicker.launch(arrayOf("application/octet-stream", "application/x-gguf"))
            }
        }
    }
    AiRouterScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRouterScreen(
    state: AiRouterUiState,
    effects: Flow<AiRouterEffect>,
    onIntent: (AiRouterIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val hasSecretEditor = when (val editor = state.editor) {
        is AiRouterEditor.Credential -> true
        is AiRouterEditor.ProviderConfig -> editor.authType != AiProviderAuthType.NONE
        else -> false
    }

    DisposableEffect(hasSecretEditor) {
        if (hasSecretEditor) {
            AiScreenContextRegistry.register(
                AiScreenContextSnapshot(
                    ownerId = AI_ROUTER_SECRET_CONTEXT_OWNER,
                    screen = "AiRouterSecretEditor",
                    sensitive = true,
                )
            )
            ChatBubbleCoordinator.refresh()
        }
        onDispose {
            if (hasSecretEditor) {
                AiScreenContextRegistry.clear(AI_ROUTER_SECRET_CONTEXT_OWNER)
                ChatBubbleCoordinator.refresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiRouterEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is AiRouterEffect.OpenUrl -> uriHandler.openUri(effect.url)
                AiRouterEffect.OpenLocalGgufPicker -> Unit
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = "AI Router",
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            AiRouterTabs(
                selected = state.selectedTab,
                onSelected = { onIntent(AiRouterIntent.SelectTab(it)) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = 0.dp,
                    bottom = 120.dp,
                ),
            ) {
            if (state.selectedTab == AiRouterTab.OVERVIEW) {
                item { AiRouterHealthSummarySection(state.healthSummary) }
                item { AiRouterProviderPoolSummarySection(state) }
            }

            if (state.selectedTab == AiRouterTab.COMBOS) {
            item {
                AiRouterRouteCardsSection(
                    routes = state.routes,
                    onOpenRoute = { routeId -> onIntent(AiRouterIntent.OpenRoute(routeId)) },
                    onOpenTarget = { routeId, targetId ->
                        onIntent(AiRouterIntent.OpenTarget(routeId, targetId))
                    },
                )
            }

            item {
                SplicedColumnGroup(title = "Combo fallback mẫu") {
                    SettingItem(
                        title = "Chọn theo model",
                        description = "Router tự xoay tài khoản/API key đang bật của provider tương ứng.",
                    )
                    state.comboTemplates.forEach { combo ->
                        ClickableSettingItem(
                            title = combo.name,
                            description = combo.description,
                            onClick = {
                                onIntent(AiRouterIntent.CreateComboTemplate(combo.id))
                            },
                        )
                    }
                }
            }

            }

            if (state.selectedTab == AiRouterTab.API_KEYS) {
            item {
                AiRouterProviderGridSection(
                    searchQuery = state.providerSearchQuery,
                    filters = state.providerFilters,
                    selectedFilter = state.providerFilter,
                    providers = state.filteredProviderDashboardItems,
                    onSearchChange = { onIntent(AiRouterIntent.UpdateProviderSearch(it)) },
                    onFilterChange = { onIntent(AiRouterIntent.SelectProviderFilter(it)) },
                    onProviderClick = { provider ->
                        if (provider.category == AiRouterProviderFilter.OAUTH) {
                            onIntent(AiRouterIntent.OpenProviderCredentials(provider.id))
                        } else {
                            onIntent(AiRouterIntent.OpenProviderConfig(provider.id))
                        }
                    },
                )
            }

            item {
                SplicedColumnGroup(title = "Credential bảo mật") {
                    state.credentials
                        .filter { it.oauthProvider == null }
                        .forEach { credential ->
                        ClickableSettingItem(
                            title = credential.label,
                            description = buildString {
                                append(credential.providerName).append(" · ").append(credential.kind)
                                credential.accountLabel?.let { append(" · ").append(it) }
                                if (credential.consecutiveFailures > 0) {
                                    append(" · ").append(credential.consecutiveFailures)
                                        .append(" lỗi liên tiếp")
                                }
                            },
                            option = when {
                                !credential.enabled -> "Tắt"
                                credential.status == "relogin_required" -> "Cần đăng nhập lại"
                                credential.status == "refreshing" -> "Đang làm mới"
                                !credential.hasSecret -> "Thiếu token"
                                credential.lastFailureKind != null -> credential.lastFailureKind
                                else -> "Sẵn sàng"
                            },
                            onClick = { onIntent(AiRouterIntent.OpenCredential(credential.id)) },
                        )
                    }
                    ClickableSettingItem(
                        title = "+ Thêm API key/token",
                        description = "Chỉ dùng credential do provider chính thức cấp.",
                        onClick = { onIntent(AiRouterIntent.OpenCredential()) },
                    )
                }
            }
            }

            if (state.selectedTab == AiRouterTab.SIGN_IN) {
                item {
                    AiRouterOAuthAccountsSection(
                        state = state,
                        onOpenProvider = { onIntent(AiRouterIntent.OpenProviderCredentials(it)) },
                        onOpenCredential = { onIntent(AiRouterIntent.OpenCredential(it)) },
                    )
                }
            }

            if (state.selectedTab == AiRouterTab.MODELS) {
                item {
                    AiRouterModelLibrarySection(
                        state = state,
                        onOpenProvider = { providerId ->
                            val provider = state.providerDashboardItems.firstOrNull { it.id == providerId }
                            if (provider?.category == AiRouterProviderFilter.OAUTH) {
                                onIntent(AiRouterIntent.OpenProviderCredentials(providerId))
                            } else {
                                onIntent(AiRouterIntent.OpenProviderConfig(providerId))
                            }
                        },
                    )
                }
            }

            if (state.selectedTab == AiRouterTab.LOGS || state.selectedTab == AiRouterTab.OVERVIEW) {
            item { AiRouterDiagnosticsSection(state.diagnostics) }
            item { AiRouterAttemptHistorySection(state.attempts) }
            }
            }
        }
    }

    AppModalBottomSheet(
        show = state.editor != null,
        onDismissRequest = { onIntent(AiRouterIntent.DismissEditor) },
        title = editorTitle(state.editor),
        endAction = {
            TextButton(
                enabled = state.editor is AiRouterEditor.ProviderCredentials || !state.saving,
                onClick = {
                    onIntent(
                        when (state.editor) {
                            is AiRouterEditor.Credential -> AiRouterIntent.SaveCredential
                            is AiRouterEditor.ProviderConfig -> AiRouterIntent.SaveProviderConfig
                            is AiRouterEditor.ProviderCredentials -> AiRouterIntent.DismissEditor
                            is AiRouterEditor.Route -> AiRouterIntent.SaveRoute
                            is AiRouterEditor.Target -> AiRouterIntent.SaveTarget
                            null -> AiRouterIntent.DismissEditor
                        }
                    )
                },
            ) {
                Text(if (state.editor is AiRouterEditor.ProviderCredentials) "Đóng" else if (state.saving) "Đang lưu…" else "Lưu")
            }
        },
    ) {
        when (val editor = state.editor) {
            is AiRouterEditor.Credential -> CredentialEditor(
                state = state,
                editor = editor,
                onChange = { onIntent(AiRouterIntent.UpdateCredential(it)) },
                onSyncOAuth = editor.id?.let { id ->
                    { onIntent(AiRouterIntent.SyncOAuthModels(id)) }
                },
                onDelete = editor.id?.let { id ->
                    { onIntent(AiRouterIntent.DeleteCredential(id)) }
                },
                onResetHealth = editor.id?.let { id ->
                    { onIntent(AiRouterIntent.ResetCredentialHealth(id)) }
                },
            )

            is AiRouterEditor.ProviderConfig -> ProviderConfigEditor(
                editor = editor,
                credentials = state.credentials.filter {
                    it.providerId == editor.providerProfileId && it.oauthProvider == null
                },
                saving = state.saving,
                onChange = { onIntent(AiRouterIntent.UpdateProviderConfig(it)) },
                onTest = { onIntent(AiRouterIntent.TestProviderConfig) },
                onOpenLocalGgufCatalog = { onIntent(AiRouterIntent.OpenLocalGgufCatalog) },
                onChooseLocalGguf = { onIntent(AiRouterIntent.ChooseLocalGguf) },
                onOpenCredential = { onIntent(AiRouterIntent.OpenCredential(it)) },
                onAddCredential = {
                    onIntent(
                        AiRouterIntent.OpenCredentialForProvider(
                            providerId = editor.providerProfileId ?: "catalog_${editor.catalogId}",
                            providerName = editor.name,
                        )
                    )
                },
            )

            is AiRouterEditor.ProviderCredentials -> ProviderCredentialPoolEditor(
                editor = editor,
                credentials = state.credentials.filter { credential ->
                    if (editor.oauthProviderId != null) {
                        credential.oauthProvider == editor.oauthProviderId
                    } else {
                        credential.providerId == editor.providerProfileId
                    }
                },
                models = state.models,
                saving = state.saving,
                onStartOAuth = { onIntent(AiRouterIntent.StartOAuth(it)) },
                onSyncOAuth = { onIntent(AiRouterIntent.SyncOAuthModels(it)) },
                onOpenCredential = { onIntent(AiRouterIntent.OpenCredential(it)) },
                onAddCredential = {
                    onIntent(
                        AiRouterIntent.OpenCredentialForProvider(
                            providerId = editor.providerProfileId.orEmpty(),
                            providerName = editor.name,
                        )
                    )
                },
            )

            is AiRouterEditor.Route -> RouteEditor(
                editor = editor,
                onChange = { onIntent(AiRouterIntent.UpdateRoute(it)) },
                onDelete = editor.id?.let { id ->
                    { onIntent(AiRouterIntent.DeleteRoute(id)) }
                },
            )

            is AiRouterEditor.Target -> TargetEditor(
                state = state,
                editor = editor,
                onChange = { onIntent(AiRouterIntent.UpdateTarget(it)) },
                onDelete = editor.id?.let { id ->
                    { onIntent(AiRouterIntent.DeleteTarget(id)) }
                },
                onResetHealth = editor.id?.let { id ->
                    { onIntent(AiRouterIntent.ResetTargetHealth(id)) }
                },
            )

            null -> Unit
        }
    }
}

@Composable
private fun AiRouterTabs(
    selected: AiRouterTab,
    onSelected: (AiRouterTab) -> Unit,
) {
    val tabs = AiRouterTab.entries
    AppTabRow(
        tabTitles = tabs.map { it.label() },
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        onTabSelected = { index -> tabs.getOrNull(index)?.let(onSelected) },
    )
}

@Composable
private fun AiRouterProviderPoolSummarySection(
    state: AiRouterUiState,
) {
    val oauthAccountCount = state.credentials.count { it.oauthProvider != null }
    val apiKeyCount = state.credentials.count { it.oauthProvider == null && it.hasSecret }
    SplicedColumnGroup(title = "Provider pool") {
        ClickableSettingItem(
            title = "${state.models.size} model · $apiKeyCount API key · $oauthAccountCount tài khoản",
            description = "Combo chỉ giữ thứ tự model; router tự chọn credential đang bật của provider khi chạy.",
            option = "${state.routes.size} combo",
            onClick = {},
        )
    }
}

@Composable
private fun AiRouterOAuthAccountsSection(
    state: AiRouterUiState,
    onOpenProvider: (String) -> Unit,
    onOpenCredential: (String) -> Unit,
) {
    val oauthItems = state.providerDashboardItems.filter { it.category == AiRouterProviderFilter.OAUTH }
    SplicedColumnGroup(title = "Đăng nhập OAuth") {
        if (oauthItems.isEmpty()) {
            SettingItem(title = "Chưa có provider OAuth")
        }
        oauthItems.forEach { provider ->
            val accounts = state.credentials.filter { it.oauthProvider == provider.id }
            ClickableSettingItem(
                title = provider.name,
                description = listOf(
                    provider.notice,
                    "${accounts.size} tài khoản đã thêm",
                ).filter(String::isNotBlank).joinToString("\n"),
                option = if (provider.statusLabel.isNotBlank()) provider.statusLabel else provider.authLabel,
                onClick = { onOpenProvider(provider.id) },
            )
            accounts.forEach { credential ->
                val modelCount = state.models.count { it.providerId == credential.providerId }
                ClickableSettingItem(
                    title = "↳ ${credential.accountLabel ?: credential.label}",
                    description = buildString {
                        append(credential.label)
                        append("\n").append(modelCount).append(" model đã đồng bộ")
                        credential.expiresAt?.let {
                            append(" · token hết hạn ").append(formatCredentialExpiry(it))
                        }
                        append("\nHạn mức: provider không cung cấp số dư qua API")
                    },
                    option = credentialStatusOption(credential),
                    onClick = { onOpenCredential(credential.id) },
                )
            }
        }
    }
}

@Composable
private fun AiRouterModelLibrarySection(
    state: AiRouterUiState,
    onOpenProvider: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val providerNameById = state.providers.associate { it.id to it.name }
    val providerDashboardByProfileId = state.providerDashboardItems
        .mapNotNull { item -> item.providerProfileId?.let { it to item } }
        .toMap()
    val filteredModels = remember(state.models, providerNameById, query) {
        filterAiRouterLibraryModels(
            models = state.models,
            providerNameById = providerNameById,
            query = query,
        )
    }
    val modelsByProvider = filteredModels
        .groupBy(AiRouterModelUi::providerId)
        .toSortedMap(
            compareBy<String> { providerId ->
                providerNameById[providerId].orEmpty().ifBlank { providerId }
            }
        )
    Column(modifier = Modifier.fillMaxWidth()) {
        SearchBar(
            query = query,
            backgroundColor = io.legado.app.ui.theme.LegadoTheme.colorScheme.onSheetContent,
            onQueryChange = { query = it },
            placeholder = "Tìm trong ${state.models.size} model",
            autoFocus = false,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (modelsByProvider.isEmpty()) {
                item(key = "empty_models", contentType = "message") {
                    SettingItem(title = "Không có model phù hợp")
                }
            } else {
                modelsByProvider.forEach { (providerId, models) ->
                    val providerName = providerNameById[providerId].orEmpty().ifBlank { providerId }
                    val dashboard = providerDashboardByProfileId[providerId]
                    item(key = "provider_$providerId", contentType = "provider") {
                        ClickableSettingItem(
                            title = providerName,
                            description = "${models.size} model · ${dashboard?.credentialCount ?: 0} credential",
                            option = dashboard?.statusLabel,
                            onClick = {
                                if (dashboard != null) {
                                    onOpenProvider(dashboard.id)
                                }
                            },
                        )
                    }
                    items(
                        items = models,
                        key = { model -> "model_${model.id}" },
                        contentType = { "model" },
                    ) { model ->
                        ClickableSettingItem(
                            title = "↳ ${model.label.ifBlank { model.id }}",
                            description = model.id,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

internal fun filterAiRouterLibraryModels(
    models: List<AiRouterModelUi>,
    providerNameById: Map<String, String>,
    query: String,
): List<AiRouterModelUi> {
    val normalizedQuery = normalizeAiRouterSearch(query.trim())
    if (normalizedQuery.isBlank()) return models
    return models.filter { model ->
        normalizeAiRouterSearch(model.label).contains(normalizedQuery) ||
            normalizeAiRouterSearch(model.id).contains(normalizedQuery) ||
            normalizeAiRouterSearch(providerNameById[model.providerId].orEmpty())
                .contains(normalizedQuery)
    }
}

private fun credentialStatusOption(credential: AiRouterCredentialUi): String = when {
    !credential.enabled -> "Tắt"
    credential.status == "relogin_required" -> "Cần đăng nhập"
    credential.status == "refreshing" -> "Đang làm mới"
    !credential.hasSecret -> "Thiếu token"
    credential.lastFailureKind != null -> credential.lastFailureKind
    else -> "Sẵn sàng"
}

private fun AiRouterTab.label(): String = when (this) {
    AiRouterTab.OVERVIEW -> "Tổng quan"
    AiRouterTab.SIGN_IN -> "Đăng nhập"
    AiRouterTab.API_KEYS -> "API key"
    AiRouterTab.COMBOS -> "Combo"
    AiRouterTab.MODELS -> "Model"
    AiRouterTab.LOGS -> "Log"
}

private const val AI_ROUTER_SECRET_CONTEXT_OWNER = "ai_router_secret_editor"

@Composable
private fun ProviderCredentialPoolEditor(
    editor: AiRouterEditor.ProviderCredentials,
    credentials: List<AiRouterCredentialUi>,
    models: List<AiRouterModelUi>,
    saving: Boolean,
    onStartOAuth: (String) -> Unit,
    onSyncOAuth: (String) -> Unit,
    onOpenCredential: (String) -> Unit,
    onAddCredential: () -> Unit,
) {
    val accountCount = credentials.count { it.oauthProvider != null }
    val apiKeyCount = credentials.count { it.oauthProvider == null }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingItem(
            title = editor.name,
            description = listOf(
                editor.connectionMode,
                editor.authLabel,
                editor.notice,
            ).filter(String::isNotBlank).joinToString(" · "),
            option = editor.statusLabel,
        )
        SettingItem(
            title = "$accountCount tài khoản · $apiKeyCount API key/token",
            description = "Combo chỉ chọn model; router tự luân phiên credential đang bật của provider này.",
        )
        editor.oauthProviderId?.let { providerId ->
            ClickableSettingItem(
                title = "+ Đăng nhập thêm tài khoản",
                description = if (saving) {
                    "Đã nhận yêu cầu đăng nhập; đang đổi token, lấy project và lưu tài khoản."
                } else {
                    "Mỗi lần đăng nhập sẽ thêm một tài khoản vào pool của provider."
                },
                option = when {
                    saving -> "Đang hoàn tất…"
                    !editor.oauthAvailable -> "Chưa sẵn sàng"
                    else -> null
                },
                onClick = { onStartOAuth(providerId) },
            )
        }
        if (editor.supportsApiKey && !editor.providerProfileId.isNullOrBlank()) {
            ClickableSettingItem(
                title = "+ Thêm API key/token",
                description = "Thêm key vào pool của provider hiện tại.",
                onClick = onAddCredential,
            )
        }
        if (credentials.isEmpty()) {
            SettingItem(
                title = if (saving) "Đang hoàn tất đăng nhập" else "Chưa có thông tin xác thực",
                description = if (saving) {
                    "Tài khoản sẽ xuất hiện ngay sau khi ứng dụng lấy project và lưu token xong."
                } else {
                    "Đăng nhập tài khoản hoặc thêm API key để sử dụng model của provider."
                },
            )
        } else {
            credentials.forEach { credential ->
                val modelCount = models.count { it.providerId == credential.providerId }
                ClickableSettingItem(
                    title = credential.accountLabel ?: credential.label,
                    description = buildString {
                        append(credential.kind)
                        if (credential.oauthProvider != null) {
                            append(" · OAuth · ").append(modelCount).append(" model")
                            credential.expiresAt?.let {
                                append("\nToken hết hạn: ").append(formatCredentialExpiry(it))
                            }
                            append("\nHạn mức: provider không cung cấp số dư qua API")
                        }
                        if (credential.consecutiveFailures > 0) {
                            append(" · ").append(credential.consecutiveFailures).append(" lỗi liên tiếp")
                        }
                    },
                    option = credentialStatusOption(credential),
                    onClick = { onOpenCredential(credential.id) },
                )
                if (credential.oauthProvider != null) {
                    ClickableSettingItem(
                        title = "↳ Kiểm tra tài khoản và đồng bộ model",
                        description = "Gửi yêu cầu nhỏ để xác nhận từng model thực sự dùng được.",
                        onClick = { onSyncOAuth(credential.id) },
                    )
                }
            }
        }
    }
}

private fun formatCredentialExpiry(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

@Composable
private fun CredentialEditor(
    state: AiRouterUiState,
    editor: AiRouterEditor.Credential,
    onChange: (AiRouterEditor.Credential) -> Unit,
    onSyncOAuth: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onResetHealth: (() -> Unit)?,
) {
    val credential = editor.id?.let { id -> state.credentials.firstOrNull { it.id == id } }
    if (editor.kind == AiCredentialKind.OAUTH_ACCESS_TOKEN && credential != null) {
        val modelCount = state.models.count { it.providerId == credential.providerId }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingItem(
                title = credential.accountLabel ?: credential.label,
                description = listOf(
                    credential.providerName,
                    "Trạng thái: ${credentialStatusOption(credential)}",
                    credential.expiresAt?.let { "Token hết hạn: ${formatCredentialExpiry(it)}" },
                    "$modelCount model đã đồng bộ",
                    "Hạn mức: provider không cung cấp số dư qua API",
                ).filterNotNull().joinToString("\n"),
            )
            onSyncOAuth?.let { sync ->
                ClickableSettingItem(
                    title = "Kiểm tra tài khoản và đồng bộ model",
                    description = "Làm mới token và thử các model bằng một yêu cầu ngắn.",
                    onClick = sync,
                )
            }
            SwitchSettingItem(
                title = "Cho phép sử dụng",
                checked = editor.enabled,
                onCheckedChange = { onChange(editor.copy(enabled = it)) },
            )
            EditorActions(onResetHealth, onDelete)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownListSettingItem(
            title = "Provider",
            selectedValue = editor.providerId,
            displayEntries = state.providers.map(AiRouterProviderUi::name).toTypedArray(),
            entryValues = state.providers.map(AiRouterProviderUi::id).toTypedArray(),
            onValueChange = { onChange(editor.copy(providerId = it)) },
        )
        AppTextField(
            value = editor.label,
            onValueChange = { onChange(editor.copy(label = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Tên credential",
            singleLine = true,
        )
        DropdownListSettingItem(
            title = "Loại xác thực",
            selectedValue = editor.kind,
            displayEntries = arrayOf("API key", "Bearer token", "OAuth access token"),
            entryValues = arrayOf(
                AiCredentialKind.API_KEY,
                AiCredentialKind.BEARER_TOKEN,
                AiCredentialKind.OAUTH_ACCESS_TOKEN,
            ),
            onValueChange = { onChange(editor.copy(kind = it)) },
        )
        AppTextField(
            value = editor.secret,
            onValueChange = { onChange(editor.copy(secret = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = if (editor.hasStoredSecret) "Token mới (để trống để giữ nguyên)" else "API key/token",
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        SwitchSettingItem(
            title = "Cho phép sử dụng",
            checked = editor.enabled,
            onCheckedChange = { onChange(editor.copy(enabled = it)) },
        )
        EditorActions(onResetHealth, onDelete)
    }
}

@Composable
private fun RouteEditor(
    editor: AiRouterEditor.Route,
    onChange: (AiRouterEditor.Route) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = editor.name,
            onValueChange = { onChange(editor.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Tên tuyến",
            singleLine = true,
        )
        DropdownListSettingItem(
            title = "Tác vụ",
            selectedValue = editor.taskType,
            displayEntries = aiRouterTaskTypes.map(::taskLabel).toTypedArray(),
            entryValues = aiRouterTaskTypes.toTypedArray(),
            onValueChange = { onChange(editor.copy(taskType = it)) },
        )
        DropdownListSettingItem(
            title = "Chiến lược",
            selectedValue = editor.strategy,
            displayEntries = arrayOf("Ưu tiên", "Luân phiên", "Luân phiên có trọng số"),
            entryValues = arrayOf(
                AiRouteStrategy.PRIORITY,
                AiRouteStrategy.ROUND_ROBIN,
                AiRouteStrategy.WEIGHTED_ROUND_ROBIN,
            ),
            onValueChange = { onChange(editor.copy(strategy = it)) },
        )
        NumericField("Số lượt thử tối đa", editor.maxAttempts) {
            onChange(editor.copy(maxAttempts = it))
        }
        SwitchSettingItem(
            title = "Giữ model theo phiên",
            description = "Chat hoặc sách đang xử lý tiếp tục dùng cùng đích nếu đích còn khoẻ.",
            checked = editor.stickySession,
            onCheckedChange = { onChange(editor.copy(stickySession = it)) },
        )
        SwitchSettingItem(
            title = "Bật tuyến",
            checked = editor.enabled,
            onCheckedChange = { onChange(editor.copy(enabled = it)) },
        )
        EditorActions(onResetHealth = null, onDelete = onDelete)
    }
}

@Composable
private fun TargetEditor(
    state: AiRouterUiState,
    editor: AiRouterEditor.Target,
    onChange: (AiRouterEditor.Target) -> Unit,
    onDelete: (() -> Unit)?,
    onResetHealth: (() -> Unit)?,
) {
    var showModelComboPicker by remember(editor.id, editor.routeProfileId) { mutableStateOf(false) }
    val selectedModelIds = editor.selectedModelProfileIds
        .takeIf { it.isNotEmpty() }
        ?: listOf(editor.modelProfileId).filter(String::isNotBlank)
    val selectedModels = selectedModelIds.mapNotNull { modelId ->
        state.models.firstOrNull { it.id == modelId }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (editor.id == null) {
            ClickableSettingItem(
                title = "Combo fallback model",
                description = selectedModels.joinToString("\n") { it.label.ifBlank { it.id } }
                    .ifBlank { "Chưa chọn model" },
                option = "${selectedModels.size} model",
                onClick = { showModelComboPicker = true },
            )
            SettingItem(
                title = "Credential pool",
                description = "Khi lưu combo, app tự ghép credential đang bật theo provider của từng model.",
            )
        } else {
        DropdownListSettingItem(
            title = "Model",
            selectedValue = editor.modelProfileId,
            displayEntries = state.models.map(AiRouterModelUi::label).toTypedArray(),
            entryValues = state.models.map(AiRouterModelUi::id).toTypedArray(),
            onValueChange = { modelId ->
                onChange(
                    editor.copy(
                        modelProfileId = modelId,
                        selectedModelProfileIds = listOf(modelId).toImmutableList(),
                        credentialId = "",
                    )
                )
            },
        )
        SettingItem(
            title = "Credential pool",
            description = "Target này dùng tất cả tài khoản/API key đang bật của provider model.",
        )
        }
        NumericField("Ưu tiên (số nhỏ chạy trước)", editor.priority) {
            onChange(editor.copy(priority = it))
        }
        NumericField("Trọng số (1–100)", editor.weight) {
            onChange(editor.copy(weight = it))
        }
        NumericField("Giới hạn đồng thời (0 = không giới hạn)", editor.maxConcurrency) {
            onChange(editor.copy(maxConcurrency = it))
        }
        SwitchSettingItem(
            title = "Bật đích tuyến",
            checked = editor.enabled,
            onCheckedChange = { onChange(editor.copy(enabled = it)) },
        )
        EditorActions(onResetHealth, onDelete)
    }
    AiRouterModelComboPickerSheet(
        show = showModelComboPicker,
        state = state,
        selectedModelIds = selectedModelIds,
        onToggleModel = { modelId ->
            val next = if (modelId in selectedModelIds) {
                selectedModelIds - modelId
            } else {
                selectedModelIds + modelId
            }
            onChange(
                editor.copy(
                    modelProfileId = next.firstOrNull().orEmpty(),
                    selectedModelProfileIds = next.toImmutableList(),
                    credentialId = "",
                )
            )
        },
        onDismissRequest = { showModelComboPicker = false },
    )
}

@Composable
private fun AiRouterModelComboPickerSheet(
    show: Boolean,
    state: AiRouterUiState,
    selectedModelIds: List<String>,
    onToggleModel: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var query by remember(show) { mutableStateOf("") }
    val providerNameById = remember(state.providers) {
        state.providers.associate { it.id to it.name }
    }
    val filteredModels = remember(state.models, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            state.models
        } else {
            state.models.filter { model ->
                model.label.contains(normalizedQuery, ignoreCase = true) ||
                    model.id.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val groupedModels = remember(filteredModels, providerNameById) {
        filteredModels
            .groupBy { providerNameById[it.providerId].orEmpty().ifBlank { it.providerId } }
            .entries
            .sortedBy { it.key.lowercase() }
    }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "Chọn combo fallback",
        endAction = {
            TextButton(onClick = onDismissRequest) { Text("Xong") }
        },
    ) {
        Column {
            SearchBar(
                query = query,
                backgroundColor = io.legado.app.ui.theme.LegadoTheme.colorScheme.onSheetContent,
                onQueryChange = { query = it },
                placeholder = "Tìm provider hoặc model",
                autoFocus = false,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (filteredModels.isEmpty()) {
                    item {
                        SettingItem(title = "Không có model phù hợp")
                    }
                } else {
                    groupedModels.forEach { (providerName, models) ->
                        item(key = "provider_$providerName") {
                            SplicedColumnGroup(title = providerName) {
                                models.forEach { model ->
                                    val selected = model.id in selectedModelIds
                                    ClickableSettingItem(
                                        title = model.label.ifBlank { model.id },
                                        option = if (selected) "Đã chọn" else null,
                                        trailingContent = if (selected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = { onToggleModel(model.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericField(label: String, value: String, onValueChange: (String) -> Unit) {
    AppTextField(
        value = value,
        onValueChange = { updated -> onValueChange(updated.filter(Char::isDigit)) },
        modifier = Modifier.fillMaxWidth(),
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun EditorActions(
    onResetHealth: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    if (onResetHealth == null && onDelete == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        onResetHealth?.let { action ->
            TextButton(onClick = action) { Text("Đặt lại sức khoẻ") }
        }
        onDelete?.let { action ->
            TextButton(onClick = action) { Text("Xoá") }
        }
    }
}

private fun editorTitle(editor: AiRouterEditor?): String = when (editor) {
    is AiRouterEditor.Credential -> if (editor.id == null) "Thêm credential" else "Sửa credential"
    is AiRouterEditor.ProviderConfig -> "Cấu hình ${editor.name.ifBlank { "provider" }}"
    is AiRouterEditor.ProviderCredentials -> "Provider ${editor.name}"
    is AiRouterEditor.Route -> if (editor.id == null) "Tạo tuyến AI" else "Sửa tuyến AI"
    is AiRouterEditor.Target -> if (editor.id == null) "Thêm đích tuyến" else "Sửa đích tuyến"
    null -> "AI Router"
}

private fun strategyLabel(strategy: String): String = when (strategy) {
    AiRouteStrategy.ROUND_ROBIN -> "Luân phiên"
    AiRouteStrategy.WEIGHTED_ROUND_ROBIN -> "Luân phiên có trọng số"
    else -> "Ưu tiên"
}

private fun taskLabel(taskType: String): String = when (taskType) {
    "chat" -> "Chatbot"
    "translate_chapter" -> "Dịch chương"
    "summarize_chapter" -> "Tóm tắt chương"
    "summarize_book" -> "Tóm tắt sách"
    "explain_selection" -> "Giải thích đoạn chọn"
    "clean_selection" -> "Làm sạch đoạn chọn"
    "text_factory" -> "Xử lý văn bản"
    "rewrite_text" -> "Viết lại"
    else -> taskType
}
