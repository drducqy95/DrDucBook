package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.MediaDownloadItemEntity
import io.legado.app.data.entities.MediaDownloadTaskEntity
import io.legado.app.domain.model.MediaDownloadState
import io.legado.app.domain.model.MediaProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MediaDownloadRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var repository: MediaDownloadRepository

    @Before
    fun setUp() {
        val application: Application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MediaDownloadRepository(database.mediaDownloadDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reconcileAfterProcessStartMovesRunningItemsBackToQueueAndDeletesScratchFiles() = runBlocking {
        val temp = temporaryFolder.newFile("episode.downloading").apply { writeText("partial") }
        val hlsScratch = File(temp.parentFile, "${temp.name}.segment").apply { writeText("hls") }
        val dashScratch = File(temp.parentFile, "${temp.name}.dash-segment").apply { writeText("dash") }
        insertTask("task_running", MediaDownloadState.RUNNING)
        insertItem(
            id = "item_running",
            taskId = "task_running",
            state = MediaDownloadState.RUNNING,
            tempPath = temp.absolutePath,
        )

        repository.reconcileAfterProcessStart()

        assertEquals(MediaDownloadState.PENDING.name, database.mediaDownloadDao.getItem("item_running")!!.status)
        assertEquals(MediaDownloadState.PENDING.name, database.mediaDownloadDao.observeTasks().first().single().status)
        assertTrue(temp.isFile)
        assertFalse(hlsScratch.exists())
        assertFalse(dashScratch.exists())

        val claimed = repository.claimNext()

        assertEquals(MediaDownloadState.RUNNING, claimed!!.state)
        assertEquals(MediaDownloadState.RUNNING.name, database.mediaDownloadDao.getItem("item_running")!!.status)
        assertEquals(MediaDownloadState.RUNNING.name, database.mediaDownloadDao.observeTasks().first().single().status)
    }

    @Test
    fun batchRecoveryActionsUpdateRecoverableItemsAndPreserveCompletedFiles() = runBlocking {
        val pendingTemp = temporaryFolder.newFile("pending.downloading")
        val runningTemp = temporaryFolder.newFile("running.downloading")
        val failedTemp = temporaryFolder.newFile("failed.downloading")
        val completedFile = temporaryFolder.newFile("completed.mp4")
        insertTask("task_pending", MediaDownloadState.PENDING)
        insertTask("task_running", MediaDownloadState.RUNNING)
        insertTask("task_failed", MediaDownloadState.FAILED)
        insertTask("task_completed", MediaDownloadState.COMPLETED)
        insertItem("item_pending", "task_pending", MediaDownloadState.PENDING, pendingTemp.absolutePath)
        insertItem("item_running", "task_running", MediaDownloadState.RUNNING, runningTemp.absolutePath)
        insertItem("item_failed", "task_failed", MediaDownloadState.FAILED, failedTemp.absolutePath)
        insertItem(
            id = "item_completed",
            taskId = "task_completed",
            state = MediaDownloadState.COMPLETED,
            tempPath = "",
            localPath = completedFile.absolutePath,
        )

        repository.pauseActive()

        assertEquals(MediaDownloadState.PAUSED.name, database.mediaDownloadDao.getItem("item_pending")!!.status)
        assertEquals(MediaDownloadState.PAUSED.name, database.mediaDownloadDao.getItem("item_running")!!.status)
        assertEquals(MediaDownloadState.FAILED.name, database.mediaDownloadDao.getItem("item_failed")!!.status)

        repository.resumeRecoverable()

        assertEquals(MediaDownloadState.PENDING.name, database.mediaDownloadDao.getItem("item_pending")!!.status)
        assertEquals(MediaDownloadState.PENDING.name, database.mediaDownloadDao.getItem("item_running")!!.status)
        assertEquals(MediaDownloadState.PENDING.name, database.mediaDownloadDao.getItem("item_failed")!!.status)

        repository.cancelActive()

        assertEquals(MediaDownloadState.CANCELED.name, database.mediaDownloadDao.getItem("item_pending")!!.status)
        assertEquals(MediaDownloadState.CANCELED.name, database.mediaDownloadDao.getItem("item_running")!!.status)
        assertEquals(MediaDownloadState.CANCELED.name, database.mediaDownloadDao.getItem("item_failed")!!.status)
        assertEquals(MediaDownloadState.COMPLETED.name, database.mediaDownloadDao.getItem("item_completed")!!.status)
        assertFalse(pendingTemp.exists())
        assertFalse(runningTemp.exists())
        assertFalse(failedTemp.exists())
        assertTrue(completedFile.exists())
    }

    private suspend fun insertTask(id: String, state: MediaDownloadState) {
        database.mediaDownloadDao.insertTask(
            MediaDownloadTaskEntity(
                id = id,
                bookUrl = "book://$id",
                bookTitle = id,
                coverUrl = null,
                status = state.name,
                createdAt = 1L,
                updatedAt = 1L,
                errorMessage = null,
            )
        )
    }

    private suspend fun insertItem(
        id: String,
        taskId: String,
        state: MediaDownloadState,
        tempPath: String,
        localPath: String = "",
    ) {
        database.mediaDownloadDao.insertItem(
            MediaDownloadItemEntity(
                id = id,
                taskId = taskId,
                bookUrl = "book://$taskId",
                chapterIndex = id.hashCode().and(0xFF),
                episodeTitle = id,
                variantId = "default",
                sourceUri = "https://example.com/$id.mp4",
                mimeType = "video/mp4",
                headersJson = "{}",
                protocol = MediaProtocol.DIRECT.name,
                expiresAt = null,
                responseEtag = null,
                responseLastModified = null,
                responseContentLength = 0L,
                status = state.name,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                segmentIndex = 0,
                tempPath = tempPath,
                localPath = localPath,
                checksum = "",
                errorMessage = if (state == MediaDownloadState.FAILED) "network" else null,
                retryCount = 0,
                sortOrder = id.hashCode().and(0xFF),
                updatedAt = 1L,
            )
        )
    }
}
