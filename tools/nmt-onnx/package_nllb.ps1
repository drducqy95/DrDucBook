param(
    [string]$DistDir = "dist\nllb_200_distilled_600m",
    [string]$OutputZip = "dist\nllb-200-distilled-600m-onnx.zip"
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$distPath = Join-Path $scriptDir $DistDir
$zipPath = Join-Path $scriptDir $OutputZip

Write-Host "=== PACKAGING NLLB-200-DISTILLED-600M ONNX INT8 ==="
Write-Host "Source directory: $distPath"
Write-Host "Target ZIP: $zipPath"

if (-not (Test-Path $distPath)) {
    throw "Directory $distPath not found. Please run convert_nllb.py and quantize_nllb.py first."
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
    modelId = "nllb_200_distilled_600m"
    displayName = "NLLB-200 Distilled 600M (Meta AI)"
    sourceRepo = "facebook/nllb-200-distilled-600M"
    sourceLanguage = "zh"
    targetLanguage = "vi"
    style = "Multilingual Standard / Literary Prose"
    license = "CC-BY-NC-4.0"
    attribution = "facebook/nllb-200-distilled-600M - Meta AI"
    requiredFiles = $requiredFiles
    sha256 = $shaMap
    decoderLayers = 12
    attentionHeads = 16
    headDimension = 64
    vocabSize = 256206
    decoderStartTokenId = 2
    forcedBosTokenId = 256193
    eosTokenId = 2
    padTokenId = 1
    specialTokenIds = @(0, 1, 2, 3, 256193, 256200)
    recommendedNoRepeatNgramSize = 2
    supportsSourcePrompt = $false
    minRamRequiredMb = 3000
}

$manifestJson = $manifest | ConvertTo-Json -Depth 5
$manifestPath = Join-Path $distPath "model_manifest.json"
[System.IO.File]::WriteAllText($manifestPath, $manifestJson, [System.Text.UTF8Encoding]::new($false))
Write-Host "Generated $manifestPath"

# 3. Write NOTICE.txt
$noticeContent = @"
NLLB-200-Distilled-600M (ONNX INT8 Distribution for DrDucBook / Legado)
========================================================================

This model package is an optimized ONNX INT8 conversion of Meta AI's NLLB-200 Distilled 600M.

Original Model:
- Repository: https://huggingface.co/facebook/nllb-200-distilled-600M
- Author: Meta AI / FAIR (No Language Left Behind)
- Architecture: NllbM2M / Seq2Seq Transformer (encoder_layers=12, decoder_layers=12, d_model=1024, heads=16, head_dim=64)
- Vocab Size: 256,206 (Shared SentencePiece with Language Tokens)
- Source Language: zho_Hans (Chinese Simplified) -> Target Language: vie_Latn (Vietnamese)
- License: Creative Commons Attribution-NonCommercial 4.0 International (CC-BY-NC-4.0)

Conversion & Packaging:
- Tooling: Optimum ONNX Runtime, Hugging Face Transformers, ONNX Runtime Extensions, PyTorch
- Optimization: Subgraph-Aware Dynamic INT8 Quantization (QInt8) for CPU mobile inference
- Quantized Models: encoder_model.onnx (~399 MB), decoder_model_merged.onnx (~702 MB)
- Tokenization: Native ONNX SentencePiece Tokenizers (tokenizer.onnx, target_tokenizer.onnx, detokenizer.onnx)
- Recommended Decoding Setting: no_repeat_ngram_size = 2, minRamRequiredMb = 3000
"@
$noticePath = Join-Path $distPath "NOTICE.txt"
[System.IO.File]::WriteAllText($noticePath, $noticeContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "Generated $noticePath"

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
