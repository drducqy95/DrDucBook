---
title: Reader/PerBookPrompt - LogicError Pattern
type: pattern
slug: readerperbookprompt-logicerror-pattern
category: patterns
created: 2026-09-14T05:33:22
updated: 2026-09-14T05:33:22
status: active
source: /debug
tags: ["Reader/PerBookPrompt", "LogicError", "auto-generated"]
---

# Reader/PerBookPrompt - LogicError Pattern

> [!WARNING]
> **Origin:** `ERR_0027`
> **Module:** `Reader/PerBookPrompt` | **Type:** `LogicError`

## 🚨 The Issue

**Message:** 
```text
Nut luu prompt rieng khong phan hoi UI va tron lan prompt AI Translation voi AI Rewrite
```

**Root Cause:** 
savePerBookPrompt khong cap nhat _uiState.perBookPromptModified; resolveEffectivePrompt dung chung 1 ham va 1 field customTranslationPrompt cho ca translation va rewrite

## 🛠️ The Fix

Cap nhat _uiState trong savePerBookPrompt kem toast xac nhan; them customRewritePrompt vao Book va tach 2 ham resolveEffective rieng biet

**Files Affected:**
- `Book.kt`
- `TranslateChapterUseCase.kt`
- `ReadBookContract.kt`
- `ReadBookViewModel.kt`
- `PerBookTranslationPromptSection.kt`
- `TranslationProgressSheet.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `Reader/PerBookPrompt` module
> - Add test case to prevent regression
