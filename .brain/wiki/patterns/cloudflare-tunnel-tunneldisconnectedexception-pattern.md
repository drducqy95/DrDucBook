---
title: Cloudflare Tunnel - TunnelDisconnectedException Pattern
type: pattern
slug: cloudflare-tunnel-tunneldisconnectedexception-pattern
category: patterns
created: 2026-08-26T01:30:15
updated: 2026-08-26T01:30:15
status: active
source: /debug
tags: ["Cloudflare Tunnel", "TunnelDisconnectedException", "auto-generated"]
---

# Cloudflare Tunnel - TunnelDisconnectedException Pattern

> [!WARNING]
> **Origin:** `ERR_0002`
> **Module:** `Cloudflare Tunnel` | **Type:** `TunnelDisconnectedException`

## 🚨 The Issue

**Message:** 
```text
Error 1033 and unexpected tunnel stop on network flapping without auto-reconnect
```

**Root Cause:** 
CloudflareTunnelManager had no retry loop on process exit and MyViewModel was actively stopping the tunnel on temporary network disconnects without restarting

## 🛠️ The Fix

Added RECONNECTING phase, exponential backoff auto-restart (5 retries: 2s to 32s), removed kill-on-network-drop in MyViewModel, and added retryIfFailed on network recovery

**Files Affected:**
- `CloudflareTunnelModels.kt`
- `CloudflareTunnelManager.kt`
- `MyViewModel.kt`
- `MyScreen.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `Cloudflare Tunnel` module
> - Add test case to prevent regression
