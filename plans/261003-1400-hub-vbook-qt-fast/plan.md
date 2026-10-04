# Plan: Source Hub Installed Detection, Batch Install, VBook No Auto-Select, Instant QT Engine & API Key Cloud Sync

Created: 2026-10-03T14:00:00+07:00
Updated: 2026-10-03T14:22:00+07:00
Status: 🟡 Pending User Approval

## 1. Overview & Mục Tiêu

Kế hoạch này giải quyết **5 yêu cầu quan trọng** nhằm tối ưu hóa trải nghiệm quản lý nguồn truyện, tốc độ đọc sách, và tính liền mạch đa thiết bị:

1. **Đánh dấu nguồn đã cài trong Kho Nguồn**: Kiểm tra và hiển thị huy hiệu trực quan "✓ Đã cài" cho các nguồn truyện trong Kho Nguồn (`BookSourceHub`) đã có trong cơ sở dữ liệu Room (`BookSourceDao`).
2. **Chọn nhiều & Cài đặt hàng loạt trong Kho Nguồn**: Cho phép chọn checkbox từng nguồn, nút "Chọn tất cả" / "Bỏ chọn" và thanh công cụ "Cài đặt hàng loạt (X)" để nạp nhiều nguồn cùng lúc thay vì bấm từng nguồn lẻ tẻ.
3. **Sửa VBook Importer không tự tích nguồn**: Khi xem trước (preview) danh sách plugin Vbook, không tự động tích chọn toàn bộ plugin, chỉ nạp những nguồn mà người dùng đã chủ động tích.
4. **Tối ưu tốc độ Quick Translator về thuật toán QT gốc & vBook (Instant Output)**:
   - Sử dụng đúng và đầy đủ **5 bộ quy tắc cốt lõi** chuẩn Quick Translator gốc: **LuatNhan, Name, Vietphrase, Pronoun, PhienAm**.
   - Loại bỏ các cỗ máy phân tích ngữ pháp nặng nề bằng thuật toán **Greedy Longest Match First (Maximum Matching)** trên cây tiền tố (Trie).
   - Tốc độ trả output đạt mức **ngay lập tức (< 2ms / chương)**.
5. **Đồng bộ API Key & Tự Động Kích Hoạt Provider**: Lưu API key các AI Provider vào tài khoản Supabase (mã hóa zero-knowledge), đồng bộ giữa các thiết bị, tự động kích hoạt provider tương ứng khi đăng nhập trên thiết bị mới.

---

## 2. Phân Tích Kỹ Thuật

### 2.1. Kho Nguồn: Kiểm Tra Nguồn Đã Cài
- `BookSourceHubViewModel` sẽ kết nối luồng `appDb.bookSourceDao.flowAll()`.
- Trích xuất tập `installedUrls` và `installedNames`.
- Khi render card `OnlineBookSourceItem`, kiểm tra nếu trùng `originUrl` hoặc `name` -> đánh dấu `isInstalled = true`.
- Hiển thị badge "✓ Đã cài" và đổi nút icon tải thành biểu tượng trạng thái rõ ràng.

### 2.2. Kho Nguồn: Chọn Nhiều & Cài Hàng Loạt
- Thêm `selectedIds: ImmutableSet<String>` và `isBatchImporting: Boolean` vào `BookSourceHubUiState`.
- Bổ sung Checkbox trên từng item card.
- Thêm thanh công cụ hàng loạt ở cạnh dưới màn hình: đếm số lượng chọn, nút "Chọn tất cả", "Bỏ chọn", nút "Cài đặt (X)".
- Hàm `importSelected()` tải và import tuần tự/song song có kiểm soát, báo cáo tiến độ và hiển thị Toast kết quả.

### 2.3. VBook: Không Tự Động Tích Chọn
- Trong `VbookImportViewModel.kt`:
  Khi `loadPreview()` thành công, thay vì gán `selectedPluginIds = installable.toImmutableSet()`, gán `selectedPluginIds = persistentSetOf()`.
- Người dùng tự tick nguồn mong muốn hoặc chủ động bấm nút "Chọn tất cả" khi cần.

