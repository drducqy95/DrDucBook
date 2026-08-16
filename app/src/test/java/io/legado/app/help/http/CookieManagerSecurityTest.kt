package io.legado.app.help.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieManagerSecurityTest {

    @Test
    fun failureMessageDoesNotIncludeCookieOrExceptionMessage() {
        val secret = "session=top-secret-cookie"
        val message = cookieFailureMessage(
            domain = "example.test",
            error = IllegalArgumentException("Invalid header: $secret"),
        )

        assertTrue(message.contains("example.test"))
        assertTrue(message.contains("IllegalArgumentException"))
        assertFalse(message.contains(secret))
        assertFalse(message.contains("Invalid header"))
    }
}
