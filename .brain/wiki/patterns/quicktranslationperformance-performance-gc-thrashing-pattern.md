---
title: QuickTranslation/Performance - PERFORMANCE_GC_THRASHING Pattern
type: pattern
slug: quicktranslationperformance-performance-gc-thrashing-pattern
category: patterns
created: 2026-10-04T08:12:54
updated: 2026-10-04T08:12:54
status: active
source: /debug
tags: ["QuickTranslation/Performance", "PERFORMANCE_GC_THRASHING", "auto-generated"]
---

# QuickTranslation/Performance - PERFORMANCE_GC_THRASHING Pattern

> [!WARNING]
> **Origin:** `ERR_0040`
> **Module:** `QuickTranslation/Performance` | **Type:** `PERFORMANCE_GC_THRASHING`

## 🚨 The Issue

**Message:** 
```text
Severe GC thrashing and lag during translation rendering due to untriggered regex storm, unbounded candidate exploration, and TOC emission thrashing
```

**Root Cause:** 
1) 64/202 post-rules lacked literal triggers, evaluating regexes unconditionally; 2) SelectionResolver tested up to 400 candidate substrings per tap; 3) TocViewModel emitted every batch of 50 chapters causing continuous remapping and NativeAlloc GC of 750k objects; 4) NetworkUtils threw MalformedURLException on data URIs; 5) Ephemeral UI cache used multi-step atomic writes

## 🛠️ The Fix

1) Expanded literal trigger detection to 97.5% of post-rules with fast paths; 2) Bounded selection candidates to 40; 3) Throttled TocViewModel emissions; 4) Added data URL fast path in NetworkUtils; 5) Replaced atomic writes with direct writeText for UI cache

**Files Affected:**
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`
- `app/src/main/java/io/legado/app/ui/book/toc/TocViewModel.kt`
- `app/src/main/java/io/legado/app/utils/NetworkUtils.kt`
- `app/src/main/java/io/legado/app/data/repository/TranslationCacheRepositoryImpl.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `QuickTranslation/Performance` module
> - Add test case to prevent regression
