package io.legado.app.ui.ai.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.help.config.ChatbotToolCategory
import io.legado.app.help.config.ChatbotToolPermissionConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatbotToolSettingsSheet(
    config: ChatbotToolPermissionConfig?,
    onDismissRequest: () -> Unit,
    onCategoryToggle: (ChatbotToolCategory, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val permissions by (config?.permissionsFlow?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(emptyMap())
    })

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = LegadoTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        text = "Quyền công cụ Agent & Chatbot",
                        style = LegadoTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = LegadoTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Các quyền ghi/sửa đổi bên dưới sẽ tự động duyệt thực thi mà không cần hỏi lại. Các công cụ đọc & tìm kiếm an toàn luôn tự động hoạt động.",
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ChatbotToolCategory.entries, key = { it.key }) { category ->
                    val isChecked = permissions[category] ?: config?.isCategoryAutoApproved(category) ?: false
                    NormalCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = iconForCategory(category),
                                        contentDescription = null,
                                        tint = if (isChecked) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Column {
                                        Text(
                                            text = category.title,
                                            style = LegadoTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = LegadoTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = category.description,
                                            style = LegadoTheme.typography.bodySmall,
                                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        onCategoryToggle(category, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = LegadoTheme.colorScheme.primary,
                                    ),
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                category.tools.forEach { toolName ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = toolName,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                                            labelColor = LegadoTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        border = null,
                                        modifier = Modifier.height(26.dp),
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

private fun iconForCategory(category: ChatbotToolCategory): ImageVector = when (category) {
    ChatbotToolCategory.BOOKSHELF_AND_READING -> Icons.Default.MenuBook
    ChatbotToolCategory.BOOK_SOURCE_AND_PLUGINS -> Icons.Default.Extension
    ChatbotToolCategory.DICTIONARY_AND_MEMORY -> Icons.Default.Psychology
    ChatbotToolCategory.AUTHORING_AND_ARTIFACTS -> Icons.Default.EditNote
    ChatbotToolCategory.AGENT_SKILLS -> Icons.Default.AutoStories
}
