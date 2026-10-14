package io.legado.app.model.translation

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HachimiOnnxModelRegistryTest {

    private lateinit var context: Context
    private lateinit var registry: HachimiOnnxModelRegistry

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        registry = HachimiOnnxModelRegistry(context)
    }

    @After
    fun tearDown() {
        File(context.filesDir, HachimiOnnxModelRegistry.MODEL_ROOT).deleteRecursively()
    }

    @Test
    fun defaultDescriptorHasStandardMarianValues() {
        val descriptor = registry.modelDescriptor(HachimiOnnxModelRegistry.DEFAULT_MODEL_ID)
        assertEquals(HachimiOnnxModelRegistry.DEFAULT_MODEL_ID, descriptor.id)
        assertEquals(64L, descriptor.headDimension)
        assertEquals(2, descriptor.decoderLayers)
        assertEquals(8L, descriptor.attentionHeads)
        assertEquals(2, descriptor.defaultNoRepeatNgramSize)
        assertEquals(1, descriptor.decoderStartTokenId)
        assertEquals(null, descriptor.forcedBosTokenId)
        assertEquals(2, descriptor.eosTokenId)
        assertEquals(24000, descriptor.vocabSize)
    }

    @Test
    fun qtDescriptorHasCorrect72HeadDimAndZeroNoRepeat() {
        val descriptor = registry.modelDescriptor(HachimiOnnxModelRegistry.HACHIMI_QT_MODEL_ID)
        assertEquals(HachimiOnnxModelRegistry.HACHIMI_QT_MODEL_ID, descriptor.id)
        assertEquals(72L, descriptor.headDimension)
        assertEquals(2, descriptor.decoderLayers)
        assertEquals(8L, descriptor.attentionHeads)
        assertEquals(0, descriptor.defaultNoRepeatNgramSize)
        assertEquals(1, descriptor.decoderStartTokenId)
        assertEquals(null, descriptor.forcedBosTokenId)
    }

    @Test
    fun nllbDescriptorHasCorrect12Layers16HeadsAndForcedBosToken() {
        val descriptor = registry.modelDescriptor(HachimiOnnxModelRegistry.NLLB_MODEL_ID)
        assertEquals(HachimiOnnxModelRegistry.NLLB_MODEL_ID, descriptor.id)
        assertEquals(64L, descriptor.headDimension)
        assertEquals(12, descriptor.decoderLayers)
        assertEquals(16L, descriptor.attentionHeads)
        assertEquals(2, descriptor.decoderStartTokenId)
        assertEquals(256193, descriptor.forcedBosTokenId)
        assertEquals(2, descriptor.eosTokenId)
        assertEquals(256206, descriptor.vocabSize)
        assertEquals(3000, descriptor.minRamRequiredMb)
    }

    @Test
    fun parsesNllbManifestFileCorrectly() {
        val modelDir = File(registry.modelRoot(), HachimiOnnxModelRegistry.NLLB_MODEL_ID).apply { mkdirs() }
        val manifestJson = """
            {
                "schemaVersion": 2,
                "modelId": "nllb_200_distilled_600m",
                "displayName": "NLLB-200 Distilled 600M (Meta AI)",
                "decoderLayers": 12,
                "attentionHeads": 16,
                "headDimension": 64,
                "decoderStartTokenId": 2,
                "forcedBosTokenId": 256193,
                "eosTokenId": 2,
                "vocabSize": 256206,
                "recommendedNoRepeatNgramSize": 2,
                "supportsSourcePrompt": false,
                "minRamRequiredMb": 3000
            }
        """.trimIndent()
        File(modelDir, HachimiOnnxModelRegistry.MANIFEST_FILE).writeText(manifestJson)

        val descriptor = registry.modelDescriptor(HachimiOnnxModelRegistry.NLLB_MODEL_ID)
        assertEquals("nllb_200_distilled_600m", descriptor.id)
        assertEquals("NLLB-200 Distilled 600M (Meta AI)", descriptor.name)
        assertEquals(12, descriptor.decoderLayers)
        assertEquals(16L, descriptor.attentionHeads)
        assertEquals(64L, descriptor.headDimension)
        assertEquals(2, descriptor.decoderStartTokenId)
        assertEquals(256193, descriptor.forcedBosTokenId)
        assertEquals(2, descriptor.eosTokenId)
        assertEquals(256206, descriptor.vocabSize)
        assertEquals(3000, descriptor.minRamRequiredMb)
    }
}
