package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType

class SyncStoryMemoryWithQuickDictionaryUseCase(
    private val translationStoryMemoryUseCase: TranslationStoryMemoryUseCase,
    private val quickDictionaryGateway: QuickDictionaryGateway,
) {
    suspend fun syncStoryMemoryToQuickDictionary(bookUrl: String) {
        val snapshot = translationStoryMemoryUseCase.loadSnapshot(bookUrl)
        snapshot.entities.forEach { entity ->
            if (entity.raw.isNotBlank() && entity.target.isNotBlank()) {
                quickDictionaryGateway.save(
                    QuickDictionaryEntry(
                        raw = entity.raw,
                        hanViet = "",
                        target = entity.target,
                        type = QuickDictionaryType.NAME,
                        scope = QuickDictionaryScope.PROJECT,
                        scopeKey = bookUrl,
                    )
                )
            }
        }
        snapshot.worldBuilding.forEach { world ->
            if (world.raw.isNotBlank() && world.target.isNotBlank()) {
                quickDictionaryGateway.save(
                    QuickDictionaryEntry(
                        raw = world.raw,
                        hanViet = "",
                        target = world.target,
                        type = QuickDictionaryType.VIETPHRASE,
                        scope = QuickDictionaryScope.PROJECT,
                        scopeKey = bookUrl,
                    )
                )
            }
        }
    }

    suspend fun syncQuickDictionaryToStoryMemory(book: Book) {
        val entries = quickDictionaryGateway.getEffectiveEntries(book)
        entries.filter { it.scope == QuickDictionaryScope.PROJECT && it.scopeKey == book.bookUrl }
            .forEach { entry ->
                if (entry.raw.isNotBlank() && entry.target.isNotBlank()) {
                    try {
                        translationStoryMemoryUseCase.addQuickDictionaryEntry(
                            book = book,
                            raw = entry.raw,
                            target = entry.target,
                            type = entry.type,
                        )
                    } catch (_: Throwable) {
                    }
                }
            }
    }
}
