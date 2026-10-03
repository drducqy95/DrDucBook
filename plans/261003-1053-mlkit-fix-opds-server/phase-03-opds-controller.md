# Phase 03: OPDS Drive Proxy Server (Ktor)

Status: ⬜ Pending
Dependencies: Phase 02 (OPDS Models)

## Objective

Tạo local OPDS proxy server chạy trong app, tương tự `DriveWebDavService` + `GoBridge`. Server nhận link cloud drive (Google Drive, OneDrive — public & private), fetch file listing qua API, serve dưới dạng OPDS catalog trên localhost.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  DriveOpdsService (Foreground Service)                   │
│  ├── Manages lifecycle, notification, wake locks         │
│  └── Controls DriveOpdsProxyServer                       │
│       │                                                  │
│       ▼                                                  │
│  DriveOpdsProxyServer (Ktor CIO, localhost:dynamic_port) │
│  ├── GET /opds/catalog         → root navigation feed    │
│  ├── GET /opds/catalog/{path}  → subfolder feed          │
│  ├── GET /opds/search?q=...    → search feed             │
│  ├── GET /opds/download/{path} → proxy download file     │
│  ├── GET /opds/cover/{path}    → proxy cover image       │
│  └── GET /opds/opensearch.xml  → OpenSearch descriptor   │
│       │                                                  │
│       ▼ (backend)                                        │
│  CloudDriveListingProvider                               │
│  ├── Uses GoBridge WebDAV proxy (existing) for file list │
│  ├── OR direct Google Drive API for private drives       │
│  ├── OR OkHttp for public links / HTTP index             │
│  └── Converts to DriveFileInfo[]                         │
└─────────────────────────────────────────────────────────┘
```

**Key insight:** Reuse the **existing GoBridge WebDAV proxy** as backend! The OPDS server sits on top of it, translating WebDAV PROPFIND responses into OPDS Atom XML feeds. This way we don't duplicate cloud drive access logic.

## Implementation Steps

### 1. [ ] Create DriveOpdsProxyServer
- **File:** `web/DriveOpdsProxyServer.kt` (NEW)
- Lightweight Ktor CIO server on a dynamic port (separate from main KtorServer)
```kotlin
class DriveOpdsProxyServer(
    private val port: Int = 0,  // 0 = dynamic
    private val webDavBaseUrl: String,  // existing GoBridge WebDAV URL
    private val webDavAuth: Authorization,
) {
    private var server: EmbeddedServer<*, *>? = null
    
    suspend fun start(): Int { /* returns actual port */ }
    fun stop() { }
    
    // Routes:
    // GET /opds/catalog → fetch WebDAV root via PROPFIND, convert to OPDS navigation feed
    // GET /opds/catalog/{path...} → fetch subfolder, convert to OPDS acquisition feed
    // GET /opds/download/{path...} → proxy download from WebDAV
    // GET /opds/cover/{path...} → proxy cover from WebDAV
    // GET /opds/opensearch.xml → search descriptor
}
```

### 2. [ ] Create CloudDriveListingProvider
- **File:** `web/CloudDriveListingProvider.kt` (NEW)
- Fetches file listing from the existing GoBridge WebDAV proxy:
```kotlin
class CloudDriveListingProvider(
    private val webDavBaseUrl: String,
    private val auth: Authorization,
) {
    suspend fun listDirectory(path: String): List<DriveFileInfo> {
        val fullUrl = "$webDavBaseUrl/${path.trimStart('/')}"
        val webDavFiles = WebDav(fullUrl, auth).listFiles()
        return webDavFiles.map { it.toDriveFileInfo(path) }
    }
    
    suspend fun downloadFile(path: String): InputStream {
        val fullUrl = "$webDavBaseUrl/${path.trimStart('/')}"
        return WebDav(fullUrl, auth).downloadInputStream()
    }
}
```
- Reuses `lib/webdav/WebDav.kt` client — no new cloud drive API code needed!

### 3. [ ] Create DriveOpdsService (Foreground Service)
- **File:** `service/DriveOpdsService.kt` (NEW)
- Mirrors `DriveWebDavService.kt` pattern:
```kotlin
class DriveOpdsService : BaseService() {
    companion object {
        const val ACTION_START = "...DriveOpdsService.START"
        const val ACTION_STOP = "...DriveOpdsService.STOP"
        const val EXTRA_WEBDAV_URL = "webdav_url"
        const val EXTRA_WEBDAV_AUTH = "webdav_auth"
    }
    
