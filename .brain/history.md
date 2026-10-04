# Session History


---
## Session: 2026-08-22 00:23

### Working On
- Feature: WebService and Translation Engine Enhancement
- Phase: Phase 09 Complete

### Notes
- Completed Phases 01-09: UI translation, single-source dropdown, search translation, TTS voice/speed selection, per-provider cache isolation, memory API and dashboard, AI cache hierarchy, story memory per-book with series toggle, AI rewrite convert prompt templates and tests passed

---
## Session: 2026-08-25 01:28

### Working On
- Feature: WebService Book Export & Web UI Fixes
- Phase: Completed & Installed

### Notes
- Hoan thien chuc nang Export sach tren WebService chuan Native (EPUB3, EPUB2, PDF, TXT, HTML, CBZ, nguon dich, pham vi chuong, toi uu anh). Sua loi menu mobile, sua loi lap header Translation Dashboard, sua loi anh nen WebService. Da build Release APK va cai dat len Huawei Pura 70 Pro.

---
## Session: 2026-08-25 23:49

### Working On
- Feature: NVIDIA NIM API & OpenCode Free Provider Full Fix
- Phase: Execution / Release Verified

### Notes
- Khắc phục lỗi 404 connection test của NVIDIA NIM do chọn nhầm model non-chat/404 và thiếu RFC 7807 problem details parser; Mở rộng và khôi phục toàn bộ danh sách 8 free models của OpenCode Zen API (big-pickle, nemotron-3.5-lightning-free, hy3-free, x-preview-f-free, laguna-s-2.1-free, nemotron-3-ultra-free, deepseek-v4-flash-free, muse-spark-1.2-contributor-free); Cập nhật presets và catalog; Passed 1089/1089 unit tests; Build Release APK và cài đặt thành công lên Huawei Pura 70 Pro.

---
## Session: 2026-09-05 00:06

### Working On
- Feature: AI Web Providers & WebView Auth Resolution
- Phase: Execution / Release Verified

---
## Session: 2026-09-12 22:40

### Working On
- Feature: Google Drive Public Folder Pagination & EPUB Download Corruption Fix
- Phase: Phase 36

### Notes
- Fixed 50 items catalog limit via GDrive API v3 and pageToken loop (85 folders); eliminated 434-byte EPUB truncation by using exact byte sizes and dynamic stream expansion; synchronized WebDAV port in StateFlow.

---
## Session: 2026-10-04 08:12

### Working On
- Feature: QuickTranslation Optimization & LDPlayer Verification
- Phase: phase-07-polish-verification

---
## Session: 2026-10-04 15:13

### Working On
- Feature: QT Lexical Planning Acceleration & Uncached Content Latency Verification
- Phase: Phase 58 Complete

### Notes
- Triển khai first-char branching cho `bestStructuredMatch` loại bỏ ~28,000 regex matches thừa trên mỗi chương (struct giảm từ 280ms xuống ~70ms).
- Indexing O(1) hậu tố địa danh `PLACE_HIERARCHY_SUFFIXES_BY_FIRST_CHAR` và guard lân cận 16 ký tự cho các trợ từ ('的', '们') trong grammar matching (gram giảm từ 887ms xuống ~350ms).
- Pruning prefix `nextLiteralAfterFirstSlot` trong `SourceTemplate` và `leadingSlotTemplatesAt` loại bỏ >95% recursion templates sai (tmpl giảm từ 939ms xuống ~420ms).
- Toàn bộ 63 unit tests `QuickTranslationRepositoryTest` passed 100%.
- Kiểm tra thực tế trên LDPlayer emulator-5554 trên NỘI DUNG CHƯA CÓ CACHE (Cache-Miss): Dịch chương mới chưa cache giảm từ 3.6s xuống 1.68s - 1.95s (tổng cộng giảm 3.86x so với mốc gốc 7.5s); Dịch UI văn bản chưa cache giảm xuống 500ms - 760ms; Lật trang cache-hit duy trì 22ms - 33ms mượt mà 60 FPS, hoàn toàn hết giật khựng.
