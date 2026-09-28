---
title: AssetDelivery/ZeroTTS - ArtifactNotAllowListed Pattern
type: pattern
slug: assetdeliveryzerotts-artifactnotallowlisted-pattern
category: patterns
created: 2026-09-14T20:48:25
updated: 2026-09-14T20:48:25
status: active
source: /debug
tags: ["AssetDelivery/ZeroTTS", "ArtifactNotAllowListed", "auto-generated"]
---

# AssetDelivery/ZeroTTS - ArtifactNotAllowListed Pattern

> [!WARNING]
> **Origin:** `ERR_0029`
> **Module:** `AssetDelivery/ZeroTTS` | **Type:** `ArtifactNotAllowListed`

## 🚨 The Issue

**Message:** 
```text
Asset ticket request failed (404): Artifact is not allow-listed when downloading tts-zerotts-base
```

**Root Cause:** 
The Supabase Edge Functions asset-ticket and asset-download in the cloud held the previous manifest bundle without ZeroTTS artifacts

## 🛠️ The Fix

Generated new hf-artifacts-manifest.json with all 9 ZeroTTS artifacts and deployed functions asset-ticket and asset-download to project faegbafmkpsocoecrhvz using Supabase CLI

**Files Affected:**
- `supabase/artifacts/hf-artifacts-manifest.json`
- `scripts/build-hf-asset-manifest.ps1`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AssetDelivery/ZeroTTS` module
> - Add test case to prevent regression
