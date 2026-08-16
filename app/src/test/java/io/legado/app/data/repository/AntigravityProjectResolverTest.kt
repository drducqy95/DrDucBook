package io.legado.app.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AntigravityProjectResolverTest {

    @Test
    fun existingProjectDoesNotRunOnboardingAgain() = runBlocking {
        var onboardingCalls = 0

        val project = resolveAntigravityProject(
            loadPayload = json(
                """{"cloudaicompanionProject":{"id":"existing-project"}}"""
            ),
            onboard = {
                onboardingCalls += 1
                json("""{"done":true}""")
            },
            waitBeforeRetry = {},
        )

        assertEquals("existing-project", project)
        assertEquals(0, onboardingCalls)
    }

    @Test
    fun newAccountIsOnboardedWithDefaultTierAndPersistsReturnedProject() = runBlocking {
        val requestedTiers = mutableListOf<String>()

        val project = resolveAntigravityProject(
            loadPayload = json(
                """{"allowedTiers":[{"id":"STANDARD","isDefault":true}]}"""
            ),
            onboard = { tierId ->
                requestedTiers += tierId
                json(
                    if (requestedTiers.size == 1) {
                        """{"done":false}"""
                    } else {
                        """{"done":true,"response":{"cloudaicompanionProject":{"id":"new-project"}}}"""
                    }
                )
            },
            waitBeforeRetry = {},
        )

        assertEquals("new-project", project)
        assertEquals(listOf("STANDARD", "STANDARD"), requestedTiers)
    }

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
}
