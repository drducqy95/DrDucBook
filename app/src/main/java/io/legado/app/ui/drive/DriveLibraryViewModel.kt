package io.legado.app.ui.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.Server
import io.legado.app.data.repository.ManagedSourceRegistry
import io.legado.app.domain.model.DriveSourceType
import io.legado.app.domain.model.ManagedDriveSource
import io.legado.app.domain.usecase.DriveWebDavConnectionUseCase
import io.legado.app.help.drive.DriveLinkResolver
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.LogUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import io.legado.app.data.repository.RemoteBookMetadataRepository
import io.legado.app.help.drive.extractor.CompanionCoverResolver

class DriveLibraryViewModel(
    private val registry: ManagedSourceRegistry,
    private val connectionUseCase: DriveWebDavConnectionUseCase,
    private val metadataRepository: RemoteBookMetadataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveLibraryUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DriveLibraryEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var activeWebDavAuth: Authorization? = null
    private var activeBaseUrl: String = ""
    private var connectJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch {
            registry.sources.collectLatest { sourcesList ->
                val active = registry.getActiveSource()
                _uiState.update {
                    it.copy(
                        sources = sourcesList.toImmutableList(),
                        activeSource = active
                    )
                }
                if (active != null && _uiState.value.connectedServerId == null && !_uiState.value.loading) {
                    connectSource(active)
                }
            }
        }
    }

    fun onIntent(intent: DriveLibraryIntent) {
        when (intent) {
            is DriveLibraryIntent.SelectSource -> selectSource(intent.sourceId)
            is DriveLibraryIntent.ConnectSource -> connectSource(intent.source)
            is DriveLibraryIntent.Disconnect -> disconnect()
            is DriveLibraryIntent.OpenFolder -> openFolder(intent.path)
            is DriveLibraryIntent.NavigateBreadcrumb -> navigateBreadcrumb(intent.index)
            is DriveLibraryIntent.DownloadAndImport -> downloadAndImport(intent.item)
            is DriveLibraryIntent.OpenBook -> openBook(intent.item)
            is DriveLibraryIntent.SetViewMode -> _uiState.update { it.copy(viewMode = intent.mode) }
            is DriveLibraryIntent.ShowBookPreview -> _uiState.update {
                it.copy(previewBook = intent.item, activeSheet = DriveLibrarySheet.BookPreview(intent.item))
            }
            is DriveLibraryIntent.DismissBookPreview -> _uiState.update {
                it.copy(previewBook = null, activeSheet = null)
            }
            is DriveLibraryIntent.PrefetchMetadata -> prefetchSingleItem(intent.item)
            is DriveLibraryIntent.ShowAddSourceSheet -> {
                _uiState.update { it.copy(activeSheet = DriveLibrarySheet.AddSource) }
            }
            is DriveLibraryIntent.DismissSheet -> {
                _uiState.update { it.copy(activeSheet = null) }
            }
            is DriveLibraryIntent.ShowDeleteDialog -> {
                _uiState.update { it.copy(activeDialog = DriveLibraryDialog.ConfirmDelete(intent.source)) }
            }
            is DriveLibraryIntent.DismissDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }
            is DriveLibraryIntent.ConfirmDeleteSource -> deleteSource(intent.source)
            is DriveLibraryIntent.RequestGoogleAuth -> _effects.tryEmit(DriveLibraryEffect.RequestGoogleAuth)
            is DriveLibraryIntent.AddPublicLink -> addPublicLink(intent.url, intent.name)
            is DriveLibraryIntent.AddGoogleAccount -> addGoogleAccount(intent.email, intent.rootFolderId, intent.name)
        }
    }

    private fun selectSource(sourceId: String) {
        viewModelScope.launch {
            registry.setActiveSource(sourceId)
            val source = registry.getActiveSource() ?: return@launch
            connectSource(source)
        }
    }

    private fun connectSource(source: ManagedDriveSource, accessToken: String = "") {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            LogUtils.d("DriveLibraryViewModel", "Starting connection to source: ${source.name} (${source.id})")
            val result = connectionUseCase.connectSource(source, accessToken)
            result.onSuccess { serverId ->
                LogUtils.d("DriveLibraryViewModel", "Connection successful! serverId=$serverId")
                val server = withContext(Dispatchers.IO) { appDb.serverDao.get(serverId) }
                val webDavConfig = server?.getWebDavConfig()
                if (webDavConfig != null) {
                    LogUtils.d("DriveLibraryViewModel", "WebDAV server config: url=${webDavConfig.url}")
                    activeBaseUrl = webDavConfig.url
                    activeWebDavAuth = Authorization(webDavConfig.username, webDavConfig.password)
                    _uiState.update {
                        it.copy(
                            connectedServerId = serverId,
                            currentPath = "/",
                            breadcrumbs = listOf("/").toImmutableList()
                        )
                    }
                    loadDirectory("/")
                } else {
                    LogUtils.e("DriveLibraryViewModel", "Server entity $serverId not found or missing WebDAV config")
                }
            }.onFailure { error ->
                LogUtils.e("DriveLibraryViewModel", "Connection failed: ${error.message}")
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.message ?: "Kết nối thất bại"
                    )
                }
                _effects.tryEmit(DriveLibraryEffect.ShowToast(error.message ?: "Kết nối thất bại"))
            }
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            connectionUseCase.disconnect()
            activeWebDavAuth = null
            activeBaseUrl = ""
            _uiState.update {
                it.copy(
                    connectedServerId = null,
                    items = emptyList<DriveCatalogItem>().toImmutableList(),
                    loading = false
                )
            }
        }
    }

    private fun openFolder(folderPath: String) {
        val currentBreadcrumbs = _uiState.value.breadcrumbs.toMutableList()
        currentBreadcrumbs.add(folderPath)
        _uiState.update {
            it.copy(
                currentPath = folderPath,
                breadcrumbs = currentBreadcrumbs.toImmutableList()
            )
        }
        loadDirectory(folderPath)
    }

    private fun navigateBreadcrumb(index: Int) {
        val breadcrumbs = _uiState.value.breadcrumbs
        if (index in breadcrumbs.indices) {
            val targetPath = breadcrumbs[index]
            val newBreadcrumbs = breadcrumbs.subList(0, index + 1).toImmutableList()
            _uiState.update {
                it.copy(
                    currentPath = targetPath,
                    breadcrumbs = newBreadcrumbs
                )
            }
            loadDirectory(targetPath)
        }
    }

    private fun loadDirectory(relPath: String) {
        val auth = activeWebDavAuth ?: return
        prefetchJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val fullUrl = buildFullUrl(activeBaseUrl, relPath)
                LogUtils.d("DriveLibraryViewModel", "Listing WebDAV directory: $fullUrl (relPath=$relPath)")
                val rawFiles = withContext(Dispatchers.IO) {
                    WebDav(fullUrl, auth).listFiles()
                }
                LogUtils.d("DriveLibraryViewModel", "Received ${rawFiles.size} WebDAV items from $fullUrl")

                val allFilenames = rawFiles.map { it.displayName }
                val initialItems = rawFiles.map { f ->
                    val isDir = f.isDir
                    val ext = f.displayName.substringAfterLast(".", "").lowercase()
                    val itemPath = if (relPath.endsWith("/")) "$relPath${f.displayName}" else "$relPath/${f.displayName}"

                    val companionCover = if (!isDir) {
                        CompanionCoverResolver.resolveCompanionCover(f.displayName, allFilenames)?.let { companionName ->
                            val compRelPath = if (relPath.endsWith("/")) "$relPath$companionName" else "$relPath/$companionName"
                            buildFullUrl(activeBaseUrl, compRelPath)
                        }
                    } else null

                    val initialCover = f.thumbnailUrl ?: companionCover

                    DriveCatalogItem(
                        name = f.displayName,
                        path = itemPath,
                        isDir = isDir,
                        size = f.size,
                        lastModified = f.lastModify,
                        isImported = _uiState.value.importedPaths.contains(itemPath),
                        coverUrl = initialCover,
                        intro = f.description,
                        format = if (isDir) "" else ext
                    )
                }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))

                // Preload cached metadata from RAM/Room
                val itemsWithCache = initialItems.map { item ->
                    if (!item.isDir) {
                        val cached = metadataRepository.getCached(item.path)
                        if (cached != null) {
                            item.copy(
                                title = cached.title.ifBlank { item.title },
                                author = cached.author ?: item.author,
                                intro = cached.intro ?: item.intro,
                                coverUrl = cached.coverUrl ?: item.coverUrl
                            )
                        } else item
                    } else item
                }

                _uiState.update {
                    it.copy(
                        items = itemsWithCache.toImmutableList(),
                        loading = false
                    )
                }

                prefetchDirectoryMetadata(itemsWithCache, activeBaseUrl, auth, allFilenames, relPath)
            } catch (e: Exception) {
                LogUtils.e("DriveLibraryViewModel", "Error loading directory: ${e.message}")
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = "Không thể tải thư mục: ${e.message}"
                    )
                }
            }
        }
    }

    private fun prefetchDirectoryMetadata(
        items: List<DriveCatalogItem>,
        baseUrl: String,
        auth: Authorization,
        allFilenames: List<String>,
        relPath: String
    ) {
        prefetchJob = viewModelScope.launch {
            for (item in items) {
                if (item.isDir) continue
                if (item.author != null && item.coverUrl != null && item.intro != null) continue

                try {
                    val itemUrl = buildFullUrl(baseUrl, item.path)
                    val companionCover = CompanionCoverResolver.resolveCompanionCover(item.name, allFilenames)?.let { compName ->
                        val compRelPath = if (relPath.endsWith("/")) "$relPath$compName" else "$relPath/$compName"
                        buildFullUrl(baseUrl, compRelPath)
                    }

                    val resolved = metadataRepository.resolveMetadata(
                        url = itemUrl,
                        auth = auth,
                        path = item.path,
                        filename = item.name,
                        fileSize = item.size,
                        existingThumbnail = item.coverUrl,
                        companionCover = companionCover
                    )

                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map { cur ->
                                if (cur.path == item.path) {
                                    cur.copy(
                                        title = resolved.title.ifBlank { cur.title },
                                        author = resolved.author ?: cur.author,
                                        intro = resolved.intro ?: cur.intro,
                                        coverUrl = resolved.coverUrl ?: cur.coverUrl
                                    )
                                } else cur
                            }.toImmutableList()
                        )
                    }
                } catch (e: Throwable) {
                    // Fault isolation: continue with next item
                }
            }
        }
    }

    private fun prefetchSingleItem(item: DriveCatalogItem) {
        val auth = activeWebDavAuth ?: return
        if (item.isDir) return
        viewModelScope.launch {
            try {
                val itemUrl = buildFullUrl(activeBaseUrl, item.path)
                val resolved = metadataRepository.resolveMetadata(
                    url = itemUrl,
                    auth = auth,
                    path = item.path,
                    filename = item.name,
                    fileSize = item.size,
                    existingThumbnail = item.coverUrl,
                    companionCover = null
                )
                _uiState.update { state ->
                    state.copy(
                        items = state.items.map { cur ->
                            if (cur.path == item.path) {
                                cur.copy(
                                    title = resolved.title.ifBlank { cur.title },
                                    author = resolved.author ?: cur.author,
                                    intro = resolved.intro ?: cur.intro,
                                    coverUrl = resolved.coverUrl ?: cur.coverUrl
                                )
                            } else cur
                        }.toImmutableList()
                    )
                }
            } catch (e: Throwable) {
                // Ignore single item prefetch failure
            }
        }
    }

    private fun downloadAndImport(item: DriveCatalogItem) {
        val auth = activeWebDavAuth ?: return
        viewModelScope.launch {
            val downloading = _uiState.value.downloadingPaths.toMutableSet()
            downloading.add(item.path)
            _uiState.update { it.copy(downloadingPaths = downloading.toImmutableSet()) }

            try {
                val fullUrl = buildFullUrl(activeBaseUrl, item.path)
                val savedUri = withContext(Dispatchers.IO) {
                    val stream = WebDav(fullUrl, auth).downloadInputStream()
                    LocalBook.saveBookFile(stream, item.name)
                }

                withContext(Dispatchers.IO) {
                    LocalBook.importFiles(listOf(savedUri))
                }
                    val imported = _uiState.value.importedPaths.toMutableSet()
                    imported.add(item.path)
                    _uiState.update {
                        it.copy(
                            importedPaths = imported.toImmutableSet(),
                            items = it.items.map { i ->
                                if (i.path == item.path) i.copy(isImported = true) else i
                            }.toImmutableList()
                        )
                    }
                    _effects.tryEmit(DriveLibraryEffect.ShowToast("Đã tải ${item.name} vào kệ sách"))
            } catch (e: Exception) {
                _effects.tryEmit(DriveLibraryEffect.ShowToast("Lỗi tải: ${e.message}"))
            } finally {
                val currentDownloading = _uiState.value.downloadingPaths.toMutableSet()
                currentDownloading.remove(item.path)
                _uiState.update { it.copy(downloadingPaths = currentDownloading.toImmutableSet()) }
            }
        }
    }

    private fun openBook(item: DriveCatalogItem) {
        viewModelScope.launch {
            val books = withContext(Dispatchers.IO) {
                appDb.bookDao.findByName(item.name)
            }
            val book = books.firstOrNull()
            if (book != null) {
                _effects.tryEmit(DriveLibraryEffect.OpenBookInfo(book.bookUrl))
            } else {
                downloadAndImport(item)
            }
        }
    }

    private fun addPublicLink(url: String, name: String) {
        viewModelScope.launch {
            val resolved = DriveLinkResolver.resolve(url)
            resolved.onSuccess { linkInfo ->
                val source = DriveLinkResolver.createManagedSource(linkInfo, name)
                val saved = registry.addOrUpdateSource(source)
                _uiState.update { it.copy(activeSheet = null) }
                connectSource(saved)
            }.onFailure { error ->
                _effects.tryEmit(DriveLibraryEffect.ShowToast("Link không hợp lệ: ${error.message}"))
            }
        }
    }

    private fun addGoogleAccount(email: String, rootFolderId: String, name: String) {
        viewModelScope.launch {
            val source = ManagedDriveSource(
                id = "gdrive_${System.currentTimeMillis()}",
                name = name.ifEmpty { "Google Drive ($email)" },
                type = DriveSourceType.GOOGLE_DRIVE_ACCOUNT,
                rootFolderId = rootFolderId,
                accountEmail = email
            )
            val saved = registry.addOrUpdateSource(source)
            _uiState.update { it.copy(activeSheet = null) }
            connectSource(saved)
        }
    }

    private fun deleteSource(source: ManagedDriveSource) {
        viewModelScope.launch {
            if (_uiState.value.activeSource?.id == source.id) {
                disconnect()
            }
            registry.deleteSource(source.id)
            _uiState.update { it.copy(activeDialog = null) }
            _effects.tryEmit(DriveLibraryEffect.ShowToast("Đã xóa nguồn ${source.name}"))
        }
    }

    private fun buildFullUrl(base: String, relPath: String): String {
        val trimmedBase = base.trimEnd('/')
        val trimmedRel = relPath.trimStart('/')
        return if (trimmedRel.isEmpty()) "$trimmedBase/" else "$trimmedBase/$trimmedRel"
    }
}
