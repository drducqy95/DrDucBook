---
title: AiTranslationRefinePipeline / StoryMemory - TranslationCjkResidueException Pattern
type: pattern
slug: aitranslationrefinepipeline-storymemory-translationcjkresidueexception-pattern
category: patterns
created: 2026-09-12T08:22:20
updated: 2026-09-12T08:22:20
status: active
source: /debug
tags: ["AiTranslationRefinePipeline / StoryMemory", "TranslationCjkResidueException", "auto-generated"]
---

# AiTranslationRefinePipeline / StoryMemory - TranslationCjkResidueException Pattern

> [!WARNING]
> **Origin:** `ERR_0019`
> **Module:** `AiTranslationRefinePipeline / StoryMemory` | **Type:** `TranslationCjkResidueException`

## 🚨 The Issue

**Message:** 
```text
Translation parse error: segment 24 still contains CJK text; segment 25 still contains CJK text
```

**Root Cause:** 
TranslationStoryMemoryUseCase created relationship placeholder entities with target = raw (untranslated CJK string). AiTranslationStoryMemory exported them into entityDictionary, and lockedDictionaryFor locked Chinese targets into locked_dictionary. System prompt Rule 3 forced AI to output Chinese target, violating Rule 5 in a perpetual retry loop.

## 🛠️ The Fix

1) Set placeholder entity target = empty string; 2) Filter out CJK/raw targets in AiTranslationStoryMemory.selectContext; 3) Hard guard in lockedDictionaryFor rejecting CJK targets for Vietnamese; 4) Sanitize existing poisoned entities on loadSnapshot; 5) Enhance retry instruction with specific CJK failure details.

**Files Affected:**
- `app/src/main/java/io/legado/app/domain/model/AiTranslationRefinePipeline.kt`
- `app/src/main/java/io/legado/app/domain/model/AiTranslationStoryMemory.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslationStoryMemoryUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AiTranslationRefinePipeline / StoryMemory` module
> - Add test case to prevent regression
