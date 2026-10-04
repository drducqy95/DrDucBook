---
title: QuickDictionarySelectionResolver - MAPPING_ALIGNMENT_DESYNC Pattern
type: pattern
slug: quickdictionaryselectionresolver-mapping-alignment-desync-pattern
category: patterns
created: 2026-10-04T22:03:19
updated: 2026-10-04T22:03:19
status: active
source: /debug
tags: ["QuickDictionarySelectionResolver", "MAPPING_ALIGNMENT_DESYNC", "auto-generated"]
---

# QuickDictionarySelectionResolver - MAPPING_ALIGNMENT_DESYNC Pattern

> [!WARNING]
> **Origin:** `ERR_0044`
> **Module:** `QuickDictionarySelectionResolver` | **Type:** `MAPPING_ALIGNMENT_DESYNC`

## 🚨 The Issue

**Message:** 
```text
Raw mapping error when adding QT dictionary entry: whole paragraph offered and raw field left blank
```

**Root Cause:** 
1) alignedParagraphMapping collapses entire chapter to 1 low-confidence segment when source and display line counts differ (80 vs 79); 2) sourceSearchWindow abandons paragraph mapping on line count mismatch and falls back to linear interpolation with 192-char radius; 3) MAX_GLOBAL_ALIGNMENT_SOURCE_CHARS (2400) blocks fallback on chapters > 2400 chars (standard is 3500-8000 chars); 4) selectionStart drifts due to paragraphIndent not accounted for; 5) whole paragraph offered as dictionary term alternative

## 🛠️ The Fix

Open

**Files Affected:**
- `app/src/main/java/io/legado/app/domain/model/MappedDisplayText.kt`
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `QuickDictionarySelectionResolver` module
> - Add test case to prevent regression
