package io.legado.app.help.config

import io.legado.app.domain.model.AppearanceIconSpec
import io.legado.app.domain.model.AppearancePresets
import io.legado.app.domain.model.IconSlot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ThemePackageSecurityPolicyTest {
    private val root = Files.createTempDirectory("drductheme-security-test").toFile()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun versionTwoManifestRoundTripsWithChecksumAndMime() {
        val asset = createPng("assets/navigation/home.png")
        val base = ThemePackageManifest(
            formatVersion = ThemePackageSecurityPolicy.CURRENT_VERSION,
            assets = mapOf("navigation.home" to relative(asset)),
        )
        val metadata = ThemePackageSecurityPolicy.buildMetadata(
            root,
            ThemePackageSecurityPolicy.referencedPaths(base),
        )
        val manifest = base.copy(
            checksums = metadata.checksums,
            mimeTypes = metadata.mimeTypes,
        )
        File(root, "manifest.json").writeText("{}")

        ThemePackageSecurityPolicy.validateExtracted(root, manifest)

        assertEquals("image/png", manifest.mimeTypes.getValue(relative(asset)))
        assertEquals(64, manifest.checksums.getValue(relative(asset)).length)
    }

    @Test
    fun checksumTamperingIsRejected() {
        val asset = createPng("assets/background/light.png")
        val base = ThemePackageManifest(
            formatVersion = ThemePackageSecurityPolicy.CURRENT_VERSION,
            assets = mapOf("background.light" to relative(asset)),
        )
        val metadata = ThemePackageSecurityPolicy.buildMetadata(
            root,
            ThemePackageSecurityPolicy.referencedPaths(base),
        )
        val manifest = base.copy(
            checksums = metadata.checksums,
            mimeTypes = metadata.mimeTypes,
        )
        File(root, "manifest.json").writeText("{}")
        asset.appendBytes(byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            ThemePackageSecurityPolicy.validateExtracted(root, manifest)
        }
    }

    @Test
    fun appearanceProfileAssetsMustBeDeclaredAndVerified() {
        val asset = createPng("appearance/assets/icon.png")
        val profile = AppearancePresets.fallback().copy(
            id = "custom-portable",
            builtIn = false,
            iconSlots = mapOf(
                IconSlot.NAV_HOME.key to AppearanceIconSpec(assetId = "icon.png")
            ),
        )
        val base = ThemePackageManifest(
            formatVersion = ThemePackageSecurityPolicy.CURRENT_VERSION,
            appearanceProfile = profile,
            appearanceAssets = mapOf("icon.png" to relative(asset)),
        )
        val metadata = ThemePackageSecurityPolicy.buildMetadata(
            root,
            ThemePackageSecurityPolicy.referencedPaths(base),
        )
        val manifest = base.copy(
            checksums = metadata.checksums,
            mimeTypes = metadata.mimeTypes,
        )
        File(root, "manifest.json").writeText("{}")

        ThemePackageSecurityPolicy.validateExtracted(root, manifest)

        assertThrows(IllegalArgumentException::class.java) {
            ThemePackageSecurityPolicy.validateExtracted(
                root,
                manifest.copy(appearanceAssets = emptyMap()),
            )
        }
    }

    @Test
    fun traversalExecutableAndUnreferencedContentAreRejected() {
        listOf("../theme.js", "/absolute.png", "C:/escape.png", "a//b.png").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                ThemePackageSecurityPolicy.validateRelativePath(path)
            }
        }

        val asset = createPng("assets/home.png")
        File(root, "payload.js").writeText("alert(1)")
        File(root, "manifest.json").writeText("{}")
        val manifest = ThemePackageManifest(
            formatVersion = 1,
            assets = mapOf("navigation.home" to relative(asset)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ThemePackageSecurityPolicy.validateExtracted(root, manifest)
        }
    }

    @Test
    fun maliciousSvgIsRejectedEvenWhenChecksumMatches() {
        val svg = File(root, "assets/navigation/home.svg")
        svg.parentFile?.mkdirs()
        svg.writeText("<svg onload=\"alert(1)\"><script>alert(1)</script></svg>")
        val base = ThemePackageManifest(
            formatVersion = ThemePackageSecurityPolicy.CURRENT_VERSION,
            assets = mapOf("navigation.home" to relative(svg)),
        )
        val metadata = ThemePackageSecurityPolicy.buildMetadata(
            root,
            ThemePackageSecurityPolicy.referencedPaths(base),
        )
        val manifest = base.copy(
            checksums = metadata.checksums,
            mimeTypes = metadata.mimeTypes,
        )
        File(root, "manifest.json").writeText("{}")

        assertThrows(IllegalArgumentException::class.java) {
            ThemePackageSecurityPolicy.validateExtracted(root, manifest)
        }
    }

    @Test
    fun futurePackageVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemePackageSecurityPolicy.validateVersion(
                ThemePackageSecurityPolicy.CURRENT_VERSION + 1
            )
        }
    }

    private fun createPng(path: String): File {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeBytes(
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00,
            )
        )
        return file
    }

    private fun relative(file: File): String =
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
}
