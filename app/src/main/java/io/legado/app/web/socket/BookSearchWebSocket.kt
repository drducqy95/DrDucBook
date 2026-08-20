package io.legado.app.web.socket

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import com.drducbook.app.R
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.MatchMode
import io.legado.app.domain.usecase.BookSearchControl
import io.legado.app.domain.usecase.BookSearchRequest
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SearchRunEvent
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJson
import io.legado.app.utils.printOnDebug
import io.legado.app.web.WebServicePolicyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import splitties.init.appCtx

class BookSearchWebSocket(private val session: DefaultWebSocketServerSession) : CoroutineScope by session {

    private val searchBooksUseCase: SearchBooksUseCase by lazy { GlobalContext.get().get() }
    private val localPreferencesRepository: LocalPreferencesRepository by lazy {
        GlobalContext.get().get()
    }
    private val searchControl = BookSearchControl()
    private val sentBookUrls = linkedSetOf<String>()
    private var searchJob: Job? = null

    private val SEARCH_FINISH = "Tìm kiếm hoàn tất"

    suspend fun handle() {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (!text.isJson()) {
                        session.send("Dữ liệu phải ở định dạng JSON")
                        session.close(CloseReason(CloseReason.Codes.NORMAL, SEARCH_FINISH))
                        break
                    }
                    val searchRequest = GSON.fromJsonObject<WebSearchRequest>(text).getOrNull()
                    if (searchRequest != null) {
                        val key = searchRequest.key.trim()
                        if (key.isNullOrBlank()) {
                            session.send(appCtx.getString(R.string.cannot_empty))
                            session.close(CloseReason(CloseReason.Codes.NORMAL, SEARCH_FINISH))
                            break
                        }
                        startSearch(key, searchRequest.sourceUrls)
                    }
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
        } finally {
            searchJob?.cancel()
        }
    }

    private fun startSearch(key: String, requestedSourceUrls: List<String>?) {
        searchJob?.cancel()
        sentBookUrls.clear()
        searchControl.resume()
        searchJob = launch(Dispatchers.IO) {
            try {
                searchBooksUseCase
                    .execute(
                        BookSearchRequest(
                            keyword = key,
                            page = 1,
                            scope = resolveScope(requestedSourceUrls),
                            matchMode = MatchMode.of(
                                localPreferencesRepository
                                    .getPreference(
                                        LocalPreferencesKeys.MATCH_MODE,
                                        MatchMode.DEFAULT.value
                                    )
                                    .first()
                            ),
                            concurrency = OtherConfig.threadCount,
                        ),
                        searchControl
                    )
                    .collect { event ->
                        when (event) {
                            SearchRunEvent.Started -> Unit
                            is SearchRunEvent.Progress -> {
                                val newBooks = event.upsertBooks.filter { sentBookUrls.add(it.bookUrl) }
                                if (newBooks.isNotEmpty()) {
                                    session.send(GSON.toJson(newBooks))
                                }
                            }

                            is SearchRunEvent.Finished -> session.close(CloseReason(CloseReason.Codes.NORMAL, SEARCH_FINISH))
                        }
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                session.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, exception.toString()))
            }
        }
    }

    /**
     * Web source selection must be independent from the native search preference. The web UI
     * stores its selected sources in the WebService policy; an explicit request list is accepted
     * as well for external web clients. If neither is present, preserve the native preference.
     */
    private suspend fun resolveScope(requestedSourceUrls: List<String>?): BookSearchScope {
        val requested = requestedSourceUrls
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val policySources = WebServicePolicyStore.read(appCtx).webDiscoverySourceUrls
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val sourceUrls = (requested.ifEmpty { policySources })
        if (sourceUrls.isNotEmpty()) {
            val sourceDao = GlobalContext.get().get<BookSourceDao>()
            val sources = sourceUrls.mapNotNull { url ->
                sourceDao.getBookSource(url)?.let { source ->
                    BookSearchScope.ScopeSourceItem(source.bookSourceName, source.bookSourceUrl)
                }
            }
            if (sources.isNotEmpty()) return BookSearchScope.encodeSources(sources).let(::BookSearchScope)
        }
        return BookSearchScope(
            localPreferencesRepository
                .getPreference(LocalPreferencesKeys.SEARCH_SCOPE, "")
                .first()
        )
    }

    private data class WebSearchRequest(
        val key: String = "",
        val sourceUrls: List<String>? = null,
    )
}
