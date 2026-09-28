param(
    [string]$DistDir = "dist\hachimi_mt60_qt_zh_vi",
    [string]$OutputZip = "dist\hachimi-mt60-qt-zh-vi-onnx.zip"
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$distPath = Join-Path $scriptDir $DistDir
$zipPath = Join-Path $scriptDir $OutputZip

Write-Host "=== PACKAGING HACHIMIMT-60-QT ONNX INT8 ==="
Write-Host "Source directory: $distPath"
Write-Host "Target ZIP: $zipPath"

if (-not (Test-Path $distPath)) {
    throw "Directory $distPath not found. Please run convert_hachimi_qt.py and quantize.py first."
}

# 1. Compute SHA-256 for each ONNX model file
$requiredFiles = @(
    "encoder_model.onnx",
    "decoder_model_merged.onnx",
    "tokenizer.onnx",
    "target_tokenizer.onnx",
    "detokenizer.onnx"
)

$shaMap = @{}
foreach ($file in $requiredFiles) {
    $filePath = Join-Path $distPath $file
    if (-not (Test-Path $filePath)) {
        throw "Required file $file missing in $distPath"
    }
    $hash = (Get-FileHash -Path $filePath -Algorithm SHA256).Hash.ToLower()
    $shaMap[$file] = $hash
    Write-Host "  - ${file}: $hash"
}

# 2. Generate model_manifest.json
$manifest = [ordered]@{
    schemaVersion = 2
    modelId = "hachimi_mt60_qt_zh_vi"
    displayName = "HachimiMT-60-QT zh→vi (QT/Hán Việt)"
    sourceRepo = "ngocdang83/HachimiMT-60-QT"
    sourceRevision = "5588b84c0496ea582bb02c13e29af9b368a48150"
    sourceLanguage = "zh"
    targetLanguage = "vi"
    style = "QT/convert_register"
    license = "CC-BY-4.0"
    attribution = "ngocdang83/HachimiMT-60-QT (CC-BY-4.0)"
    requiredFiles = $requiredFiles
    sha256 = $shaMap
    recommendedNoRepeatNgramSize = 0
    supportsSourcePrompt = $false
    decoderLayers = 2
    attentionHeads = 8
    headDimension = 72
    decoderStartTokenId = 1
    eosTokenId = 2
    specialTokenIds = @(0, 1, 2, 3)
}

$manifestJson = $manifest | ConvertTo-Json -Depth 5
$manifestPath = Join-Path $distPath "model_manifest.json"
Set-Content -Path $manifestPath -Value $manifestJson -Encoding utf8 -Force
Write-Host "✅ Generated $manifestPath"

# 3. Copy NOTICE.txt into dist
$noticeSrc = Join-Path $scriptDir "NOTICE.txt"
if (Test-Path $noticeSrc) {
    Copy-Item -Path $noticeSrc -Destination (Join-Path $distPath "NOTICE.txt") -Force
}

# 4. Create ZIP package
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($distPath, $zipPath)

$zipSizeMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)
$zipHash = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash
Set-Content -Path "$zipPath.sha256" -Value $zipHash -Force

Write-Host "`n🎉 Packaging successful!"
Write-Host "ZIP: $zipPath ($zipSizeMb MB)"
Write-Host "SHA256: $zipHash"
