---
title: VietnameseTranslationPostProcessor - PatternSyntaxException Pattern
type: pattern
slug: vietnamesetranslationpostprocessor-patternsyntaxexception-pattern
category: patterns
created: 2026-09-12T07:34:58
updated: 2026-09-12T07:34:58
status: active
source: /debug
tags: ["VietnameseTranslationPostProcessor", "PatternSyntaxException", "auto-generated"]
---

# VietnameseTranslationPostProcessor - PatternSyntaxException Pattern

> [!WARNING]
> **Origin:** `ERR_0018`
> **Module:** `VietnameseTranslationPostProcessor` | **Type:** `PatternSyntaxException`

## 🚨 The Issue

**Message:** 
```text
Syntax error in regexp pattern near index 3 (?U)(?<=\p{L})\?(?=\p{L})
```

**Root Cause:** 
Android ART / ICU regex compiler does not support JDK (?U) embedded UNICODE_CHARACTER_CLASS flag, causing PatternSyntaxException at runtime

## 🛠️ The Fix

Removed (?U) flag; replaced with portable Unicode character classes (?<=\p{L})\?(?=\p{L}) and (?<!\p{L})(\p{Lu}\p{Ll}{1,20})\s*\?\s+(\p{Lu}\p{Ll}{1,20})(?!\p{L})

**Files Affected:**
- `VietnameseTranslationPostProcessor.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `VietnameseTranslationPostProcessor` module
> - Add test case to prevent regression
