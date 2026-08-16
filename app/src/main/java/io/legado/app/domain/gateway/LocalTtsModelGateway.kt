package io.legado.app.domain.gateway

import android.net.Uri
import io.legado.app.domain.model.LocalTtsImportProgress
import io.legado.app.domain.model.LocalTtsModelInfo
import io.legado.app.domain.model.LocalTtsModelTestResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

interface LocalTtsModelGateway {
    fun observeModels(): Flow<ImmutableList<LocalTtsModelInfo>>

    suspend fun refresh()

    suspend fun importModel(
        uri: Uri,
        onProgress: (LocalTtsImportProgress) -> Unit = {},
    ): LocalTtsModelInfo

    suspend fun deleteModel(modelId: String)

    suspend fun testModel(
        modelId: String,
        voiceId: Int,
        testPhrase: String,
    ): LocalTtsModelTestResult

    suspend fun selectDefaultModel(modelId: String, voiceId: Int)

    fun getModelInfo(modelId: String): LocalTtsModelInfo?
}