### 2.4. Thuật Toán QT Gốc & vBook: 5 Tầng Quy Tắc Chuẩn (Instant Output)
```
HIỆN TẠI (Rất chậm, giật lag):
  Văn bản -> Jieba Segmenter (Java) -> Aho-Corasick allMatchesByStart -> 
  Quy hoạch động Viterbi DP (bestTranslationPlan) -> Scoring ngữ pháp & đại từ -> 
  PostProcess -> RemapSegments (nhiều vòng lặp indexOf) -> Output (100ms - 2000ms)

MỚI (QT Gốc & vBook - Đủ 5 bộ quy tắc, Ngay lập tức):
  Văn bản -> Quét tuyến tính trái qua phải O(N):
    ├─ Ký tự không phải chữ Hán (Dấu câu, số, chữ cái, xuống dòng): Giữ nguyên 100%
    └─ Chữ Hán: Phân tầng ưu tiên theo chuẩn Quick Translator gốc:
         1. LuatNhan (Luật nhân ngữ pháp / templates: {0}的{1}, {0}了, {0}们...)
         2. Name (Project Name > Bundled Name: tên riêng nhân vật, địa danh, bang phái)
         3. Pronoun (Đại từ nhân xưng: ta, ngươi, hắn, nàng, bọn họ,...)
         4. Vietphrase (Cụm từ tiếng Trung sang tiếng Việt, longest match, lấy nghĩa đầu)
         5. PhienAm (Âm Hán-Việt từng chữ đơn khi không khớp cụm từ nào)
  DisplaySourceSegment sinh trực tiếp O(1) ngay trong vòng quét 1 lượt duy nhất.
  Tốc độ: < 2ms / chương truyện (~10.000 từ).
```

### 2.5. Đồng Bộ API Key & Tự Động Kích Hoạt Provider
- **Hiện trạng**: API key được lưu cục bộ trong `AndroidAiSecretStore` (AES-GCM + Android Keystore). Room DB chỉ lưu `secretRef` (tham chiếu opaque). Key bị mất khi đổi thiết bị/cài lại app.
- **Giải pháp**: Mã hóa toàn bộ API key bundle bằng AES-256-GCM client-side (HKDF-SHA256 từ Supabase access_token hash), upload lên Supabase Storage `user-secrets/{userId}/api-keys.enc`.
- **Khi đăng nhập thiết bị mới**: Pull encrypted bundle → giải mã → tạo/cập nhật `AiProviderProfile` → lưu key vào `AndroidAiSecretStore` → auto-enable provider.
- **Zero-knowledge**: Supabase KHÔNG BAO GIỜ nhìn thấy API key plaintext.
- **Auto-sync**: Tự động push sau mỗi thay đổi key (debounce 5s), tự động pull khi đăng nhập lần đầu.

---

## 3. Các Phases Thực Thi

| Phase | Tên Phase | Nội dung chính | Trạng thái | Tasks |
|---|---|---|---|---|
| **01** | **VBook Selection Fix** | Bỏ auto-check trong `VbookImportViewModel`, chỉ cài các nguồn người dùng tick | ⬜ Pending | 2 |
| **02** | **Hub Installed Detection** | Kết nối `BookSourceDao`, nhận diện và gắn nhãn "Đã cài" trên giao diện Kho Nguồn | ⬜ Pending | 4 |
| **03** | **Hub Batch Install** | Thêm chọn nhiều nguồn, Checkbox, thanh công cụ và nạp hàng loạt trong Kho Nguồn | ⬜ Pending | 5 |
| **04** | **Instant QT Engine (5 Rules)** | Tái cấu trúc thuật toán QT chuẩn 5 bộ quy tắc: LuatNhan, Name, Vietphrase, Pronoun, PhienAm | ⬜ Pending | 6 |
| **05** | **Testing & Verification** | Unit tests cho Hub, Vbook và benchmark đo lường tốc độ QT | ⬜ Pending | 4 |
| **06** | **API Key Cloud Sync** | Đồng bộ API key lên Supabase, mã hóa client-side, tự động kích hoạt provider khi đăng nhập mới | ⬜ Pending | 10 |
| **07** | **Polish & Device Verification** | HTML markup bypass, FilterChip "Chưa cài", bundle tab, R8 keep rules, kiểm thử thiết bị thực | ⬜ Pending | 7 |

**Tổng cộng:** 38 tasks | Ước tính: 2-3 sessions

---

## 4. Danh Sách Tài Liệu Chi Tiết

- [phase-01-vbook-selection-fix.md](./phase-01-vbook-selection-fix.md)
- [phase-02-hub-installed-detection.md](./phase-02-hub-installed-detection.md)
- [phase-03-hub-batch-install.md](./phase-03-hub-batch-install.md)
- [phase-04-qt-instant-engine.md](./phase-04-qt-instant-engine.md)
- [phase-05-testing-verification.md](./phase-05-testing-verification.md)
- [phase-06-api-key-cloud-sync.md](./phase-06-api-key-cloud-sync.md)
- [phase-07-polish-verification.md](./phase-07-polish-verification.md)
