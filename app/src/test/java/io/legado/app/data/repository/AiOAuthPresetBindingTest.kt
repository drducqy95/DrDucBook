package io.legado.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOAuthPresetBindingTest {

    @Test
    fun blankPresetRouteIsRepairedAfterOAuthLogin() {
        assertTrue(shouldBindOAuthPresetToDefaultRoute("", "route_default_chat"))
    }

    @Test
    fun existingOAuthDefaultRouteIsReboundToCurrentModel() {
        assertTrue(
            shouldBindOAuthPresetToDefaultRoute(
                "route_default_chat",
                "route_default_chat",
            )
        )
    }

    @Test
    fun explicitUserRouteIsPreserved() {
        assertFalse(
            shouldBindOAuthPresetToDefaultRoute(
                "route_my_translation_combo",
                "route_default_translation",
            )
        )
    }
}
