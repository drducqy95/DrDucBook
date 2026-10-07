# Plan: ML Kit Dynamic Dictionary, Story Memory Integration & Pure Grammar Engine

Created: 2026-10-07T07:20:00+07:00
Status: 🟡 In Progress

## Overview
Hiệu chỉnh chất lượng dịch của Google ML Kit theo nguyên lý kiến trúc bền vững, **hoàn toàn không phụ thuộc hardcode**:
1. Cho phép ML Kit đọc trực tiếp và đầy đủ từ **Từ điển QuickTranslator (QT Trie pack: Names, VietPhrase)**, **Bộ nhớ dịch (BookStoryMemory / CanonicalTranslationMemory)**, và **QuickDictionary Room DB** (Global, Series, Book scopes).
2. Tự động trích xuất thực thể và thuật ngữ xuất hiện trong đoạn văn CJK, thực hiện Pre-translation Masking trước khi gửi tới ML Kit và Post-translation Unmasking & Alignment chuẩn xác.
3. Tinh gọn và chuyên biệt hóa `MlKitGrammarPostProcessor` cùng `MlKitPronounNeutralizer` để **chỉ hiệu chỉnh phần ngữ pháp thuần túy** (đại từ, viết hoa giữa câu, cấu trúc dịch máy tiếng Anh chung) mà không chứa bất kỳ câu văn truyện hardcode nào.

## Tech Stack
- **Language / Runtime:** Kotlin 2.1+, Android SDK 37 (Min SDK 26), Android ICU Regex Engine
- **Dictionaries:** QuickTranslator Trie Pack (Assets: Names, VietPhrase, PhienAm, LuatNhan)
- **Database:** Room DB (AppDatabase v105: QuickDictionaryEntryDao, BookStoryMemoryDao)
- **NMT Engine:** Google ML Kit Translate (Offline on-device CJK -> EN -> VI)
- **Architecture:** Clean Architecture (Domain UseCase -> Gateway -> Repository) + UDF/MVI

## Phases

| Phase | Name | Status | Progress |
|---|---|---|---|
| 01 | QT Trie Dynamic Term Matcher Gateway | ⬜ Pending | 0% |
| 02 | Story Memory & Dynamic QT Pipeline in ML Kit | ⬜ Pending | 0% |
| 03 | Pure Vietnamese Grammar & Syntax Healing Engine | ⬜ Pending | 0% |
| 04 | Automated Testing, APK Build & LDPlayer Live Validation | ⬜ Pending | 0% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
