---
title: WebService TTS - AudioPlaybackLatency Pattern
type: pattern
slug: webservice-tts-audioplaybacklatency-pattern
category: patterns
created: 2026-08-26T01:30:28
updated: 2026-08-26T01:30:28
status: active
source: /debug
tags: ["WebService TTS", "AudioPlaybackLatency", "auto-generated"]
---

# WebService TTS - AudioPlaybackLatency Pattern

> [!WARNING]
> **Origin:** `ERR_0003`
> **Module:** `WebService TTS` | **Type:** `AudioPlaybackLatency`

## 🚨 The Issue

**Message:** 
```text
Audible pause and stutter between chunk transitions in WebService reader
```

**Root Cause:** 
Sequential audio element creation after onended, cache-busting timestamp bypassing browser cache, and roundtrip network download for silent/whitespace chunks

## 🛠️ The Fix

Implemented audio double buffering, preloaded Audio instances in background, removed cache-busting query param, and skipped network roundtrip for silent chunks using backend silent flag

**Files Affected:**
- `WebServiceModels.kt`
- `KtorServer.kt`
- `WebServiceTtsController.kt`
- `webService.ts`
- `BookChapter.vue`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `WebService TTS` module
> - Add test case to prevent regression
