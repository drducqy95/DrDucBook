---
title: AssetDelivery/ZeroTTS - INTEGRITY_AND_CHECKSUM_DESYNC Pattern
type: pattern
slug: assetdeliveryzerotts-integrity-and-checksum-desync-pattern
category: patterns
created: 2026-09-14T22:49:49
updated: 2026-09-14T22:49:49
status: active
source: /debug
tags: ["AssetDelivery/ZeroTTS", "INTEGRITY_AND_CHECKSUM_DESYNC", "auto-generated"]
---

# AssetDelivery/ZeroTTS - INTEGRITY_AND_CHECKSUM_DESYNC Pattern

> [!WARNING]
> **Origin:** `ERR_0031`
> **Module:** `AssetDelivery/ZeroTTS` | **Type:** `INTEGRITY_AND_CHECKSUM_DESYNC`

## 🚨 The Issue

**Message:** 
```text
Download finished reaching 100% but failed to complete and restart from 0% due to SHA-256 mismatch and retry loop
```

**Root Cause:** 
The base ZeroTTS model package in dist/zerotts/legado-tts-zerotts-base.zip was repackaged with mixed-precision INT8 quantization giving SHA-256 57bbbbd098ac4a048d8d3b2264ebf31f785af17c66b2a2649aee830b38685cbd, and uploaded to Hugging Face. However, ExternalAssetCatalog.kt and hf-artifacts-manifest.json contained a stale hash 855d4ff756f8cf0f8fbb0652128f0e89f719aaa5b38d0907072ace05d921573a from an earlier packaging run. When download finished, verifyFile/actualSha256 check failed, deleted the temp part file, and isRetryableAssetFailure returned true for non-HTTP IOExceptions, causing the download to loop from 0%.

## 🛠️ The Fix

1) Updated ExternalAssetCatalog.kt with exact SHA-256 hashes for ZeroTTS base (57bbbb...) and 8 voice addons. 2) Updated build-hf-asset-manifest.ps1 to compute sizes and SHA-256 dynamically from repo files. 3) Re-generated hf-artifacts-manifest.json and deployed updated functions asset-ticket and asset-download to Supabase project faegbafmkpsocoecrhvz. 4) Modified AssetDeliveryRepository.kt isRetryableAssetFailure to immediately abort without retrying on permanent checksum or size mismatch errors.

**Files Affected:**
- `app/src/main/java/io/legado/app/domain/model/ExternalAssetCatalog.kt`
- `scripts/build-hf-asset-manifest.ps1`
- `supabase/artifacts/hf-artifacts-manifest.json`
- `app/src/main/java/io/legado/app/data/repository/AssetDeliveryRepository.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AssetDelivery/ZeroTTS` module
> - Add test case to prevent regression
