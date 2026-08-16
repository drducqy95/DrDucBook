package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCatalogTest {

    @Test
    fun commandCodeIsCatalogedWithDedicatedProtocolAndCliHeaders() {
        val entry = AiProviderCatalog.byId("commandcode")
        requireNotNull(entry)
        assertEquals(AiProtocol.COMMAND_CODE, entry.protocol)
        assertEquals("https://api.commandcode.ai/alpha/generate", entry.baseUrl)
        assertEquals("0.25.7", entry.customHeaders["x-command-code-version"])
        assertEquals("cli", entry.customHeaders["x-cli-environment"])
        assertTrue(entry.models.isNotEmpty())
    }
}
