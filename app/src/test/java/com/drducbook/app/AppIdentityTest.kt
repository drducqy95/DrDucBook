package com.drducbook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppIdentityTest {

    @Test
    fun productIdentityIsIsolatedFromTheLegacyApplication() {
        assertEquals("com.drducbook.app", AppIdentity.APPLICATION_ID)
        assertEquals("com.drducbook.app.readerProvider", AppIdentity.READER_PROVIDER_AUTHORITY)
        assertEquals("drducbook://auth/callback", AppIdentity.AUTH_CALLBACK)
        assertNotEquals("io.legato.kazusa", AppIdentity.APPLICATION_ID)
    }
}
