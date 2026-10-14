# Phase 02: Fix Vietnamese Mojibake & Manifest Sanitizer

Status: ⬜ Pending
Dependencies: None

## Objective
Sửa triệt để lỗi hiển thị chuỗi ký tự bị vỡ font/sai bảng mã tiếng Việt: `HachimiMT-60-QT zhât'vi (QT/HĂ¡n Viá»‡t)` hiển thị trong hộp thoại "Mô hình NMT hoạt động", sửa nguồn gốc trong các file manifest và script đóng gói, đồng thời bổ sung bộ giải mã an toàn (Mojibake Sanitizer) tại runtime trong `HachimiOnnxModelRegistry`.

## Root Cause
1. Trong `tools/nmt-onnx/dist/hachimi_mt60_qt_zh_vi/model_manifest.json`:
   - Dòng 4 lưu: `"displayName": "HachimiMT-60-QT zhâ†’vi (QT/HĂ¡n Viá»‡t)"`.
   - Nguyên nhân: Script PowerShell `package_hachimi_qt.ps1` khi xuất file JSON sử dụng `Set-Content` hoặc `ConvertTo-Json` với bảng mã mặc định của Windows PowerShell (ANSI/Windows-1252), khiến các byte UTF-8 của ký tự mũi tên `→` (`\xe2\x86\x92`) bị đọc thành `â†’` (`zhât'vi`), và các byte UTF-8 của chữ `Hán Việt` (`\xc3\xa1`, `\xe1\xbb\x87`) bị biến thành `HĂ¡n Viá»‡t`.
2. Trong `supabase/artifacts/hf-artifacts-manifest.json`:
   - Dòng 75 lưu: `"displayName": "HachimiMT-60-QT zh-vi ONNX (QT/HĂ¡n Viá»‡t)"`.
3. Trong `HachimiOnnxModelRegistry.kt`:
   - Khi đọc manifest từ thư mục model trên thiết bị (`model_manifest.json`), phương thức `modelDescriptor` lấy trực tiếp `json.optString("displayName")` mà không kiểm tra hay làm sạch Mojibake. Do đó, các thiết bị đã tải hoặc giải nén gói cũ sẽ tiếp tục hiển thị chuỗi lỗi này trên UI dialog "Mô hình NMT hoạt động".

## Implementation Steps
1. [ ] **Sửa file nguồn manifest và script**:
   - `supabase/artifacts/hf-artifacts-manifest.json`: Đổi dòng 75 thành `"displayName": "HachimiMT-60-QT zh-vi ONNX (QT/Hán Việt)"`.
   - `tools/nmt-onnx/dist/hachimi_mt60_qt_zh_vi/model_manifest.json`: Đổi thành `"displayName": "HachimiMT-60-QT zh-vi ONNX (QT/Hán Việt)"`.
   - `tools/nmt-onnx/package_hachimi_qt.ps1`: Sử dụng `[System.IO.File]::WriteAllText($path, $json, [System.Text.UTF8Encoding]::new($false))` để đảm bảo xuất UTF-8 thuần (no BOM), tránh lỗi PowerShell ANSI.
2. [ ] **Bổ sung Mojibake Sanitizer tại `HachimiOnnxModelRegistry.kt`**:
   - Thêm hàm `sanitizeModelDisplayName(raw: String): String` để phát hiện và thay thế tự động các mẫu lỗi phổ biến:
     + `HĂ¡n Viá»‡t` hoặc `HÃ¡n Viá»‡t` -> `Hán Việt`
     + `zhâ†’vi` hoặc `zhât'vi` -> `zh-vi`
     + Chuẩn hóa tên model `HACHIMI_QT_MODEL_ID` luôn trả về `"HachimiMT-60-QT zh-vi (QT/Hán Việt)"`.
3. [ ] **Đồng bộ hóa tên hiển thị trong Dialog & Settings**:
   - Đảm bảo `TranslationConfigScreen` và Dialog "Mô hình NMT hoạt động" hiển thị tên sạch sẽ, chuẩn tiếng Việt có dấu.

## Files to Create/Modify
- `supabase/artifacts/hf-artifacts-manifest.json` - Sửa displayName của gói hachimi-qt.
- `tools/nmt-onnx/dist/hachimi_mt60_qt_zh_vi/model_manifest.json` - Sửa displayName sạch.
- `tools/nmt-onnx/package_hachimi_qt.ps1` - Sửa encoding UTF-8 no BOM.
- `app/src/main/java/io/legado/app/model/translation/HachimiOnnxModelRegistry.kt` - Bổ sung sanitizeModelDisplayName.
- `app/src/test/java/io/legado/app/model/translation/HachimiOnnxModelRegistryTest.kt` - Thêm test case khử Mojibake.

## Test Criteria
- [ ] Hộp thoại "Mô hình NMT hoạt động" trên UI hiển thị chính xác: `HachimiMT-60-QT zh-vi (QT/Hán Việt)`.
- [ ] Không còn bất kỳ ký tự lạ `HĂ¡n Viá»‡t` hay `zhât'vi` nào trong ứng dụng.
- [ ] Unit test cho `HachimiOnnxModelRegistry` pass 100%.
