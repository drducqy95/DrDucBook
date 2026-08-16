package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiProfileRepositoryPromptTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AiProfileRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        val dao = database.aiProfileDao
        runBlocking {
            dao.insertProvider(
                AiProviderProfile(
                    id = "provider",
                    name = "Provider",
                    protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://example.invalid/v1",
                    apiKey = "test",
                )
            )
            dao.insertModel(
                AiModelProfile(
                    id = "model",
                    providerId = "provider",
                    displayName = "Model",
                    modelId = "model-id",
                    maxOutputTokens = 8_192,
                )
            )
        }
        repository = AiProfileRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingAndActivatingNamedPromptsChangesRuntimePreset() = runBlocking {
        val first = repository.saveTaskPreset(
            draft(name = "Ancient", prompt = "Ancient prompt", makeDefault = true)
        )
        val second = repository.saveTaskPreset(
            draft(name = "Modern", prompt = "Modern prompt", makeDefault = true)
        )

        assertFalse(database.aiProfileDao.getPreset(first.id)!!.isDefault)
        assertTrue(database.aiProfileDao.getPreset(second.id)!!.isDefault)
        assertEquals("Modern prompt", repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!.promptTemplate)

        repository.setDefaultTaskPreset(first.id)

        val active = repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!
        assertEquals(first.id, active.id)
        assertEquals("Preset description", active.description)
        assertEquals("Ancient prompt", active.promptTemplate)
        assertEquals(0.45f, active.params.temperature)
        assertEquals(40, active.params.topK)
        assertEquals(1.12f, active.params.repetitionPenalty)
        assertEquals(7_000, active.runtimeOptions.maxInputChars)
        assertEquals(2, active.runtimeOptions.concurrentRequests)
    }

    @Test
    fun legacyDefaultTranslationPresetMigratesToSafeChunkSize() = runBlocking {
        val legacyRuntime = AiTaskRuntimeOptions(
            maxInputChars = 10_000,
            concurrentRequests = 1,
            retryCount = 2,
        )
        database.aiProfileDao.upsertTaskPreset(
            AiTaskPreset(
                id = "default_translate_chapter",
                taskType = AiTaskType.TRANSLATE_CHAPTER,
                name = "Legacy default",
                modelProfileId = "model",
                promptTemplate = "Translate",
                chunkPolicyJson = GSON.toJson(legacyRuntime),
                isDefault = true,
            )
        )

        val active = repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!
        val stored = database.aiProfileDao.getPreset("default_translate_chapter")!!

        assertEquals(1_000, active.runtimeOptions.maxInputChars)
        assertEquals(
            1_000,
            GSON.fromJson(stored.chunkPolicyJson, AiTaskRuntimeOptions::class.java).maxInputChars,
        )
    }

    @Test
    fun customTranslationPresetKeepsLegacySizedChunkWhenUserConfiguredIt() = runBlocking {
        database.aiProfileDao.upsertTaskPreset(
            AiTaskPreset(
                id = "custom_translate",
                taskType = AiTaskType.TRANSLATE_CHAPTER,
                name = "Custom",
                modelProfileId = "model",
                promptTemplate = "Translate",
                chunkPolicyJson = GSON.toJson(
                    AiTaskRuntimeOptions(
                        maxInputChars = 10_000,
                        concurrentRequests = 1,
                        retryCount = 2,
                    )
                ),
                isDefault = true,
            )
        )

        assertEquals(
            10_000,
            repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!.runtimeOptions.maxInputChars,
        )
    }

    @Test
    fun deletingActivePromptPromotesAnotherEnabledPreset() = runBlocking {
        val first = repository.saveTaskPreset(
            draft(name = "First", prompt = "First prompt", makeDefault = true)
        )
        val second = repository.saveTaskPreset(
            draft(name = "Second", prompt = "Second prompt", makeDefault = false)
        )

        repository.deleteTaskPreset(first.id)

        val promoted = database.aiProfileDao.getPreset(second.id)!!
        assertTrue(promoted.isDefault)
        assertEquals(second.id, repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!.id)
    }

    @Test
    fun movingActivePromptPromotesAnotherPresetInItsPreviousTask() = runBlocking {
        val moved = repository.saveTaskPreset(
            draft(name = "Move me", prompt = "First prompt", makeDefault = true)
        )
        val remaining = repository.saveTaskPreset(
            draft(name = "Keep me", prompt = "Second prompt", makeDefault = false)
        )

        repository.saveTaskPreset(
            draft(
                name = "Moved",
                prompt = "Chat prompt",
                makeDefault = true,
                presetId = moved.id,
                taskType = AiTaskType.CHAT,
            )
        )

        assertTrue(database.aiProfileDao.getPreset(remaining.id)!!.isDefault)
        assertEquals(
            remaining.id,
            repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!.id,
        )
        assertEquals(moved.id, repository.getTaskPreset(AiTaskType.CHAT)!!.id)
    }

    @Test
    fun changingDefaultModelKeepsSelectedPromptsAndTheirText() = runBlocking {
        database.aiProfileDao.insertModel(
            AiModelProfile(
                id = "model-2",
                providerId = "provider",
                displayName = "Model 2",
                modelId = "model-id-2",
                maxOutputTokens = 16_384,
            )
        )
        val translation = repository.saveTaskPreset(
            draft(
                name = "Literary translation",
                prompt = "Keep the exact paragraph structure.",
                makeDefault = true,
            )
        )
        val chat = repository.saveTaskPreset(
            draft(
                name = "Book assistant",
                prompt = "Answer as the user's book assistant.",
                makeDefault = true,
                taskType = AiTaskType.CHAT,
            )
        )

        val selected = repository.setDefaultModel("model-2")

        assertEquals(translation.id, selected.id)
        assertEquals("model-2", selected.model.id)
        assertEquals("Keep the exact paragraph structure.", selected.promptTemplate)
        val activeChat = repository.getTaskPreset(AiTaskType.CHAT)!!
        assertEquals(chat.id, activeChat.id)
        assertEquals("model-2", activeChat.model.id)
        assertEquals("Answer as the user's book assistant.", activeChat.promptTemplate)
        val presets = database.aiProfileDao.observePresets().first()
        assertEquals(
            1,
            presets.count { it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.isDefault },
        )
        assertEquals(
            1,
            presets.count { it.taskType == AiTaskType.CHAT && it.isDefault },
        )
    }

    @Test
    fun legacyProfileSaveKeepsRuntimeOptionsOfSelectedTranslationPrompt() = runBlocking {
        val selected = repository.saveTaskPreset(
            draft(
                name = "Selected translation",
                prompt = "Keep this prompt and its chunk policy.",
                makeDefault = true,
            )
        )

        repository.saveDefaultChatProfile(
            AiProfileDraft(
                providerId = "provider",
                modelProfileId = "model",
                providerName = "Provider",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://example.invalid/v1",
                apiKey = "test",
                modelName = "Model",
                modelId = "model-id",
                maxInputChars = 321,
                concurrentRequests = 1,
                retryCount = 0,
            )
        )

        val active = repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!
        assertEquals(selected.id, active.id)
        assertEquals("Keep this prompt and its chunk policy.", active.promptTemplate)
        assertEquals(7_000, active.runtimeOptions.maxInputChars)
        assertEquals(2, active.runtimeOptions.concurrentRequests)
        assertEquals(3, active.runtimeOptions.retryCount)
    }

    @Test
    fun editingActivePresetCannotLeaveTaskWithoutDefault() = runBlocking {
        val selected = repository.saveTaskPreset(
            draft(name = "Selected", prompt = "Before", makeDefault = true)
        )

        repository.saveTaskPreset(
            draft(
                name = "Selected edited",
                prompt = "After",
                makeDefault = false,
                presetId = selected.id,
            )
        )

        val stored = database.aiProfileDao.getPreset(selected.id)!!
        assertTrue(stored.isDefault)
        assertEquals("After", repository.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!.promptTemplate)
    }

    @Test
    fun chapterSummaryPresetPersistsRouterFallbackCombo() = runBlocking {
        repository.saveTaskPreset(
            draft(
                name = "Summary with fallback",
                prompt = "Summarize through the configured combo.",
                makeDefault = true,
                taskType = AiTaskType.SUMMARIZE_CHAPTER,
                routeProfileId = "route_summary",
            )
        )

        val active = repository.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)!!

        assertEquals("route_summary", active.runtimeOptions.routeProfileId)
        assertEquals("model", active.model.id)
    }

    @Test
    fun importedProviderModelsKeepApiOrderForFallbackRouting() = runBlocking {
        val imported = repository.importProviderModels(
            providerId = "provider",
            models = listOf(
                AiAvailableModel(id = "primary", name = "Primary"),
                AiAvailableModel(id = "fallback", name = "Fallback"),
            ),
        )

        assertEquals(listOf(0, 1), imported.map { it.sortNumber })
        assertEquals(
            listOf("primary", "fallback"),
            database.aiProfileDao.getModelsByProvider("provider")
                .filter { it.modelId in setOf("primary", "fallback") }
                .map { it.modelId },
        )
    }

    private fun draft(
        name: String,
        prompt: String,
        makeDefault: Boolean,
        presetId: String? = null,
        taskType: String = AiTaskType.TRANSLATE_CHAPTER,
        routeProfileId: String = "",
    ) = AiTaskPresetDraft(
        presetId = presetId,
        taskType = taskType,
        name = name,
        modelProfileId = "model",
        promptTemplate = prompt,
        params = AiGenerationParams(
            temperature = 0.45f,
            maxOutputTokens = 4_096,
            topP = 0.9f,
            topK = 40,
            repetitionPenalty = 1.12f,
        ),
        runtimeOptions = AiTaskRuntimeOptions(
            targetLanguage = "vi",
            maxInputChars = 7_000,
            concurrentRequests = 2,
            retryCount = 3,
            routeProfileId = routeProfileId,
        ),
        description = "Preset description",
        makeDefault = makeDefault,
    )
}
