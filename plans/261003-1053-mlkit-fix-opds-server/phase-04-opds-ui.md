# Phase 04: OPDS Client + DriveLibrary UI Integration

Status: ⬜ Pending
Dependencies: Phase 03 (OPDS Proxy Server)

## Objective

Tạo OPDS client trong app để consume local OPDS proxy server, tích hợp vào DriveLibrary UI hiện có. User có thể chọn giữa WebDAV view (hiện tại) và OPDS view khi duyệt cloud drive.

## Flow

```
User adds Drive source → GoBridge starts (WebDAV proxy)
                        → DriveOpdsService starts (OPDS proxy on top)
                        │
                        ├── WebDAV mode (existing): WebDav client → DriveCatalogItem[]
                        │
                        └── OPDS mode (NEW): OpdsClient → OpdsCatalog → DriveCatalogItem[]
                             │
                             ▼
                        DriveLibrary UI (reuse existing cards, preview, download)
```

## Implementation Steps

### 1. [ ] Create OPDS Client
- **File:** `help/drive/OpdsClient.kt` (NEW)
```kotlin
class OpdsClient(private val httpClient: OkHttpClient = okHttpClient) {
    /** Fetch and parse OPDS catalog from URL */
    suspend fun fetchCatalog(url: String): OpdsCatalog = withContext(Dispatchers.IO) {
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val xml = response.body?.string() ?: error("Empty OPDS response")
        OpdsParser.parse(xml, url)
    }
    
    /** Download book file from acquisition URL */
    suspend fun downloadBook(url: String): InputStream = withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(url).build()).execute()
            .body?.byteStream() ?: error("Download failed")
    }
}
```

### 2. [ ] Create OPDS → DriveCatalogItem converter
- **File:** `help/drive/OpdsDriveAdapter.kt` (NEW)
```kotlin
object OpdsDriveAdapter {
    fun toDriveCatalogItems(catalog: OpdsCatalog): List<DriveCatalogItem> {
        return catalog.entries.map { entry ->
            DriveCatalogItem(
                name = entry.title,
                path = entry.navigationUrl ?: entry.acquisitionUrl ?: entry.id,
                isDir = entry.isNavigation,
                title = entry.title,
                author = entry.author,
                intro = entry.summary,
                coverUrl = entry.coverUrl,
                format = entry.acquisitionFormat ?: "",
            )
        }
    }
}
```

### 3. [ ] Add OPDS connection mode to DriveLibraryViewModel
- **File:** `ui/drive/DriveLibraryViewModel.kt` (modify)
- After GoBridge + OPDS server both start:
```kotlin
// Store OPDS server URL alongside WebDAV URL
private var activeOpdsUrl: String = ""  // e.g. "http://127.0.0.1:8765/opds/catalog"

// In connectSource(), after successful connection:
activeOpdsUrl = DriveOpdsServiceController.serverInfo.value.opdsUrl
```
- Add OPDS-mode loadDirectory:
```kotlin
private fun loadDirectoryViaOpds(opdsUrl: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(loading = true) }
        val catalog = opdsClient.fetchCatalog(opdsUrl)
        val items = OpdsDriveAdapter.toDriveCatalogItems(catalog)
        _uiState.update { it.copy(items = items.toImmutableList(), loading = false) }
    }
}
```

### 4. [ ] Add OPDS view mode toggle in DriveLibraryContract
- **File:** `ui/drive/DriveLibraryContract.kt` (modify)
```kotlin
enum class DriveViewMode {
    CATALOG,   // existing WebDAV folder view
    GRID,      // existing grid view
    LIST,      // existing list view
    OPDS,      // NEW: OPDS catalog view
}
```
- Add intent:
```kotlin
data object ToggleOpdsMode : DriveLibraryIntent
```

### 5. [ ] Add OPDS tab/toggle in DriveLibrary UI
- **File:** `ui/drive/DriveLibrarySection.kt` or `DriveLibraryRouteScreen.kt` (modify)
- When OPDS server is running, show a toggle/tab bar:
  - 📁 Thư mục (WebDAV) | 📚 OPDS Catalog
- OPDS mode shows richer metadata: cover, author, summary from OPDS entries
- WebDAV mode shows raw file listing (existing behavior)

