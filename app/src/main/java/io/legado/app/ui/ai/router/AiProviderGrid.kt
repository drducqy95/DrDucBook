package io.legado.app.ui.ai.router

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem

@Composable
internal fun AiRouterHealthSummarySection(
    summary: AiRouterHealthSummaryUi,
) {
    SplicedColumnGroup(title = "Tổng quan") {
        ClickableSettingItem(
            title = "${summary.activeRouteCount} route đang bật · ${summary.readyProviderCount} provider sẵn sàng",
            description = buildString {
                append("${summary.degradedProviderCount} provider cần kiểm tra")
                if (summary.loginRequiredCredentialCount > 0) {
                    append(" · ${summary.loginRequiredCredentialCount} credential cần đăng nhập")
                }
                if (summary.recentAttemptCount > 0) {
                    append(" · success ${summary.successRatePercent}%")
                    append(" · ${summary.averageLatencyMs} ms trung bình")
                }
            },
            onClick = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiRouterProviderGridSection(
    searchQuery: String,
    filters: List<AiRouterProviderFilterUi>,
    selectedFilter: String,
    providers: List<AiRouterProviderDashboardItemUi>,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onProviderClick: (AiRouterProviderDashboardItemUi) -> Unit,
) {
    var visibleProviderCount by rememberSaveable(searchQuery, selectedFilter) {
        mutableIntStateOf(INITIAL_VISIBLE_PROVIDER_COUNT)
    }
    val visibleProviders = providers.take(visibleProviderCount)
    SplicedColumnGroup(title = "Provider") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Tìm provider, model, trạng thái",
                autoFocus = false,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter.id,
                        onClick = { onFilterChange(filter.id) },
                        label = { Text("${filter.label} ${filter.count}") },
                    )
                }
            }
            if (providers.isEmpty()) {
                ClickableSettingItem(
                    title = "Không có provider phù hợp",
                    description = "Thử đổi bộ lọc hoặc từ khóa tìm kiếm.",
                    onClick = {},
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    visibleProviders.forEach { provider ->
                        ProviderTile(
                            provider = provider,
                            onClick = { onProviderClick(provider) },
                        )
                    }
                }
                if (visibleProviders.size < providers.size) {
                    val remaining = providers.size - visibleProviders.size
                    ClickableSettingItem(
                        title = "Hiển thị thêm ${remaining.coerceAtMost(PROVIDER_PAGE_SIZE)} provider",
                        description = "Còn $remaining provider khớp bộ lọc hiện tại.",
                        onClick = { visibleProviderCount += PROVIDER_PAGE_SIZE },
                    )
                }
            }
        }
    }
}

private const val INITIAL_VISIBLE_PROVIDER_COUNT = 16
private const val PROVIDER_PAGE_SIZE = 16

