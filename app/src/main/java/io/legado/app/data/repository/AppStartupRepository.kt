package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.AppStartupGateway
import io.legado.app.help.DefaultData
import io.legado.app.help.vbook.VbookPluginAdapter
import io.legado.app.help.vbook.VbookPluginImporter
import splitties.init.appCtx

class AppStartupRepository(
    private val appDatabase: AppDatabase
) : AppStartupGateway {

    override suspend fun deleteNotShelfBooks() {
        appDatabase.bookDao.deleteNotShelfBook()
    }

    override suspend fun ensureDefaultHttpTts() {
        if (appDatabase.httpTTSDao.count == 0) {
            appDatabase.httpTTSDao.insert(*DefaultData.httpTTS.toTypedArray())
        }
    }

    override suspend fun reconcileVbookSourceTypes(): Int {
        val changed = appDatabase.bookSourceDao.all
            .asSequence()
            .filter { it.bookSourceUrl.startsWith(VbookPluginAdapter.SOURCE_PREFIX) }
            .filter { VbookPluginImporter.reconcileInstalledSourceType(appCtx, it) }
            .toList()
        if (changed.isNotEmpty()) {
            appDatabase.bookSourceDao.update(*changed.toTypedArray())
        }
        return changed.size
    }
}
