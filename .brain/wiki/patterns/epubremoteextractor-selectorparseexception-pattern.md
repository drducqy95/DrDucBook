---
title: EpubRemoteExtractor - SelectorParseException Pattern
type: pattern
slug: epubremoteextractor-selectorparseexception-pattern
category: patterns
created: 2026-09-13T11:21:14
updated: 2026-09-13T11:21:14
status: active
source: /debug
tags: ["EpubRemoteExtractor", "SelectorParseException", "auto-generated"]
---

# EpubRemoteExtractor - SelectorParseException Pattern

> [!WARNING]
> **Origin:** `ERR_0026`
> **Module:** `EpubRemoteExtractor` | **Type:** `SelectorParseException`

## 🚨 The Issue

**Message:** 
```text
org.jsoup.select.Selector$SelectorParseException: String must not be empty at io.legado.app.help.drive.extractor.EpubRemoteExtractor.extract(EpubRemoteExtractor.kt:52)
```

**Root Cause:** 
In EpubRemoteExtractor.kt line 52, doc.select had empty id value. In line 93, resolveZipPath had slash split bug. Missing namespace handling in tags. Lack of try-catch isolation.

## 🛠️ The Fix

Use getElementById and getElementsByTag. Fix resolveZipPath. Add try-catch in repository and viewmodel. Add robust date parsing in SupabaseAccountAccessRepository.

**Files Affected:**
- `app/src/main/java/io/legado/app/help/drive/extractor/EpubRemoteExtractor.kt`
- `app/src/main/java/io/legado/app/data/repository/RemoteBookMetadataRepository.kt`
- `app/src/main/java/io/legado/app/ui/drive/DriveLibraryViewModel.kt`
- `app/src/main/java/io/legado/app/data/repository/SupabaseAccountAccessRepository.kt`
- `app/src/main/java/io/legado/app/ui/account/AccountScreen.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `EpubRemoteExtractor` module
> - Add test case to prevent regression
