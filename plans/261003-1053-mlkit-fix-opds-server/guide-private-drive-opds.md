# Hướng Dẫn Sử Dụng Private Drive với OPDS

## Tổng Quan

Hệ thống OPDS mới hỗ trợ **4 chế độ truy cập** cloud drive:

| # | Chế độ | Đăng nhập? | Truy cập private? | Providers |
|---|--------|-----------|-------------------|-----------|
| 1 | **Public Link** | ❌ Không | ❌ Chỉ public | Google Drive, OneDrive, Dropbox, HTTP |
| 2 | **API Key** | ❌ Không | ⚠️ "Anyone with link" | Google Drive |
| 3 | **Service Account** ⭐ | ❌ Không | ✅ Private folder | Google Drive |
| 4 | **OAuth** | ✅ Cần | ✅ Toàn bộ My Drive | Google Drive |

---

## 1. Public Link (Đơn giản nhất)

### Cách dùng
1. Mở **Thư viện đám mây** trong app
2. Nhấn **"+ Thêm thư viện"** → tab **"Public Link"**
3. Dán link chia sẻ công khai:
   - Google Drive: `https://drive.google.com/drive/folders/1ABC...`
   - OneDrive: `https://1drv.ms/f/s!ABC...`
   - Dropbox: `https://dropbox.com/scl/fo/ABC.../`
   - HTTP Index: `https://example.com/books/`
4. Đặt tên nguồn → **"Thêm thư viện"**

### Hạn chế
- Folder **phải ở chế độ công khai** (Anyone with the link can view)
- Không xem được folder private

---

## 2. API Key (Không cần đăng nhập, folder "Anyone with link")

### Cách hoạt động
App nhúng sẵn `GOOGLE_DRIVE_API_KEY` trong BuildConfig. Khi user dán link Google Drive có chế độ chia sẻ "Bất kỳ ai có link đều xem được", API Key cung cấp quota cho requests mà không cần OAuth.

### Cách dùng
- Giống hệt Public Link — user chỉ dán URL, không cần cấu hình gì thêm
- API Key tự động được gửi kèm trong config GoBridge

### Hạn chế
- **Chỉ hoạt động khi folder đã bật "Anyone with the link"** — nếu folder hoàn toàn private (chỉ chia sẻ với email cụ thể) thì không truy cập được
- API Key có quota giới hạn (~10,000 requests/day)

---

## 3. Service Account — Private Drive KHÔNG cần đăng nhập ⭐

> [!IMPORTANT]
> **Đây là chế độ quan trọng nhất** cho use case: truy cập folder Google Drive private mà KHÔNG bắt user đăng nhập Google. User chỉ cần cung cấp file JSON credentials của Service Account.

### Nguyên lý hoạt động

```
Google Cloud Console
  └── Tạo Service Account → nhận email: myapp@project.iam.gserviceaccount.com
  └── Tạo JSON Key file → tải về

Google Drive
  └── Chia sẻ folder với email Service Account (Viewer quyền đọc)

DrDucBook App
  └── User import file JSON Key
  └── App dùng Service Account credentials để gọi Google Drive API
  └── KHÔNG cần đăng nhập Google, KHÔNG popup consent
  └── GoBridge nhận mode="service_account" + credentials JSON
  └── OPDS proxy serve catalog từ folder private
```

### Hướng dẫn từng bước

#### Bước 1: Tạo Service Account trên Google Cloud