    private var opdsServer: DriveOpdsProxyServer? = null
    
    // Start OPDS server that reads from existing GoBridge WebDAV
    // Notification: "OPDS: http://127.0.0.1:{port}/opds/catalog"
}
```

### 4. [ ] Create DriveOpdsServiceController
- **File:** `help/drive/DriveOpdsServiceController.kt` (NEW)
- Mirrors `DriveWebDavServiceController.kt`:
```kotlin
object DriveOpdsServiceController {
    val serverInfo: StateFlow<DriveOpdsServerInfo>
    val isRunning: Boolean
    
    fun start(context: Context, webDavBaseUrl: String, webDavAuth: String)
    fun stop(context: Context)
}

data class DriveOpdsServerInfo(
    val state: DriveOpdsState,
    val port: Int = 0,
    val opdsUrl: String = "",  // e.g. "http://127.0.0.1:8765/opds/catalog"
)
```

### 5. [ ] Register DriveOpdsService in AndroidManifest
- **File:** `AndroidManifest.xml`
```xml
<service
    android:name=".service.DriveOpdsService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### 6. [ ] Update DriveWebDavConnectionUseCase to also start OPDS
- **File:** `domain/usecase/DriveWebDavConnectionUseCase.kt` (modify)
- After GoBridge starts successfully → also start DriveOpdsService with the WebDAV URL:
```kotlin
// After WebDAV proxy starts:
DriveOpdsServiceController.start(
    context = context,
    webDavBaseUrl = "http://127.0.0.1:$port/",
    webDavAuth = "$sessionSecret"
)
```

### 7. [ ] Add Google Drive API for private drives
- **File:** `web/GoogleDriveApiProvider.kt` (NEW)
- For private drives (authenticated Google accounts), use Google Drive REST API directly:
```kotlin
class GoogleDriveApiProvider(
    private val accessToken: String,
    private val rootFolderId: String = "root",
) {
    suspend fun listFiles(folderId: String): List<DriveFileInfo> { ... }
    suspend fun downloadFile(fileId: String): InputStream { ... }
    suspend fun getThumbnail(fileId: String): ByteArray? { ... }
}
```
- Uses `https://www.googleapis.com/drive/v3/files` with Bearer token auth
- Supports: list files, download, thumbnails, search
- Falls back to GoBridge for non-Google drives

### 8. [ ] Add NotificationId for OPDS service
- **File:** `constant/NotificationId.kt` (modify)
- Add: `const val DriveOpdsService = <next_id>`

### 9. [ ] Add `GOOGLE_DRIVE_SERVICE_ACCOUNT` to DriveSourceType
- **File:** `domain/model/ManagedDriveSource.kt` (modify)
```kotlin
@Serializable
enum class DriveSourceType {
    GOOGLE_DRIVE_ACCOUNT,
    GOOGLE_DRIVE_PUBLIC,
    GOOGLE_DRIVE_SERVICE_ACCOUNT,  // ← NEW: Private drive without OAuth
    ONEDRIVE_PUBLIC,
    DROPBOX_PUBLIC,
    HTTP_INDEX,
}
```
- Add Service Account fields to `ManagedDriveSource`:
```kotlin
@Serializable
@Immutable
data class ManagedDriveSource(
    // ... existing fields
    val serviceAccountJson: String = "",  // ← NEW: Encrypted SA JSON key
)
```

