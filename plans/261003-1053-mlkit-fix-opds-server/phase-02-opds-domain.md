# Phase 02: OPDS Models + Atom XML Serializer

Status: ⬜ Pending
Dependencies: None (parallel với Phase 01)

## Objective

Tạo domain models cho OPDS catalog và serializer để chuyển file listing từ cloud drive thành OPDS 1.2 Atom XML. Đây là output format của local OPDS proxy server.

## Implementation Steps

### 1. [ ] Create OPDS domain models
- **File:** `domain/model/OpdsModels.kt` (NEW)
```kotlin
@Immutable
data class OpdsCatalog(
    val id: String,
    val title: String,
    val updated: String = "",
    val selfUrl: String = "",
    val links: List<OpdsLink> = emptyList(),
    val entries: List<OpdsEntry> = emptyList(),
)

@Immutable
data class OpdsEntry(
    val id: String,
    val title: String,
    val updated: String = "",
    val author: String? = null,
    val summary: String? = null,
    val links: List<OpdsLink> = emptyList(),
)

@Immutable
data class OpdsLink(
    val href: String,
    val rel: String = "",
    val type: String = "",
    val title: String? = null,
)
```

### 2. [ ] Create Atom XML serializer
- **File:** `web/OpdsAtomSerializer.kt` (NEW)
- Serialize `OpdsCatalog` → valid OPDS 1.2 Atom XML
- Navigation feeds (`kind=navigation`) cho folder listing
- Acquisition feeds (`kind=acquisition`) cho book listing
- Cover image links (`rel=http://opds-spec.org/image`)
- OpenSearch descriptor link

### 3. [ ] Create OPDS MIME type constants
- **File:** `domain/model/OpdsModels.kt` (thêm vào)
```kotlin
object OpdsMimeTypes {
    const val NAVIGATION = "application/atom+xml;profile=opds-catalog;kind=navigation"
    const val ACQUISITION = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    const val SEARCH = "application/opensearchdescription+xml"
    const val ACQUISITION_REL = "http://opds-spec.org/acquisition"
    const val IMAGE_REL = "http://opds-spec.org/image"
    const val THUMBNAIL_REL = "http://opds-spec.org/image/thumbnail"
}
```

### 4. [ ] Create OPDS Atom XML parser (client-side)
- **File:** `help/drive/OpdsParser.kt` (NEW)
- Parse Atom XML received from local OPDS server → `OpdsCatalog`
- Sử dụng `XmlPullParser` (Android built-in)
- Handle namespaces: `atom:`, `opds:`, `dc:`, `dcterms:`

### 5. [ ] Create Drive-to-OPDS mapper
- **File:** `web/OpdsDriveMapper.kt` (NEW)
- Convert cloud drive file listing → `OpdsCatalog`
```kotlin
object OpdsDriveMapper {
    /** Map a directory listing from the cloud drive proxy to an OPDS navigation/acquisition feed */
    fun mapDirectoryToFeed(
        path: String,
        files: List<DriveFileInfo>,
        baseOpdsUrl: String,
    ): OpdsCatalog { ... }
}

data class DriveFileInfo(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val mimeType: String = "",
    val lastModified: Long = 0L,
    val thumbnailUrl: String? = null,
)
```

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `domain/model/OpdsModels.kt` | Create | OPDS data models + MIME constants |
| `web/OpdsAtomSerializer.kt` | Create | OpdsCatalog → Atom XML |
| `help/drive/OpdsParser.kt` | Create | Atom XML → OpdsCatalog |
| `web/OpdsDriveMapper.kt` | Create | Drive file listing → OpdsCatalog |

## Test Criteria

- [ ] Serializer produces valid OPDS 1.2 Atom XML
- [ ] Parser can roundtrip: serialize → parse → identical data
- [ ] Drive files correctly mapped: folders → navigation, books → acquisition
- [ ] Cover image links included for book entries

---
Next Phase: [Phase 03 - OPDS Drive Proxy Server](./phase-03-opds-proxy-server.md)