1. Vào [Google Cloud Console](https://console.cloud.google.com/)
2. Tạo hoặc chọn Project
3. Vào **APIs & Services** → **Credentials**
4. Nhấn **"+ Create Credentials"** → **"Service Account"**
5. Đặt tên (ví dụ: `drducbook-reader`)
6. Bỏ qua bước Grant access → **Done**
7. Click vào Service Account vừa tạo
8. Tab **"Keys"** → **"Add Key"** → **"Create new key"** → **JSON**
9. File `xxx-yyy.json` sẽ tự tải về — **lưu file này cẩn thận**

> Nội dung file JSON sẽ có dạng:
> ```json
> {
>   "type": "service_account",
>   "project_id": "my-project-123",
>   "private_key_id": "abc123...",
>   "private_key": "-----BEGIN PRIVATE KEY-----\nMIIE...",
>   "client_email": "drducbook-reader@my-project-123.iam.gserviceaccount.com",
>   "client_id": "1234567890",
>   "auth_uri": "https://accounts.google.com/o/oauth2/auth",
>   "token_uri": "https://oauth2.googleapis.com/token"
> }
> ```

#### Bước 2: Bật Google Drive API

1. Trong Google Cloud Console → **APIs & Services** → **Library**
2. Tìm **"Google Drive API"** → **Enable**

#### Bước 3: Chia sẻ folder trên Google Drive

1. Mở Google Drive web
2. Chuột phải vào folder muốn chia sẻ → **"Share"**
3. Trong ô "Add people", nhập email Service Account:
   `drducbook-reader@my-project-123.iam.gserviceaccount.com`
4. Đặt quyền: **"Viewer"** (chỉ đọc)
5. Nhấn **"Send"** (không cần notify)
6. Copy **Folder ID** từ URL: `drive.google.com/drive/folders/`**`1ABCxyz...`**

#### Bước 4: Thêm nguồn trong DrDucBook

1. Mở **Thư viện đám mây** trong app
2. Nhấn **"+ Thêm thư viện"**
3. Chọn tab **"Service Account"** ← TAB MỚI
4. Nhấn **"Chọn file JSON"** → chọn file JSON key đã tải ở Bước 1
   - Hoặc **dán nội dung JSON** trực tiếp
5. Nhập **Folder ID**: `1ABCxyz...` (từ Bước 3)
6. Đặt tên nguồn → Nhấn **"Thêm thư viện"**
7. App kết nối → hiển thị nội dung folder private!

### Flow kỹ thuật chi tiết

```
AddDriveSourceSheet (tab "Service Account")
         │
         ▼ User chọn JSON file + nhập Folder ID
ManagedDriveSource(
    type = GOOGLE_DRIVE_SERVICE_ACCOUNT,  ← NEW enum
    serviceAccountJson = "<encrypted>",    ← NEW field
    rootFolderId = "1ABCxyz",
)
         │
         ▼
DriveWebDavConnectionUseCase.connectSource():
    configMap["provider"] = "google_drive"
    configMap["mode"] = "service_account"    ← NEW mode
    configMap["service_account_json"] = decryptedJson
    configMap["root_folder_id"] = source.rootFolderId
         │
         ▼
GoBridge.start(configJson, accessToken="")
    → Go native server nhận JSON credentials
    → Tự sign JWT → exchange cho access_token
    → Mount folder private lên WebDAV localhost
         │
         ▼
DriveOpdsProxyServer đọc WebDAV → serialize OPDS XML
         │
         ▼
OpdsClient → DriveLibrary UI → user browse + download
```

### Bảo mật

| Vấn đề | Giải pháp |
|--------|-----------|
| JSON Key chứa private key | Mã hóa bằng `EncryptedSharedPreferences` trước khi lưu |
| Hiển thị key trong UI | Chỉ hiển thị `client_email`, KHÔNG hiển thị private key |
| Xóa nguồn | Xóa hoàn toàn JSON key khỏi storage |
| Leak risk | Service Account chỉ có quyền Viewer trên folder cụ thể |

### Ưu điểm so với OAuth

| Tiêu chí | Service Account | OAuth |
|----------|----------------|-------|
| Cần đăng nhập Google? | ❌ KHÔNG | ✅ CẦN |
| Popup consent? | ❌ KHÔNG | ✅ CÓ |
| Token hết hạn? | Tự refresh vĩnh viễn | Cần re-auth định kỳ |
| Chia sẻ cho người khác | Gửi file JSON + Folder ID | Không thể |
| Setup phức tạp? | Trung bình (1 lần) | Đơn giản |
| Phù hợp cho | Thư viện nhóm, server share | Cá nhân |

---

## 4. OAuth Private (Đăng nhập Google)

### Cách dùng
1. Mở **Thư viện đám mây** → **"+ Thêm thư viện"** → tab **"Google Drive"**
2. Nhấn **"Đăng nhập bằng Google"**
3. Chọn tài khoản → cấp quyền `drive.file`
4. Nhập Folder ID (tùy chọn) → **"Lưu nguồn"**

### Xử lý lỗi

| Lỗi | Nguyên nhân | Giải pháp |
|-----|-------------|-----------|
| Status 403 | Drive API chưa bật / user chưa trong test list | Bật API + thêm Test users |
| Status 401 | Token hết hạn | App tự refresh hoặc đăng nhập lại |
| Status 10 | SHA-1 / package không khớp | Kiểm tra OAuth client config |

---

## So sánh đầy đủ 4 chế độ

| Tiêu chí | Public Link | API Key | Service Account ⭐ | OAuth |
|----------|-------------|---------|-------------------|-------|
| Đăng nhập Google | ❌ | ❌ | ❌ | ✅ |
| Popup consent | ❌ | ❌ | ❌ | ✅ |
| Folder public | ✅ Cần | ✅ Cần "Anyone with link" | ❌ Không cần | ❌ Không cần |
| Folder private | ❌ | ❌ | ✅ (share với SA email) | ✅ |
| Setup | 0 bước | 0 bước | 4 bước (1 lần) | 1 bước |
| Token refresh | N/A | N/A | Tự động vĩnh viễn | Cần re-auth |
| Thumbnails | ❌ | ❌ | ✅ (qua Drive API) | ✅ |
| Full metadata | ❌ | ❌ | ✅ | ✅ |
| OneDrive | ✅ | ❌ | ❌ | ❌ (tương lai) |
| Dropbox | ✅ | ❌ | ❌ | ❌ (tương lai) |
| Chia sẻ cấu hình | Gửi URL | N/A | Gửi JSON + Folder ID | Không thể |
| Use case chính | Thư viện mở | Thư viện mở | **Nhóm bạn / team share** | Cá nhân |

---

## OPDS Catalog Output (thống nhất cho mọi chế độ)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"
      xmlns:opds="http://opds-spec.org/2010/catalog">
  <id>urn:drducbook:drive:source-id</id>
  <title>My Private Library</title>
  <updated>2026-10-03T10:00:00Z</updated>
  
  <!-- Folder → Navigation entry -->
  <entry>
    <title>Light Novels</title>
    <id>urn:drducbook:folder:Light%20Novels</id>
    <link rel="subsection" 
          href="/opds/catalog/Light%20Novels"
          type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  </entry>
  
  <!-- Book → Acquisition entry -->
  <entry>
    <title>Sword Art Online Vol 1</title>
    <author><name>Kawahara Reki</name></author>
    <summary>Aincrad arc</summary>
    <link rel="http://opds-spec.org/acquisition"
          href="/opds/download/SAO_Vol1.epub"
          type="application/epub+zip" length="2456789"/>
    <link rel="http://opds-spec.org/image"
          href="/opds/cover/SAO_Vol1.epub" type="image/jpeg"/>
  </entry>
</feed>
```

---

## Lộ trình mở rộng Private cho OneDrive / Dropbox

| Provider | Status | Private method |
|----------|--------|----------------|
| Google Drive | ✅ Service Account + OAuth | SA JSON key / OAuth2 `drive.file` |
| OneDrive | 🔜 Tương lai | Azure AD App Registration + Client Secret |
| Dropbox | 🔜 Tương lai | Dropbox App + Access Token (long-lived) |
| WebDAV server | 🔜 Tương lai | HTTP Basic Auth (username/password) |

Kiến trúc OPDS proxy đã sẵn sàng — chỉ cần thêm auth provider mới vào `DriveWebDavConnectionUseCase` và GoBridge config.