### 10. [ ] Add Service Account mode to DriveWebDavConnectionUseCase
- **File:** `domain/usecase/DriveWebDavConnectionUseCase.kt` (modify)
- Add new branch in `connectSource()`:
```kotlin
DriveSourceType.GOOGLE_DRIVE_SERVICE_ACCOUNT -> {
    configMap["provider"] = "google_drive"
    configMap["mode"] = "service_account"  // ← NEW GoBridge mode
    configMap["service_account_json"] = decryptServiceAccountJson(source.serviceAccountJson)
    if (source.rootFolderId.isNotBlank()) {
        configMap["root_folder_id"] = source.rootFolderId
    }
}
```
- GoBridge Go native server sẽ:
  1. Parse JSON credentials
  2. Sign JWT với private key
  3. Exchange JWT cho Google OAuth2 access token
  4. Mount folder private lên WebDAV proxy
  5. Token tự refresh vĩnh viễn (không cần user re-auth)

### 11. [ ] Encrypt/decrypt Service Account JSON credentials
- **File:** `help/drive/ServiceAccountCredentialStore.kt` (NEW)
- Mã hóa JSON key trước khi lưu vào `ManagedDriveSource`:
```kotlin
object ServiceAccountCredentialStore {
    /** Encrypt SA JSON before storing in ManagedDriveSource */
    fun encrypt(rawJson: String): String { ... }
    
    /** Decrypt SA JSON for passing to GoBridge */
    fun decrypt(encryptedJson: String): String { ... }
    
    /** Extract client_email from JSON for display (without exposing private key) */
    fun extractEmail(rawJson: String): String? { ... }
    
    /** Validate JSON structure is a valid SA key */
    fun validate(rawJson: String): Result<String> { ... }
}
```
- Uses Android Keystore + AES-GCM encryption (hoặc `EncryptedSharedPreferences` pattern)
- **KHÔNG BAO GIỜ** hiển thị private key trong UI — chỉ hiển thị `client_email`

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `web/DriveOpdsProxyServer.kt` | Create | OPDS Ktor server |
| `web/CloudDriveListingProvider.kt` | Create | Fetch listings via WebDAV |
| `web/GoogleDriveApiProvider.kt` | Create | Direct Google Drive API |
| `service/DriveOpdsService.kt` | Create | Foreground service |
| `help/drive/DriveOpdsServiceController.kt` | Create | Service state management |
| `help/drive/ServiceAccountCredentialStore.kt` | Create | SA JSON encrypt/decrypt |
| `domain/model/ManagedDriveSource.kt` | Modify | Add SA type + fields |
| `domain/usecase/DriveWebDavConnectionUseCase.kt` | Modify | Chain OPDS + SA mode |
| `AndroidManifest.xml` | Modify | Register service |
| `constant/NotificationId.kt` | Modify | Add notification ID |

## Test Criteria

- [ ] OPDS server starts on dynamic port when Drive connects
- [ ] `GET /opds/catalog` returns valid OPDS navigation XML
- [ ] Subfolder navigation returns acquisition feed with book entries
- [ ] File download proxied correctly through OPDS server
- [ ] Cover images proxied correctly
- [ ] Server stops when Drive disconnects
- [ ] Google Drive private API lists files correctly (OAuth mode)
- [ ] **Service Account mode connects to private folder without OAuth**
- [ ] **SA JSON key encrypted before storage, decrypted only at connect time**
- [ ] **Invalid SA JSON rejected with clear error message**

## Design Decisions

1. **Layer on top of GoBridge** — reuse existing cloud drive proxy as backend, OPDS server translates WebDAV → OPDS. Avoids duplicating drive access logic.
2. **Separate Ktor instance** — dedicated port, independent lifecycle from main web service.
3. **Google Drive API direct** — for private drives (OAuth + SA), access the API alongside OPDS proxy for richer metadata.
4. **Dynamic port** — avoid conflicts with main KtorServer (1124) and WebSocket (1125).
5. **Service Account = no login** — SA credentials embedded/imported by user, GoBridge handles JWT→token exchange, auto-refresh forever.
6. **SA JSON encrypted** — private key stored encrypted via Android Keystore, only decrypted at connect time.

---
Next Phase: [Phase 04 - OPDS Client + DriveLibrary Integration](./phase-04-opds-client-ui.md)
