package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.PreWritingSectionKey
import io.legado.app.domain.model.PreWritingSectionSource
import io.legado.app.domain.model.WritingWorkflowPolicy
import io.legado.app.domain.model.WritingWorkflowStage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AuthoringWorkflowUseCase(
    private val aiTextFactoryUseCase: AiTextFactoryUseCase,
) {

    @Serializable
    data class BlueprintDraft(
        val detailedOutline: String,
        val worldView: String,
        val mainPlot: String,
        val characters: String,
    )

    @Serializable
    data class NarrativePlanDraft(
        val actsAndVolumes: String,
        val plotProgression: String,
        val chapterRoadmap: String,
    )

    @Serializable
    private data class BlueprintInput(
        val idea: String,
        val userOutline: String,
    )

    @Serializable
    private data class NarrativePlanInput(
        val idea: String,
        val userOutline: String,
        val detailedOutline: String,
        val worldView: String,
        val mainPlot: String,
        val characters: String,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun generateBlueprint(project: AuthoringProject): BlueprintDraft {
        require(WritingWorkflowPolicy.canGenerateBlueprint(project)) {
            "Cần nhập đầy đủ ý tưởng và dàn ý ban đầu"
        }
        val input = BlueprintInput(
            idea = project.preproduction.premise.content.trim(),
            userOutline = project.preproduction.outline.content.trim(),
        )
        val request = AiTextFactoryUseCase.Request(
            bookUrl = "authoring:${project.id}",
            inputText = json.encodeToString(input),
            taskType = AiTaskType.AUTHORING_DIRECTOR,
            userInstruction = "Hoàn thiện đề cương chi tiết từ dữ liệu người dùng. Giữ nguyên ý tưởng cốt lõi và viết bằng ngôn ngữ của dữ liệu đầu vào.",
            outputContract = BLUEPRINT_OUTPUT_CONTRACT,
            skipCache = true,
        )
        return generateAndDecode(request) { raw ->
            json.decodeFromString<BlueprintDraft>(extractJsonObject(raw)).validated()
        }
    }

    suspend fun generateNarrativePlan(project: AuthoringProject): NarrativePlanDraft {
        require(WritingWorkflowPolicy.canGenerateNarrativePlan(project)) {
            "Cần duyệt đề cương chi tiết trước khi triển khai hồi và quyển"
        }
        val preproduction = project.preproduction
        val input = NarrativePlanInput(
            idea = preproduction.premise.content.trim(),
            userOutline = preproduction.outline.content.trim(),
            detailedOutline = preproduction.detailedOutline.content.trim(),
            worldView = preproduction.worldBible.content.trim(),
            mainPlot = preproduction.plotThreads.content.trim(),
            characters = preproduction.characterBible.content.trim(),
        )
        val request = AiTextFactoryUseCase.Request(
            bookUrl = "authoring:${project.id}",
            inputText = json.encodeToString(input),
            taskType = AiTaskType.AUTHORING_DIRECTOR,
            userInstruction = "Từ đề cương đã được duyệt, chia truyện thành các hồi và quyển, triển khai mạch truyện có cao trào, chuyển biến và kết quả rõ ràng, sau đó tạo lộ trình chương. Viết bằng ngôn ngữ của dữ liệu đầu vào.",
            outputContract = NARRATIVE_OUTPUT_CONTRACT,
            skipCache = true,
        )
        return generateAndDecode(request) { raw ->
            json.decodeFromString<NarrativePlanDraft>(extractJsonObject(raw)).validated()
        }
    }

    fun applyBlueprint(
        project: AuthoringProject,
        draft: BlueprintDraft,
        now: Long = System.currentTimeMillis(),
    ): AuthoringProject {
        val preproduction = project.preproduction
            .update(PreWritingSectionKey.DETAILED_OUTLINE, draft.detailedOutline, PreWritingSectionSource.AI_APPLIED, now)
            .update(PreWritingSectionKey.WORLD_BIBLE, draft.worldView, PreWritingSectionSource.AI_APPLIED, now)
            .update(PreWritingSectionKey.PLOT_THREADS, draft.mainPlot, PreWritingSectionSource.AI_APPLIED, now)
            .update(PreWritingSectionKey.CHARACTER_BIBLE, draft.characters, PreWritingSectionSource.AI_APPLIED, now)
        return project.copy(
            preproduction = preproduction,
            writingWorkflow = project.writingWorkflow.copy(
                stage = WritingWorkflowStage.BLUEPRINT_REVIEW,
                blueprintGeneratedAt = now,
                blueprintApprovedAt = null,
                narrativePlanApprovedAt = null,
            ),
            updatedAt = now,
        )
    }

    fun approveBlueprint(
        project: AuthoringProject,
        now: Long = System.currentTimeMillis(),
    ): AuthoringProject {
        require(WritingWorkflowPolicy.canApproveBlueprint(project)) {
            "Đề cương chi tiết chưa đầy đủ"
        }
        return project.copy(
            writingWorkflow = project.writingWorkflow.copy(
                stage = WritingWorkflowStage.NARRATIVE_PLANNING,
                blueprintApprovedAt = now,
                narrativePlanApprovedAt = null,
            ),
            updatedAt = now,
        )
    }

    fun applyNarrativePlan(
        project: AuthoringProject,
        draft: NarrativePlanDraft,
        now: Long = System.currentTimeMillis(),
    ): AuthoringProject {
        val preproduction = project.preproduction
            .update(PreWritingSectionKey.ARC_VOLUME_OUTLINE, draft.actsAndVolumes, PreWritingSectionSource.AI_APPLIED, now)
            .update(PreWritingSectionKey.TIMELINE, draft.plotProgression, PreWritingSectionSource.AI_APPLIED, now)
            .update(PreWritingSectionKey.CHAPTER_ROADMAP, draft.chapterRoadmap, PreWritingSectionSource.AI_APPLIED, now)
        return project.copy(
            preproduction = preproduction,
            writingWorkflow = project.writingWorkflow.copy(
                stage = WritingWorkflowStage.NARRATIVE_REVIEW,
                narrativePlanGeneratedAt = now,
                narrativePlanApprovedAt = null,
            ),
            updatedAt = now,
        )
    }

    fun approveNarrativePlan(
        project: AuthoringProject,
        now: Long = System.currentTimeMillis(),
    ): AuthoringProject {
        require(WritingWorkflowPolicy.canApproveNarrativePlan(project)) {
            "Đại cương hồi, quyển và lộ trình chương chưa đầy đủ"
        }
        return project.copy(
            writingWorkflow = project.writingWorkflow.copy(
                stage = WritingWorkflowStage.READY_TO_WRITE,
                narrativePlanApprovedAt = now,
            ),
            updatedAt = now,
        )
    }

    private suspend fun <T> generateAndDecode(
        request: AiTextFactoryUseCase.Request,
        decode: (String) -> T,
    ): T {
        val raw = aiTextFactoryUseCase.execute(request).getOrThrow()
        val decoded = runCatching { decode(raw) }
        if (decoded.isSuccess) return decoded.getOrThrow()
        val repaired = aiTextFactoryUseCase.execute(
            request.copy(
                inputText = raw,
                userInstruction = "Sửa đầu ra thành đúng một JSON hợp lệ theo output contract. Không thay đổi nội dung có nghĩa và không thêm giải thích.",
                skipCache = true,
            )
        ).getOrThrow()
        return decode(repaired)
    }

    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI không trả về JSON hợp lệ" }
        return raw.substring(start, end + 1)
    }

    private fun BlueprintDraft.validated(): BlueprintDraft = apply {
        require(detailedOutline.isNotBlank()) { "AI bỏ trống đề cương chi tiết" }
        require(worldView.isNotBlank()) { "AI bỏ trống thế giới quan" }
        require(mainPlot.isNotBlank()) { "AI bỏ trống tuyến truyện chính" }
        require(characters.isNotBlank()) { "AI bỏ trống nhân vật" }
    }

    private fun NarrativePlanDraft.validated(): NarrativePlanDraft = apply {
        require(actsAndVolumes.isNotBlank()) { "AI bỏ trống đại cương hồi và quyển" }
        require(plotProgression.isNotBlank()) { "AI bỏ trống mạch truyện" }
        require(chapterRoadmap.isNotBlank()) { "AI bỏ trống lộ trình chương" }
    }

    private companion object {
        val BLUEPRINT_OUTPUT_CONTRACT = """
            Return exactly one valid JSON object without Markdown fences or commentary.
            Required schema:
            {"detailedOutline":"...","worldView":"...","mainPlot":"...","characters":"..."}
            Every value must be a non-empty detailed string. The characters field must describe each major character, their motivation, conflict, relationships, and development arc.
        """.trimIndent()

        val NARRATIVE_OUTPUT_CONTRACT = """
            Return exactly one valid JSON object without Markdown fences or commentary.
            Required schema:
            {"actsAndVolumes":"...","plotProgression":"...","chapterRoadmap":"..."}
            Every value must be a non-empty detailed string. actsAndVolumes must name and describe each act and volume. plotProgression must explain setup, escalation, turning points, climax, and resolution. chapterRoadmap must list chapters in order with the act/volume and main purpose of each chapter.
        """.trimIndent()
    }
}
