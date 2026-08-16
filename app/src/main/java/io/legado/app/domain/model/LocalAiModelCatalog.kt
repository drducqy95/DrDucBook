package io.legado.app.domain.model

data class LocalAiCatalogModel(
    val id: String,
    val repository: String,
    val revision: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val contextWindow: Int,
    val defaultParams: AiGenerationParams,
    val artifactId: String = id,
)

object LocalAiModelCatalog {
    private const val HY_MT2_REPOSITORY = "Hugging Face/Drduc/Legadofork"
    private const val HY_MT2_REVISION = "2026-07-21"

    private val hyMt2DefaultParams = AiGenerationParams(
        temperature = 0.7f,
        maxOutputTokens = 4_096,
        topP = 0.6f,
        topK = 20,
        repetitionPenalty = 1.05f,
        reasoningLevel = AiReasoningLevel.OFF,
    )

    val hyMt2V1 = LocalAiCatalogModel(
        id = "hy-mt2-1.8b-stq-stride16",
        repository = HY_MT2_REPOSITORY,
        revision = HY_MT2_REVISION,
        fileName = "Hy-MT2-1.8B-1.25bit-original.gguf",
        downloadUrl = AssetDeliveryCatalog.downloadUri("hy-mt2-1.8b-stq-stride16"),
        sizeBytes = 461_860_800L,
        sha256 = "cc497fe8f033b52b3b8b00a7669e9661435432f9d4cd43f7ed24400c01507a93",
        contextWindow = 4_096,
        defaultParams = hyMt2DefaultParams,
    )

    val hyMt2V2 = LocalAiCatalogModel(
        id = "hy-mt2-1.8b-v2",
        repository = HY_MT2_REPOSITORY,
        revision = HY_MT2_REVISION,
        fileName = "Hy-MT2-1.8B-1.25bit-v2.gguf",
        downloadUrl = AssetDeliveryCatalog.downloadUri("hy-mt2-1.8b-v2"),
        sizeBytes = 461_860_800L,
        sha256 = "13a33fc4f72d5c92c439a65fd343696de4ccd0485bca84de2712bc0d8cc4e773",
        contextWindow = 4_096,
        defaultParams = hyMt2DefaultParams,
    )

    val hyMt2V2Stq42 = LocalAiCatalogModel(
        id = "hy-mt2-1.8b-v2-stq42",
        repository = HY_MT2_REPOSITORY,
        revision = HY_MT2_REVISION,
        fileName = "Hy-MT2-1.8B-1.25bit-v2-stq42.gguf",
        downloadUrl = AssetDeliveryCatalog.downloadUri("hy-mt2-1.8b-v2-stq42"),
        sizeBytes = 461_860_800L,
        sha256 = "dca0302d5bd54f70e90287332e4169305ca3602d1052c6480d49b732fcccefbc",
        contextWindow = 4_096,
        defaultParams = hyMt2DefaultParams,
    )

    val recommended: LocalAiCatalogModel = hyMt2V1
    val all: List<LocalAiCatalogModel> = listOf(hyMt2V1, hyMt2V2, hyMt2V2Stq42)
}
