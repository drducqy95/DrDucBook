package io.legado.app.ui.main.my

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.domain.usecase.WebServiceAccessUseCase
import io.legado.app.domain.webservice.CloudflareTunnelMode
import io.legado.app.domain.webservice.CloudflareTunnelPhase
import io.legado.app.service.CloudflareTunnelManager
import io.legado.app.service.WebService
import io.legado.app.utils.eventBus.FlowEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class MyUiState(
    val webServiceAllowed: Boolean = false,
    val isWebServiceRun: Boolean = false,
    val webServiceAddress: String = "",
    val cloudflareMode: CloudflareTunnelMode = CloudflareTunnelMode.OFF,
    val cloudflarePhase: CloudflareTunnelPhase = CloudflareTunnelPhase.STOPPED,
    val cloudflarePublicUrl: String = "",
    val cloudflarePairingEnabled: Boolean = true,
    val cloudflarePairingCode: String = "",
    val cloudflareDetail: String = "",
    val showNamedTunnelDialog: Boolean = false,
    val namedTunnelToken: String = "",
    val namedTunnelPublicUrl: String = "",
)

sealed class PrefClickEvent {
    data class OpenUrl(val url: String) : PrefClickEvent()
    data class CopyUrl(val url: String) : PrefClickEvent()
    data class ShowMd(val title: String, val path: String) : PrefClickEvent()
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : PrefClickEvent()
    object OpenReadRecord : PrefClickEvent()
    object OpenBookCacheManage : PrefClickEvent()
    object OpenHighlightTagRule : PrefClickEvent()
    object OpenAbout : PrefClickEvent()
    object ToggleWebService : PrefClickEvent()
    object StartQuickTunnel : PrefClickEvent()
    object OpenNamedTunnelDialog : PrefClickEvent()
    object CloseNamedTunnelDialog : PrefClickEvent()
    data class UpdateNamedTunnelToken(val value: String) : PrefClickEvent()
    data class UpdateNamedTunnelPublicUrl(val value: String) : PrefClickEvent()
    object StartNamedTunnel : PrefClickEvent()
    object StopCloudflareTunnel : PrefClickEvent()
    object RefreshWebPairingCode : PrefClickEvent()
    data class SetCloudflarePairingEnabled(val enabled: Boolean) : PrefClickEvent()
    object ExitApp : PrefClickEvent()
}

class MyViewModel(
    application: Application,
    private val webServiceAccessUseCase: WebServiceAccessUseCase,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(
        MyUiState(
            isWebServiceRun = WebService.isRun,
            webServiceAddress = WebService.hostAddress,
            cloudflarePairingEnabled = CloudflareTunnelManager.isPairingEnabled(application),
        )
    )
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            webServiceAccessUseCase.observeAllowed().collect { allowed ->
                _uiState.update { state -> state.copy(webServiceAllowed = allowed) }
                if (!allowed && WebService.isRun) {
                    WebService.stop(context)
                }
            }
        }
        viewModelScope.launch {
            CloudflareTunnelManager.state.collect { tunnel ->
                _uiState.update { state ->
                    state.copy(
                        cloudflareMode = tunnel.mode,
                        cloudflarePhase = tunnel.phase,
                        cloudflarePublicUrl = tunnel.publicUrl,
                        cloudflarePairingCode = tunnel.pairingCode,
                        cloudflarePairingEnabled = tunnel.pairingEnabled,
                        cloudflareDetail = tunnel.detail,
                    )
                }
            }
        }
        viewModelScope.launch {
            FlowEventBus.with<String>(EventBus.WEB_SERVICE)
                .collect { address ->
                    if (address.isEmpty()) CloudflareTunnelManager.stop()
                    _uiState.update { state ->
                        state.copy(
                            isWebServiceRun = address.isNotEmpty(),
                            webServiceAddress = address,
                        )
                    }
                }
        }
    }

    fun onEvent(event: PrefClickEvent) {
        when (event) {
            PrefClickEvent.ToggleWebService -> {
                val currentIsRun = _uiState.value.isWebServiceRun

                if (!currentIsRun) {
                    viewModelScope.launch {
                        if (runCatching { webServiceAccessUseCase.isAllowed() }.getOrDefault(false)) {
                            WebService.start(context)
                        }
                    }
                } else {
                    CloudflareTunnelManager.stop()
                    WebService.stop(context)
                    _uiState.update {
                        it.copy(
                            isWebServiceRun = false,
                            webServiceAddress = "",
                        )
                    }
                }

            }
            PrefClickEvent.StartQuickTunnel -> {
                if (_uiState.value.isWebServiceRun) {
                    CloudflareTunnelManager.startQuick(context, WebService.activeHttpPort)
                }
            }
            PrefClickEvent.OpenNamedTunnelDialog -> _uiState.update {
                it.copy(showNamedTunnelDialog = true)
            }
            PrefClickEvent.CloseNamedTunnelDialog -> _uiState.update {
                it.copy(showNamedTunnelDialog = false, namedTunnelToken = "")
            }
            is PrefClickEvent.UpdateNamedTunnelToken -> _uiState.update {
                it.copy(namedTunnelToken = event.value)
            }
            is PrefClickEvent.UpdateNamedTunnelPublicUrl -> _uiState.update {
                it.copy(namedTunnelPublicUrl = event.value)
            }
            PrefClickEvent.StartNamedTunnel -> {
                val state = _uiState.value
                if (state.isWebServiceRun) {
                    CloudflareTunnelManager.startNamed(
                        context = context,
                        token = state.namedTunnelToken,
                        publicUrl = state.namedTunnelPublicUrl,
                    )
                    _uiState.update {
                        it.copy(showNamedTunnelDialog = false, namedTunnelToken = "")
                    }
                }
            }
            PrefClickEvent.StopCloudflareTunnel -> CloudflareTunnelManager.stop()
            PrefClickEvent.RefreshWebPairingCode -> CloudflareTunnelManager.refreshPairingCode()
            is PrefClickEvent.SetCloudflarePairingEnabled ->
                CloudflareTunnelManager.setPairingEnabled(context, event.enabled)
            else -> Unit
        }
    }

}
