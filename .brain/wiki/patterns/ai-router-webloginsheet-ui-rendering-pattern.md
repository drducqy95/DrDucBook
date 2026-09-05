---
title: AI Router / WebLoginSheet - UI_RENDERING Pattern
type: pattern
slug: ai-router-webloginsheet-ui-rendering-pattern
category: patterns
created: 2026-09-04T16:38:23
updated: 2026-09-04T16:38:23
status: active
source: /debug
tags: ["AI Router / WebLoginSheet", "UI_RENDERING", "auto-generated"]
---

# AI Router / WebLoginSheet - UI_RENDERING Pattern

> [!WARNING]
> **Origin:** `ERR_0009`
> **Module:** `AI Router / WebLoginSheet` | **Type:** `UI_RENDERING`

## 🚨 The Issue

**Message:** 
```text
WebView black screen and cramped height during ChatGPT / Gemini web login
```

**Root Cause:** 
WebView inside ModalBottomSheet rendered black due to unset background color, lack of explicit hardware layer, unhandled redirects, and fixed 420dp height

## 🛠️ The Fix

Set explicit white background, hardware layer type, dynamic screen height 68% (480-700dp), shouldOverrideUrlLoading handling, and reload button

**Files Affected:**
- `WebLoginSheet.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AI Router / WebLoginSheet` module
> - Add test case to prevent regression
