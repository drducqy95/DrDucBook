package io.legado.app.ui.main.my

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import com.drducbook.app.auth.GoogleCredentialBridge
import io.legado.app.ui.account.AccountAuthSection
import io.legado.app.ui.account.AccountEffect
import io.legado.app.ui.account.AccountIntent
import io.legado.app.ui.account.AccountViewModel
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.domain.webservice.CloudflareTunnelMode
import io.legado.app.domain.webservice.CloudflareTunnelPhase
import io.legado.app.service.WebService
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyScreen(
    viewModel: MyViewModel = koinViewModel(),
    accountViewModel: AccountViewModel = koinViewModel(),
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAiRouter: () -> Unit,
    onNavigate: (PrefClickEvent) -> Unit
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    LaunchedEffect(accountViewModel, context) {
        accountViewModel.effects.collectLatest { effect ->
            when (effect) {
                is AccountEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is AccountEffect.RequestGoogleCredential -> {
                    GoogleCredentialBridge.requestIdToken(
                        context = context,
                        serverClientId = effect.clientId,
                        nonce = effect.nonce,
                    ).onSuccess { token ->
                        accountViewModel.onIntent(
                            AccountIntent.SubmitGoogleToken(token.idToken, token.nonce)
                        )
                    }.onFailure { error ->
                        snackbarHostState.showSnackbar(GoogleCredentialBridge.userMessage(error))
                    }
                }
                is AccountEffect.RequestGoogleDriveAuthorization,
                AccountEffect.RequestSafFolder -> Unit
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.my),
                actions = {
                    TopBarActionButton(
                        onClick = {
                            onNavigate(
                                PrefClickEvent.ShowMd(
                                    title = "",
                                    path = "appHelp"
                                )
                            )
                        },
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.help)
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    adaptiveContentPadding(
                        top = padding.calculateTopPadding(),
                        bottom = 120.dp
                    )
                )
        ) {
            if (accountState.session == null) {
                AccountAuthSection(
                    state = accountState,
                    onIntent = accountViewModel::onIntent,
                )
            }

            if (uiState.webServiceAllowed) {
                SplicedColumnGroup(
                    title = ""
                ) {
                    WebServiceSettingBlock(
                        uiState = uiState,
                        onToggleWebService = {
                            viewModel.onEvent(PrefClickEvent.ToggleWebService)
                        },
                        onEvent = viewModel::onEvent,
                        onNavigate = onNavigate,
                    )
                }
            }

            SplicedColumnGroup(
                title = stringResource(R.string.rule_segment),
            ) {
                ClickableSettingItem(
                    title = stringResource(R.string.book_source_manage),
                    description = stringResource(R.string.book_source_manage_desc),
                    imageVector = Icons.Default.Source,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(BookSourceActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.replace_purify),
                    imageVector = Icons.Default.FindReplace,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(ReplaceRuleActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.txt_toc_rule),
                    imageVector = Icons.AutoMirrored.Filled.Rule,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(TxtTocRuleActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.dict_rule),
                    imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(DictRuleActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.highlight_tag_config),
                    imageVector = Icons.Default.Sell,
                    onClick = { onNavigate(PrefClickEvent.OpenHighlightTagRule) }
                )
            }

            SplicedColumnGroup(
                title = stringResource(R.string.other)
            ) {
                ClickableSettingItem(
                    title = stringResource(R.string.ai_router),
                    description = stringResource(R.string.ai_router_short),
                    imageVector = Icons.Default.AutoAwesome,
                    onClick = onNavigateToAiRouter,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.ai_chat),
                    imageVector = Icons.Default.AutoAwesome,
                    onClick = onNavigateToChat
                )
                ClickableSettingItem(
                    title = stringResource(R.string.setting),
                    imageVector = Icons.Default.Settings,
                    onClick = {
                        onOpenSettings()
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.account_title),
                    description = accountState.session?.email
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.account_anonymous_free),
                    imageVector = Icons.Default.AccountCircle,
                    onClick = onOpenAccount,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.bookmark),
                    imageVector = Icons.Default.Bookmark,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(AllBookmarkActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.read_record),
                    imageVector = Icons.Default.History,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenReadRecord)
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.cache_management),
                    imageVector = Icons.Default.Download,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenBookCacheManage)
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.file_manage),
                    imageVector = Icons.Default.Folder,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(FileManageActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.about),
                    imageVector = Icons.Default.Info,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenAbout)
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.exit),
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    onClick = {
                        onNavigate(PrefClickEvent.ExitApp)
                    }
                )
            }
        }
    }
}