@Composable
private fun ProviderTile(
    provider: AiRouterProviderDashboardItemUi,
    onClick: () -> Unit,
) {
    NormalCard(
        modifier = Modifier
            .width(112.dp)
            .heightIn(min = 112.dp),
        onClick = onClick,
        cornerRadius = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = provider.familyName.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor(provider.status), CircleShape),
                )
            }
            Text(
                text = provider.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = provider.connectionMode,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = provider.statusLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AiRouterRouteCardsSection(
    routes: List<AiRouterRouteUi>,
    onOpenRoute: (String?) -> Unit,
    onOpenTarget: (routeId: String, targetId: String?) -> Unit,
) {
    var visibleRouteCount by rememberSaveable { mutableIntStateOf(INITIAL_VISIBLE_ROUTE_COUNT) }
    var expandedRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleRoutes = routes.take(visibleRouteCount)
    SplicedColumnGroup(title = "Tuyến theo tác vụ") {
        visibleRoutes.forEach { route ->
            val expanded = expandedRouteId == route.id
            ClickableSettingItem(
                title = route.name,
                description = buildString {
                    append(taskLabel(route.taskType))
                    append(" · ").append(strategyLabel(route.strategy))
                    append(" · ").append(statusLabel(route.healthStatus))
                    append(" · ").append(route.targets.size).append(" model")
                    if (route.recentAttemptCount > 0) {
                        append(" · success ").append(route.successRatePercent).append("%")
                        append(" · ").append(route.averageLatencyMs).append(" ms")
                    }
                },
                option = when {
                    !route.enabled -> "Tắt"
                    expanded -> "Thu gọn"
                    else -> "Xem"
                },
                onClick = {
                    expandedRouteId = if (expanded) null else route.id
                },
            )
            if (expanded) {
                ClickableSettingItem(
                    title = "↳ Sửa thiết lập combo",
                    description = "Tác vụ, chiến lược, số lượt thử và trạng thái.",
                    onClick = { onOpenRoute(route.id) },
                )
                route.targets.forEach { target ->
                    ClickableSettingItem(
                        title = "↳ ${target.modelLabel.ifBlank { target.modelProfileId }}",
                        description = buildString {
                            append(target.credentialLabel ?: "Pool tài khoản/API key của provider")
                            append(" · ưu tiên ").append(target.priority)
                            append(" · trọng số ").append(target.weight)
                            if (target.maxConcurrency > 0) {
                                append(" · đồng thời ").append(target.maxConcurrency)
                            }
                            if (target.consecutiveFailures > 0) {
                                append(" · lỗi ").append(target.consecutiveFailures)
                            }
                        },
                        option = target.lastFailureKind ?: if (target.enabled) "Sẵn sàng" else "Tắt",
                        onClick = { onOpenTarget(route.id, target.id) },
                    )
                }
                ClickableSettingItem(
                    title = "+ Thêm model vào ${route.name}",
                    onClick = { onOpenTarget(route.id, null) },
                )
            }
        }
        if (visibleRoutes.size < routes.size) {
            ClickableSettingItem(
                title = "+ Hiện thêm ${minOf(ROUTE_PAGE_SIZE, routes.size - visibleRoutes.size)} combo",
                description = "Đang hiển thị ${visibleRoutes.size}/${routes.size} combo.",
                onClick = {
                    visibleRouteCount = (visibleRouteCount + ROUTE_PAGE_SIZE).coerceAtMost(routes.size)
                },
            )
        }
        ClickableSettingItem(
            title = "+ Tạo combo fallback",
            description = "Chọn tác vụ, chiến lược, thứ tự model và ngân sách fallback.",
            onClick = { onOpenRoute(null) },
        )
    }
}

private const val INITIAL_VISIBLE_ROUTE_COUNT = 6
private const val ROUTE_PAGE_SIZE = 6

@Composable
internal fun AiRouterDiagnosticsSection(
    diagnostics: List<AiRouterDiagnosticUi>,
) {
    if (diagnostics.isEmpty()) return
    SplicedColumnGroup(title = "Chẩn đoán") {
        diagnostics.forEach { item ->
            ClickableSettingItem(
                title = item.title,
                description = item.description,
                option = statusLabel(item.status),
                onClick = {},
            )
        }
    }
}

@Composable
internal fun AiRouterAttemptHistorySection(
    attempts: List<AiRouterAttemptUi>,
) {
    if (attempts.isEmpty()) return
    SplicedColumnGroup(title = "Lịch sử định tuyến gần đây") {
        attempts.take(20).forEach { attempt ->
            ClickableSettingItem(
                title = attempt.targetLabel,
                description = buildString {
                    attempt.credentialLabel?.let { append(it).append(" · ") }
                    append(attempt.latencyMs).append(" ms")
                    attempt.firstEventMs?.let { append(" · token đầu ").append(it).append(" ms") }
                },
                option = if (attempt.success) "Thành công" else attempt.failureKind ?: "Lỗi",
                onClick = {},
            )
        }
    }
}

@Composable
private fun statusColor(status: String): Color =
    when (status) {
        AiConnectionStatus.READY -> MaterialTheme.colorScheme.primary
        AiConnectionStatus.DEGRADED,
        AiConnectionStatus.LOGIN_REQUIRED -> MaterialTheme.colorScheme.tertiary
        AiConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
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
