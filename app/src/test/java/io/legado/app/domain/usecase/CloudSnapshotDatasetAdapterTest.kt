package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotDataset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CloudSnapshotDatasetAdapterTest {

    @Test
    fun defaultArchiveUsesEveryIncludedDatasetAdapterAndNoExcludedDataset() = runBlocking {
        val useCase = CloudSnapshotUseCase(
            adapters = CloudSnapshotPolicy.includedDatasets.map(::FakeSnapshotAdapter),
            clock = fixedClock,
            snapshotIdFactory = { "snapshot-fixed" },
        )

        val built = useCase.buildArchive(
            deviceId = "device-1",
            revision = "rev-1",
        )

        assertEquals("snapshot-fixed", built.manifest.snapshotId)
        assertEquals(fixedClock.millis(), built.manifest.createdAtEpochMillis)
        assertEquals(
            CloudSnapshotPolicy.includedDatasets,
            built.manifest.entries.mapTo(linkedSetOf()) { it.dataset },
        )
        assertFalse(
            built.manifest.entries.any { it.dataset in CloudSnapshotPolicy.excludedDatasets }
        )
    }

    @Test
    fun registryRejectsMissingDatasetAdapter() {
        val adapters = CloudSnapshotPolicy.includedDatasets
            .filterNot { it == CloudSnapshotDataset.SETTINGS }
            .map(::FakeSnapshotAdapter)

        val coverage = CloudSnapshotDatasetRegistry(adapters).validateCoverage()

        assertTrue(coverage.isFailure)
    }

    @Test
    fun registryRejectsExcludedDatasetAdapter() {
        val adapters = CloudSnapshotPolicy.includedDatasets
            .map(::FakeSnapshotAdapter) + FakeSnapshotAdapter(CloudSnapshotDataset.COOKIES)

        val coverage = CloudSnapshotDatasetRegistry(adapters).validateCoverage()

        assertTrue(coverage.isFailure)
    }

    @Test
    fun registryRejectsNonTransactionalRestoreAdapter() {
        val adapters = CloudSnapshotPolicy.includedDatasets.map { dataset ->
            FakeSnapshotAdapter(
                dataset = dataset,
                transactional = dataset != CloudSnapshotDataset.APPEARANCE,
            )
        }

        val coverage = CloudSnapshotDatasetRegistry(adapters).validateCoverage()

        assertTrue(coverage.isFailure)
    }

    @Test
    fun restoreValidatesAllStagedEntriesBeforeCommit() = runBlocking {
        val settings = FakeSnapshotAdapter(CloudSnapshotDataset.SETTINGS)
        val appearance = FakeSnapshotAdapter(CloudSnapshotDataset.APPEARANCE)
        val useCase = CloudSnapshotUseCase(
            adapters = listOf(settings, appearance),
            clock = fixedClock,
            snapshotIdFactory = { "snapshot-restore" },
        )
        val built = useCase.buildArchive(
            deviceId = "device-1",
            revision = "rev-restore",
            datasets = setOf(CloudSnapshotDataset.SETTINGS, CloudSnapshotDataset.APPEARANCE),
        )

        val result = useCase.stageAndRestoreArchive(
            archiveBytes = built.bytes,
            stagingRoot = Files.createTempDirectory("drducbook-snapshot-adapter-test").toFile(),
        )

        assertEquals(
            setOf(CloudSnapshotDataset.APPEARANCE, CloudSnapshotDataset.SETTINGS),
            result.restoredDatasets,
        )
        assertEquals(2, result.restoredRecordCount)
        assertTrue(settings.validatedBeforeRestore)
        assertTrue(appearance.validatedBeforeRestore)
        assertEquals(listOf(CloudSnapshotDataset.SETTINGS), settings.restoredDatasets)
        assertEquals(listOf(CloudSnapshotDataset.APPEARANCE), appearance.restoredDatasets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreRejectsArchiveEntryWithoutAdapter() {
        runBlocking {
            val built = CloudSnapshotArchive.build(
                snapshotId = "snapshot-1",
                revision = "rev-1",
                deviceId = "device-1",
                createdAtEpochMillis = fixedClock.millis(),
                payloads = listOf(
                    CloudSnapshotPayload(
                        dataset = CloudSnapshotDataset.SETTINGS,
                        bytes = "{}".toByteArray(),
                        recordCount = 1,
                    ),
                ),
            )
            val useCase = CloudSnapshotUseCase(
                adapters = listOf(FakeSnapshotAdapter(CloudSnapshotDataset.APPEARANCE)),
                clock = fixedClock,
            )

            useCase.stageAndRestoreArchive(
                archiveBytes = built.bytes,
                stagingRoot = Files.createTempDirectory("drducbook-snapshot-missing-adapter-test").toFile(),
            )
        }
    }

    private class FakeSnapshotAdapter(
        override val dataset: CloudSnapshotDataset,
        private val transactional: Boolean = true,
    ) : CloudSnapshotDatasetAdapter {

        override val supportsTransactionalRestore: Boolean
            get() = transactional

        var validatedBeforeRestore: Boolean = false
            private set

        val restoredDatasets = mutableListOf<CloudSnapshotDataset>()

        override suspend fun exportPayload(): CloudSnapshotPayload =
            CloudSnapshotPayload(
                dataset = dataset,
                bytes = """{"dataset":"${dataset.storageKey}"}""".toByteArray(),
                recordCount = 1,
            )

        override suspend fun validateRestorePayload(
            entry: StagedCloudSnapshotEntry,
        ): Result<Unit> = runCatching {
            require(entry.dataset == dataset)
            validatedBeforeRestore = true
        }

        override suspend fun restorePayload(entry: StagedCloudSnapshotEntry) {
            require(validatedBeforeRestore)
            restoredDatasets += entry.dataset
        }
    }

    private companion object {
        val fixedClock: Clock = Clock.fixed(
            Instant.parse("2026-07-31T00:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}
