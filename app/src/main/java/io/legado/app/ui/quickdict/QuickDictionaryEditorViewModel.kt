package io.legado.app.ui.quickdict

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.keyFor
import io.legado.app.domain.model.quickDictionaryUniverseKey
import io.legado.app.domain.model.toQuickPhoneticPair
import io.legado.app.domain.model.toQuickTranslationPair
import io.legado.app.domain.usecase.TranslateChapterUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuickDictionaryEditorViewModel(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val quickDictionaryGateway: QuickDictionaryGateway,
    private val quickTranslationGateway: QuickTranslationGateway,
    private val translateChapterUseCase: TranslateChapterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickDictionaryUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<QuickDictionaryEditorEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var currentBook: Book? = null
    private var currentAnchor: QuickDictionarySelectionAnchor? = null
    private var mappingAlternatives: List<QuickDictionarySelectionAnchor> = emptyList()
    private var loadJob: Job? = null
    private var suggestionJob: Job? = null

    fun onIntent(intent: QuickDictionaryEditorIntent) {
        when (intent) {
            is QuickDictionaryEditorIntent.Load -> load(intent.request)
            is QuickDictionaryEditorIntent.SetRaw -> setRaw(intent.value)
            is QuickDictionaryEditorIntent.SetHanViet -> updateForm { copy(hanViet = intent.value) }
            is QuickDictionaryEditorIntent.SetTarget -> updateForm { copy(target = intent.value) }
            is QuickDictionaryEditorIntent.RequestSuggestion -> requestSuggestion(intent.provider)
            is QuickDictionaryEditorIntent.ApplySuggestion -> updateForm { copy(target = intent.value) }
            is QuickDictionaryEditorIntent.SetType -> updateForm { copy(type = intent.value) }
            is QuickDictionaryEditorIntent.SetScope -> updateForm {
                copy(
                    scope = intent.value,
                    universeKey = if (intent.value == QuickDictionaryScope.UNIVERSE) universeKey else "",
                    universeName = if (intent.value == QuickDictionaryScope.UNIVERSE) universeName else "",
                    contextMarkers = if (intent.value == QuickDictionaryScope.UNIVERSE) contextMarkers else "",
                )
            }
            is QuickDictionaryEditorIntent.AdjustSelection -> adjustSelection(intent.action)
            is QuickDictionaryEditorIntent.SelectUniverse -> selectUniverse(intent.key)
            is QuickDictionaryEditorIntent.SetUniverseName -> updateForm {
                copy(universeName = intent.value)
            }
            is QuickDictionaryEditorIntent.SetContextMarkers -> updateForm {
                copy(contextMarkers = intent.value)
            }
            is QuickDictionaryEditorIntent.SelectMappingAlternative -> {
                selectMappingAlternative(intent.index)
            }
            QuickDictionaryEditorIntent.DismissMappingAlternatives -> updateForm {
                copy(showSelectionChooser = false)
            }
            QuickDictionaryEditorIntent.Save -> save()
        }
    }

    private fun load(request: QuickDictionaryRequest) {
        loadJob?.cancel()
        suggestionJob?.cancel()
        currentBook = null
        currentAnchor = null
        mappingAlternatives = emptyList()
        val fallback = request.selectedText.trim()
        if (fallback.isBlank()) return
        val canUseDirectFallback = request.sourceText.isBlank() ||
            request.sourceText == request.displayText
        _uiState.value = QuickDictionaryUiState(
            raw = fallback.takeIf { canUseDirectFallback }.orEmpty(),
            sourceLocation = request.sourceLocation,
            sourceUrl = request.sourceUrl,
            providerOptions = providerOptions(),
        )
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val book = bookRepository.getBook(request.bookUrl)
            val effectiveEntries = book?.let {
                quickDictionaryGateway.getEffectiveEntries(it, request.sourceText)
            }.orEmpty()
            val scopedTerms = effectiveEntries.mapNotNull { it.toQuickTranslationPair() }
            val scopedPhonetics = effectiveEntries.mapNotNull { it.toQuickPhoneticPair() }
            val resolution = resolveQuickDictionarySelectionResult(
                request = request,
                quickTranslationGateway = quickTranslationGateway,
                candidateTranslator = { candidate ->
                    quickTranslationGateway.translate(candidate, scopedTerms, scopedPhonetics)
                },
                candidatePhoneticReader = { candidate ->
                    quickTranslationGateway.hanViet(candidate, scopedPhonetics)
                },
            )
            val universes = quickDictionaryGateway.getUniverses()
            currentBook = book
            mappingAlternatives = resolution.alternatives
            val anchor = resolution.anchor
            if (anchor != null) {
                applyResolvedAnchor(anchor, book, universes)
                requestSuggestion(TranslationConstants.PROVIDER_QUICK_TRANSLATOR)
            } else {
                currentAnchor = null
                _uiState.update {
                    it.copy(
                        raw = fallback.takeIf { canUseDirectFallback }.orEmpty(),
                        hanViet = "",
                        contextBefore = "",
                        contextAfter = "",
                        scope = if (book == null) {
                            QuickDictionaryScope.GLOBAL
                        } else {
                            QuickDictionaryScope.PROJECT
                        },
                        availableUniverses = universes.toImmutableList(),
                        selectionAlternatives = resolution.alternatives.map { alternative ->
                            QuickDictionarySelectionAlternativeUi(
                                raw = alternative.rawText,
                                contextBefore = alternative.contextBefore,
                                contextAfter = alternative.contextAfter,
                            )
                        }.toImmutableList(),
                        showSelectionChooser = resolution.requiresConfirmation,
                        errorMessage = if (canUseDirectFallback) null else application.getString(
                            R.string.quick_dictionary_mapping_confirmation_required
                        ),
                    )
                }
            }
        }
    }

    private fun selectMappingAlternative(index: Int) {
        val anchor = mappingAlternatives.getOrNull(index) ?: return
        val book = currentBook
        currentAnchor = anchor
        _uiState.update {
            it.copy(
                raw = anchor.rawText,
                hanViet = quickTranslationGateway.hanViet(anchor.rawText),
                target = "",
                contextBefore = anchor.contextBefore,
                contextAfter = anchor.contextAfter,
                canExpandSelectionLeft = anchor.canExpandLeft,
                canExpandSelectionRight = anchor.canExpandRight,
                canShrinkSelectionLeft = anchor.canShrinkLeft,
                canShrinkSelectionRight = anchor.canShrinkRight,
                scope = if (book == null) QuickDictionaryScope.GLOBAL else QuickDictionaryScope.PROJECT,
                showSelectionChooser = false,
                errorMessage = null,
            )
        }
        requestSuggestion(TranslationConstants.PROVIDER_QUICK_TRANSLATOR)
    }

    private fun applyResolvedAnchor(
        anchor: QuickDictionarySelectionAnchor,
        book: Book?,
        universes: List<QuickDictionaryUniverse>,
    ) {
        currentAnchor = anchor
        _uiState.update {
            it.copy(
                raw = anchor.rawText,
                hanViet = quickTranslationGateway.hanViet(anchor.rawText),
                contextBefore = anchor.contextBefore,
                contextAfter = anchor.contextAfter,
                scope = if (book == null) QuickDictionaryScope.GLOBAL else QuickDictionaryScope.PROJECT,
                availableUniverses = universes.toImmutableList(),
                canExpandSelectionLeft = anchor.canExpandLeft,
                canExpandSelectionRight = anchor.canExpandRight,
                canShrinkSelectionLeft = anchor.canShrinkLeft,
                canShrinkSelectionRight = anchor.canShrinkRight,
                selectionAlternatives = persistentListOf(),
                showSelectionChooser = false,
                errorMessage = null,
            )
        }
    }

    private fun setRaw(value: String) {
        currentAnchor = null
        updateForm {
            copy(
                raw = value,
                target = "",
                contextBefore = "",
                contextAfter = "",
                suggestions = persistentListOf(),
                canExpandSelectionLeft = false,
                canExpandSelectionRight = false,
                canShrinkSelectionLeft = false,
                canShrinkSelectionRight = false,
                selectionAlternatives = persistentListOf(),
                showSelectionChooser = false,
            )
        }
    }

    private fun adjustSelection(action: QuickDictionarySelectionAction) {
        val adjusted = when (action) {
            QuickDictionarySelectionAction.EXPAND_LEFT -> currentAnchor?.expandLeft()
            QuickDictionarySelectionAction.EXPAND_RIGHT -> currentAnchor?.expandRight()
            QuickDictionarySelectionAction.SHRINK_LEFT -> currentAnchor?.shrinkLeft()
            QuickDictionarySelectionAction.SHRINK_RIGHT -> currentAnchor?.shrinkRight()
        } ?: return
        currentAnchor = adjusted
        val raw = adjusted.rawText
        _uiState.update {
            it.copy(
                raw = raw,
                hanViet = quickTranslationGateway.hanViet(raw),
                target = "",
                contextBefore = adjusted.contextBefore,
                contextAfter = adjusted.contextAfter,
                suggestions = persistentListOf(),
                canExpandSelectionLeft = adjusted.canExpandLeft,
                canExpandSelectionRight = adjusted.canExpandRight,
                canShrinkSelectionLeft = adjusted.canShrinkLeft,
                canShrinkSelectionRight = adjusted.canShrinkRight,
                errorMessage = null,
            )
        }
        requestSuggestion(TranslationConstants.PROVIDER_QUICK_TRANSLATOR)
    }

    private fun requestSuggestion(provider: String) {
        if (provider !in TranslationConstants.providerValues) return
        val raw = _uiState.value.raw.trim()
        if (raw.isBlank()) return
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(selectedProvider = provider, isSuggesting = true, errorMessage = null)
        }
        val anchor = currentAnchor
        suggestionJob = viewModelScope.launch(Dispatchers.IO) {
            translateChapterUseCase.executeSuggestion(
                text = raw,
                provider = provider,
                book = currentBook,
                previousContext = anchor?.contextBefore.orEmpty(),
                nextContext = anchor?.contextAfter.orEmpty(),
            ).onSuccess { translated ->
                _uiState.update { current ->
                    if (current.raw.trim() != raw || current.selectedProvider != provider) current
                    else current.copy(
                        target = translated,
                        suggestions = (current.suggestions.filterNot { it.provider == provider } +
                            QuickDictionarySuggestionUi(
                                provider = provider,
                                providerLabel = providerLabel(provider),
                                text = translated,
                            )).toImmutableList(),
                        isSuggesting = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    if (current.raw.trim() != raw || current.selectedProvider != provider) current
                    else current.copy(
                        isSuggesting = false,
                        errorMessage = error.localizedMessage
                            ?: application.getString(R.string.quick_dictionary_suggestion_failed),
                    )
                }
            }
        }
    }

    private fun selectUniverse(key: String) {
        updateForm {
            val universe = availableUniverses.firstOrNull { it.key == key }
            if (universe == null) copy(universeKey = "", universeName = "", contextMarkers = "")
            else copy(
                universeKey = universe.key,
                universeName = universe.name,
                contextMarkers = universe.contextMarkers.joinToString("\n"),
            )
        }
    }

    private fun save() {
        val form = _uiState.value
        val book = currentBook
        val requiresValue = form.type != QuickDictionaryType.IGNORE
        if (form.raw.isBlank() || (requiresValue && form.hanViet.isBlank() && form.target.isBlank())) {
            _uiState.update {
                it.copy(errorMessage = application.getString(R.string.quick_dictionary_required_error))
            }
            return
        }
        if (form.scope == QuickDictionaryScope.PROJECT && book == null) {
            _uiState.update {
                it.copy(errorMessage = application.getString(R.string.quick_dictionary_required_error))
            }
            return
        }
        val markers = form.contextMarkers.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        val universeKey = if (form.scope == QuickDictionaryScope.UNIVERSE) {
            form.universeKey.ifBlank { quickDictionaryUniverseKey(form.universeName) }
        } else {
            ""
        }
        if (form.scope == QuickDictionaryScope.UNIVERSE &&
            (universeKey.isBlank() || form.universeName.isBlank() || markers.isEmpty())
        ) {
            _uiState.update {
                it.copy(errorMessage = application.getString(R.string.quick_dictionary_universe_required_error))
            }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (form.scope == QuickDictionaryScope.UNIVERSE) {
                    quickDictionaryGateway.saveUniverse(
                        QuickDictionaryUniverse(
                            key = universeKey,
                            name = form.universeName,
                            contextMarkers = markers,
                        )
                    )
                }
                val scopeKey = if (book == null) "" else form.scope.keyFor(book, universeKey)
                quickDictionaryGateway.save(
                    QuickDictionaryEntry(
                        raw = form.raw,
                        hanViet = form.hanViet,
                        target = if (form.type == QuickDictionaryType.IGNORE) "" else form.target,
                        type = form.type,
                        scope = form.scope,
                        scopeKey = scopeKey,
                    )
                )
            }.onSuccess {
                _uiState.value = QuickDictionaryUiState()
                _effects.tryEmit(QuickDictionaryEditorEffect.Saved)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = error.localizedMessage)
                }
            }
        }
    }

    private fun providerOptions(): ImmutableList<QuickDictionaryProviderUi> =
        TranslationConstants.providerValues.zip(TranslationConstants.providerDisplayNames)
            .map { (value, label) -> QuickDictionaryProviderUi(value, label) }
            .toImmutableList()

    private fun providerLabel(provider: String): String =
        providerOptions().firstOrNull { it.value == provider }?.label ?: provider

    private fun updateForm(update: QuickDictionaryUiState.() -> QuickDictionaryUiState) {
        _uiState.update { it.update().copy(errorMessage = null) }
    }

    override fun onCleared() {
        loadJob?.cancel()
        suggestionJob?.cancel()
    }
}
