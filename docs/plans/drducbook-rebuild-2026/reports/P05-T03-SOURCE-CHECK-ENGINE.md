# P05.T03 - Engine Quick/Standard/Full va classification

## Muc tieu

Tao engine chung cho scheduled/manual/post-login checks, dung adapter P05.T02,
aggregate stage evidence thanh run/summary deterministic va phan loai loi ro rang.

## Ket qua trien khai

- Them `SourceCheckClassifier` de aggregate stage evidence:
  - thanh cong: `HEALTHY`, `DEGRADED`
  - khong co stage supported: `UNSUPPORTED`
  - failure dau tien theo `stageOrder/stageKey` quyet dinh status
- Mo rong `BookSourceHealthStatus`:
  - `RATE_LIMITED`
  - `NETWORK_ERROR`
  - `TLS_ERROR`
  - `CONTENT_EMPTY`
  - `MEDIA_ERROR`
  - `UNSUPPORTED`
  - `STALE`
- Them `SourceCheckEngine`:
  - `checkBookSource(...)`
  - `checkRssSource(...)`
  - Book/VBook tu dong chon adapter theo `VbookSourceHealthProbeGateway.supports(source)`
  - RSS dung `RssSourceHealthProbeGateway`
  - cancellation mark run `CANCELED` va khong persist summary
  - unexpected failure tao stage `engine` va run `FAILED`
- Mo `SourceCheckRepository.beginRun(...)` overload theo source fields chung va tuy
  chon `createInitialStage = false` de engine khong tao stage `probe` gia.
- Chuyen `BookSourceHealthCheckProcessor` sang `SourceCheckEngine` cho dashboard
  all-source va targeted post-login probe.
- Cap nhat UI label/color Browser + Source Health cho status moi; `DEGRADED` va
  `STALE` khong bi tinh la error/failure.

## Classification table

| Tin hieu | Status |
|---|---|
| No failed stage, latency < 8s | `HEALTHY` |
| No failed stage, latency >= 8s | `DEGRADED` |
| Tat ca stage deu skip/khong supported | `UNSUPPORTED` |
| 401/403/login/forbidden | `AUTH_REQUIRED` |
| captcha/challenge/cloudflare | `CAPTCHA_REQUIRED` |
| 429/rate limit/throttle/quota | `RATE_LIMITED` |
| DNS/UnknownHost/timeout/connect reset/network | `NETWORK_ERROR` |
| SSL/TLS/certificate/handshake | `TLS_ERROR` |
| parse/rule/selector/xpath/script | `BROKEN_RULE` |
| empty/no items/no chapters/content empty | `CONTENT_EMPTY` |
| media/track/variant/HLS/DASH failure | `MEDIA_ERROR` |
| stale/outdated | `STALE` |
| fallback HTTP/server error | `HTTP_ERROR` |
| explicit offline/no network/airplane mode | `UNKNOWN_OFFLINE` |

`UNKNOWN_OFFLINE`, `DEGRADED` va `STALE` reset failure count; offline khong lam tang
failure count.

## Kiem tra

- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - PASS.
  - Con canh bao deprecated `ListItem` co san trong `BrowserScreen.kt`.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest" --tests "io.legado.app.domain.sourcehealth.SourceCheckClassifierTest" --tests "io.legado.app.domain.model.BookSourceHealthModelsTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --no-daemon --console=plain`
  - PASS.
  - `SourceCheckEngineTest`: 3 tests, 0 failures/errors/skipped.
  - `SourceCheckClassifierTest`: 2 tests, 0 failures/errors/skipped.
  - `BookSourceHealthModelsTest`: 2 tests, 0 failures/errors/skipped.
  - `BookSourceHealthCheckProcessorTest`: 3 tests, 0 failures/errors/skipped.
  - `BookSourceHealthWorkerTest`: 2 tests, 0 failures/errors/skipped.

## Bang chung

- `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckClassifier.kt`
- `app/src/main/java/io/legado/app/domain/model/BookSourceHealthModels.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckEngine.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepository.kt`
- `app/src/main/java/io/legado/app/worker/BookSourceHealthCheckProcessor.kt`
- `app/src/test/java/io/legado/app/data/repository/sourcehealth/SourceCheckEngineTest.kt`
- `app/src/test/java/io/legado/app/domain/sourcehealth/SourceCheckClassifierTest.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.sourcehealth.SourceCheckClassifierTest.xml`

## Rui ro va viec tiep theo

- P05.T03 da noi worker Quick Book/VBook qua engine; RSS engine path da san sang
  nhung scheduling/foreground orchestration thuoc P05.T04.
- Full foreground pause/resume/cancel, rate-limit theo domain va backoff/jitter se
  duoc dong trong P05.T04.
