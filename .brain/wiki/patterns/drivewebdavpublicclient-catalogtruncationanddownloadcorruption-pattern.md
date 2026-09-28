---
title: DriveWebDav/PublicClient - CatalogTruncationAndDownloadCorruption Pattern
type: pattern
slug: drivewebdavpublicclient-catalogtruncationanddownloadcorruption-pattern
category: patterns
created: 2026-09-12T22:38:52
updated: 2026-09-12T22:38:52
status: active
source: /debug
tags: ["DriveWebDav/PublicClient", "CatalogTruncationAndDownloadCorruption", "auto-generated"]
---

# DriveWebDav/PublicClient - CatalogTruncationAndDownloadCorruption Pattern

> [!WARNING]
> **Origin:** `ERR_0025`
> **Module:** `DriveWebDav/PublicClient` | **Type:** `CatalogTruncationAndDownloadCorruption`

## 🚨 The Issue

**Message:** 
```text
Google Drive public folder truncation to 50 items and EPUB download central directory corruption
```

**Root Cause:** 
1) SSR HTML only embeds 50 items; 2) parseHumanSize estimated sizes truncating ZIP central directory by hundreds of bytes; 3) StateFlow stale port race condition in connection use case

## 🛠️ The Fix

1) Integrated DefaultGDrivePublicAPIKey and Google Drive REST API v3 with pageSize=1000 pagination loop; 2) Enforced exact byte size via API v3 and HTTP HEAD probeSize; 3) Reset serverInfo port to 0 on start and wait for port > 0

**Files Affected:**
- `native/go-webdav/drive/public_client.go`
- `native/go-webdav/drive/reader.go`
- `DriveWebDavServiceController.kt`
- `DriveWebDavConnectionUseCase.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `DriveWebDav/PublicClient` module
> - Add test case to prevent regression
