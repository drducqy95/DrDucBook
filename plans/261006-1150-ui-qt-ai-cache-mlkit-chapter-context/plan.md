# Plan: Khóa Dịch UI với QT, Ưu Tiên Cache AI, Hoàn Thiện Dịch ML Kit (Từ Điển & Xưng Hô Trung Tính) & Sao Chép Ngữ Cảnh Chương

Created: 2026-10-06T11:50:00+07:00
Status: 🟡 In Progress (Planning Complete - Waiting for User Approval)

## 1. Overview (Tổng Quan Tính Năng)

Kế hoạch này giải quyết trọn vẹn 4 yêu cầu cốt lõi nhằm hoàn thiện hệ sinh thái dịch thuật, quản lý từ điển và xuất bản ebook trong DrDucBook:

1. **Khóa dịch UI với QT & Tự động ghi đè / Ưu tiên Cache AI:**
   - Mặc định dịch động UI (Bookshelf, BookInfo, TOC, Reader TopBar, Metadata) bị **khóa vào bộ dịch Quick Translator (QT) offline**, đảm bảo tốc độ tức thì, không phát sinh chi phí API và không phụ thuộc mạng.
   - Khi người dùng đã thực hiện dịch AI (qua AI Cloud hoặc Local AI GGUF), cache bản dịch của AI (bao gồm nội dung chương, tiêu đề chương được chuẩn hóa ngữ pháp / Title Case, và metadata tác phẩm) sẽ được **lưu đè vào hệ thống UI Cache**.
   - Tại mọi màn hình UI (Mục lục TOC, chi tiết sách, tiêu đề trình đọc): Nếu đã có cache AI thì **hiển thị bản AI thay thế cho QT**.
   - Khi xuất ebook (EPUB/TXT/HTML/PDF): Sử dụng dữ liệu tiêu đề chương và metadata đã dịch bằng AI (hoặc fallback QT) làm dữ liệu xuất bản chính thức thay vì giữ nguyên raw chữ Hán.

2. **Hoàn thiện Dịch Chương Bằng Google ML Kit:**
   - Tự động áp dụng **Từ điển Bộ nhớ dịch (Story Translation Memory)** và **Từ điển QT riêng của dự án (Project QuickDictionary & Book Terms)** vào bản dịch của ML Kit.
   - Bổ sung bộ xử lý **sửa đổi xưng hô sang trung tính** (`neutralizeMlKitPronouns`): loại bỏ xưng hô máy móc thô ráp (anh ấy -> hắn, cô ấy -> nàng, họ -> bọn họ...), tuân theo chế độ xưng hô được chọn cho truyện (`QuickTranslationPronounMode`).

3. **Đề xuất bản dịch bằng Google ML Kit đa ngôn ngữ mục tiêu cho cụm từ ngoại lai:**
   - Bổ sung bộ chọn đề xuất bản dịch bằng ML Kit với các cặp ngôn ngữ:
     - **Trung - Nhật (`zh -> ja`)**: Ví dụ: 漩涡鸣人 -> Uzumaki Naruto, 宇智波佐助 -> Uchiha Sasuke, 白眼 -> Byakugan.
     - **Trung - Anh (`zh -> en`)**: Ví dụ: 凯撒 -> Caesar, 亚历山大 -> Alexander.
     - **Trung - Hàn (`zh -> ko`)**: Ví dụ: 陈然竣 -> Choi Yeon-jun, 金泰亨 -> Kim Tae-hyung.
     - **Trung - Việt (`zh -> vi`)**: Dịch máy offline sang tiếng Việt.
   - Hệ thống tự động sử dụng model ML Kit đã tải sẵn trên máy theo cặp ngôn ngữ được chọn.
   - Tích hợp tính năng đề xuất này vào cả 3 vị trí:
     - **Chỉnh sửa từ điển** (`QuickDictionaryManagerScreen` - `DictionaryEditorSheet`).
     - **Chỉnh sửa bộ nhớ dịch** (`BookStoryMemoryScreen` - `StoryMemoryEditorDialog` & `CharacterDossierSheet`).
     - **Thêm từ điển QT** (`QuickDictionaryForm` trong Reader `QuickDictionarySheet` & `QuickDictionaryEditorSheet`).

4. **Sao chép toàn bộ ngữ cảnh chương (Copy Chapter Context):**
   - Bổ sung tính năng cho phép sao chép toàn bộ văn bản của chương hiện tại vào bộ nhớ tạm (Clipboard).
   - Cho phép người dùng tùy chọn nguồn văn bản:
     - **Bản gốc (Raw)**: Nội dung tiếng Trung gốc đã làm sạch.
     - **Các bản dịch theo từng Provider đã có cache**: AI Cloud (Gemini/OpenAI...), Local AI, QT, ML Kit, NMT, Google Translate.
     - **Bản viết lại (Rewrite)** nếu có cache.
   - Hiển thị danh sách trực quan qua BottomSheet kèm số lượng ký tự, trạng thái dịch và nút 1 chạm để sao chép.

---

## 2. Tech Stack & Architectural Context

- **UI Framework:** Jetpack Compose Material 3 (MVI/UDF Contract pattern).
- **Domain & DI:** Koin dependency injection, Clean Architecture (`TranslateDynamicUiTextUseCase`, `TranslateChapterUseCase`, `TranslationManager`, `ExportBookService`).
- **Translation Engine:** Quick Translator (QT), Google ML Kit (`MlKitTranslationGateway`), AI Cloud Router, Local AI GGUF.
- **Cache Persistence:** `TranslationCacheGateway`, Room SQLite (`AppDatabase` v105), JSON disk cache.

---

## 3. Phases Breakdown

| Phase | Tên Phase | Trạng thái | Tiến độ |
| :--- | :--- | :--- | :--- |
| **Phase 01** | `phase-01-ui-qt-ai-cache-integration.md` | ✅ Complete | 100% |
| **Phase 02** | `phase-02-ebook-export-translated-data.md` | ✅ Complete | 100% |
| **Phase 03** | `phase-03-mlkit-chapter-translation-pipeline.md` | ✅ Complete | 100% |
| **Phase 04** | `phase-04-mlkit-multilingual-suggestions.md` | 🟡 In Progress | 0% |
| **Phase 05** | `phase-05-chapter-context-copy-feature.md` | ⬜ Pending Approval | 0% |
| **Phase 06** | `phase-06-verification-and-testing.md` | ⬜ Pending Approval | 0% |

---

## 4. Quick Commands

- Duyệt và bắt đầu Phase 1: `/code phase-01`
- Kiểm tra tiến độ: `/next`
- Lưu bộ nhớ Trinity: `/save-brain`
