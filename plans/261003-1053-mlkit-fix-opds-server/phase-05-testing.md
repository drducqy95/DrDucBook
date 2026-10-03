# Phase 05: Testing & Integration

Status: ⬜ Pending
Dependencies: Phase 01, Phase 03, Phase 04

## Objective

Unit tests, integration tests, và E2E verification cho cả ML Kit fix và OPDS proxy.

## Implementation Steps

### 1. [ ] ML Kit source language resolution tests
- **File:** `test/.../MlKitTranslationRepositoryTest.kt` (modify)
- Test explicit source language bypasses auto-detect
- Test "auto" falls through to existing behavior
- Test short text with explicit source → no crash

### 2. [ ] OPDS Atom XML serializer tests
- **File:** `test/.../OpdsAtomSerializerTest.kt` (NEW)
- Serialize navigation/acquisition feeds → valid XML
- Roundtrip: serialize → parse → identical data

### 3. [ ] OPDS parser tests
- **File:** `test/.../OpdsParserTest.kt` (NEW)
- Parse sample OPDS feeds (Calibre, COPS format)
- Handle missing fields gracefully
- Relative URL resolution

### 4. [ ] Drive-to-OPDS mapping tests
- **File:** `test/.../OpdsDriveMapperTest.kt` (NEW)
- Folders → navigation entries
- Book files → acquisition entries with correct MIME types
- Cover URLs included

### 5. [ ] DriveLinkResolver OPDS detection tests
- **File:** `test/.../DriveLinkResolverTest.kt` (modify existing)
- Add OPDS_CATALOG type detection cases

### 6. [ ] Compile check + full test suite
```bash
.\gradlew.bat :app:compileAppDebugKotlin
.\gradlew.bat test
```

## E2E Verification

- [ ] ML Kit: Set source language to "zh" → translate Chinese text → success
- [ ] ML Kit: Set "auto" → existing behavior preserved
- [ ] OPDS: Add Google Drive public link → GoBridge starts → OPDS proxy starts
- [ ] OPDS: Browse OPDS catalog → see folders and books with metadata
- [ ] OPDS: Download book via OPDS → imported to bookshelf
- [ ] OPDS: Private Google Drive → API auth → OPDS catalog with thumbnails
- [ ] OPDS: Disconnect → both GoBridge and OPDS service stop

---
**Plan complete!** 5 phases, 34 tasks.
