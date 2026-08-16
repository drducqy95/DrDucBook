# P00.T03 - Compatibility corpus Legado/VBook

## Ket qua

Trang thai: `DONE`

Corpus synthetic/CC0 da khoa cac hop dong can bao toan khi doi package, UI va kien truc cloud. Tat ca URL dung `.test`; khong co credential, cookie, user data hoac noi dung sach co ban quyen.

## Pham vi corpus

| Nhom | Payload | Baseline duoc khoa |
|---|---:|---|
| Legado | 3 | BookSource, RssSource, HttpTTS; JS/cookie fields duoc parse va giu nguyen |
| VBook registry | 1 | 6 declared kinds va stable unique plugin IDs |
| VBook plugins | 13 | 6 manifests, 7 scripts; text/comic/audio/video/TTS/translator |
| Public contracts | 3 | 16 ReaderProvider operations, 27 HTTP routes, 3 WebSocket routes, deep links/file/share intents |
| Tong | 20 | SHA-256 va provenance cho tung payload |

## Automated coverage

`CompatibilityCorpusTest` bao gom 5 test:

1. Parse Book/RSS/TTS va xac nhan cac truong JavaScript/cookie/media khong bi mat.
2. Parse VBook registry va xac nhan du 6 plugin kinds.
3. Cai fixture vao thu muc plugin, inspect bang `VbookPluginInspector`, sau do chay 8 script qua production `VbookExecutor`/safe Rhino runtime.
4. Kiem tra count va uniqueness cua ReaderProvider, deep link, HTTP va WebSocket contracts.
5. Kiem tra coverage va SHA-256 cua toan bo provenance manifest.

## Provenance va ban quyen

- Origin: synthetic, tao rieng cho regression test.
- License: `CC0-1.0` cho tat ca payload.
- Network: reserved `.test`; test khong thuc hien request mang.
- Checksums: `app/src/test/resources/compat/provenance.json`.
- Huong dan corpus: `app/src/test/resources/compat/README.md`.

## Bang chung thuc thi

Lenh:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.compat.CompatibilityCorpusTest" --no-daemon --console=plain
```

Ket qua ngay 2026-07-29:

- `BUILD SUCCESSFUL` trong 1 phut 16 giay.
- 5 tests, 0 failures, 0 errors, 0 skipped; test runtime 11.027 giay.
- XML evidence: `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.compat.CompatibilityCorpusTest.xml`.

## Cach dung cho phase sau

- P01/P11 phai chay corpus sau khi doi package/facade/authorities.
- P04/P05 tham chieu Book/RSS cookie va source fixtures.
- P06 tham chieu WebService contract; route moi co the them, route legacy khong duoc mat am tham.
- P08/P09 tham chieu VBook service/media fixtures.
- Moi thay doi payload phai cap nhat checksum va provenance trong cung task.
