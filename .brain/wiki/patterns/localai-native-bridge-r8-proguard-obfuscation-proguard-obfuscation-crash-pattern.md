---
title: LocalAI Native Bridge R8 Proguard Obfuscation - PROGUARD_OBFUSCATION_CRASH Pattern
type: pattern
slug: localai-native-bridge-r8-proguard-obfuscation-proguard-obfuscation-crash-pattern
category: patterns
created: 2026-08-26T19:22:40
updated: 2026-08-26T19:22:40
status: active
source: /debug
tags: ["LocalAI Native Bridge R8 Proguard Obfuscation", "PROGUARD_OBFUSCATION_CRASH", "auto-generated"]
---

# LocalAI Native Bridge R8 Proguard Obfuscation - PROGUARD_OBFUSCATION_CRASH Pattern

> [!WARNING]
> **Origin:** `ERR_0006`
> **Module:** `LocalAI Native Bridge R8 Proguard Obfuscation` | **Type:** `PROGUARD_OBFUSCATION_CRASH`

## 🚨 The Issue

**Message:** 
```text
App crash with SIGABRT / AssertNoPendingExceptionForNewException in Java_io_legado_app_data_repository_LocalAiNativeBridge_generate during test translation in release build
```

**Root Cause:** 
R8 minification obfuscated LocalAiNativeBridge.Callback methods (onToken, isCancelled), causing JNI GetMethodID to fail with NoSuchMethodError in release build. In addition, throw_java did not call ExceptionClear prior to throwing new Java exceptions

## 🛠️ The Fix

1) Added -keep rules in proguard-rules.pro for LocalAiNativeBridge, inner Callback interface, and domain/repository classes; 2) Added ExceptionClear before GetMethodID lookups and ThrowNew in local_ai.cpp

**Files Affected:**
- `proguard-rules.pro`
- `local_ai.cpp`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `LocalAI Native Bridge R8 Proguard Obfuscation` module
> - Add test case to prevent regression
