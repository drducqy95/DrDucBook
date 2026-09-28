---
title: Use Gomobile AAR for Local WebDAV Proxy
type: decision
slug: use-gomobile-aar-for-local-webdav-proxy
category: decisions
created: 2026-09-12T12:12:40
updated: 2026-09-12T12:12:40
status: active
source: /plan
tags: ["webdav", "gomobile", "architecture", "p34", "auto"]
---

# Use Gomobile AAR for Local WebDAV Proxy

Context: Need read-only access to Google Drive and public links over standard WebDAV protocol on localhost. Rationale: Go has robust webdav server and Drive v3 SDK. Gomobile generates standard AAR for Android. Alternatives: Ktor WebDAV, Python runtime. Impact: Increases split APK size by ~5-10MB per ABI, provides fast native HTTP/WebDAV loopback proxy.
