# Phase 03 - Ca nhan hoa giao dien Android

## Muc tieu phase

Hop nhat theme/icon/background hien co thanh profile co preview, fallback, backup va import/export an toan.

## Pham vi file chinh

- `ui/theme/**`, `ui/config/themeConfig/**`, `ui/config/customTheme/**`
- `ui/personalization/**` `[NEW]`
- `domain/model/Appearance*.kt`, gateway/usecase/repository `[NEW]`
- `AppScaffold`, legacy View theme bridge, launcher icon helper
- `filesDir/appearance` asset store, backup/restore va tests

## Task chi tiet

### P03.T01 - AppearanceProfile schema va repository

**Muc tieu:** Tao single source of truth cho theme Android.

**Pham vi file:** Appearance domain models/gateways/repository, theme config adapters, asset store va persistence tests.

**Thuc hien:** Model profile/version/assets, light/dark tokens, font, opacity/blur, nav style; adapter doc preference cu; atomic save va content-hash asset store.

**Dieu kien thong qua:** Round-trip/migration/fallback test pass; profile loi khong crash app; Material/Miuix cung doc mot contract.

**Log:** Ghi schema version, defaults va migration mapping.

### P03.T02 - Trung tam ca nhan hoa Compose/MVI

**Muc tieu:** Cung cap tabs Chu de, Bieu tuong, Hinh nen, Xem truoc.

**Pham vi:** Contract/ViewModel/Screen/RouteScreen, DI/nav, activity result image/font pickers.

**Thuc hien:** Preview draft tach active profile; Apply/Discard/Reset; clone/rename/delete; loading/error/unsaved-change confirmation.

**Dieu kien thong qua:** Khong thay active UI truoc Apply; process recreation giu draft; stateless screen va tests pass.

**Log:** Screenshot phone/tablet ca hai engine va test evidence.

### P03.T03 - IconSlot va trinh chinh icon

**Muc tieu:** Cho thay icon o navigation, Workspace, toolbar, shortcut va menu doc.

**Pham vi file:** IconSlot registry/resolver, personalization UI/editor, AndroidSVG utilities, icon asset store va tests.

**Thuc hien:** Registry slot on dinh; bundled picker; import PNG/WebP/SVG; crop/scale/padding/tint/background/selected preview; sanitize/rasterize SVG.

**Dieu kien thong qua:** Missing/corrupt/oversize asset fallback; contentDescription khong thay doi; icon khong lam dich layout.

**Log:** Ghi slot coverage, file limits, malicious SVG tests va screenshots.

### P03.T04 - Hinh nen toan app va theo module

**Muc tieu:** Ho tro global light/dark va override theo module.

**Pham vi file:** Wallpaper models/repository/editor, `AppScaffold`, module/Reader background bridges, image pipeline va visual tests.

**Thuc hien:** Target Home/Bookshelf/Workspace/Agent/Authoring/Ebook/Reader; crop, cover/contain, alignment, opacity, blur, overlay, dim; decode downsample.

**Dieu kien thong qua:** Contrast warning, memory test va rotation pass; text khong bi nen che; legacy View va Compose nhat quan.

**Log:** Ghi asset size/memory measurements va visual evidence.

### P03.T05 - Import/export `.drductheme`

**Muc tieu:** Theme package versioned, portable va khong chua executable content.

**Pham vi file:** Theme package manifest/parser/writer/validator, SAF import/export RouteScreen va security tests.

**Thuc hien:** ZIP manifest/checksum/MIME/size/path traversal validation; preview truoc import; ID collision tao ban sao; khong cho script/raw CSS.

**Dieu kien thong qua:** Golden round-trip pass; zip bomb/traversal/bad checksum bi chan; package version moi/qua cu co loi ro.

**Log:** Ghi format version va security test results.

### P03.T06 - Backup, fallback va visual QA

**Muc tieu:** Bao ve profile/assets va chat luong tren thiet bi.

**Pham vi file:** Backup/restore snapshot adapters, appearance cleanup worker, screenshot/UI tests va launcher resource verification.

**Thuc hien:** Them profile/assets vao snapshot; exclude temp/cache; cleanup unreferenced assets; screenshot day/night/engine/viewport; launcher aliases QA.

**Dieu kien thong qua:** Backup/restore hash match; asset thieu fallback; khong leak SAF permission/path; visual regression pass.

**Log:** Lien ket snapshot test va screenshot matrix.

## Gate dong phase

- Tat ca profile/icon/background flows pass ca Material va Miuix.
- Theme package va backup co security tests.
- Khong anh huong WebService appearance.
