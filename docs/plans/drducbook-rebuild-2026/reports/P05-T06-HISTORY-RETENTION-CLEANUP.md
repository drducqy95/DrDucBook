# P05.T06 - History retention va cleanup

## Muc tieu

Kiem soat kich thuoc CSDL source health va giam rui ro rieng tu bang retention deterministic, cleanup theo source delete, va khong giu HTML/noi dung/cookie trong lich su check.

## Thay doi chinh

- Them `SourceCheckRetentionPolicy` voi mac dinh giu toi da 30 ngay va 100 run moi source.
- Them `SourceCheckCleanupResult` de worker/report biet so run da inspect, xoa va con lai.
- Mo rong `SourceCheckDao` voi query cleanup, xoa run theo danh sach id, xoa run theo source, va dem run/stage.
- Mo rong `BookSourceHealthDao` voi xoa summary theo source trong transaction dong bo.
- Them `SourceCheckRepository.cleanup()`:
  - Khong xoa run dang `RUNNING`.
  - Luon giu latest finished run cho moi source, ke ca khi qua tuoi.
  - Xoa run cu theo ca gioi han tuoi va so luong.
  - Idempotent neu chay lap lai.
- Them `SourceHealthRetentionWorker` chay dinh ky 24h, flex 6h, cung feature flag `sourceDailyHealth`.
- Khi xoa Book/RSS source qua `SourceHelp`, xoa kem `book_source_health` va `source_check_runs`; stage duoc xoa cascade theo foreign key.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckRetentionPolicy.kt`
- `app/src/main/java/io/legado/app/data/dao/SourceCheckDao.kt`
- `app/src/main/java/io/legado/app/data/dao/BookSourceHealthDao.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepository.kt`
- `app/src/main/java/io/legado/app/worker/SourceHealthRetentionWorker.kt`
- `app/src/main/java/io/legado/app/App.kt`
- `app/src/main/java/io/legado/app/help/source/SourceHelp.kt`
- `app/src/test/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepositoryTest.kt`

## Dieu kien thong qua

- Cleanup khong xoa run active.
- Cleanup giu latest finished run theo source.
- Cleanup xoa run qua tuoi va vuot gioi han count.
- Cleanup lap lai khong tao loi hoac xoa them sai.
- Xoa source xoa kem summary, run va stage cua source do.
- Source entity khong bi sua group/comment/rule/enabled trong qua trinh cleanup.

## Kiem thu

Da chay:

```text
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --no-daemon --console=plain
```

Ket qua:

- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- Focused unit tests: BUILD SUCCESSFUL.
- `SourceCheckRepositoryTest`: 4 tests PASS.
- `BookSourceHealthWorkerTest`: 2 tests PASS.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthWorkerTest.xml`

## Ghi chu

- Retention hien tinh theo `startedAt`, phu hop voi lich su check vi moi run deu co moc bat dau bat buoc.
- Worker retention dung chung feature flag daily health; khi tat daily health, ca worker probe va worker cleanup deu bi huy.
- Warning build lien quan AGP/Baseline Profile la warning cau hinh hien co, khong phat sinh tu task nay.
