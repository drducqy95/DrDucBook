# P09.T01 - Source content-rule media resolution

## Muc tieu

Sua luong resolve media tu source content rule de ket qua nhu `HD 1920x800 + https://...m3u8` khong bi hien nhu text, dong thoi text chapter binh thuong khong bi nhan nham thanh media.

## Pham vi da xu ly

| Hang muc | Ket qua |
|---|---|
| Content rule pipeline | `MediaResolverRepository` dung source content pipeline cho audio/video source khong phai VBook, voi `book`, `chapter`, `nextChapterUrl`, `webJs`, `sourceRegex`, login check va `needSave=false`. |
| Label + URL parser | Them `MediaSourceRuleResultParser` parse raw line co label va URL media, vi du `HD 1920x800 + https://cdn.test/video/master.m3u8`. |
| JSON/VBook-style parser | Reuse `VbookMediaParser` cho JSON co `data`, `sources`, `variants`, `headers`, `subtitles`, `audioTracks`. |
| Header/referrer | Fallback headers tu source duoc merge vao variant/subtitle/audio tracks; JSON headers co uu tien cao hon fallback. |
| Relative media URL | Raw single media URL tu content rule co the normalize theo chapter base URL. |
| Text safety | Parser reject normal paragraph khong co URL media de tranh nhan nham text chapter. |
| VBook boundary | VBook source tiep tuc di qua `VbookPluginAdapter.resolveMedia`, khong doi semantics adapter. |

## File tac dong

- `app/src/main/java/io/legado/app/data/repository/MediaResolverRepository.kt`
- `app/src/main/java/io/legado/app/help/media/MediaSourceRuleResultParser.kt`
- `app/src/test/java/io/legado/app/help/media/MediaSourceRuleResultParserTest.kt`

## Before/After fixture

| Fixture | Truoc | Sau |
|---|---|---|
| `HD 1920x800 + https://cdn.test/video/master.m3u8?expires=2000000000` | Co the bi coi nhu raw text/URL khong co quality label. | Resolve thanh 1 HLS variant, title `HD 1920x800`, kind `VIDEO`, expiry `2000000000000`, giu `Referer`. |
| JSON `{data, headers, subtitles}` | Chua co parser rieng cho source content-rule result. | Resolve thanh HLS variant + subtitle, merge source headers va JSON headers. |
| Normal paragraph | Co nguy co neu luong media fallback qua raw string. | Parser reject voi stage `Media source parse stage failed`. |

## Lenh kiem tra

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain
```

## Ket qua

- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- Focused parser JVM tests: 11 tests PASS, 0 failures, 0 errors, 0 skipped.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaSourceRuleResultParserTest.xml`: 4 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaUriResolverTest.xml`: 3 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookMediaParserTest.xml`: 4 tests.

## Rui ro con lai

- P09.T01 chua co full repository integration test voi Room DAO gia lap; bang chung hien tai la parser fixture + compile + VBook parser regression.
- Parser chap nhan raw single HTTP(S) URL de giu tuong thich voi media source cu, ke ca khi URL khong co extension; cac chuoi co label van can media extension ro rang de tranh nhan nham link trang web.
- P09.T02 can khoa model contract/golden serialization sau hon cho direct/HLS/DASH/local, redirects va credential-safe persistence.
