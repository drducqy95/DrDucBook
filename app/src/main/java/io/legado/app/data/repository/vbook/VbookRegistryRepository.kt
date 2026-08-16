package io.legado.app.data.repository.vbook

import io.legado.app.domain.gateway.VbookRegistryGateway
import io.legado.app.domain.model.VbookRegistryOrigin
import io.legado.app.domain.model.VbookRegistrySnapshot
import io.legado.app.help.http.await
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class VbookRegistryRepository(
    private val root: File = File(appCtx.filesDir, CACHE_DIRECTORY),
    private val client: OkHttpClient = okHttpClient,
    private val registryUrl: String = DEFAULT_REGISTRY_URL,
    private val now: () -> Long = System::currentTimeMillis,
) : VbookRegistryGateway {

    private val mutex = Mutex()

    override suspend fun load(forceRefresh: Boolean): Result<VbookRegistrySnapshot> {
        return try {
            Result.success(withContext(Dispatchers.IO) {
                mutex.withLock { loadInternal(forceRefresh) }
            })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override suspend fun loadCached(): Result<VbookRegistrySnapshot> {
        return try {
            Result.success(withContext(Dispatchers.IO) {
                mutex.withLock {
                    val cached = readValidCache()
                        ?: throw IOException("Chưa có cache VBook registry hợp lệ")
                    cached.snapshot(
                        fetchedAt = cached.state.fetchedAt,
                        origin = VbookRegistryOrigin.CACHE_FRESH,
                    )
                }
            })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun loadInternal(forceRefresh: Boolean): VbookRegistrySnapshot {
        val cached = readValidCache()
        val currentTime = now()
        if (
            !forceRefresh &&
            cached != null &&
            currentTime - cached.state.fetchedAt in 0 until CACHE_FRESH_MS
        ) {
            return cached.snapshot(
                fetchedAt = cached.state.fetchedAt,
                origin = VbookRegistryOrigin.CACHE_FRESH,
            )
        }
        return try {
            val request = Request.Builder()
                .url(registryUrl)
                .get()
                .apply {
                    cached?.state?.etag?.takeIf(String::isNotBlank)?.let {
                        header("If-None-Match", it)
                    }
                    cached?.state?.lastModified?.takeIf(String::isNotBlank)?.let {
                        header("If-Modified-Since", it)
                    }
                }
                .build()
            client.newCall(request).await().use { response ->
                when {
                    response.code == 304 && cached != null -> {
                        val refreshedState = cached.state.copy(fetchedAt = currentTime)
                        writeState(refreshedState)
                        cached.snapshot(
                            fetchedAt = currentTime,
                            origin = VbookRegistryOrigin.CACHE_VALIDATED,
                        )
                    }

                    response.isSuccessful -> {
                        val json = response.body.string()
                        val snapshot = VbookRegistryParser.parse(
                            json = json,
                            fetchedAt = currentTime,
                            origin = VbookRegistryOrigin.NETWORK,
                        )
                        writeCache(
                            json = json,
                            state = CacheState(
                                etag = response.header("ETag").orEmpty(),
                                lastModified = response.header("Last-Modified").orEmpty(),
                                sha256 = sha256(json),
                                fetchedAt = currentTime,
                            ),
                        )
                        snapshot
                    }

                    else -> throw IOException(
                        "Không thể tải VBook registry: HTTP ${response.code}"
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            cached?.snapshot(
                fetchedAt = cached.state.fetchedAt,
                origin = VbookRegistryOrigin.CACHE_STALE_FALLBACK,
            ) ?: throw error
        }
    }

    private fun readValidCache(): CachedRegistry? {
        val jsonFile = File(root, REGISTRY_FILE)
        val stateFile = File(root, STATE_FILE)
        if (!jsonFile.isFile || !stateFile.isFile) return null
        return runCatching {
            val json = jsonFile.readText(Charsets.UTF_8)
            val state = GSON.fromJson(stateFile.readText(Charsets.UTF_8), CacheState::class.java)
            require(state.sha256.equals(sha256(json), ignoreCase = true)) {
                "VBook registry cache checksum không khớp"
            }
            VbookRegistryParser.parse(
                json = json,
                fetchedAt = state.fetchedAt,
                origin = VbookRegistryOrigin.CACHE_FRESH,
            )
            CachedRegistry(json, state)
        }.getOrNull()
    }

    private fun CachedRegistry.snapshot(
        fetchedAt: Long,
        origin: VbookRegistryOrigin,
    ): VbookRegistrySnapshot = VbookRegistryParser.parse(
        json = json,
        fetchedAt = fetchedAt,
        origin = origin,
    )

    private fun writeCache(json: String, state: CacheState) {
        root.mkdirs()
        writeAtomically(File(root, REGISTRY_FILE), json)
        writeState(state)
    }

    private fun writeState(state: CacheState) {
        root.mkdirs()
        writeAtomically(File(root, STATE_FILE), GSON.toJson(state))
    }

    private fun writeAtomically(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private data class CacheState(
        val etag: String = "",
        val lastModified: String = "",
        val sha256: String = "",
        val fetchedAt: Long = 0L,
    )

    private data class CachedRegistry(
        val json: String,
        val state: CacheState,
    )

    companion object {
        const val DEFAULT_REGISTRY_URL =
            "https://www.vbookext.me/api/registry/vbook-fd1246b6.json"
        private const val CACHE_DIRECTORY = "vbook_registry"
        private const val REGISTRY_FILE = "registry.json"
        private const val STATE_FILE = "state.json"
        private const val CACHE_FRESH_MS = 24L * 60L * 60L * 1_000L
    }
}
