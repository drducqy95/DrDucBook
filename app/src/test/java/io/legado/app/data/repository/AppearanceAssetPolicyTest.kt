package io.legado.app.data.repository

import io.legado.app.domain.model.AppearanceAssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppearanceAssetPolicyTest {

    @Test
    fun safeSvgWithStandardNamespaceIsAccepted() {
        val svg = """
            <?xml version="1.0"?>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
              <path d="M2 2h20v20H2z"/>
            </svg>
        """.trimIndent().toByteArray()

        val result = AppearanceAssetPolicy.validate(
            bytes = svg,
            displayName = "icon.svg",
            mimeType = "image/svg+xml",
            kind = AppearanceAssetKind.ICON,
        )

        assertEquals("svg", result.extension)
        assertEquals(64, result.sha256.length)
    }

    @Test
    fun executableSvgContentIsRejected() {
        val attacks = listOf(
            "<svg><script>alert(1)</script></svg>",
            "<svg onload=\"alert(1)\"></svg>",
            "<svg><foreignObject><html/></foreignObject></svg>",
            "<svg><image href=\"https://example.com/x.png\"/></svg>",
        )

        attacks.forEach { attack ->
            assertThrows(IllegalArgumentException::class.java) {
                AppearanceAssetPolicy.validate(
                    bytes = attack.toByteArray(),
                    displayName = "icon.svg",
                    mimeType = "image/svg+xml",
                    kind = AppearanceAssetKind.ICON,
                )
            }
        }
    }

    @Test
    fun wallpaperCannotMasqueradeAsSvg() {
        assertThrows(IllegalArgumentException::class.java) {
            AppearanceAssetPolicy.validate(
                bytes = "<svg/>".toByteArray(),
                displayName = "wallpaper.png",
                mimeType = "image/png",
                kind = AppearanceAssetKind.WALLPAPER,
            )
        }
    }
}
