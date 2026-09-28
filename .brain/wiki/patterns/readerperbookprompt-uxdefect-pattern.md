---
title: Reader/PerBookPrompt - UXDefect Pattern
type: pattern
slug: readerperbookprompt-uxdefect-pattern
category: patterns
created: 2026-09-14T05:50:51
updated: 2026-09-14T05:50:51
status: active
source: /debug
tags: ["Reader/PerBookPrompt", "UXDefect", "auto-generated"]
---

# Reader/PerBookPrompt - UXDefect Pattern

> [!WARNING]
> **Origin:** `ERR_0028`
> **Module:** `Reader/PerBookPrompt` | **Type:** `UXDefect`

## 🚨 The Issue

**Message:** 
```text
Phan chinh sua prompt rieng khong an gon di sau khi luu
```

**Root Cause:** 
PerBookCustomPromptSection thieu trang thai compact collapsed khi da co prompt va khong auto-collapse khi bam Luu

## 🛠️ The Fix

Them trang thai isEditing, tu dong an gon thanh card tom tat kem xem truoc 2 dong va nut Sua; tu dong thu gon khi bam Luu va bo sung nut Thu gon

**Files Affected:**
- `PerBookTranslationPromptSection.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `Reader/PerBookPrompt` module
> - Add test case to prevent regression
