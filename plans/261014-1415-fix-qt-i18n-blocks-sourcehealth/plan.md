# Plan: Tối ưu QT (Chất lượng 6 Chương, Tốc độ, Mapping), Sửa Lỗi Việt Hóa Mojibake, Thu Gọn Block Tải Gói & Đồng Nhất Sức Khỏe Nguồn

Created: 2026-10-14T14:15:00+07:00
Updated: 2026-10-14T15:00:00+07:00
Milestone: P80
Status: 🟡 In Progress

## Overview

Kế hoạch giải quyết toàn diện 4 vấn đề được người dùng phản ánh, hoàn thiện dựa trên phân tích thực tế 6 chương đầu (`数据修仙，我在五行观证长生` - Chương 1 đến 6 trên LDPlayer):
1. **QuickTranslator Core**:
   - Khắc phục dứt điểm 424 lỗi `đích` và 54 lỗi `liễu` xuất hiện trong 6 chương thực tế.
   - Sửa lỗi cụm số `四周` $\to$ `4 tuần` và các lỗi từ vựng ngô nghê (`放空` $\to$ `chạy xe không`, `一块` $\to$ `1 tệ`).
   - Khắc phục các lỗi gãy từ ngữ Tiên hiệp & tên riêng (`庶务殿` $\to$ `Sở Vụ Điện`, `木灵内观经` $\to$ `Mộc Linh Nội Quan Kinh`, `万叶飞花` $\to$ `Vạn Diệp Phi Hoa`, `小聚气丹` $\to$ `Tiểu Tụ Khí Đan`, `许有德` $\to$ `Hứa Hữu Đức`, `陈师兄` $\to$ `Trần sư huynh`, `月儿` $\to$ `vầng trăng`).
   - Đảo ngữ chính xác các cấu trúc định ngữ, trạng ngữ vị trí (`ở phía sau`, `ở bên trong`, `ở bên dưới`) và cấu trúc so sánh nhất (`最...`).
   - Tối ưu hóa thuật toán xuất output QT nhanh hơn 3-5 lần bằng cách chuyển đổi Trie match từ eager `allMatchesByStart` sang lazy on-demand FMM.
   - Tăng tốc mapping "Thêm từ điển" trong Reader xuống dưới 50ms (giới hạn cửa sổ ngữ cảnh 500 ký tự, query Trie trực tiếp).
2. **Sửa lỗi hiển thị Việt hóa (Mojibake)**:
   - Khắc phục triệt để chuỗi `HachimiMT-60-QT zhât'vi (QT/HĂ¡n Viá»‡t)` do lỗi bảng mã ANSI trong manifest và artifact.
   - Thêm bộ giải mã an toàn `sanitizeModelDisplayName()` tại `HachimiOnnxModelRegistry`.
3. **Chuyển Cài đặt TTS ra ngoài UI chính & Thu gọn Card TTS (Piper, Valtec, ZeroTTS) / NMT**:
   - Bổ sung thẻ mục **"Cài đặt TTS"** trực tiếp trên màn hình Cài đặt chính (`ConfigNavScreen`), kết nối thẳng tới `MainRouteSettingsTtsModels` và khu vực tải model TTS.
   - Chuẩn hóa toàn bộ các gói tải model TTS (Piper, Valtec, ZeroTTS) và NMT thành Card hiện đại theo chuẩn mockup (`media_1791965348043_7a0e4df7.png`):
     + Leading badge mã ngôn ngữ (`[ VI ]`, `[ ZH ]`).
     + Title to rõ, tag pill engine thực tế (`[Piper]`, `[Valtec]`, `[ZeroTTS]`, `[ONNX]`). Không dùng tag ví dụ `VMODEL`.
     + Subtitle ngắn gọn gạch chấm: `${Ngôn ngữ} • ${Thể loại/Đặc tính} • ${Dung lượng}`.
     + Nút tải hình tròn (`FilledTonalIconButton`).
   - Loại bỏ triệt để mã băm SHA-256 và tên file zip kỹ thuật trên toàn bộ các block Asset Delivery, TTS Model Manager và Cài đặt dịch.
4. **Đồng nhất tính năng Sức khỏe nguồn**:
   - Khắc phục hiện tượng báo cáo và thực tế không đồng nhất (nguồn vẫn đọc tốt nhưng báo lỗi `HTTP_ERROR: VbookPluginException: test`).
   - Sửa từ khóa tìm kiếm hardcode `"test"` sang tên sách thực tế từ stage HOME hoặc theo ngôn ngữ nguồn.
   - Phân loại chính xác lỗi tìm kiếm rỗng thành `CONTENT_EMPTY` / `DEGRADED`, không dán nhãn `HTTP_ERROR`.
   - Sửa lỗi kế thừa nhầm `messageRedacted` khiến các stage PASSED vẫn bị gán lỗi FAILED.

## Tech Stack & Affected Modules
- **UI & Presentation**: Jetpack Compose, Material 3 Expressive, `AssetDeliveryScreen.kt`, `TranslationConfigScreen.kt`, `TtsModelManagerScreen.kt`, `SourceHealthScreen.kt`, `ReadBookViewModel.kt`.
- **Core Engine & Translation**: Kotlin Coroutines, Room DB, Trie FMM, `QuickTranslationRepository.kt`, `QuickDictionarySelectionResolver.kt`, `HachimiOnnxModelRegistry.kt`.
- **Source Health Diagnostics**: WorkManager, `SourceCheckEngine.kt`, `SourceCheckRepository.kt`, `SourceCheckClassifier.kt`, `BookSourceHealthProbeRepository.kt`, `VbookSourceHealthProbeRepository.kt`.

## Phases

| Phase | Name | Status | Description |
|---|---|---|---|
| 01 | QT Quality (6 Chapters), Output Speed & Fast Mapping | ⬜ Pending | Khử 424 'đích' & 54 'liễu', sửa '四周' & từ vựng tiên hiệp, on-demand Trie traversal, localized mapping window (<50ms) |
| 02 | Fix Vietnamese Mojibake & Manifest Sanitizer | ⬜ Pending | Sửa `model_manifest.json`, `hf-artifacts-manifest.json`, bổ sung regex Mojibake sanitizer tại runtime |
| 03 | Main Settings TTS Entry & Compact Cards (Piper/Valtec/ZeroTTS/NMT) | ⬜ Pending | Bổ sung thẻ Cài đặt TTS ra ngoài UI chính (ConfigNavScreen), chuẩn hóa card tải TTS (Piper/Valtec/ZeroTTS) & NMT theo mockup, xóa SHA/zip |
| 04 | Source Health Diagnostic Accuracy & Consistency | ⬜ Pending | Sửa probe search keyword, fix classifier `CONTENT_EMPTY` vs `HTTP_ERROR`, fix stage message leak |
| 05 | Verification, Benchmarks & LDPlayer Live Test | ⬜ Pending | Chạy Unit Tests 6 chương thực tế, benchmark tốc độ QT/Mapping, biên dịch Kotlin, build APK và test trực tiếp trên LDPlayer |

## Quick Commands
- Bắt đầu Phase 1: `/code phase-01`
- Kiểm tra tiến độ: `/next`
- Lưu bộ nhớ: `/save-brain`
