package io.legado.app.ui.dict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.QUICK_DICTIONARY_IGNORE_TARGET
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.toQuickPhoneticPair
import io.legado.app.domain.model.toQuickTranslationPair
import com.drducbook.app.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictViewModel(
    private val dictRuleRepository: DictRuleRepository,
    private val bookRepository: BookRepository,
    private val dictionaryGateway: DictionaryGateway,
    private val quickDictionaryGateway: QuickDictionaryGateway,
    private val quickTranslationGateway: QuickTranslationGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DictEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var dictRules: List<DictRule> = emptyList()
    private var dictJob: Job? = null

    fun onIntent(intent: DictIntent) {
        when (intent) {
            is DictIntent.Load -> load(intent.word, intent.bookUrl)
            is DictIntent.SelectRule -> selectRule(intent.index)
        }
    }

    private fun load(word: String, bookUrl: String?) {
        dictJob?.cancel()
        val query = word.trim()
        _uiState.value = DictUiState(
            word = query,
            emptyReason = if (query.isBlank()) DictEmptyReason.BlankWord else null,
        )
        if (query.isBlank()) return

        viewModelScope.launch {
            val (rules, quickLookup) = withContext(Dispatchers.IO) {
                dictRuleRepository.getEnabled() to loadQuickLookup(query, bookUrl)
            }
            dictRules = rules
            _uiState.update { state ->
                state.copy(
                    rules = (listOf(DictRuleUi(nameRes = R.string.dict_quick_translator)) +
                        dictRules.map { DictRuleUi(name = it.name) }).toImmutableList(),
                    pages = (listOf(DictPageUiState(quickLookup = quickLookup)) +
                        dictRules.map { DictPageUiState() }).toImmutableList(),
                    emptyReason = null,
                )
            }
        }
    }

    private suspend fun loadQuickLookup(word: String, bookUrl: String?): QuickLookupUiState {
        val book = bookUrl?.let { bookRepository.getBook(it) }
        val scopedEntries = book
            ?.let { quickDictionaryGateway.getEffectiveEntries(it, word) }
            .orEmpty()
        val quickPairs = scopedEntries.mapNotNull { it.toQuickTranslationPair() }
        val ignored = quickPairs
            .filter { it.translation == QUICK_DICTIONARY_IGNORE_TARGET }
            .map { it.original }
        val bookPairs = book?.let(dictionaryGateway::getBookDictionaries)?.pairs.orEmpty()
        val phonetics = scopedEntries.mapNotNull { it.toQuickPhoneticPair() }
        val sourceText = ignored.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sortedByDescending(String::length)
            .fold(word) { text, term -> text.replace(term, "") }
        val translation = quickTranslationGateway.translate(
            text = sourceText,
            projectTerms = (quickPairs.filterNot {
                it.translation == QUICK_DICTIONARY_IGNORE_TARGET
            } + bookPairs).distinctBy { it.original.trim().lowercase() },
            customPhonetics = phonetics,
        )
        val relevantEntries = scopedEntries.asSequence()
            .filter { entry ->
                entry.raw.isNotBlank() &&
                    (word.contains(entry.raw, ignoreCase = true) ||
                        entry.raw.contains(word, ignoreCase = true))
            }
            .map { entry ->
                QuickLookupEntryUi(
                    raw = entry.raw,
                    value = when (entry.type) {
                        QuickDictionaryType.PHONETIC -> entry.hanViet.ifBlank { entry.target }
                        QuickDictionaryType.IGNORE -> "—"
                        else -> entry.target.ifBlank { entry.hanViet }
                    },
                    type = entry.type,
                    scope = entry.scope,
                )
            }
            .toImmutableList()
        return QuickLookupUiState(
            hanViet = quickTranslationGateway.hanViet(word, phonetics),
            translation = translation,
            scopedEntries = relevantEntries,
        )
    }

    private fun selectRule(index: Int) {
        val state = _uiState.value
        if (index !in state.pages.indices) return
        val page = state.pages[index]
        if (index == 0) {
            _uiState.update { it.copy(selectedIndex = 0) }
            return
        }
        if (page.isLoading) {
            _uiState.update { it.copy(selectedIndex = index) }
            return
        }
        if (page.htmlContent.isNotBlank() || page.emptyReason == DictEmptyReason.NoResult) {
            _uiState.update { it.copy(selectedIndex = index) }
            return
        }
        searchRule(index = index, word = state.word)
    }

    private fun searchRule(index: Int, word: String) {
        dictJob?.cancel()
        val rule = dictRules.getOrNull(index - 1) ?: return
        _uiState.update {
            it.copy(
                selectedIndex = index,
                emptyReason = null,
            ).updatePage(index) { page ->
                page.copy(
                    isLoading = true,
                    htmlContent = "",
                    emptyReason = null,
                )
            }
        }
        dictJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    rule.search(word)
                }
            } catch (error: CancellationException) {
                _uiState.update { state ->
                    if (state.word == word && dictRules.getOrNull(index - 1) === rule) {
                        state.updatePage(index) { page ->
                            if (page.isLoading) {
                                page.copy(isLoading = false)
                            } else {
                                page
                            }
                        }
                    } else {
                        state
                    }
                }
                throw error
            } catch (error: Throwable) {
                error.localizedMessage ?: "ERROR"
            }
            _uiState.update {
                if (it.word != word || dictRules.getOrNull(index - 1) !== rule) {
                    return@update it
                }
                if (result.isBlank()) {
                    it.updatePage(index) { page ->
                        page.copy(
                            isLoading = false,
                            htmlContent = "",
                            emptyReason = DictEmptyReason.NoResult,
                        )
                    }
                } else {
                    it.updatePage(index) { page ->
                        page.copy(
                            isLoading = false,
                            htmlContent = result,
                            emptyReason = null,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        dictJob?.cancel()
    }
}

private fun DictUiState.updatePage(
    index: Int,
    transform: (DictPageUiState) -> DictPageUiState,
): DictUiState {
    if (index !in pages.indices) return this
    return copy(
        pages = pages.mapIndexed { pageIndex, page ->
            if (pageIndex == index) transform(page) else page
        }.toImmutableList()
    )
}
