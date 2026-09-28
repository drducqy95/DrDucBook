# Plan: Drive Remote Rich Book Catalog, DrDucBook WebDAV & Admin Account Management

Created: 2026-09-13T07:55:00+07:00
Status: 🟡 In Progress

## Overview
1. Xây dựng hệ thống trích xuất ảnh bìa và metadata từ xa mà không cần tải toàn bộ tệp sách (Zero-Download Remote Metadata Extraction), hỗ trợ đầy đủ 10 định dạng phổ biến (`html`, `md`, `txt`, `doc`, `docx`, `pdf`, `azw3`, `prc`, `mobi`, `epub`) và nâng cấp giao diện duyệt Drive sang dạng thẻ sách phong phú (Rich Book Card) giống như màn hình Nguồn Sách / Khám Phá.
2. Chuẩn hóa thương hiệu: Đổi tên hiển thị WebDAV từ "Legado Drive WebDAV" sang "DrDucBook WebDAV".
3. Hoàn thiện tính năng Quản trị tài khoản (Admin): Bổ sung ngày đăng ký, lần đăng nhập gần nhất, bộ lọc nâng cao và sắp xếp đa tiêu chí.

## Tech Stack
- Backend: Go / Gomobile (`native/go-webdav`), Google Drive REST API v3, HTTP Range Request
- Android: Kotlin, Jetpack Compose Material 3 Expressive, Coil Image Loading
- Database & Auth: AndroidX Room (Schema v106 / RemoteBookMetadataEntity), Supabase Auth / PostgreSQL

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | WebDAV Branding ("DrDucBook WebDAV") & Google Drive API v3 Native Thumbnails | ⬜ Pending | 0% |
| 02 | Zero-Download Range Extractors (EPUB, MOBI/AZW3/PRC, DOCX, PDF) | ⬜ Pending | 0% |
| 03 | Text Format Analyzers, Companion Covers & Typography Cover Generator | ⬜ Pending | 0% |
| 04 | Database Schema, Room Cache & Viewport Async Prefetcher | ⬜ Pending | 0% |
| 05 | Rich Book Card UI, View Modes Switcher & Preview Sheet | ⬜ Pending | 0% |
| 06 | Admin Account Management Enhancement (Created Date, Last Sign-in, Filters & Sorting) | ⬜ Pending | 0% |
| 07 | Automated Testing, Bandwidth Benchmarks & Device Verification | ⬜ Pending | 0% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
