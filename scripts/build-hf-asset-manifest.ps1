param(
    [string]$Root = (Resolve-Path "$PSScriptRoot/..").Path,
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

$manifestVersion = "2026.07.31-p10t01"
$repository = "Drduc/Legadofork"
$revision = "main"
$driveAssetRoot = Join-Path $Root "artifacts/drive-assets"

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $Root "supabase/artifacts/hf-artifacts-manifest.json"
}

function Test-RepoFile {
    param([string]$RelativePath)
    return Test-Path -LiteralPath (Join-Path $Root $RelativePath) -PathType Leaf
}

function New-Artifact {
    param(
        [string]$Id,
        [string]$DisplayName,
        [string]$FileName,
        [string]$Category,
        [string]$HfPath,
        [long]$SizeBytes,
        [string]$Sha256,
        [string]$License,
        [string]$Provenance,
        [string]$DeliveryClass,
        [string]$LocalSource,
        [string]$InventoryState
    )

    if (-not [string]::IsNullOrWhiteSpace($LocalSource)) {
        $file = Get-Item -LiteralPath (Join-Path $Root $LocalSource)
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        if ($file.Length -ne $SizeBytes) {
            throw "Size mismatch for ${Id}: expected $SizeBytes, got $($file.Length)"
        }
        if ($actualHash -ne $Sha256.ToLowerInvariant()) {
            throw "SHA-256 mismatch for ${Id}: expected $Sha256, got $actualHash"
        }
    }

    [ordered]@{
        id = $Id
        displayName = $DisplayName
        fileName = $FileName
        category = $Category
        hfRepo = $repository
        hfRevision = $revision
        hfPath = $HfPath
        sizeBytes = $SizeBytes
        sha256 = $Sha256.ToLowerInvariant()
        license = $License
        provenance = $Provenance
        deliveryClass = $DeliveryClass
        localSource = if ([string]::IsNullOrWhiteSpace($LocalSource)) { $null } else { $LocalSource.Replace("\", "/") }
        inventoryState = $InventoryState
    }
}

$artifacts = @()

$valtecLocalSource = ".codex-tmp/release-assets/legado-tts-valtec-vietnamese-20260721.zip"
$valtecHasLocalSource = Test-RepoFile $valtecLocalSource

$artifacts += New-Artifact `
    -Id "translation-hachimi-onnx-arm64" `
    -DisplayName "HachimiMT zh-vi ONNX arm64" `
    -FileName "legado-hachimi-onnx-arm64-20260721.zip" `
    -Category "translation" `
    -HfPath "packages/translation/legado-hachimi-onnx-arm64-20260721.zip" `
    -SizeBytes 58266032 `
    -Sha256 "8429161d6e3fdd504dedaf69054b2ab7b672c948948a38322b701900d10cf3db" `
    -License "CC-BY-4.0" `
    -Provenance "ZIP includes model_manifest.json and NOTICE.txt for ngocdang83/HachimiMT-60-zh-vi." `
    -DeliveryClass "hf_proxy" `
    -LocalSource "artifacts/drive-assets/legado-hachimi-onnx-arm64-20260721.zip" `
    -InventoryState "local_verified"

$artifacts += New-Artifact `
    -Id "translation-quick-clean" `
    -DisplayName "Quick Translation clean pack" `
    -FileName "legado-qt-clean-20260721.zip" `
    -Category "translation" `
    -HfPath "packages/translation/legado-qt-clean-20260721.zip" `
    -SizeBytes 575677 `
    -Sha256 "e5223206d5d119916620c1101208aa1d7c10d1df26ea2e996d89c441e18591ca" `
    -License "Mixed: CC0-1.0, CC-BY-SA-4.0, Apache-2.0, Unicode-3.0" `
    -Provenance "ZIP includes qt_clean_pack_manifest.json with per-component license and file hashes." `
    -DeliveryClass "hf_proxy" `
    -LocalSource "artifacts/drive-assets/legado-qt-clean-20260721.zip" `
    -InventoryState "local_verified"

$artifacts += New-Artifact `
    -Id "tts-valtec-vietnamese" `
    -DisplayName "Valtec Vietnamese TTS" `
    -FileName "legado-tts-valtec-vietnamese-20260721.zip" `
    -Category "tts" `
    -HfPath "packages/tts/valtec/legado-tts-valtec-vietnamese-20260721.zip" `
    -SizeBytes 159792388 `
    -Sha256 "c7ae93f15ec2aa39b7e9f6e4ca520c9c86298b7183c5b8c99ea6eb768eeeb77e" `
    -License "CC-BY-NC-4.0" `
    -Provenance $(if ($valtecHasLocalSource) { "Local source archive verified from .codex-tmp release assets and uploaded to Hugging Face dataset." } else { "Pinned catalog metadata; local source archive not present in this workspace." }) `
    -DeliveryClass $(if ($valtecHasLocalSource) { "hf_proxy" } else { "storage_mirror_required" }) `
    -LocalSource $(if ($valtecHasLocalSource) { $valtecLocalSource } else { "" }) `
    -InventoryState $(if ($valtecHasLocalSource) { "local_verified" } else { "metadata_only_pending_source" })

$piperRoot = Join-Path $driveAssetRoot "tts-piper-voices"
$piperApacheReviewedVoices = @(
    "adam1",
    "banmai",
    "calmwoman3688",
    "chieuthanh",
    "deepman3909",
    "duyoryx3175",
    "lacphi",
    "maiphuong",
    "manhdung",
    "minhkhang",
    "minhquang",
    "minhthu",
    "mytam2",
    "mytam2794",
    "ngochuyen",
    "ngochuyennew",
    "ngocngan3701",
    "phuongtrang",
    "taian2",
    "taian4",
    "thanhphuong2",
    "thientam",
    "tranthanh3870",
    "vietthao3886",
    "yannew"
)
Get-ChildItem -LiteralPath $piperRoot -Filter "legado-tts-piper-*-20260721.zip" -File |
    Sort-Object Name |
    ForEach-Object {
        $voiceId = $_.BaseName -replace "^legado-tts-piper-", "" -replace "-20260721$", ""
        $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
        $relative = $_.FullName.Substring($Root.Length + 1).Replace("\", "/")
        $isApacheReviewed = $piperApacheReviewedVoices -contains $voiceId
        $artifacts += New-Artifact `
            -Id "tts-piper-$voiceId" `
            -DisplayName "Piper voice $voiceId" `
            -FileName $_.Name `
            -Category "tts" `
            -HfPath "packages/tts/piper/$($_.Name)" `
            -SizeBytes $_.Length `
            -Sha256 $sha `
            -License $(if ($isApacheReviewed) { "Apache-2.0" } else { "license-review-required" }) `
            -Provenance $(if ($isApacheReviewed) { "Local Piper ZIP contains ONNX and ONNX JSON only; voice name is covered by doof-ferb/nghitts-copy piper-tts Apache-2.0 mirror of NGHI-TTS, review captured in docs/release/piper-voice-license-review.md." } else { "Local Piper ZIP contains ONNX and ONNX JSON only; no matching Apache-2.0 NGHI-TTS voice card was found, so upstream voice license card must be attached before public release." }) `
            -DeliveryClass "hf_proxy" `
            -LocalSource $relative `
            -InventoryState $(if ($isApacheReviewed) { "local_verified" } else { "local_verified_license_pending" })
    }

$hyMt2 = @(
    @{
        Id = "hy-mt2-1.8b-stq-stride16"
        FileName = "Hy-MT2-1.8B-1.25bit-original.gguf"
        Sha256 = "cc497fe8f033b52b3b8b00a7669e9661435432f9d4cd43f7ed24400c01507a93"
    },
    @{
        Id = "hy-mt2-1.8b-v2"
        FileName = "Hy-MT2-1.8B-1.25bit-v2.gguf"
        Sha256 = "13a33fc4f72d5c92c439a65fd343696de4ccd0485bca84de2712bc0d8cc4e773"
    },
    @{
        Id = "hy-mt2-1.8b-v2-stq42"
        FileName = "Hy-MT2-1.8B-1.25bit-v2-stq42.gguf"
        Sha256 = "dca0302d5bd54f70e90287332e4169305ca3602d1052c6480d49b732fcccefbc"
    }
)

foreach ($model in $hyMt2) {
    $modelLocalSource = ".codex-tmp/models/$($model.FileName)"
    $modelHasLocalSource = Test-RepoFile $modelLocalSource
    $artifacts += New-Artifact `
        -Id $model.Id `
        -DisplayName $model.FileName `
        -FileName $model.FileName `
        -Category "local-ai" `
        -HfPath "models/local-ai/hy-mt2/$($model.FileName)" `
        -SizeBytes 461860800 `
        -Sha256 $model.Sha256 `
        -License "upstream-license-required" `
        -Provenance $(if ($modelHasLocalSource) { "Local GGUF source file verified from .codex-tmp models and uploaded to Hugging Face dataset." } else { "Pinned catalog metadata; local GGUF source file not present in this workspace." }) `
        -DeliveryClass $(if ($modelHasLocalSource) { "hf_proxy" } else { "storage_mirror_required" }) `
        -LocalSource $(if ($modelHasLocalSource) { $modelLocalSource } else { "" }) `
        -InventoryState $(if ($modelHasLocalSource) { "local_verified" } else { "metadata_only_pending_source" })
}

$manifest = [ordered]@{
    schemaVersion = 1
    manifestVersion = $manifestVersion
    repository = $repository
    revision = $revision
    generatedBy = "scripts/build-hf-asset-manifest.ps1"
    generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    artifacts = @($artifacts | Sort-Object { $_.id })
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
$json = $manifest | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote $OutputPath with $($artifacts.Count) artifacts"
