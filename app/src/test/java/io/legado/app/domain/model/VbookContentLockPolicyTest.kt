package io.legado.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VbookContentLockPolicyTest {

    @Test
    fun externalVbookSourceIsLockedWithoutUnlockCode() {
        assertTrue(
            VbookContentLockPolicy.isLocked(
                origin = "vbook://plugin/source-id",
                configuredCode = null,
            )
        )
    }

    @Test
    fun externalVbookSourceIsUnlockedWithRequiredCode() {
        assertFalse(
            VbookContentLockPolicy.isLocked(
                origin = "vbook://plugin/source-id",
                configuredCode = VbookContentLockPolicy.REQUIRED_UNLOCK_CODE,
            )
        )
    }

    @Test
    fun nonVbookSourcesAreAlwaysAllowed() {
        assertFalse(VbookContentLockPolicy.isLocked("https://example.org/source", null))
        assertFalse(VbookContentLockPolicy.isLocked("local", null))
        assertFalse(VbookContentLockPolicy.isLocked("custom-source", null))
    }
}
