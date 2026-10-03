# Plan: ML Kit Language Fix + Local OPDS Drive Proxy Server

Created: 2026-10-03T10:53:00+07:00
Updated: 2026-10-03T13:00:00+07:00
Status: 🟢 Completed (100%)

## Overview

Hai tính năng / bugfix:

1. **ML Kit Language Detection Fix** — ML Kit không tự nhận diện được ngôn ngữ nguồn. Khắc phục bằng cách cho user tự chọn ngôn ngữ nguồn-đích.

2. **Local OPDS Drive Proxy Server** — Tương tự hệ thống Drive WebDAV Local hiện tại (GoBridge → localhost WebDAV), tạo thêm một server proxy OPDS chạy local trong app. Server nhận link cloud drive (Google Drive, OneDrive — cả public & private với API), fetch file listing, cấu trúc thành OPDS catalog XML, serve trên localhost. App consume local OPDS để browse & import sách vào bookshelf.

## Architecture Comparison

```
HIỆN TẠI (Drive WebDAV Local):
  Cloud Drive → GoBridge (Go native) → localhost:port WebDAV → WebDav client → UI

MỚI (Drive OPDS Local):
  Cloud Drive → Ktor OPDS Proxy → localhost:port/opds/ → OPDS client → UI
```

## Tech Stack
- Kotlin (JVM 21), Jetpack Compose Material 3, Clean Architecture + MVI/UDF
- Ktor CIO (existing), Koin DI, Room (v105)
- Existing: DriveLibrary UI, DriveLinkResolver, ManagedDriveSource, GoBridge

## Phases

| Phase | Name | Status | Progress | Tasks |
|-------|------|--------|----------|-------|
| 01 | ML Kit Source Language Selector | 🟢 Completed | 100% | 8/8 |
| 02 | OPDS Models + Atom XML Serializer | 🟢 Completed | 100% | 5/5 |
| 03 | OPDS Drive Proxy Server + Service Account | 🟢 Completed | 100% | 11/11 |
| 04 | OPDS Client + DriveLibrary + SA UI | 🟢 Completed | 100% | 8/8 |
| 05 | Testing & Integration | 🟢 Completed | 100% | 6/6 |

**Tổng:** 38/38 tasks | Hoàn thành: 100% | Compile & Unit Tests Verified PASS

## Documents

| File | Purpose |
|------|---------|
| [plan.md](./plan.md) | Overview |
| [phase-01](./phase-01-mlkit-source-language.md) | ML Kit source language fix |
| [phase-02](./phase-02-opds-domain.md) | OPDS models + Atom XML serializer/parser |
| [phase-03](./phase-03-opds-controller.md) | OPDS Drive Proxy Server (Ktor + Service) |
| [phase-04](./phase-04-opds-ui.md) | OPDS Client + DriveLibrary UI integration |
| [phase-05](./phase-05-testing.md) | Testing |
| [**guide-private-drive-opds.md**](./guide-private-drive-opds.md) | **📖 Hướng dẫn chi tiết dùng Private Drive với OPDS** |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
