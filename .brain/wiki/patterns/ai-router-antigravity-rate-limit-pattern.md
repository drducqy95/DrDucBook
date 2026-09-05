---
title: AI Router / Antigravity - RATE_LIMIT Pattern
type: pattern
slug: ai-router-antigravity-rate-limit-pattern
category: patterns
created: 2026-09-04T20:59:00
updated: 2026-09-04T20:59:00
status: active
source: /debug
tags: ["AI Router / Antigravity", "RATE_LIMIT", "auto-generated"]
---

# AI Router / Antigravity - RATE_LIMIT Pattern

> [!WARNING]
> **Origin:** `ERR_0010`
> **Module:** `AI Router / Antigravity` | **Type:** `RATE_LIMIT`

## 🚨 The Issue

**Message:** 
```text
Google Antigravity HTTP 429 RESOURCE_EXHAUSTED on high-reasoning models in aicode-consumers
```

**Root Cause:** 
cloudcode-pa.googleapis.com throttles high reasoning models (gemini-3.7-flash-high, claude-opus-4-6-thinking) with HTTP 429 RESOURCE_EXHAUSTED under shared aicode-consumers pool due to MODEL_CAPACITY_EXHAUSTED or RPM limit

## 🛠️ The Fix

Recommend using lower tier flash models (gemini-3.1-flash-lite, gemini-3.8-flash-low) or spacing requests to avoid RPM throttling

**Files Affected:**
- `AntigravityHandler.kt`
- `AiProviderFailure.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AI Router / Antigravity` module
> - Add test case to prevent regression
