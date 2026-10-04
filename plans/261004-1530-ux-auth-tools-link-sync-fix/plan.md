# Plan: UX Intro Formatting, Supabase Sync Fix, Antigravity 403 Challenge, Chatbot Tool Permissions, External Link Routing & Translation Normalization (P59)

Created: 2026-10-04T15:45:00+07:00
Status: 🟡 Planning Complete

## Overview
Kế hoạch nâng cấp và giải quyết triệt để 6 vấn đề trong hệ thống:
1. **UI Giới thiệu truyện**: Tối ưu hóa hiển thị, phân đoạn, xuống dòng thông minh, tách biệt metadata tags (emoji, thông số kỹ thuật) và văn bản tóm tắt nội dung sách.
2. **Lỗi xác thực Google Antigravity HTTP 403 VALIDATION_REQUIRED**: Nhận diện checkpoint tài khoản Google, trích xuất link xác minh cho người dùng và kích hoạt cơ chế fallback chuyển đổi route dự phòng trong combo.
3. **Cài đặt quyền công cụ Chatbot**: Bổ sung UI cài đặt danh sách công cụ Chatbot với switch bật/tắt quyền; các quyền đã bật sẽ được tự động duyệt mặc định (pre-approved) mà không cần hỏi lại từng lần.
4. **Tự động nhận diện URL ngoại vi theo BookSource**: Khi nhận URL từ ngoài (intent, clipboard, chia sẻ), tự động khớp với các nguồn sách đã cài đặt; mở sách trong BookInfo hoặc mở trang danh mục Khám phá tương ứng.
5. **Sửa lỗi đồng bộ API key lên Supabase**: Khắc phục lỗi HTTP 400 `NoSuchKey` / `Object not found` do sử dụng phương thức `PUT` thay vì `POST` với `x-upsert: true` của Supabase Storage API.
6. **Sửa lỗi dịch truyện & chuẩn hóa URL (52shuku.net)**: Chuẩn hóa scheme/URL không phân biệt hoa thường, làm sạch `wordCount` bị lẫn tóm tắt, khắc phục lỗi dịch tên riêng thành Pinyin thay vì Hán-Việt.

## Tech Stack
- **Frontend / UI**: Jetpack Compose Material 3, MVI/UDF Contract, BottomSheet, Custom Typography
- **Core Domain**: Clean Architecture, AgentPermissionBroker, TranslateChapterUseCase, ExternalUrlResolverUseCase
- **Network & Storage**: Supabase Storage REST API, OkHttp, Room Database v105
- **Testing**: JUnit 4, Robolectric, LDPlayer Device Verification

## Phases

| Phase | Name | Focus | Status | Progress |
|---|---|---|---|---|
| 01 | Book Intro UI & Typography | Tách metadata tag, định dạng xuống dòng và hiển thị đoạn văn tóm tắt truyện | ⬜ Pending | 0% |
| 02 | Supabase API Key Sync Fix | Sửa lỗi `NoSuchKey` trong `ApiKeySyncRepository` & `SupabaseAuthenticatedRestClient` | ⬜ Pending | 0% |
| 03 | Antigravity 403 Validation Handler | Xử lý `VALIDATION_REQUIRED`, cung cấp URL xác minh và auto-fallback route | ⬜ Pending | 0% |
| 04 | Chatbot Tool Pre-approval Settings | Bổ sung màn hình cài đặt quyền công cụ Chatbot, bật/tắt tự động duyệt | ⬜ Pending | 0% |
| 05 | External URL Auto-detection | Nhận diện URL bên ngoài theo BookSource đã cài đặt, mở BookInfo/Khám phá | ⬜ Pending | 0% |
| 06 | Translation URL & Pinyin Normalization | Chuẩn hóa URL, khắc phục tên Pinyin, làm sạch `wordCount` | ⬜ Pending | 0% |
| 07 | Verification & Device Testing | Chạy bộ Unit Test, biên dịch APK debug, xác minh trực quan trên LDPlayer | ⬜ Pending | 0% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