### 6. [ ] Handle OPDS navigation (drill into sub-catalogs)
- When user taps a navigation entry in OPDS mode:
  - Follow the navigation link URL → fetch sub-catalog → display
  - Update breadcrumbs with catalog title
- When user taps an acquisition entry:
  - Download file via OPDS proxy → `LocalBook.saveBookFile()` → import

### 7. [ ] Inject OpdsClient into DriveLibraryViewModel
- **File:** `di/appModule.kt` (modify)
```kotlin
single { OpdsClient() }
viewModel { DriveLibraryViewModel(get(), get(), get(), get()) }  // add OpdsClient param
```

### 8. [ ] Add Service Account tab in AddDriveSourceSheet
- **File:** `ui/drive/AddDriveSourceSheet.kt` (modify)
- Add third tab **"Service Account"** bên cạnh "Google Drive" và "Public Link":
```kotlin
PrimaryTabRow(selectedTabIndex = selectedTab) {
    Tab(text = { Text("Google Drive") }, ...)    // existing
    Tab(text = { Text("Public Link") }, ...)      // existing
    Tab(text = { Text("Service Account") }, ...)  // NEW
}
```
- Tab "Service Account" UI:
  - Nút **"Chọn file JSON"** → mở file picker cho `.json` file
  - Hoặc **text field** để dán nội dung JSON trực tiếp
  - Hiển thị `client_email` sau khi import thành công (validate + extract)
  - Text field **"Folder ID"** (bắt buộc cho SA mode)
  - Text field **"Tên nguồn"** (tùy chọn)
  - Nút **"Thêm thư viện"** → validate JSON → encrypt → tạo `ManagedDriveSource`

- Add new intent:
```kotlin
data class AddServiceAccount(
    val jsonContent: String,
    val folderId: String,
    val name: String,
) : DriveLibraryIntent
```

- ViewModel handler:
```kotlin
is DriveLibraryIntent.AddServiceAccount -> {
    val validated = ServiceAccountCredentialStore.validate(intent.jsonContent)
    validated.onSuccess { email ->
        val source = ManagedDriveSource(
            id = MD5Utils.md5Encode("sa_${intent.folderId}"),
            name = intent.name.ifEmpty { "SA: $email" },
            type = DriveSourceType.GOOGLE_DRIVE_SERVICE_ACCOUNT,
            rootFolderId = intent.folderId,
            serviceAccountJson = ServiceAccountCredentialStore.encrypt(intent.jsonContent),
        )
        val saved = registry.addOrUpdateSource(source)
        connectSource(saved)
    }.onFailure { error ->
        _effects.tryEmit(DriveLibraryEffect.ShowToast("JSON không hợp lệ: ${error.message}"))
    }
}
```

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `help/drive/OpdsClient.kt` | Create | HTTP client for local OPDS |
| `help/drive/OpdsDriveAdapter.kt` | Create | OPDS → DriveCatalogItem |
| `ui/drive/DriveLibraryViewModel.kt` | Modify | Add OPDS load/navigate + SA intent |
| `ui/drive/DriveLibraryContract.kt` | Modify | Add OPDS view mode + SA intent |
| `ui/drive/DriveLibrarySection.kt` | Modify | Add OPDS toggle UI |
| `ui/drive/AddDriveSourceSheet.kt` | Modify | Add Service Account tab |
| `di/appModule.kt` | Modify | Register OpdsClient |

## Test Criteria

- [ ] OPDS client fetches catalog from local proxy correctly
- [ ] OPDS entries display with cover, author, summary
- [ ] Navigation entries → drill into sub-catalogs
- [ ] Acquisition entries → download and import book
- [ ] Toggle between WebDAV/OPDS modes works
- [ ] Breadcrumbs work in OPDS mode
- [ ] OPDS mode cleans up when disconnecting
- [ ] **Service Account tab shows file picker + JSON paste**
- [ ] **Valid SA JSON → shows client_email, enables "Thêm thư viện"**
- [ ] **Invalid SA JSON → error toast, button disabled**
- [ ] **SA source connects to private folder without Google login popup**

---
Next Phase: [Phase 05 - Testing](./phase-05-testing.md)
