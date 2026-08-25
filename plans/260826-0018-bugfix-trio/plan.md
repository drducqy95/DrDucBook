# Plan: Bugfix Trio — Local AI Crash + Cloudflare Tunnel Stability + TTS WebService Stutter

Created: 2026-08-26T00:18:00+07:00
Status: 🟡 In Progress

## Overview
Khắc phục 3 lỗi ảnh hưởng trải nghiệm người dùng:
1. **Local AI Crash** — App crash khi mở catalog GGUF từ AI Router
2. **Cloudflare Tunnel Error 1033** — Tunnel tự ngắt khi mạng chập chờn, không tự phục hồi
3. **TTS WebService Stutter** — Khoảng trống 250-900ms giữa các đoạn TTS khi đọc web

## Tech Stack
- Kotlin + Jetpack Compose (Android)
- TypeScript + Vue 3 (Web Frontend)
- Ktor (Embedded Web Server)

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Local AI URI Fix | ✅ Complete | 100% |
| 02 | Cloudflare Tunnel Auto-Restart | ✅ Complete | 100% |
| 03 | TTS Double Buffering | ✅ Complete | 100% |
| 04 | Verification & Testing | ✅ Complete | 100% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
