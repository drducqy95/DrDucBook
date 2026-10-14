# Phase 03: Classic QT Algorithm (FMM), Remove QT Beam Search & Guard NMT Beam Search (K)

Status: ⬜ PENDING
Dependencies: Phase 01, Phase 02

## Objective
Chỉ xóa bỏ Beam Search đối với Quick Translator (QT) để đưa QT về thuật toán Forward Maximum Matching (Greedy Longest Match - FMM) nguyên bản tuyến tính $O(N)$ và bảo toàn 1-1 text mapping. **Tuyệt đối bảo lưu nguyên vẹn cơ chế Beam Search cho Neural Machine Translation (NMT - Hachimi/ONNX) cùng khả năng điều chỉnh độ rộng Beam $K$ ($1 \le K \le 8$) trong cài đặt.**

## Tasks
1. [ ] **Đối với Quick Translator (QT):**
   - Xóa bỏ `beamTranslationPlan()` trong `QuickTranslationRepository.kt`.
   - Cài đặt thuật toán FMM tuyến tính:
     * Con trỏ `offset` duyệt từ đầu đến cuối chuỗi.
     * Tra cứu Longest Match trong `Project Terms` $\rightarrow$ `Names` $\rightarrow$ `VietPhrase` $\rightarrow$ `Pronouns` $\rightarrow$ `LuatNhan`.
     * Ký tự đơn Hán tra cứu phiên âm `ChinesePhienAmWords.txt`. Ký tự khác passthrough.
     * Độ trễ đạt $\le 30\text{ ms / chương}$ (nhanh hơn gấp 50 lần).
   - Xóa bỏ thanh trượt `quick_translation_beam_width` trong `TranslationConfigScreen.kt` (vì QT không còn dùng Beam Search).
   - Bảo toàn ánh xạ 1-1 (1-1 Token Mapping): Mỗi `TranslationSegment` lưu chính xác `sourceRange` $\leftrightarrow$ `targetRange`. Chạm từ trên Reader tra từ điển trúng 100%.

2. [ ] **Đối với Neural Machine Translation (NMT):**
   - **Bảo lưu 100% `decodeBeamSearch()`** trong `HachimiOnnxTranslator.kt`.
   - **Bảo lưu thanh trượt `nmt_beam_width`** trong `TranslationConfigScreen.kt`:
     * Cho phép người dùng điều chỉnh $K$ từ 1 (Greedy) đến 8 (Full Beam Search 8).
     * Mặc định $K = 4$.
   - Đảm bảo tham số $K$ (`policy.beamWidth`) được chuyển giao chính xác vào ONNX Session và bộ giải mã của NMT.

## Test Criteria
- Quick Translator chạy hoàn toàn bằng FMM tuyến tính, không còn Beam Search, tốc độ $\le 30\text{ ms/chương}$.
- NMT Hachimi vẫn thực hiện Beam Search hoàn hảo và phản hồi chính xác khi thay đổi giá trị $K$ (từ 1 đến 8) trong cài đặt.
- Ánh xạ từ điển trên Reader của QT khớp 100% từng từ.
