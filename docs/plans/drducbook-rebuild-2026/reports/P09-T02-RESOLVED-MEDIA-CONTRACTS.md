# P09.T02 - ResolvedMedia contracts

## Muc tieu

Khoa contract cho media direct/HLS/DASH/local, variants, subtitles, audio tracks va serialization version; dong thoi tranh persist/log credential nhay cam trong header/query.

## Pham vi da xu ly

| Hang muc | Ket qua |
|---|---|
| Domain model | `ResolvedMediaVariant` co them `durationMs`, `drmUnsupported`, `downloadFileName` voi default de khong pha callsite cu. |
| Versioned contract | Them `ResolvedMediaContract` schema version `1` va DTO cho variant/subtitle/audio track. |
| Round-trip serialization | `ResolvedMedia.toResolvedMediaContract()` va `ResolvedMediaContract.toResolvedMedia()` round-trip direct/HLS/DASH/local + tracks. |
| Unsupported version guard | Contract version khac `1` bi reject ro rang. |
| Credential-safe log contract | `toResolvedMediaContract(redactSecrets = true)` redact sensitive query params va sensitive headers. |
| Persistent header policy | `toPersistentMediaHeaders()` drop `Cookie`, `Authorization`, API key/token headers nhung giu `Referer`, `User-Agent`, `Origin`, `CookieJar`. |
| Download DB path | `MediaDownloadRepository.enqueue()` va `updateSource()` persist headers qua `toPersistentMediaHeaders()` thay vi raw headers. |
| Parser adapter regression | P09.T01 parser, `MediaUriResolver` va `VbookMediaParser` tiep tuc pass voi contract moi. |

## File tac dong

- `app/src/main/java/io/legado/app/domain/model/ResolvedMedia.kt`
- `app/src/main/java/io/legado/app/domain/model/ResolvedMediaContract.kt`
- `app/src/main/java/io/legado/app/data/repository/MediaDownloadRepository.kt`
- `app/src/test/java/io/legado/app/domain/model/ResolvedMediaContractTest.kt`

## Contract matrix

| Surface | Gate |
|---|---|
| HLS | Protocol `HLS`, mime `application/x-mpegURL`, download metadata, expiry va filename round-trip. |
| DASH | Protocol `DASH`, mime `application/dash+xml`, `drmUnsupported=true` round-trip. |
| Local/direct | `file://` direct variant round-trip, `downloadSupported=false`. |
| Subtitle | Subtitle track ID/label/language/mime/default/header round-trip. |
| Audio track | Audio track ID/label/language/mime/header round-trip. |
| Redacted log | Header `Cookie`/`Authorization` va query `access_token`/`signature` khong xuat hien trong redacted JSON. |
| Persistent DB headers | Raw `Cookie`/`Authorization`/`X-Api-Key` khong di vao `headersJson`; playback context safe headers duoc giu. |

## Lenh kiem tra

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain
```

## Ket qua

- Focused media contract/parser JVM tests: 15 tests PASS, 0 failures, 0 errors, 0 skipped.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.ResolvedMediaContractTest.xml`: 4 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaSourceRuleResultParserTest.xml`: 4 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaUriResolverTest.xml`: 3 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookMediaParserTest.xml`: 4 tests.

## Rui ro con lai

- Download task van persist `sourceUri` de worker co the tiep tuc tai; URI query redaction hien ap dung cho log/contract redacted, chua doi DB `sourceUri` de tranh pha resume.
- P09.T03/P09.T04 can dung CookieVault/runtime cookie bridge neu media yeu cau cookie song, vi `headersJson` khong con luu raw Cookie.
- Chua co golden JSON file rieng tren disk; test hien khoa contract bang DTO round-trip va field matrix.
