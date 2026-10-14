---
title: DI / Koin / BrowserViewModel - KOIN_NO_DEFINITION_FOUND Pattern
type: pattern
slug: di-koin-browserviewmodel-koin-no-definition-found-pattern
category: patterns
created: 2026-10-10T15:39:08
updated: 2026-10-10T15:39:08
status: active
source: /debug
tags: ["DI / Koin / BrowserViewModel", "KOIN_NO_DEFINITION_FOUND", "auto-generated"]
---

# DI / Koin / BrowserViewModel - KOIN_NO_DEFINITION_FOUND Pattern

> [!WARNING]
> **Origin:** `ERR_0048`
> **Module:** `DI / Koin / BrowserViewModel` | **Type:** `KOIN_NO_DEFINITION_FOUND`

## 🚨 The Issue

**Message:** 
```text
NoDefinitionFoundException for Function0 in TranslateBrowserPageUseCase
```

**Root Cause:** 
Koin singleOf(::TranslateBrowserPageUseCase) attempts reflection resolution on default parameter sourceLanguageProvider: () -> String? (kotlin.jvm.functions.Function0, obfuscated as b40.a) which is missing from Koin scope root

## 🛠️ The Fix

Open

**Files Affected:**
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslateBrowserPageUseCase.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `DI / Koin / BrowserViewModel` module
> - Add test case to prevent regression
