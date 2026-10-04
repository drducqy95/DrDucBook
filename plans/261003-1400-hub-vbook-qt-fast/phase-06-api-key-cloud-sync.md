# Phase 06: Đồng Bộ API Key Lên Cloud & Tự Động Kích Hoạt Provider

Status: ⬜ Pending
Dependencies: None (Độc lập, có thể triển khai song song với Phases 01-05)

## 1. Mục tiêu

Cho phép người dùng **lưu API key của các AI Provider vào tài khoản Supabase** (đã đăng nhập), đồng bộ giữa các thiết bị, và **tự động kích hoạt (enable) các provider tương ứng** khi đăng nhập trên thiết bị mới hoặc cài lại app.

### Luồng hoạt động:
```
Thiết bị A (đã cấu hình provider):
  User nhập API key cho Gemini, Claude, OpenAI → Lưu local (AndroidAiSecretStore) 
  → Bấm "Đồng bộ API key" hoặc tự động khi thay đổi 
  → Mã hóa client-side (AES-GCM với key derivation từ Supabase user_id + passphrase) 
  → Upload lên Supabase Storage bucket hoặc Postgrest table

Thiết bị B (mới đăng nhập):
  User đăng nhập Supabase → Pull encrypted API key bundle 
  → Giải mã client-side → Tạo AiProviderProfile + lưu vào AndroidAiSecretStore 
  → Auto-enable các provider có API key hợp lệ
  → Sẵn sàng sử dụng ngay
```

---

## 2. Phân Tích Kỹ Thuật

### 2.1. Mô Hình Dữ Liệu: Encrypted API Key Bundle

```kotlin
/** Cấu trúc JSON trước khi mã hóa */
@Serializable
data class ApiKeyBundle(
    val version: Int = 1,
    val updatedAt: String, // ISO-8601
    val providers: List<ApiKeyEntry>
)

@Serializable
data class ApiKeyEntry(
    val providerId: String,       // e.g. "openai", "anthropic", "gemini"
    val providerName: String,     // Display name
    val protocol: String,         // "openai_chat", "gemini", "anthropic"
    val baseUrl: String,          // API base URL
    val apiKey: String,           // Plaintext API key (encrypted at bundle level)
    val authType: String,         // "bearer", "header", "none"
    val modelsUrl: String? = null,
    val customHeadersJson: String? = null,
    val chatPath: String? = null,
    val enabled: Boolean = true
)
```

### 2.2. Mã Hóa Client-Side (Zero-Knowledge)

**Supabase KHÔNG BAO GIỜ nhìn thấy API key plaintext:**

1. **Key Derivation**: `HKDF-SHA256(masterKey = userPassword || supabaseUserId, salt = random 32 bytes, info = "drducbook-api-key-sync-v1")`
2. **Encryption**: `AES-256-GCM(derivedKey, nonce = random 12 bytes, plaintext = ApiKeyBundle.toJson())`
3. **Payload format**: `version(1) || salt(32) || nonce(12) || ciphertext || tag(16)`
4. **Storage**: Upload payload dưới dạng binary blob vào Supabase Storage bucket `user-secrets/{userId}/api-keys.enc`

**Tùy chọn passphrase:**
- Mặc định: Dùng Supabase `access_token` hash làm master key (zero-password, tiện lợi)
- Nâng cao: Cho phép user đặt passphrase riêng (an toàn hơn nhưng phải nhớ)

### 2.3. Luồng Đồng Bộ

#### Upload (Push)
1. Thu thập tất cả `AiProviderProfile` có `apiKey` không rỗng hoặc `secretRef` hợp lệ từ Room DB.
2. Resolve plaintext từ `AndroidAiSecretStore.get(secretRef)`.
3. Build `ApiKeyBundle` → Serialize JSON → Encrypt → Upload to Supabase Storage.
4. Cập nhật timestamp `lastSyncedAt` vào SharedPreferences.

#### Download (Pull) — Khi Đăng Nhập Thiết Bị Mới
1. Kiểm tra Supabase Storage có file `api-keys.enc` không.
2. Download → Decrypt → Parse `ApiKeyBundle`.
3. Với mỗi `ApiKeyEntry`:
   - Kiểm tra `AiProviderProfile` đã tồn tại chưa (theo `providerId`).
   - Nếu **chưa có**: Tạo mới `AiProviderProfile` + lưu apiKey vào `AndroidAiSecretStore`.
   - Nếu **đã có** nhưng chưa có key: Cập nhật apiKey.
   - Nếu **đã có** và đã có key: Giữ nguyên (ưu tiên local, hoặc hỏi user chọn).
4. Auto-enable: `aiProfileDao.setEnabled(providerId, true)` cho các provider vừa restore.

#### Auto-Sync Trigger
- **Sau khi save/update provider**: Nếu đã đăng nhập → tự động push (debounce 5s).
- **Sau khi đăng nhập**: Tự động pull nếu local chưa có provider nào.
- **Manual**: Nút "Đồng bộ API key" trong Settings > AI Config.

