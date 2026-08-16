package io.legado.app.data.repository

import io.legado.app.domain.model.AppearancePresets
import io.legado.app.domain.model.AppearanceSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AppearanceFileStoreTest {
    private val root = Files.createTempDirectory("appearance-store-test").toFile()
    private val store = AppearanceFileStore(root)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun snapshotRoundTripKeepsActiveProfile() {
        val snapshot = AppearanceSnapshot(
            activeProfileId = AppearancePresets.INK_AMBER_ID,
            profiles = AppearancePresets.all,
        )

        store.write(snapshot)

        assertEquals(snapshot, store.readOrNull())
        val json = store.profileFile.readText()
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("\"activeProfileId\""))
        assertTrue(json.contains("\"profiles\""))
        assertFalse(Regex("\"[a-r]\"\\s*:").containsMatchIn(json))
    }

    @Test
    fun readsSnapshotWrittenWithLegacyR8FieldNames() {
        store.profileFile.parentFile?.mkdirs()
        store.profileFile.writeText(
            """
            {
              "a": 1,
              "b": "legacy-profile",
              "c": [{
                "a": 1,
                "b": "legacy-profile",
                "c": "Legacy profile",
                "d": false,
                "e": "MATERIAL",
                "f": "SYSTEM",
                "g": {"a": 1, "b": 2, "c": 3, "d": 4, "e": 5, "f": 6},
                "h": {"a": 7, "b": 8, "c": 9, "d": 10, "e": 11, "f": 12},
                "i": 10,
                "j": 100,
                "k": 90,
                "l": 91,
                "m": true,
                "n": false,
                "o": {},
                "p": {},
                "q": {},
                "r": 42
              }]
            }
            """.trimIndent()
        )

        val restored = store.readOrNull()

        assertNotNull(restored)
        assertEquals("legacy-profile", restored?.activeProfileId)
        assertEquals("Legacy profile", restored?.profiles?.single()?.name)
        assertEquals(1, restored?.profiles?.single()?.lightColors?.primary)
        assertEquals(12, restored?.profiles?.single()?.darkColors?.container)
    }

    @Test
    fun corruptPrimaryFileFallsBackToPreviousAtomicBackup() {
        val first = AppearanceSnapshot(
            activeProfileId = AppearancePresets.COPPER_CYAN_ID,
            profiles = AppearancePresets.all,
        )
        store.write(first)
        store.write(first.copy(activeProfileId = AppearancePresets.FOREST_CORAL_ID))
        store.profileFile.writeText("{broken")

        val restored = store.readOrNull()

        assertNotNull(restored)
        assertEquals(AppearancePresets.COPPER_CYAN_ID, restored?.activeProfileId)
    }

    @Test
    fun unknownActiveIdFallsBackWithoutDroppingProfiles() {
        val validated = store.validate(
            AppearanceSnapshot(
                activeProfileId = "missing",
                profiles = listOf(AppearancePresets.fallback().copy(builtIn = false)),
            )
        )

        assertEquals(AppearancePresets.COPPER_CYAN_ID, validated.activeProfileId)
        assertEquals(1, validated.profiles.size)
    }
}
