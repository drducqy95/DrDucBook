---
title: LocalAI Model Import & Path Auto-Detection - CONFIG_AND_UI_STATE_DESYNC Pattern
type: pattern
slug: localai-model-import-path-auto-detection-config-and-ui-state-desync-pattern
category: patterns
created: 2026-08-26T18:58:29
updated: 2026-08-26T18:58:29
status: active
source: /debug
tags: ["LocalAI Model Import & Path Auto-Detection", "CONFIG_AND_UI_STATE_DESYNC", "auto-generated"]
---

# LocalAI Model Import & Path Auto-Detection - CONFIG_AND_UI_STATE_DESYNC Pattern

> [!WARNING]
> **Origin:** `ERR_0005`
> **Module:** `LocalAI Model Import & Path Auto-Detection` | **Type:** `CONFIG_AND_UI_STATE_DESYNC`

## 🚨 The Issue

**Message:** 
```text
Asset Delivery model download reported imported package but localAiModelPath was not updated and UI remained empty, causing test translation failure
```

**Root Cause:** 
AssetDeliveryImportRepository called importModel but never set TranslationConfig.localAiModelPath. Additionally, TranslationConfigScreen localAiModelPath was not reactive in Compose state and lacked auto-discovery fallback across asset_delivery and local-ai storage directories

## 🛠️ The Fix

1) Set TranslationConfig.localAiModelPath in AssetDeliveryImportRepository upon LOCAL_AI artifact import; 2) Added multi-directory fallback scan in LocalAiTranslationRepository.resolveModelPath; 3) Made localAiModelPath a reactive Compose MutableState and added auto-discovery LaunchedEffect in TranslationConfigScreen.

**Files Affected:**
- `AssetDeliveryImportRepository.kt`
- `LocalAiTranslationRepository.kt`
- `TranslationConfigScreen.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `LocalAI Model Import & Path Auto-Detection` module
> - Add test case to prevent regression
