package com.drducbook.app.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrDucBookDeepLinksTest {

    @Test
    fun authCallbackMatchesOnlyTheDedicatedHostAndPath() {
        assertTrue(DrDucBookDeepLinks.isAuthCallback("drducbook://auth/callback?code=test"))
        assertFalse(DrDucBookDeepLinks.isAuthCallback("drducbook://import"))
        assertFalse(DrDucBookDeepLinks.isAuthCallback("legado://auth/callback"))
        assertFalse(DrDucBookDeepLinks.isAuthCallback("drducbook://auth/other"))
    }
}
