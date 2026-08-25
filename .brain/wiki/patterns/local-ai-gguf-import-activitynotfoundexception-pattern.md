---
title: Local AI GGUF Import - ActivityNotFoundException Pattern
type: pattern
slug: local-ai-gguf-import-activitynotfoundexception-pattern
category: patterns
created: 2026-08-26T01:29:57
updated: 2026-08-26T01:29:57
status: active
source: /debug
tags: ["Local AI GGUF Import", "ActivityNotFoundException", "auto-generated"]
---

# Local AI GGUF Import - ActivityNotFoundException Pattern

> [!WARNING]
> **Origin:** `ERR_0001`
> **Module:** `Local AI GGUF Import` | **Type:** `ActivityNotFoundException`

## 🚨 The Issue

**Message:** 
```text
Can't open drducbook-asset://catalog/local-ai-hy-mt2 caused by ActivityNotFoundException
```

**Root Cause:** 
AiRouterScreen used Compose LocalUriHandler.openUri() which delegates to external Intent.ACTION_VIEW instead of in-app ContextExtensions.openUrl() that resolves internal drducbook-asset:// URIs

## 🛠️ The Fix

Replaced LocalUriHandler with LocalContext and used context.openUrl() in AiRouterScreen.kt

**Files Affected:**
- `AiRouterScreen.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `Local AI GGUF Import` module
> - Add test case to prevent regression
