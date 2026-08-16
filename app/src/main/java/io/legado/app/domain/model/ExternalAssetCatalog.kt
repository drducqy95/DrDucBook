package io.legado.app.domain.model

data class ExternalTtsVoiceAsset(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val engine: String,
    val importSupported: Boolean,
    val releaseEligible: Boolean = true,
    val artifactId: String = id,
)

data class ExternalPackageAsset(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val category: String,
)

object AssetDeliveryCatalog {
    const val hfRepository = "Drduc/Legadofork"
    const val hfRevision = "main"
    const val manifestVersion = "2026.07.31-p10t01"
    const val assetUriScheme = "drducbook-asset"

    fun downloadUri(assetId: String): String =
        "$assetUriScheme://download/$assetId"

    fun catalogUri(catalogId: String): String =
        "$assetUriScheme://catalog/$catalogId"
}

object ExternalAssetCatalog {
    const val hachimiOnnxAssetId = "translation-hachimi-onnx-arm64"
    const val quickTranslationCleanAssetId = "translation-quick-clean"
    const val ttsValtecModelAssetId = "tts-valtec-vietnamese"
    const val ttsPiperVoiceCatalogId = "tts-piper-voices"
    const val ggufCatalogId = "local-ai-hy-mt2"

    val hachimiOnnxZipUrl = AssetDeliveryCatalog.downloadUri(hachimiOnnxAssetId)
    val quickTranslationCleanZipUrl = AssetDeliveryCatalog.downloadUri(quickTranslationCleanAssetId)
    val ttsPiperVoiceFolderUrl = AssetDeliveryCatalog.catalogUri(ttsPiperVoiceCatalogId)
    val ttsValtecModelZipUrl = AssetDeliveryCatalog.downloadUri(ttsValtecModelAssetId)
    val ggufFolderUrl = AssetDeliveryCatalog.catalogUri(ggufCatalogId)

    val translationAssets: List<ExternalPackageAsset> = listOf(
        ExternalPackageAsset(
            id = hachimiOnnxAssetId,
            displayName = "HachimiMT zh-vi ONNX arm64",
            fileName = "legado-hachimi-onnx-arm64-20260721.zip",
            downloadUrl = hachimiOnnxZipUrl,
            sizeBytes = 58_266_032L,
            sha256 = "8429161d6e3fdd504dedaf69054b2ab7b672c948948a38322b701900d10cf3db",
            category = "translation",
        ),
        ExternalPackageAsset(
            id = quickTranslationCleanAssetId,
            displayName = "Quick Translation clean pack",
            fileName = "legado-qt-clean-20260721.zip",
            downloadUrl = quickTranslationCleanZipUrl,
            sizeBytes = 575_677L,
            sha256 = "e5223206d5d119916620c1101208aa1d7c10d1df26ea2e996d89c441e18591ca",
            category = "translation",
        ),
    )

    val ttsImportableModels: List<ExternalTtsVoiceAsset> = listOf(
        ExternalTtsVoiceAsset(
            id = ttsValtecModelAssetId,
            displayName = "Valtec Vietnamese TTS",
            fileName = "legado-tts-valtec-vietnamese-20260721.zip",
            downloadUrl = ttsValtecModelZipUrl,
            sizeBytes = 159_792_388L,
            sha256 = "c7ae93f15ec2aa39b7e9f6e4ca520c9c86298b7183c5b8c99ea6eb768eeeb77e",
            engine = "Valtec VITS ONNX",
            importSupported = true,
        ),
    )

