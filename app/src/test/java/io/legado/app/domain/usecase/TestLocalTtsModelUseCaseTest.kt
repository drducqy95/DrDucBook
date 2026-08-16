package io.legado.app.domain.usecase

import android.net.Uri
import io.legado.app.domain.model.LocalTtsImportProgress
import io.legado.app.domain.gateway.LocalTtsModelGateway
import io.legado.app.domain.model.LocalTtsModelInfo
import io.legado.app.domain.model.LocalTtsModelTestResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestLocalTtsModelUseCaseTest {

    @Test
    fun blankPhraseIsRejectedBeforeRuntimeCall() = runBlocking {
        val gateway = RecordingGateway()

        val result = TestLocalTtsModelUseCase(gateway)("model", 0, "  ")

        assertFalse(result.success)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun validPhraseDelegatesToGateway() = runBlocking {
        val gateway = RecordingGateway()

        val result = TestLocalTtsModelUseCase(gateway)("model", 2, "Xin chào")

        assertTrue(result.success)
        assertEquals(1, gateway.calls)
        assertEquals("model:2:Xin chào", gateway.lastCall)
    }

    private class RecordingGateway : LocalTtsModelGateway {
        var calls = 0
        var lastCall = ""

        override fun observeModels(): Flow<ImmutableList<LocalTtsModelInfo>> =
            flowOf(persistentListOf())

        override suspend fun refresh() = Unit

        override suspend fun importModel(
            uri: Uri,
            onProgress: (LocalTtsImportProgress) -> Unit,
        ): LocalTtsModelInfo = error("unused")

        override suspend fun deleteModel(modelId: String) = Unit

        override suspend fun testModel(
            modelId: String,
            voiceId: Int,
            testPhrase: String,
        ): LocalTtsModelTestResult {
            calls++
            lastCall = "$modelId:$voiceId:$testPhrase"
            return LocalTtsModelTestResult(success = true, sampleRate = 24_000, frameCount = 10)
        }

        override suspend fun selectDefaultModel(modelId: String, voiceId: Int) = Unit

        override fun getModelInfo(modelId: String): LocalTtsModelInfo? = null
    }
}
