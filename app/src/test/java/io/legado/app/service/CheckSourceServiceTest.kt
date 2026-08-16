package io.legado.app.service

import android.app.Application
import android.content.Intent
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CheckSourceServiceTest {

    @Test
    fun buildSessionFromStartIntentDeduplicatesAndUsesDefaults() {
        val intent = Intent().apply {
            putStringArrayListExtra(
                CheckSourceService.EXTRA_SELECTED_IDS,
                arrayListOf(
                    "https://one.example/source",
                    "https://two.example/source",
                    "https://one.example/source",
                ),
            )
            putExtra(CheckSourceService.EXTRA_PROFILE, SourceCheckProfile.STANDARD.name)
            putExtra(CheckSourceService.EXTRA_TIMEOUT_MS, 42_000L)
        }

        val session = CheckSourceService.buildSessionFromStartIntent(
            intent = intent,
            now = 123L,
            defaultProfile = SourceCheckProfile.QUICK,
            defaultTimeoutMs = 180_000L,
            checkSearch = true,
            checkDiscovery = false,
            checkInfo = true,
            checkCategory = false,
            checkContent = false,
        )!!

        assertEquals(
            listOf(
                "https://one.example/source",
                "https://two.example/source",
            ),
            session.sourceUrls,
        )
        assertEquals(session.sourceUrls, session.pendingSourceUrls)
        assertEquals(SourceCheckProfile.STANDARD, session.profile)
        assertEquals(42_000L, session.timeoutMs)
        assertEquals(true, session.checkSearch)
        assertEquals(false, session.checkDiscovery)
        assertEquals(true, session.checkInfo)
        assertEquals(false, session.checkCategory)
        assertEquals(false, session.checkContent)
        assertEquals(123L, session.startedAt)
        assertEquals(123L, session.updatedAt)
    }

    @Test
    fun domainKeyUsesRegistrableDomain() {
        assertEquals(
            "example.com",
            CheckSourceService.domainKey("https://www.example.com/path"),
        )
    }
}
