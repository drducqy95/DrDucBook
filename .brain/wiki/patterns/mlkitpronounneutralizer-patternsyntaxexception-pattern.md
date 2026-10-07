---
title: MlKitPronounNeutralizer - PatternSyntaxException Pattern
type: pattern
slug: mlkitpronounneutralizer-patternsyntaxexception-pattern
category: patterns
created: 2026-10-06T15:51:09
updated: 2026-10-06T15:51:09
status: active
source: /debug
tags: ["MlKitPronounNeutralizer", "PatternSyntaxException", "auto-generated"]
---

# MlKitPronounNeutralizer - PatternSyntaxException Pattern

> [!WARNING]
> **Origin:** `ERR_0046`
> **Module:** `MlKitPronounNeutralizer` | **Type:** `PatternSyntaxException`

## 🚨 The Issue

**Message:** 
```text
Crash NoClassDefFoundError: io.legado.app.domain.model.MlKitPronounNeutralizer due to ExceptionInInitializerError
```

**Root Cause:** 
Android ICU regex engine requires bounded lookbehind; unbounded \s+ in HO_REGEX caused PatternSyntaxException during static clinit

## 🛠️ The Fix

Open

**Files Affected:**
- `app/src/main/java/io/legado/app/domain/model/MlKitPronounNeutralizer.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `MlKitPronounNeutralizer` module
> - Add test case to prevent regression
