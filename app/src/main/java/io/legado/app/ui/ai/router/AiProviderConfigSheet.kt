package io.legado.app.ui.ai.router

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProtocol
import io.legado.app.ui.config.ai.AiModelPickerOptionUi
import io.legado.app.ui.config.ai.AiModelPickerSheet
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun ProviderConfigEditor(
    editor: AiRouterEditor.ProviderConfig,
    credentials: List<AiRouterCredentialUi>,
    saving: Boolean,
    onChange: (AiRouterEditor.ProviderConfig) -> Unit,
    onTest: () -> Unit,
    onOpenLocalGgufCatalog: () -> Unit,
    onChooseLocalGguf: () -> Unit,
    onOpenCredential: (String) -> Unit,
    onAddCredential: () -> Unit,
) {
    var showModelPicker by remember(editor.catalogId, editor.providerProfileId) { mutableStateOf(false) }
    var showAdvanced by remember(editor.catalogId, editor.providerProfileId) { mutableStateOf(false) }
    val modelOptions = editor.discoveredModels.map { option ->
        AiModelPickerOptionUi(
            id = option.id,
            providerName = editor.familyName.ifBlank { editor.name },
            modelName = option.name.ifBlank { option.id },
            modelId = option.id,
            contextWindow = option.contextWindow,
            maxOutputTokens = option.maxOutputTokens,
        )
    }.toImmutableList()
    val selectedModelMissing = editor.modelId.isNotBlank() &&
        modelOptions.none { it.id == editor.modelId }
    val pickerOptions = if (selectedModelMissing) {
        (modelOptions + AiModelPickerOptionUi(
            id = editor.modelId,
            providerName = editor.familyName.ifBlank { editor.name },
            modelName = editor.modelName.ifBlank { editor.modelId },
            modelId = editor.modelId,
            contextWindow = editor.contextWindow.toIntOrNull() ?: 0,
            maxOutputTokens = editor.maxOutputTokens.toIntOrNull() ?: 0,
            isMissing = true,
        )).toImmutableList()
    } else {
        modelOptions
    }
    val isLocalGguf = editor.protocol == AiProtocol.LOCAL_GGUF
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClickableSettingItem(
            title = editor.familyName.ifBlank { editor.name },
            description = listOf(editor.connectionMode, editor.notice)
                .filter(String::isNotBlank)
                .joinToString(" · "),
            option = statusLabel(editor.testStatus),
            onClick = {},
        )
        AppTextField(
            value = editor.name,
            onValueChange = { onChange(editor.copy(name = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Tên provider",
            singleLine = true,
        )
        AppTextField(
            value = editor.baseUrl,
            onValueChange = { newBaseUrl ->
                onChange(
                    editor.copy(
                        baseUrl = newBaseUrl,
                        protocol = protocolForRebasedEndpoint(
                            currentProtocol = editor.protocol,
                            previousBaseUrl = editor.baseUrl,
                            newBaseUrl = newBaseUrl,
                        ),
                        modelsUrl = rebaseDerivedModelsUrl(
                            previousBaseUrl = editor.baseUrl,
                            newBaseUrl = newBaseUrl,
                            currentModelsUrl = editor.modelsUrl,
                        ),
                        testStatus = AiConnectionStatus.UNVERIFIED,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = if (isLocalGguf) "GGUF model path" else "Base URL",
            singleLine = true,
        )
        if (isLocalGguf) {
            ClickableSettingItem(
                title = "Tải model GGUF",
                description = "Mở kho model đã tải lên để lấy link tải.",
                onClick = onOpenLocalGgufCatalog,
            )
            ClickableSettingItem(
                title = "Chọn file GGUF",
                description = editor.baseUrl.ifBlank { "Chưa chọn file model" },
                onClick = onChooseLocalGguf,
            )
            SettingItem(
                title = "Runtime cục bộ",
                description = listOf(
                    editor.localPrimaryAbi.takeIf(String::isNotBlank)?.let { "ABI $it" },
                    editor.localTotalMemoryMb.takeIf { it > 0 }?.let { "RAM ${it} MB" },
                    editor.localRuntimeProfile.takeIf(String::isNotBlank),
                    editor.localModelSizeBytes.takeIf { it > 0 }
                        ?.let { "Model ${it / (1_024L * 1_024L)} MB" },
                    editor.localModelSha256.takeIf(String::isNotBlank)
                        ?.let { "SHA-256 ${it.take(12)}…" },
                ).filterNotNull().joinToString("\n").ifBlank {
                    "Chọn model để kiểm tra cấu hình thiết bị"
                },
                option = when (editor.localRuntimeAvailable) {
                    true -> "Sẵn sàng"
                    false -> "Không hỗ trợ"
                    null -> "Chưa kiểm tra"
                },
            )
        }
        DropdownListSettingItem(
            title = "Loại xác thực",
            selectedValue = editor.authType,
            displayEntries = arrayOf("Không cần key", "Bearer token", "Header key"),
            entryValues = arrayOf(
                AiProviderAuthType.NONE,
                AiProviderAuthType.BEARER,
                AiProviderAuthType.HEADER,
            ),
            onValueChange = { onChange(editor.copy(authType = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
        )
        if (editor.authType != AiProviderAuthType.NONE) {
            AppTextField(
                value = editor.apiKey,
                onValueChange = {
                    onChange(editor.copy(apiKey = it, testStatus = AiConnectionStatus.UNVERIFIED))
                },
                modifier = Modifier.fillMaxWidth(),
                label = if (editor.hasStoredSecret) {
                    "Token mới (để trống để giữ Đã lưu)"
                } else {
                    "API key/token"
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            ProviderConnectionTest(
                saving = saving,
                testStatus = editor.testStatus,
                testMessage = editor.testMessage,
                testLatencyMs = editor.testLatencyMs,
                discoveredModelCount = editor.discoveredModels.size,
                onTest = onTest,
            )
            SettingItem(
                title = "API key pool",
                description = if (credentials.isEmpty()) {
                    "Chưa có API key riêng cho provider này."
                } else {
                    "${credentials.size} API key đang lưu; router sẽ tự xoay khi combo dùng model của provider này."
                },
            )
            credentials.forEach { credential ->
                ClickableSettingItem(
                    title = credential.label,
                    description = buildString {
                        append(credential.kind)
                        if (credential.consecutiveFailures > 0) {
                            append(" · ").append(credential.consecutiveFailures).append(" lỗi liên tiếp")
                        }
                    },
                    option = providerCredentialStatusOption(credential),
                    onClick = { onOpenCredential(credential.id) },
                )
            }
            ClickableSettingItem(
                title = "+ Thêm API key/token",
                description = "Thêm key vào pool của provider hiện tại.",
                onClick = onAddCredential,
            )
        }
        if (editor.authType == AiProviderAuthType.NONE) {
            ProviderConnectionTest(
                saving = saving,
                testStatus = editor.testStatus,
                testMessage = editor.testMessage,
                testLatencyMs = editor.testLatencyMs,
                discoveredModelCount = editor.discoveredModels.size,
                onTest = onTest,
            )
        }
        if (pickerOptions.isNotEmpty()) {
            ClickableSettingItem(
                title = "Model (${editor.discoveredModels.size} model từ provider)",
                description = pickerOptions.firstOrNull { it.id == editor.modelId }
                    ?.let { it.modelName.ifBlank { it.modelId } }
                    ?: "Chưa chọn model",
                onClick = { showModelPicker = true },
            )
        }
        AppTextField(
            value = editor.modelId,
            onValueChange = { onChange(editor.copy(modelId = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Nhập model ID thủ công",
            singleLine = true,
        )
        AppTextField(
            value = editor.modelName,
            onValueChange = { onChange(editor.copy(modelName = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Tên hiển thị model",
            singleLine = true,
        )
        ClickableSettingItem(
            title = if (showAdvanced) "Ẩn cấu hình nâng cao" else "Hiện cấu hình nâng cao",
            description = "Models URL, đường dẫn giao thức và giới hạn token.",
            onClick = { showAdvanced = !showAdvanced },
        )
        if (showAdvanced && !isLocalGguf) {
            DropdownListSettingItem(
                title = "Giao thức API",
                selectedValue = editor.protocol,
                displayEntries = arrayOf(
                    "OpenAI Chat Completions",
                    "OpenAI Responses",
                    "Anthropic Messages",
                    "Google Gemini",
                ),
                entryValues = arrayOf(
                    AiProtocol.OPENAI_CHAT_COMPLETIONS,
                    AiProtocol.OPENAI_RESPONSES,
                    AiProtocol.ANTHROPIC_MESSAGES,
                    AiProtocol.GEMINI_GENERATE_CONTENT,
                ),
                onValueChange = {
                    onChange(editor.copy(protocol = it, testStatus = AiConnectionStatus.UNVERIFIED))
                },
            )
            AppTextField(
                value = editor.modelsUrl,
                onValueChange = { onChange(editor.copy(modelsUrl = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
                modifier = Modifier.fillMaxWidth(),
                label = "Models URL",
                singleLine = true,
            )
            AppTextField(
                value = editor.chatPath,
                onValueChange = { onChange(editor.copy(chatPath = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
                modifier = Modifier.fillMaxWidth(),
                label = "Chat path",
                singleLine = true,
            )
            AppTextField(
                value = editor.responsesPath,
                onValueChange = { onChange(editor.copy(responsesPath = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
                modifier = Modifier.fillMaxWidth(),
                label = "Responses path",
                singleLine = true,
            )
            AppTextField(
                value = editor.messagesPath,
                onValueChange = { onChange(editor.copy(messagesPath = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
                modifier = Modifier.fillMaxWidth(),
                label = "Messages path",
                singleLine = true,
            )
            AppTextField(
                value = editor.modelsPath,
                onValueChange = { onChange(editor.copy(modelsPath = it, testStatus = AiConnectionStatus.UNVERIFIED)) },
                modifier = Modifier.fillMaxWidth(),
                label = "Models path",
                singleLine = true,
            )
        }
        if (showAdvanced) AppTextField(
            value = editor.contextWindow,
            onValueChange = {
                onChange(editor.copy(contextWindow = it.filter(Char::isDigit), testStatus = AiConnectionStatus.UNVERIFIED))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Context limit",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        if (showAdvanced) AppTextField(
            value = editor.maxOutputTokens,
            onValueChange = {
                onChange(
                    editor.copy(
                        maxOutputTokens = it.filter(Char::isDigit),
                        testStatus = AiConnectionStatus.UNVERIFIED,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Output limit",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
    AiModelPickerSheet(
        show = showModelPicker,
        title = "Chọn model",
        selectedModelId = editor.modelId,
        models = pickerOptions,
        onDismissRequest = { showModelPicker = false },
        onSelect = { model ->
            onChange(
                editor.copy(
                    modelId = model.id,
                    modelName = model.modelName,
                    contextWindow = model.contextWindow.takeIf { it > 0 }?.toString()
                        ?: editor.contextWindow,
                    maxOutputTokens = model.maxOutputTokens.takeIf { it > 0 }?.toString()
                        ?: editor.maxOutputTokens,
                    testStatus = AiConnectionStatus.UNVERIFIED,
                )
            )
        },
    )
}

internal fun rebaseDerivedModelsUrl(
    previousBaseUrl: String,
    newBaseUrl: String,
    currentModelsUrl: String,
): String {
    val previousBase = previousBaseUrl.trimEnd('/')
    if (previousBase.isBlank() || !currentModelsUrl.startsWith(previousBase)) {
        return currentModelsUrl
    }
    val suffix = currentModelsUrl.removePrefix(previousBase)
    if (suffix.isNotEmpty() && !suffix.startsWith('/') && !suffix.startsWith('?')) {
        return currentModelsUrl
    }
    return newBaseUrl.trimEnd('/').let { newBase ->
        if (newBase.isBlank()) "" else newBase + suffix
    }
}

internal fun protocolForRebasedEndpoint(
    currentProtocol: String,
    previousBaseUrl: String,
    newBaseUrl: String,
): String {
    if (currentProtocol != AiProtocol.OPENAI_RESPONSES) return currentProtocol
    val movedAwayFromOpenAi = isOfficialOpenAiEndpoint(previousBaseUrl) &&
        !isOfficialOpenAiEndpoint(newBaseUrl)
    return if (movedAwayFromOpenAi) {
        AiProtocol.OPENAI_CHAT_COMPLETIONS
    } else {
        currentProtocol
    }
}

internal fun shouldClearCatalogModelForEndpoint(
    catalogProtocol: String,
    catalogBaseUrl: String,
    newBaseUrl: String,
    selectedModelId: String,
    catalogModelIds: Set<String>,
): Boolean =
    selectedModelId in catalogModelIds &&
        protocolForRebasedEndpoint(
            currentProtocol = catalogProtocol,
            previousBaseUrl = catalogBaseUrl,
            newBaseUrl = newBaseUrl,
        ) != catalogProtocol

private fun isOfficialOpenAiEndpoint(baseUrl: String): Boolean =
    runCatching { java.net.URI(baseUrl.trim()).host }
        .getOrNull()
        ?.equals("api.openai.com", ignoreCase = true) == true

@Composable
private fun ProviderConnectionTest(
    saving: Boolean,
    testStatus: String,
    testMessage: String,
    testLatencyMs: Long?,
    discoveredModelCount: Int,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(enabled = !saving, onClick = onTest) {
            Text(if (saving) "Đang kiểm tra…" else "Kiểm tra API key & lấy model")
        }
    }
    if (testMessage.isNotBlank()) {
        ClickableSettingItem(
            title = statusLabel(testStatus),
            description = buildString {
                append(testMessage)
                testLatencyMs?.let { append(" · ").append(it).append(" ms") }
                if (discoveredModelCount > 0) {
                    append(" · ").append(discoveredModelCount).append(" model")
                }
            },
            onClick = {},
        )
    }
}

private fun providerCredentialStatusOption(credential: AiRouterCredentialUi): String = when {
    !credential.enabled -> "Tắt"
    credential.status == "relogin_required" -> "Cần đăng nhập"
    credential.status == "refreshing" -> "Đang làm mới"
    !credential.hasSecret -> "Thiếu token"
    credential.lastFailureKind != null -> credential.lastFailureKind
    else -> "Sẵn sàng"
}