    val ttsPiperVoiceAssets: List<ExternalTtsVoiceAsset> = listOf(
        piperVoice("adam1", "Adam1", "legado-tts-piper-adam1-20260721.zip", "1EUNrzzbTT0EFaFKRsDEf7gVhh_g-KU_X", 58_448_025L, "51e6cae08373ade2ef91a4bec82ae2bf111cb6c17647cc82b141bfeccbc8e659"),
        piperVoice("banmai", "Banmai", "legado-tts-piper-banmai-20260721.zip", "1IOcdK07hiLvdAqohcdJZPFUgtPTjriQe", 58_430_260L, "410222473232bb8cde8303dfa17fc738660685f9b902892256b1c04f6d3e52e5"),
        piperVoice("calmwoman3688", "Calmwoman3688", "legado-tts-piper-calmwoman3688-20260721.zip", "1kzx7vReciU1wBGdwmGXbtAeCnT75ea4a", 58_432_695L, "77ad227a798fbbb5969ea10db65f54f4add03f224b20a35ccec9d7f1dc837055"),
        piperVoice("chieuthanh", "Chieuthanh", "legado-tts-piper-chieuthanh-20260721.zip", "1fEBZGZ5mJ5xU3SK3Dt81f3eHvjEVbwIp", 58_440_934L, "1012a3acee36bada7ce6023eff85e8551325d7fafdef0964a70561b89cde5910"),
        piperVoice("deepman3909", "Deepman3909", "legado-tts-piper-deepman3909-20260721.zip", "1e9YBjIOApRTLH6qmsHSVIwMjeF_Nc7Kq", 58_444_508L, "f8c46a694ac57f2f04cae0ab6fa7af084617c1af3d6e2998accf3fcaa6522270"),
        piperVoice("duyoryx3175", "Duyoryx3175", "legado-tts-piper-duyoryx3175-20260721.zip", "1yB-rYtDCu3C_r4XZhhZ6nu-OK63h_r03", 58_444_175L, "bcf6394fbed721647703f99345b9ea937b468256a442cf7904be72bef73e2864"),
        piperVoice("indo_goreng", "Indo Goreng", "legado-tts-piper-indo_goreng-20260721.zip", "1MrJPYagviramWHwIBoWdPxL5uq-Ar4R6", 58_441_200L, "e14d28fbacd84fffa9ac6d992654a3c3dab52d7b170ac3fc90edd8bee603f8f2", releaseEligible = false),
        piperVoice("john", "John", "legado-tts-piper-john-20260721.zip", "15Ok-7j3v5jcNcXK09itz6IwychCAuHOU", 58_446_266L, "081456b890c71679fce35d2d0ac0c9a172c642d719da823cbd3d8dfa7d373a46", releaseEligible = false),
        piperVoice("lacphi", "Lacphi", "legado-tts-piper-lacphi-20260721.zip", "1FkHNjy9WNy0333gsF8aenEZs1fweIaBQ", 58_429_963L, "f0ce5413241e8bb660c2581fa7a45a25c8f8766b63564467756b8a84358f5b13"),
        piperVoice("maiphuong", "Maiphuong", "legado-tts-piper-maiphuong-20260721.zip", "13R6v31oZYRUQspGbbr_O0LhY5n8EbOTr", 58_432_249L, "16f4fa823ebd3d9d7fc0a916d58e4932d31b8e3209e584fa996b6b76ae7d79f2"),
        piperVoice("manhdung", "Manhdung", "legado-tts-piper-manhdung-20260721.zip", "15KyPYDX9saCskFD8rMNCijm2HBOOj9PM", 58_443_193L, "834a7ac43a077fa779ff001276226da5a73495327a75a87f3a3621919e36cabe"),
        piperVoice("mattheo", "Mattheo", "legado-tts-piper-mattheo-20260721.zip", "195gtgCwP-5M7dr-WzcCGFGF7Z4Br9hO-", 58_443_891L, "cb52946d3f2166af154ea10765c79663a6f479bad56e90b9b6463f6c7807e63f", releaseEligible = false),
        piperVoice("mattheo1", "Mattheo1", "legado-tts-piper-mattheo1-20260721.zip", "1VLOeOisf_Fyu0EjSJt9dYiTfeSFmyxX-", 58_445_153L, "780dcb50968808c351b17f3245a126e0ccd926462a61abf4cbf5c0befb1ea13e", releaseEligible = false),
        piperVoice("minhkhang", "Minhkhang", "legado-tts-piper-minhkhang-20260721.zip", "1-MBhZcNaQC-lgxaJapCDHb9EDnYgruQ2", 58_446_255L, "1a0644260a248bd64261593b34d3a6484e1075357a74992233b4563706056f4c"),
        piperVoice("minhquang", "Minhquang", "legado-tts-piper-minhquang-20260721.zip", "13w8ZgR80Csb74p2Ie38vBSg7LglBPhmG", 58_442_099L, "7db7c95d19b8132f17e9e9402123215e6d69752af889481bc0ba1f177ebad998"),
        piperVoice("minhthu", "Minhthu", "legado-tts-piper-minhthu-20260721.zip", "1ZtmIW1ynYzhcYeBkHPcfUEm8S4Pv6z14", 58_430_939L, "282ad75f2e486965dca9f8e1069f344d65c86a34f948c289d3a22f9afbf23fce"),
        piperVoice("mytam2", "Mytam2", "legado-tts-piper-mytam2-20260721.zip", "11Plf3B8laEvj2af-sYvn1CYP8hzyQJWZ", 58_446_883L, "361ec0444d255864e33207b0b979e1d83f060d20862f94d67777cdb7242da801"),
        piperVoice("mytam2794", "Mytam2794", "legado-tts-piper-mytam2794-20260721.zip", "1XRz_M-txSI0RK2bImoWqjbitDiYY0rDX", 58_433_934L, "0abf27881ff2bdd4be0ef330618ee8aae376aa787fd9b393b920cab3754a6511"),
        piperVoice("ngochuyen", "Ngochuyen", "legado-tts-piper-ngochuyen-20260721.zip", "1a8o7BH3KkSomyfcDNxq72z7KcQC6r7QF", 58_432_040L, "200fd291d3d5b044d82ef28bc6fffde673a865de734a810ef357a8f2beb9d71d"),
        piperVoice("ngochuyennew", "Ngochuyennew", "legado-tts-piper-ngochuyennew-20260721.zip", "1xz32HNVd4cSa9xiLuOyT2Zak6nA1x6lk", 58_432_963L, "2e68c4f9a568dfaa6828a90d72f5b6ed8361a68df66408c20eb8e2fece467c01"),
        piperVoice("ngocngan3701", "Ngocngan3701", "legado-tts-piper-ngocngan3701-20260721.zip", "1bqKdyNbJogj_rAHMnU62rL9sDlAblaoO", 58_445_159L, "a41984e207ace58f6230c1e4f113dcfee8a3717733fc71ebb2d6af88ef9b0595"),
        piperVoice("phuongtrang", "Phuongtrang", "legado-tts-piper-phuongtrang-20260721.zip", "1g6orO0a3uETPBsKnWNO2OivBEljVLOlm", 58_429_369L, "5694d62d2f285414933733c690174a9953e945a43c43c93aa25e2c82f50492ee"),
        piperVoice("taian2", "Taian2", "legado-tts-piper-taian2-20260721.zip", "1PMS9-odVzjaFT8NV0SFFs5XhqHRvs1BW", 58_444_598L, "28bd6b7377184abae74f00b12f221ea97965daf4e4507234192f5c036b81c88b"),
        piperVoice("taian4", "Taian4", "legado-tts-piper-taian4-20260721.zip", "1PrWdw73lxONWl2xa9SxCR3lVYYvHGKFD", 58_443_311L, "675c1d067dfcca97c46a3e33dd48e94b84448a70f9ab02705ff931a4c2073c50"),
        piperVoice("thanhphuong2", "Thanhphuong2", "legado-tts-piper-thanhphuong2-20260721.zip", "1n4HOJBCfD-0FybdTzDKEqtKy2ZQ-XVv1", 58_433_972L, "dd1d2c8bd72906dc75a1dd2bbcf0e1dd6e77949e2a39ad5d0b9eaf6b9b8f05f3"),
        piperVoice("thientam", "Thientam", "legado-tts-piper-thientam-20260721.zip", "17j05kTlf-EwL8TaEBeKAKM4a17RMVNFR", 58_433_428L, "0b0e1d03bdfc0d430335b15ae6363fce547c884fe66d85065a2bb7b3a227de27"),
        piperVoice("tranthanh3870", "Tranthanh3870", "legado-tts-piper-tranthanh3870-20260721.zip", "147pmcX81QhpItH5ihhoysPwatO_0ZDAQ", 58_446_645L, "32066dd276636d55a8ea61d46ffb14a89272eed1aef8150b6d5ab5da208e563d"),
        piperVoice("vietthao3886", "Vietthao3886", "legado-tts-piper-vietthao3886-20260721.zip", "1bpSC3IpYhtPz2uoXqpjkDaQ2wuqlwX1y", 58_444_466L, "dd1704f69a86eaaf3c4ffe8b4b93df050536686f8f5f96184cf16e1b697a5216"),
        piperVoice("yannew", "Yannew", "legado-tts-piper-yannew-20260721.zip", "1QEVm7uncARhMkmrl3yMbVI-LaE2zVqyc", 58_447_476L, "f181534cc9549206a23f58b34f72e7a787bf7a23516903264195b24f5f142d7c"),
    )

    val releaseEligibleTtsVoiceCatalog: List<ExternalTtsVoiceAsset> =
        (ttsImportableModels + ttsPiperVoiceAssets).filter { it.releaseEligible }

    val ttsVoiceCatalog: List<ExternalTtsVoiceAsset> =
        ttsImportableModels + ttsPiperVoiceAssets

    val externalPackageAssets: List<ExternalPackageAsset> = translationAssets

    private fun piperVoice(
        id: String,
        displayName: String,
        fileName: String,
        driveId: String,
        sizeBytes: Long,
        sha256: String,
        releaseEligible: Boolean = true,
    ): ExternalTtsVoiceAsset = ExternalTtsVoiceAsset(
        id = id,
        displayName = displayName,
        fileName = fileName,
        downloadUrl = AssetDeliveryCatalog.downloadUri("tts-piper-$id"),
        sizeBytes = sizeBytes,
        sha256 = sha256,
        engine = "Piper ONNX",
        importSupported = true,
        releaseEligible = releaseEligible,
        artifactId = "tts-piper-$id",
    )
}
