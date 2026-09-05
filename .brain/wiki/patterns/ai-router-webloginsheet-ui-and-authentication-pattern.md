---
title: AI Router / WebLoginSheet - UI_AND_AUTHENTICATION Pattern
type: pattern
slug: ai-router-webloginsheet-ui-and-authentication-pattern
category: patterns
created: 2026-09-04T23:13:42
updated: 2026-09-04T23:13:42
status: active
source: /debug
tags: ["AI Router / WebLoginSheet", "UI_AND_AUTHENTICATION", "auto-generated"]
---

# AI Router / WebLoginSheet - UI_AND_AUTHENTICATION Pattern

> [!WARNING]
> **Origin:** `ERR_0015`
> **Module:** `AI Router / WebLoginSheet` | **Type:** `UI_AND_AUTHENTICATION`

## 🚨 The Issue

**Message:** 
```text
Gemini Web Google sign-in blocked on WebView and UI button layout overflow
```

**Root Cause:** 
1) Google AccountsSignInUi strictly blocks embedded Android WebViews; guest SNlM0e was falsely treated as authenticated session without __Secure-1PSID; 2) SpaceBetween Row without weight crushed action buttons off-screen and lack of verticalScroll clipped WebView

## 🛠️ The Fix

1) Defaulted selectedTab to 1 for Gemini with clear step-by-step external browser flow; required __Secure-1PSID for valid Gemini session; 2) Added verticalScroll, wrapped status & buttons in dedicated full-width Surface cards with weight(1f)/weight(2f), and sized WebView to 360dp

**Files Affected:**
- `WebLoginSheet.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `AI Router / WebLoginSheet` module
> - Add test case to prevent regression
