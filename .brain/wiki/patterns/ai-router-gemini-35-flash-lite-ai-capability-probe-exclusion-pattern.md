---
title: AI Router / Gemini 3.5 Flash Lite - AI_CAPABILITY_PROBE_EXCLUSION Pattern
type: pattern
slug: ai-router-gemini-35-flash-lite-ai-capability-probe-exclusion-pattern
category: patterns
created: 2026-10-10T15:39:14
updated: 2026-10-10T15:39:14
status: active
source: /debug
tags: ["AI Router / Gemini 3.5 Flash Lite", "AI_CAPABILITY_PROBE_EXCLUSION", "auto-generated"]
---

# AI Router / Gemini 3.5 Flash Lite - AI_CAPABILITY_PROBE_EXCLUSION Pattern

> [!WARNING]
> **Origin:** `ERR_0049`
> **Module:** `AI Router / Gemini 3.5 Flash Lite` | **Type:** `AI_CAPABILITY_PROBE_EXCLUSION`

## 🚨 The Issue

**Message:** 
```text
Cấu hình AI chưa hợp lệ on gemini-3.5-flash-lite due to capability probe exclusion
```

**Root Cause:** 
probeApiCredentialCapabilities probes first 2 models with rigid JSON schema and 10s timeout marking gemini-3.5-flash-lite as CONTRACT_UNSUPPORTED; resolveDirectRequest filters out credentials with non-usable capabilities leaving request without API key

## 🛠️ The Fix

Open

**Files Affected:**
- `app/src/main/java/io/legado/app/data/repository/AiRouterRepository.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AI Router / Gemini 3.5 Flash Lite` module
> - Add test case to prevent regression
