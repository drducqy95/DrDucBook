---
title: AI Router / Antigravity - PROTOCOL_PAYLOAD Pattern
type: pattern
slug: ai-router-antigravity-protocol-payload-pattern
category: patterns
created: 2026-09-04T21:11:28
updated: 2026-09-04T21:11:28
status: active
source: /debug
tags: ["AI Router / Antigravity", "PROTOCOL_PAYLOAD", "auto-generated"]
---

# AI Router / Antigravity - PROTOCOL_PAYLOAD Pattern

> [!WARNING]
> **Origin:** `ERR_0011`
> **Module:** `AI Router / Antigravity` | **Type:** `PROTOCOL_PAYLOAD`

## 🚨 The Issue

**Message:** 
```text
Antigravity all models 429 RESOURCE_EXHAUSTED due to custom systemInstruction
```

**Root Cause:** 
cloudcode-pa.googleapis.com endpoint strictly requires ANTIGRAVITY_DEFAULT_SYSTEM in systemInstruction. When client system prompt is sent in systemInstruction, server returns HTTP 429 RESOURCE_EXHAUSTED for all models (issue #9030)

## 🛠️ The Fix

Keep only ANTIGRAVITY_DEFAULT_SYSTEM in systemInstruction; relocate client system prompt to contents[0]; cap Claude output at 16384; add Log.e and AppLog error reporting

**Files Affected:**
- `AntigravityHandler.kt`
- `AntigravityProjectResolver.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AI Router / Antigravity` module
> - Add test case to prevent regression
