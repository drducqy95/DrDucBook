package io.legado.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HachimiNmtMemoryPolicyTest {

    @Test
    fun twoHundredFiftySixMbHeapUsesLowMemorySessionProfile() {
        assertTrue(
            shouldUseLowMemoryNmtProfile(
                systemReportsLowRam = false,
                memoryClassMb = 256,
            )
        )
    }

    @Test
    fun javaHeapClassDoesNotRejectNativeNmtWhenSystemHasEnoughMemory() {
        val required = requiredNmtSystemHeadroomBytes(
            modelBytes = 96L * MIB,
            systemLowMemoryThresholdBytes = 64L * MIB,
        )

        assertTrue(
            hasEnoughNmtSystemMemory(
                availableBytes = 512L * MIB,
                systemReportsLowMemory = false,
                requiredHeadroomBytes = required,
            )
        )
    }

    @Test
    fun systemLowMemorySignalStillRejectsModelLoad() {
        assertFalse(
            hasEnoughNmtSystemMemory(
                availableBytes = 2L * 1024L * MIB,
                systemReportsLowMemory = true,
                requiredHeadroomBytes = 160L * MIB,
            )
        )
    }

    @Test
    fun modelAndSystemThresholdBothContributeToRequiredHeadroom() {
        assertFalse(
            hasEnoughNmtSystemMemory(
                availableBytes = 191L * MIB,
                systemReportsLowMemory = false,
                requiredHeadroomBytes = requiredNmtSystemHeadroomBytes(
                    modelBytes = 128L * MIB,
                    systemLowMemoryThresholdBytes = 64L * MIB,
                ),
            )
        )
    }

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
