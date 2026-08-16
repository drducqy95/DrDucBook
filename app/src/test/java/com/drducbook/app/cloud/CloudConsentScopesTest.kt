package com.drducbook.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudConsentScopesTest {

    @Test
    fun googleSignInDoesNotRequestDriveAccess() {
        assertEquals(setOf("openid", "email", "profile"), CloudConsentScopes.googleSignIn)
        assertFalse(CloudConsentScopes.googleDriveAppData in CloudConsentScopes.googleSignIn)
    }
}