@Composable
fun WebServiceSettingBlock(
    uiState: MyUiState,
    onToggleWebService: () -> Unit,
    onEvent: (PrefClickEvent) -> Unit,
    onNavigate: (PrefClickEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SwitchSettingItem(
            title = stringResource(R.string.web_service),
            description = if (uiState.isWebServiceRun) {
                uiState.webServiceAddress
            } else {
                stringResource(R.string.web_service_desc)
            },
            imageVector = Icons.Default.Web,
            checked = uiState.isWebServiceRun,
            onCheckedChange = { onToggleWebService() }
        )

        AnimatedVisibility(
            visible = uiState.isWebServiceRun,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SmallPlainButton(
                        onClick = {
                            onNavigate(PrefClickEvent.CopyUrl(uiState.webServiceAddress))
                        },
                        icon = Icons.Default.ContentCopy,
                        text = stringResource(R.string.copy_url)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    SmallPlainButton(
                        onClick = {
                            onNavigate(PrefClickEvent.OpenUrl(uiState.webServiceAddress))
                        },
                        icon = Icons.Default.OpenInBrowser,
                        text = stringResource(R.string.open_in_browser)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                AppText(
                    text = stringResource(R.string.cloudflare_tunnel_title),
                )
                AppText(
                    text = uiState.cloudflareDetail.ifBlank {
                        stringResource(R.string.cloudflare_tunnel_desc)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.cloudflare_pairing_enabled),
                    description = stringResource(R.string.cloudflare_pairing_enabled_desc),
                    checked = uiState.cloudflarePairingEnabled,
                    onCheckedChange = { enabled ->
                        onEvent(PrefClickEvent.SetCloudflarePairingEnabled(enabled))
                    },
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallPlainButton(
                        onClick = { onEvent(PrefClickEvent.StartQuickTunnel) },
                        selected = uiState.cloudflareMode == CloudflareTunnelMode.QUICK,
                        text = stringResource(R.string.cloudflare_quick_tunnel),
                    )
                    SmallPlainButton(
                        onClick = { onEvent(PrefClickEvent.OpenNamedTunnelDialog) },
                        selected = uiState.cloudflareMode == CloudflareTunnelMode.NAMED,
                        text = stringResource(R.string.cloudflare_named_tunnel),
                    )
                    if (uiState.cloudflarePhase == CloudflareTunnelPhase.STARTING ||
                        uiState.cloudflarePhase == CloudflareTunnelPhase.CONNECTED
                    ) {
                        SmallPlainButton(
                            onClick = { onEvent(PrefClickEvent.StopCloudflareTunnel) },
                            text = stringResource(R.string.stop),
                        )
                    }
                }
                if (uiState.cloudflarePublicUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(text = uiState.cloudflarePublicUrl)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SmallPlainButton(
                            onClick = {
                                onNavigate(PrefClickEvent.CopyUrl(uiState.cloudflarePublicUrl))
                            },
                            icon = Icons.Default.ContentCopy,
                            text = stringResource(R.string.copy_url),
                        )
                        SmallPlainButton(
                            onClick = {
                                onNavigate(PrefClickEvent.OpenUrl(uiState.cloudflarePublicUrl))
                            },
                            icon = Icons.Default.OpenInBrowser,
                            text = stringResource(R.string.open_in_browser),
                        )
                    }
                }
                if (uiState.cloudflarePhase == CloudflareTunnelPhase.STARTING ||
                    uiState.cloudflarePhase == CloudflareTunnelPhase.CONNECTED
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        text = if (uiState.cloudflarePairingCode.isBlank()) {
                            stringResource(R.string.web_pairing_code_used)
                        } else {
                            stringResource(
                                R.string.web_pairing_code,
                                uiState.cloudflarePairingCode,
                            )
                        },
                    )
                    SmallPlainButton(
                        onClick = { onEvent(PrefClickEvent.RefreshWebPairingCode) },
                        text = stringResource(R.string.web_pairing_refresh),
                    )
                }
            }
        }

        AppAlertDialog(
            show = uiState.showNamedTunnelDialog,
            onDismissRequest = { onEvent(PrefClickEvent.CloseNamedTunnelDialog) },
            title = stringResource(R.string.cloudflare_named_tunnel),
            text = stringResource(R.string.cloudflare_named_tunnel_help, WebService.activeHttpPort),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(
                        value = uiState.namedTunnelToken,
                        onValueChange = {
                            onEvent(PrefClickEvent.UpdateNamedTunnelToken(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.cloudflare_tunnel_token),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    AppTextField(
                        value = uiState.namedTunnelPublicUrl,
                        onValueChange = {
                            onEvent(PrefClickEvent.UpdateNamedTunnelPublicUrl(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.cloudflare_public_url),
                        singleLine = true,
                    )
                }
            },
            confirmText = stringResource(R.string.cloudflare_start),
            onConfirm = { onEvent(PrefClickEvent.StartNamedTunnel) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onEvent(PrefClickEvent.CloseNamedTunnelDialog) },
        )
    }
}
