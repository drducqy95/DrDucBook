package io.legado.app.data.repository

import android.content.Context
import io.legado.app.domain.gateway.AuthoringProjectGateway
import io.legado.app.domain.gateway.AuthoringRecoveryDiagnostic
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AuthoringProjectRepository private constructor(
    private val store: AuthoringProjectFileStore,
) : AuthoringProjectGateway {

    constructor(context: Context) : this(
        AuthoringProjectFileStore(File(context.filesDir, "authoring"))
    )

    internal constructor(root: File) : this(AuthoringProjectFileStore(root))

    private val loadMutex = Mutex()
    private val projectLocks = ConcurrentHashMap<String, Mutex>()
    private val projects = MutableStateFlow<List<AuthoringProject>>(emptyList())
    private var loaded = false

    override fun observeProjects(kind: AuthoringProjectKind): Flow<List<AuthoringProject>> = flow {
        ensureLoaded()
        emitAll(
            projects.map { list ->
                list.filter { it.kind == kind }.sortedByDescending(AuthoringProject::updatedAt)
            }
        )
    }

    override suspend fun getProject(id: String): AuthoringProject? {
        ensureLoaded()
        return projects.value.firstOrNull { it.id == id }
    }

    override suspend fun saveProject(project: AuthoringProject) = withContext(Dispatchers.IO) {
        ensureLoaded()
        projectLock(project.id).withLock {
            store.saveProject(project)
            projects.update { current ->
                (current.filterNot { it.id == project.id } + project)
                    .sortedByDescending(AuthoringProject::updatedAt)
            }
        }
    }

    override suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        projectLock(id).withLock {
            store.deleteProject(id)
            projects.update { current -> current.filterNot { it.id == id } }
        }
    }

    override suspend fun importImage(
        projectId: String,
        displayName: String,
        bytes: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        projectLock(projectId).withLock {
            store.importAsset(projectId, displayName, bytes).absolutePath
        }
    }

    override suspend fun recoveryDiagnostics(): List<AuthoringRecoveryDiagnostic> =
        withContext(Dispatchers.IO) {
            ensureLoaded()
            store.recoveryDiagnostics()
        }

    override suspend fun restoreLatestProjectSnapshot(projectId: String): AuthoringProject? =
        withContext(Dispatchers.IO) {
            ensureLoaded()
            projectLock(projectId).withLock {
                store.restoreLatestProjectSnapshot(projectId)?.also { restored ->
                    projects.update { current ->
                        (current.filterNot { it.id == restored.id } + restored)
                            .sortedByDescending(AuthoringProject::updatedAt)
                    }
                }
            }
        }

    override suspend fun deleteRecoveryDiagnostic(id: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        store.deleteRecoveryDiagnostic(id)
    }

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            if (loaded) return@withLock
            projects.value = store.loadProjects()
            loaded = true
        }
    }

    private fun projectLock(projectId: String): Mutex =
        projectLocks.getOrPut(projectId) { Mutex() }
}
