---
title: AI Router / Antigravity - CONFIGURATION Pattern
type: pattern
slug: ai-router-antigravity-configuration-pattern
category: patterns
created: 2026-09-04T16:38:13
updated: 2026-09-04T16:38:13
status: active
source: /debug
tags: ["AI Router / Antigravity", "CONFIGURATION", "auto-generated"]
---

# AI Router / Antigravity - CONFIGURATION Pattern

> [!WARNING]
> **Origin:** `ERR_0008`
> **Module:** `AI Router / Antigravity` | **Type:** `CONFIGURATION`

## 🚨 The Issue

**Message:** 
```text
Stream interrupted: Cấu hình AI chưa hợp lệ on Antigravity models
```

**Root Cause:** 
Direct no-route fallback in AiRouterRepository bypasses credential injection; Antigravity provider saves empty apiKey because OAuth token is in credentials; require check failed on empty apiKey or baseUrl

## 🛠️ The Fix

Implemented resolveDirectRequest() to inject OAuth credentials for direct fallback; defaulted effectiveBaseUrl to ANTIGRAVITY_IDE_BASE_URL

**Files Affected:**
- `AiRouterRepository.kt`
- `AntigravityHandler.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AI Router / Antigravity` module
> - Add test case to prevent regression
