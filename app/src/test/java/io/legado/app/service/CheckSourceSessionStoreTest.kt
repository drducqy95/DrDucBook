package io.legado.app.service

import android.app.Application
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CheckSourceSessionStoreTest {

    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        CheckSourceSessionStore.clear(context)
    }

    @Test
    fun saveLoadAndClearSession() {
        val session = CheckSourceSession(
            sourceUrls = listOf("https://one.example/source", "https://two.example/source"),
            pendingSourceUrls = listOf("https://two.example/source"),
            profile = SourceCheckProfile.FULL,
            timeoutMs = 12_345L,
            checkSearch = true,
            checkDiscovery = false,
            checkInfo = true,
            checkCategory = false,
            checkContent = false,
            healthyCount = 1,
            failedCount = 1,
            paused = true,
            startedAt = 100L,
            updatedAt = 200L,
        )

        CheckSourceSessionStore.save(context, session)

        assertTrue(CheckSourceSessionStore.hasSession(context))
        assertEquals(session, CheckSourceSessionStore.load(context))

        CheckSourceSessionStore.clear(context)

        assertFalse(CheckSourceSessionStore.hasSession(context))
        assertEquals(null, CheckSourceSessionStore.load(context))
    }
}
