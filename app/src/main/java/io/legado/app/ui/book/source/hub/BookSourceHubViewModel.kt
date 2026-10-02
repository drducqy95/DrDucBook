package io.legado.app.ui.book.source.hub

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.data.repository.online_source.OnlineBookSourceRepository
import io.legado.app.domain.model.OnlineBookSourceItem
import io.legado.app.domain.model.OnlineSourceCollectionItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookSourceHubViewModel(
    private val application: Application,
    private val repository: OnlineBookSourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookSourceHubUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookSourceHubEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        loadInitialData()
    }

    fun onIntent(intent: BookSourceHubIntent) {
        when (intent) {
            is BookSourceHubIntent.SwitchTab -> {
                _uiState.update { it.copy(currentTab = intent.tab) }
                if (intent.tab == BookSourceHubTab.YCKCEO && _uiState.value.yckceoSources.isEmpty()) {
                    searchYckceo()
                } else if (intent.tab == BookSourceHubTab.MIAOGONGZI && _uiState.value.miaogongziBundles.isEmpty()) {
                    loadMiaoGongZi()
                }
            }
            is BookSourceHubIntent.ChangeSearchQuery -> _uiState.update { it.copy(searchQuery = intent.query) }
            is BookSourceHubIntent.ChangeCategory -> {
                _uiState.update { it.copy(selectedCategory = intent.category) }
                searchYckceo()
            }
            BookSourceHubIntent.Search -> searchYckceo()
            BookSourceHubIntent.LoadInitialData -> loadInitialData()
            is BookSourceHubIntent.ImportYckceoSource -> importYckceo(intent.item)
            is BookSourceHubIntent.ImportBundle -> importBundle(intent.item)
        }
    }

    private fun loadInitialData() {
        searchYckceo()
        loadMiaoGongZi()
    }

    private fun searchYckceo() {
        val query = _uiState.value.searchQuery
        val category = _uiState.value.selectedCategory
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val results = repository.searchYckceo(query = query, page = 1, category = category)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        yckceoSources = results.toImmutableList(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage,
                    )
                }
            }
        }
    }

    private fun loadMiaoGongZi() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bundles = repository.getMiaoGongZiBundles()
                _uiState.update {
                    it.copy(miaogongziBundles = bundles.toImmutableList())
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun importYckceo(item: OnlineBookSourceItem) {
        val id = item.id
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(importingIds = (it.importingIds + id).toImmutableList()) }
            try {
                val count = repository.importFromUrl(item.downloadUrl)
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_imported_success, count)
                    )
                )
            } catch (e: Exception) {
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_import_failed, e.localizedMessage ?: "Lỗi")
                    )
                )
            } finally {
                _uiState.update { it.copy(importingIds = (it.importingIds - id).toImmutableList()) }
            }
        }
    }

    private fun importBundle(item: OnlineSourceCollectionItem) {
        val id = item.downloadUrl
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(importingIds = (it.importingIds + id).toImmutableList()) }
            try {
                val count = repository.importFromUrl(item.downloadUrl)
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_imported_success, count)
                    )
                )
            } catch (e: Exception) {
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_import_failed, e.localizedMessage ?: "Lỗi")
                    )
                )
            } finally {
                _uiState.update { it.copy(importingIds = (it.importingIds - id).toImmutableList()) }
            }
        }
    }
}