### 2.4. UI/UX

#### Trong AiConfigScreen (Settings > Cấu hình AI):
- Section "Đồng bộ API Key" hiển thị:
  - Trạng thái: "Đã đồng bộ lúc [timestamp]" hoặc "Chưa đồng bộ"
  - Nút "Đồng bộ lên cloud" (push)
  - Nút "Khôi phục từ cloud" (pull)
  - Toggle "Tự động đồng bộ khi thay đổi"
- Khi đồng bộ thành công: Snackbar "Đã đồng bộ X provider"

#### Trong AccountScreen (Sau khi đăng nhập):
- Thêm card "API Key Sync" hiển thị số lượng provider đã đồng bộ.
- Nút quick action "Khôi phục cài đặt AI" chỉ hiện khi local chưa có provider.

### 2.5. Bảo Mật

| Lớp | Cơ chế |
|-----|--------|
| Transport | HTTPS/TLS (Supabase mặc định) |
| Storage | AES-256-GCM client-side encryption |
| Key Derivation | HKDF-SHA256 từ Supabase access_token hash |
| Access Control | Supabase RLS: Chỉ user sở hữu mới đọc/ghi bucket path |
| Device | API key plaintext LUÔN encrypt bằng Android Keystore trên local |

---

## 3. Implementation Steps

1. [ ] **Tạo `ApiKeySyncModels.kt`** (domain/model/):
   - `ApiKeyBundle`, `ApiKeyEntry` data classes.
   - `ApiKeySyncStatus` sealed interface: `NotSynced`, `Syncing`, `Synced(timestamp)`, `Error(message)`.

2. [ ] **Tạo `ApiKeySyncRepository.kt`** (data/repository/):
   - `fun collectProviderKeys(): ApiKeyBundle` — Thu thập tất cả provider + resolve secret.
   - `suspend fun pushToCloud(session: AccountSession): Result<Unit>` — Encrypt + upload.
   - `suspend fun pullFromCloud(session: AccountSession): Result<ApiKeyBundle>` — Download + decrypt.
   - `suspend fun restoreProviders(bundle: ApiKeyBundle)` — Tạo/cập nhật AiProviderProfile + AndroidAiSecretStore.
   - `suspend fun autoActivateProviders(providerIds: List<String>)` — Enable các provider đã restore.

3. [ ] **Tạo `ApiKeySyncEncryption.kt`** (data/repository/):
   - `fun encrypt(plaintext: ByteArray, masterKey: ByteArray): ByteArray`
   - `fun decrypt(payload: ByteArray, masterKey: ByteArray): ByteArray`
   - `fun deriveKey(accessTokenHash: String, salt: ByteArray): ByteArray` — HKDF-SHA256.

4. [ ] **Cập nhật `AiConfigContract.kt`**:
   - Thêm `syncStatus: ApiKeySyncStatus` vào `AiConfigUiState`.
   - Thêm `SyncApiKeys`, `RestoreApiKeys`, `ToggleAutoSync` vào `AiConfigIntent`.

5. [ ] **Cập nhật `AiConfigViewModel.kt`**:
   - Handle sync intents, call `ApiKeySyncRepository`.
   - Observe Supabase auth state để trigger auto-restore khi login.

6. [ ] **Cập nhật `AiConfigScreen.kt`**:
   - Thêm API Key Sync section UI (trạng thái, nút push/pull, toggle auto-sync).

7. [ ] **Cập nhật `AccountScreen.kt`** (optional):
   - Thêm card hiển thị API key sync status và quick restore action.

8. [ ] **Đăng ký DI trong `appModule.kt`**:
   - `singleOf(::ApiKeySyncRepository)`.

9. [ ] **String resources** (values/strings.xml & values-vi/strings.xml):
   - `api_key_sync_title`, `api_key_sync_push`, `api_key_sync_pull`, `api_key_sync_auto`, `api_key_sync_success`, `api_key_sync_not_logged_in`.

10. [ ] **Unit tests**:
    - `ApiKeySyncEncryptionTest`: Round-trip encrypt/decrypt.
    - `ApiKeySyncRepositoryTest`: Mock Supabase, verify push/pull/restore flow.

## Files to Create
- `app/src/main/java/io/legado/app/domain/model/ApiKeySyncModels.kt`
- `app/src/main/java/io/legado/app/data/repository/ApiKeySyncRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/ApiKeySyncEncryption.kt`

## Files to Modify
- `app/src/main/java/io/legado/app/ui/ai/config/AiConfigContract.kt`
- `app/src/main/java/io/legado/app/ui/ai/config/AiConfigViewModel.kt`
- `app/src/main/java/io/legado/app/ui/ai/config/AiConfigScreen.kt`
- `app/src/main/java/io/legado/app/ui/account/AccountScreen.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`

---
Next Phase: [phase-07-polish-verification.md](./phase-07-polish-verification.md)
