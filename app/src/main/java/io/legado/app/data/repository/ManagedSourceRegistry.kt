package io.legado.app.data.repository

import android.content.Context
import io.legado.app.data.appDb
import io.legado.app.data.entities.Server
import io.legado.app.domain.model.DriveConnectionStatus
import io.legado.app.domain.model.ManagedDriveSource
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ManagedSourceRegistry(private val context: Context) {

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val storageFile by lazy {
        File(context.filesDir, "managed_drive_sources.json")
    }

    private val _sources = MutableStateFlow<List<ManagedDriveSource>>(emptyList())
    val sources: StateFlow<List<ManagedDriveSource>> = _sources.asStateFlow()

    private val _activeSourceId = MutableStateFlow<String?>(null)
    val activeSourceId: StateFlow<String?> = _activeSourceId.asStateFlow()

    init {
        scope.launch {
            loadFromDisk()
        }
    }

    private suspend fun loadFromDisk() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (storageFile.exists()) {
                    val json = storageFile.readText()
                    val list = GSON.fromJsonArray<ManagedDriveSource>(json).getOrNull() ?: emptyList()
                    _sources.value = list
                    if (_activeSourceId.value == null && list.isNotEmpty()) {
                        _activeSourceId.value = list.first().id
                    }
                }
            } catch (e: Exception) {
                LogUtils.e("ManagedSourceRegistry", "Failed to load sources: ${e.message}")
            }
        }
    }

    private suspend fun persistToDisk() = withContext(Dispatchers.IO) {
        try {
            val json = GSON.toJson(_sources.value)
            storageFile.writeText(json)
        } catch (e: Exception) {
            LogUtils.e("ManagedSourceRegistry", "Failed to persist sources: ${e.message}")
        }
    }

    suspend fun getActiveSource(): ManagedDriveSource? = mutex.withLock {
        val activeId = _activeSourceId.value ?: return null
        _sources.value.find { it.id == activeId }
    }

    suspend fun setActiveSource(sourceId: String) = mutex.withLock {
        if (_sources.value.any { it.id == sourceId }) {
            _activeSourceId.value = sourceId
        }
    }

    suspend fun addOrUpdateSource(source: ManagedDriveSource): ManagedDriveSource = mutex.withLock {
        val current = _sources.value.toMutableList()
        val index = current.indexOfFirst { it.id == source.id }
        val updated = if (index >= 0) {
            val existing = current[index]
            source.copy(
                serverId = if (source.serverId != 0L) source.serverId else existing.serverId,
                createdAt = existing.createdAt,
                lastAccessedAt = System.currentTimeMillis()
            ).also { current[index] = it }
        } else {
            source.also { current.add(0, it) }
        }

        _sources.value = current
        _activeSourceId.value = updated.id
        persistToDisk()
        updated
    }

    suspend fun updateStatus(sourceId: String, status: DriveConnectionStatus) = mutex.withLock {
        val current = _sources.value.map {
            if (it.id == sourceId) it.copy(status = status) else it
        }
        _sources.value = current
        persistToDisk()
    }

    suspend fun deleteSource(sourceId: String) = mutex.withLock {
        val target = _sources.value.find { it.id == sourceId }
        if (target != null && target.serverId != 0L) {
            withContext(Dispatchers.IO) {
                appDb.serverDao.delete(target.serverId)
            }
        }

        val current = _sources.value.filterNot { it.id == sourceId }
        _sources.value = current
        if (_activeSourceId.value == sourceId) {
            _activeSourceId.value = current.firstOrNull()?.id
        }
        persistToDisk()
    }

    suspend fun syncServerEntity(
        source: ManagedDriveSource,
        port: Int,
        sessionSecret: String
    ): Long = withContext(Dispatchers.IO) {
        val config = Server.WebDavConfig(
            url = "http://127.0.0.1:$port/",
            username = "legado",
            password = sessionSecret
        )
        val configJson = GSON.toJson(config)

        val server = if (source.serverId != 0L) {
            val existing = appDb.serverDao.get(source.serverId)
            if (existing != null) {
                existing.name = source.name
                existing.config = configJson
                appDb.serverDao.update(existing)
                existing
            } else {
                val newServer = Server(
                    id = System.currentTimeMillis(),
                    name = source.name,
                    type = Server.TYPE.WEBDAV,
                    config = configJson
                )
                appDb.serverDao.insert(newServer)
                newServer
            }
        } else {
            val newServer = Server(
                id = System.currentTimeMillis(),
                name = source.name,
                type = Server.TYPE.WEBDAV,
                config = configJson
            )
            appDb.serverDao.insert(newServer)
            newServer
        }

        addOrUpdateSource(source.copy(serverId = server.id))
        server.id
    }
}

