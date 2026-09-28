# Plan: Go/Gomobile Local WebDAV + NMT HachimiMT-60-QT (P34)

Created: 2026-09-12T12:10:00+07:00
Status: 🟡 In Progress

## Overview

Milestone P34 bao gồm hai hệ thống tính năng song song cho DrDucBook:
1. **Track A (WebDAV - Phases 01-08)**: Nhúng Go WebDAV server (`golang.org/x/net/webdav`) qua Gomobile AAR bind để proxy Google Drive (folder-scoped OAuth qua `drive.file`) và public sharing links (Google Drive, OneDrive, Dropbox, HTTP directory) về `127.0.0.1`. Bảo vệ bằng session secret (HTTP Basic Auth). Tích hợp vào **Khám phá (Explore tab)** với `DriveLibrarySection`, `ManagedSourceRegistry` (hỗ trợ nhiều source đồng thời), và tái sử dụng catalog/download pipeline của `RemoteBookWebDav`.
2. **Track B (NMT - Phases 09-14)**: Export và convert MarianMT model `ngocdang83/HachimiMT-60-QT` sang định dạng ONNX INT8 tương thích với `HachimiOnnxTranslator` (2 decoder layers, 8 heads, 64 dim, DECODER_START=1, EOS=2, `noRepeatNgramSize=0`), đóng gói CC-BY-4.0 lên HuggingFace. Tái cấu trúc runtime sang multi-model với model selector UI tại `TranslationConfigScreen` và cache isolation.

## Tech Stack
- **Native/Go**: Go ≥1.22, Gomobile (`golang.org/x/mobile/cmd/gomobile`), `golang.org/x/net/webdav`, `google.golang.org/api/drive/v3`
- **Android Runtime**: Kotlin, Coroutines, Jetpack Compose Material 3, Navigation 3, Koin DI, Room DB (v105), DataStore Preferences
- **Architecture**: Clean Architecture + UDF/MVI, Foreground Service (`dataSync`), OkHttp WebDAV client
- **AI/NMT**: PyTorch, Transformers, Optimum ONNX, ONNX Runtime Android (CPU/NNAPI), ONNX Runtime Extensions (`OrtxPackage`), SentencePiece

## Phases

| Phase | Name | Track | Status | Progress |
|-------|------|-------|--------|----------|
| 01 | Spike & Decision Gate | WebDAV | ⬜ Pending | 0% |
| 02 | Go Drive Filesystem & Multi-Provider | WebDAV | ⬜ Pending | 0% |
| 03 | WebDAV Server & Session Auth | WebDAV | ⬜ Pending | 0% |
| 04 | Gomobile Bridge & AAR Pipeline | WebDAV | ⬜ Pending | 0% |
| 05 | Android Service & Google Auth | WebDAV | ⬜ Pending | 0% |
| 06 | Explore UI & Managed Source Registry | WebDAV | ⬜ Pending | 0% |
| 07 | WebDAV Test Matrix & Verification | WebDAV | ⬜ Pending | 0% |
| 08 | CI/CD, Release & Developer Docs | WebDAV | ⬜ Pending | 0% |
| 09 | NMT Contract Audit & Golden Test Set | NMT | ⬜ Pending | 0% |
| 10 | PyTorch to ONNX Export Pipeline | NMT | ⬜ Pending | 0% |
| 11 | INT8 Quantization, Package & HuggingFace | NMT | ⬜ Pending | 0% |
| 12 | Multi-Model Runtime & Cache Isolation | NMT | ⬜ Pending | 0% |
| 13 | Model Selector UI & Asset Delivery | NMT | ⬜ Pending | 0% |
| 14 | Device Validation & Benchmark | NMT | ⬜ Pending | 0% |

## Quick Commands
- Start Phase 01: `/code phase-01`
- Start Phase 09 (Track B parallel): `/code phase-09`
- Check progress: `/next`
- Save context: `/save-brain`
