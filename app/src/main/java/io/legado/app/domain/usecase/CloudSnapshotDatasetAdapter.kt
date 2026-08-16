package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotDataset
import java.io.File
import java.time.Clock
import java.util.UUID

interface CloudSnapshotDatasetAdapter {
    val dataset: CloudSnapshotDataset
    val supportsTransactionalRestore: Boolean
        get() = true

    suspend fun exportPayload(): CloudSnapshotPayload

    suspend fun validateRestorePayload(entry: StagedCloudSnapshotEntry): Result<Unit> =
        Result.success(Unit)

    suspend fun restorePayload(entry: StagedCloudSnapshotEntry)
}

data class CloudSnapshotAdapterCoverage(
    val requiredDatasets: Set<CloudSnapshotDataset>,
    val availableDatasets: Set<CloudSnapshotDataset>,
) {
    val missingDatasets: Set<CloudSnapshotDataset>
        get() = requiredDatasets - availableDatasets
}

data class CloudSnapshotRestoreCommitResult(
    val restoredDatasets: Set<CloudSnapshotDataset>,
    val restoredRecordCount: Int,
)

class CloudSnapshotDatasetRegistry(
    private val adapters: List<CloudSnapshotDatasetAdapter>,
) {

    fun validateCoverage(
        requiredDatasets: Set<CloudSnapshotDataset> = CloudSnapshotPolicy.includedDatasets,
    ): Result<CloudSnapshotAdapterCoverage> = runCatching {
        validateRequestedDatasets(requiredDatasets)
        val duplicateDatasets = adapters
            .groupingBy { it.dataset }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateDatasets.isEmpty()) {
            "Duplicate snapshot adapters: ${duplicateDatasets.joinStorageKeys()}"
        }
        val availableDatasets = adapters.mapTo(linkedSetOf()) { it.dataset }
        val excludedAdapters = availableDatasets intersect CloudSnapshotPolicy.excludedDatasets
        require(excludedAdapters.isEmpty()) {
            "Excluded snapshot datasets cannot have adapters: ${excludedAdapters.joinStorageKeys()}"
        }
        val nonTransactional = adapters
            .filter { it.dataset in requiredDatasets && !it.supportsTransactionalRestore }
            .mapTo(linkedSetOf()) { it.dataset }
        require(nonTransactional.isEmpty()) {
            "Snapshot adapters must restore transactionally: ${nonTransactional.joinStorageKeys()}"
        }
        val coverage = CloudSnapshotAdapterCoverage(
            requiredDatasets = requiredDatasets,
            availableDatasets = availableDatasets,
        )
        require(coverage.missingDatasets.isEmpty()) {
            "Missing snapshot adapters: ${coverage.missingDatasets.joinStorageKeys()}"
        }
        coverage
    }

    suspend fun exportPayloads(
        datasets: Set<CloudSnapshotDataset> = CloudSnapshotPolicy.includedDatasets,
    ): List<CloudSnapshotPayload> {
        validateCoverage(datasets).getOrThrow()
        return datasets.sortedBy { it.storageKey }.map { dataset ->
            val adapter = adapterFor(dataset)
            adapter.exportPayload().also { payload ->
                require(payload.dataset == dataset) {
                    "Snapshot adapter returned payload for ${payload.dataset.storageKey} instead of ${dataset.storageKey}"
                }
            }
        }
    }

    suspend fun restoreStaged(
        staged: StagedCloudSnapshot,
    ): CloudSnapshotRestoreCommitResult {
        val stagedDatasets = staged.entries.mapTo(linkedSetOf()) { it.dataset }
        val manifestDatasets = staged.manifest.entries.mapTo(linkedSetOf()) { it.dataset }
        require(stagedDatasets == manifestDatasets) {
            "Staged snapshot entries do not match manifest"
        }
        validateCoverage(stagedDatasets).getOrThrow()
        val sortedEntries = staged.entries.sortedBy { it.dataset.storageKey }
        sortedEntries.forEach { entry ->
            adapterFor(entry.dataset).validateRestorePayload(entry).getOrThrow()
        }
        sortedEntries.forEach { entry ->
            adapterFor(entry.dataset).restorePayload(entry)
        }
        return CloudSnapshotRestoreCommitResult(
            restoredDatasets = stagedDatasets,
            restoredRecordCount = sortedEntries.sumOf { it.recordCount },
        )
    }

    private fun adapterFor(dataset: CloudSnapshotDataset): CloudSnapshotDatasetAdapter =
        adapters.firstOrNull { it.dataset == dataset }
            ?: throw IllegalArgumentException("Missing snapshot adapter: ${dataset.storageKey}")

    private fun validateRequestedDatasets(datasets: Set<CloudSnapshotDataset>) {
        require(datasets.isNotEmpty()) { "Snapshot dataset set must not be empty" }
        val excludedDatasets = datasets intersect CloudSnapshotPolicy.excludedDatasets
        require(excludedDatasets.isEmpty()) {
            "Excluded snapshot datasets cannot be requested: ${excludedDatasets.joinStorageKeys()}"
        }
    }

    private fun Set<CloudSnapshotDataset>.joinStorageKeys(): String =
        sortedBy { it.storageKey }.joinToString(", ") { it.storageKey }
}

class CloudSnapshotUseCase(
    adapters: List<CloudSnapshotDatasetAdapter>,
    private val clock: Clock = Clock.systemUTC(),
    private val snapshotIdFactory: () -> String = { UUID.randomUUID().toString() },
) {

    private val registry = CloudSnapshotDatasetRegistry(adapters)

    fun validateAdapterCoverage(
        datasets: Set<CloudSnapshotDataset> = CloudSnapshotPolicy.includedDatasets,
    ): Result<CloudSnapshotAdapterCoverage> =
        registry.validateCoverage(datasets)

    suspend fun buildArchive(
        deviceId: String,
        revision: String,
        datasets: Set<CloudSnapshotDataset> = CloudSnapshotPolicy.includedDatasets,
    ): BuiltCloudSnapshotArchive =
        CloudSnapshotArchive.build(
            snapshotId = snapshotIdFactory(),
            revision = revision,
            deviceId = deviceId,
            createdAtEpochMillis = clock.millis(),
            payloads = registry.exportPayloads(datasets),
        )

    fun stageArchive(
        archiveBytes: ByteArray,
        stagingRoot: File,
    ): StagedCloudSnapshot =
        CloudSnapshotRestoreStaging.stageArchive(
            archiveBytes = archiveBytes,
            stagingRoot = stagingRoot,
        )

    suspend fun restoreStaged(
        staged: StagedCloudSnapshot,
    ): CloudSnapshotRestoreCommitResult =
        registry.restoreStaged(staged)

    suspend fun stageAndRestoreArchive(
        archiveBytes: ByteArray,
        stagingRoot: File,
    ): CloudSnapshotRestoreCommitResult =
        restoreStaged(stageArchive(archiveBytes, stagingRoot))
}
