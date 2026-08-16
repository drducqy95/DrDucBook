# P10.T07 - Snapshot schema, archive, conflict va restore checkpoint

## Muc tieu

Khoa schema snapshot cloud, artifact `.drducsnapshot`, conflict/restore policy cho Supabase, Google Drive va che do `BOTH`, dam bao khong dua cookie/session/cache/model/media vao snapshot va khong auto-merge khi co conflict.

## Trang thai

IN_PROGRESS. Da co schema, archive builder/reader, restore staging, restore verification policy, conflict-choice plan, domain CAS plan, adapter coverage/restore gate, conflict UI sheet va sync planner. Chua dong DONE vi phase spec con yeu cau uploader/downloader runtime, CAS runtime Supabase/Drive, adapter runtime cho Room/file subsystems va integration scenarios.

## Pham vi da thuc hien

- Them domain snapshot models:
  - `CloudSnapshotDataset`
  - `CloudSnapshotEntry`
  - `CloudSnapshotManifest`
  - `CloudSnapshotHead`
  - `CloudSnapshotConflictState`
  - `CloudSnapshotRestorePlan`
  - `CloudSnapshotConflictChoice`
  - `CloudSnapshotResolutionPlan`
  - `CloudSnapshotHeadWritePlan`
- Them `CloudSnapshotPolicy` de:
  - tao manifest schema version `1`;
  - validate entry path/hash/size/record count;
  - reject dataset nhay cam/runtime-only;
  - classify local-only, target-only, both-changed, target divergence va invalid target;
  - tao restore plan bat buoc verify hash truoc transactional commit;
  - tao automatic resolution plan chi khi state khong can user choice;
  - bat user choice cho conflict/targets-diverged/invalid target;
  - khoa cac lua chon: keep local as new revision, restore selected target, save local as cloud copy;
  - tao compare-and-set head write plan rieng cho `SUPABASE`, `GOOGLE_DRIVE` va che do `BOTH`;
  - reject observed head sai target, head incomplete va concurrent target change truoc khi ghi.
- Them `CloudSnapshotArchive` de:
  - build `.drducsnapshot` ZIP voi `manifest.json` va `entries/{dataset}.json`;
  - tinh SHA-256/size cua archive de dua vao Supabase/Drive descriptor;
  - read archive va verify manifest/schema/hash/size truoc restore;
  - reject path traversal, duplicate file, undeclared files va checksum mismatch.
- Them `CloudSnapshotRestoreStaging` de:
  - read/verify archive truoc khi ghi file staging;
  - bung payload vao thu muc rieng theo snapshot/revision;
  - verify file staged khong thoat khoi staging root;
  - giu buoc staging tach khoi commit DB/file runtime.
- Them adapter/use-case contracts:
  - `CloudSnapshotDatasetAdapter`
  - `CloudSnapshotDatasetRegistry`
  - `CloudSnapshotUseCase`
  - `CloudSnapshotAdapterCoverage`
  - `CloudSnapshotRestoreCommitResult`
  - registry reject duplicate adapter, missing adapter, excluded dataset adapter va non-transactional restore adapter;
  - build archive mac dinh bat buoc co adapter cho toan bo included datasets;
  - restore staged data phai validate tat ca entry truoc khi goi commit adapter.
- Them Compose conflict UI bridge:
  - `CloudSnapshotConflictUiMapper` map domain prompt/option sang resource text/icon intent;
  - `CloudSnapshotConflictSheet` hien cac lua chon an toan cho conflict/targets-diverged/invalid-target;
  - UI option click tao `CloudSnapshotResolutionPlan` qua `CloudSnapshotConflictResolver`, khong tu merge conflict trong composable.
- Them `CloudSnapshotSyncPlanner` de:
  - doc `CloudSnapshotSyncState` gom target mode, base revision, local revision va observed Supabase/Drive heads;
  - uu tien chan `TARGETS_DIVERGED`/`INVALID_TARGET` truoc khi so revision local-cloud;
  - tra `Automatic` plan cho fast-forward/noop/restore target an toan;
  - tra `UserChoiceRequired` prompt cho conflict can nguoi dung;
  - tao head write CAS plans cho upload/local-copy ma khong cho tao head write tu restore plan.

## Invariants da khoa

- Included datasets mac dinh:
  - book sources
  - RSS sources
  - reading progress
  - authoring projects
  - Agent state
  - manual bookmarks
  - appearance
  - web service policy
  - source health summary
  - settings
- Excluded datasets:
  - cookies
  - auth sessions
  - cache
  - model packages
  - media downloads
- Manifest chi chap nhan mot entry cho moi dataset.
- Object path snapshot khong duoc rong hoac chua path traversal.
- Entry SHA-256 bat buoc la lowercase hex 64 ky tu.
- Archive chi duoc chua `manifest.json` va cac file entry da khai bao.
- Both-changed, Supabase/Drive divergence va invalid target deu yeu cau user choice.
- Restore plan luon `verifyBeforeCommit = true` va `transactional = true`.
- `TARGETS_DIVERGED + RESTORE_TARGET` bat buoc chon ro `SUPABASE` hoac `GOOGLE_DRIVE`.
- Restore staging reject root khong phai directory va corrupt archive truoc commit.
- Che do `BOTH` tao hai CAS plan doc lap, khong co head target `BOTH` de ghi truc tiep.
- CAS plan reject neu Supabase/Drive head doi sau luc user/build snapshot quan sat.
- Sync planner khong merge local/cloud khi Supabase/Drive diverged; divergence phai xu ly truoc revision fast-forward.
- Sync planner chi tao head write plans cho upload/local-copy; restore plan khong duoc ghi cloud head.
- Adapter registry khong cho dang ky cookies/auth sessions/cache/model packages/media downloads.
- Adapter restore phai declare transactional truoc khi duoc registry chap nhan.

## Lenh kiem tra

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotArchiveTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --tests "io.legado.app.domain.usecase.CloudSnapshotRestoreStagingTest" --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotDatasetAdapterTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --tests "io.legado.app.domain.usecase.CloudSnapshotArchiveTest" --tests "io.legado.app.domain.usecase.CloudSnapshotRestoreStagingTest" --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

## Ket qua

- `CloudSnapshotArchiveTest`: 5 tests PASS.
- `CloudSnapshotPolicyTest`: 14 tests PASS.
- `CloudSnapshotRestoreStagingTest`: 3 tests PASS.
- `CloudSnapshotDatasetAdapterTest`: 6 tests PASS.
- `CloudSnapshotConflictResolverTest`: 4 tests PASS.
- `CloudSnapshotConflictUiMapperTest`: 3 tests PASS.
- `CloudSnapshotSyncPlannerTest`: 5 tests PASS.
- Focused snapshot suite: BUILD SUCCESSFUL in 54s sau checkpoint restore staging.
- Focused snapshot suite moi: BUILD SUCCESSFUL in 1m voi 28 tests PASS sau checkpoint adapter/CAS.
- Focused conflict resolver suite: BUILD SUCCESSFUL in 4m39s voi policy/adapter/resolver tests PASS.
- Focused conflict UI mapper suite: BUILD SUCCESSFUL in 1m29s voi resolver/mapper tests PASS.
- Focused sync planner suite: BUILD SUCCESSFUL in 47s voi planner/policy/resolver tests PASS.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 31s sau checkpoint archive.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 33s sau checkpoint adapter/CAS.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 8s sau checkpoint conflict UI sheet.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 6s sau checkpoint sync planner.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotArchiveTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotPolicyTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotRestoreStagingTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotDatasetAdapterTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotSyncPlannerTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.config.backupConfig.CloudSnapshotConflictUiMapperTest.xml`

## 2026-08-01 - Conflict resolver checkpoint

### Thay doi

- Them `CloudSnapshotConflictResolver` de tao prompt/lua chon an toan cho UI/use-case:
  - conflict local/cloud khong auto-merge va hien 3 lua chon: giu local thanh revision moi, restore cloud, hoac luu local thanh cloud copy rieng;
  - target Supabase/Google Drive diverged bat buoc hien hai lua chon restore rieng, co `selectedTarget` cu the;
  - invalid target khong bao gio hien lua chon restore, chi cho giu local hoac luu local copy rieng;
  - fast-forward state co automatic plan, khong can user choice.
- Them `CloudSnapshotConflictResolverTest` khoa cac invariant tren.

### Kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --tests "io.legado.app.domain.usecase.CloudSnapshotDatasetAdapterTest" --console=plain` PASS.

### Ket qua

- Da co domain/use-case bridge de UI conflict sheet sau nay dung ma khong tu auto-merge conflict.
- `TARGETS_DIVERGED` restore target da co selected target bat buoc, tranh restore nham Supabase/Drive trong che do `BOTH`.

## 2026-08-01 - Compose conflict UI checkpoint

### Thay doi

- Them `CloudSnapshotConflictUiMapper` de tach mapping UI/resource khoi domain policy.
- Them `CloudSnapshotConflictSheet` trong `ui/config/backupConfig` de hien prompt xung dot sao luu voi cac lua chon:
  - giu may nay thanh revision moi;
  - restore cloud/Supabase/Google Drive;
  - luu ban local thanh cloud copy rieng.
- Them strings `cloud_snapshot_*` cho `values` va `values-vi`.
- Them `CloudSnapshotConflictUiMapperTest` khoa mapping va plan cho conflict, diverged target va invalid target.

### Kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest" --tests "io.legado.app.ui.config.backupConfig.CloudSnapshotConflictUiMapperTest" --console=plain` PASS.
- `.\gradlew.bat :app:compileAppDebugKotlin --console=plain` PASS.

### Ket qua

- Da co Compose sheet va UI mapper de runtime cloud sync goi khi Supabase/Drive adapter tra conflict state that.
- UI khong tu tao resolution plan bang logic rieng; moi click option di qua `CloudSnapshotConflictResolver`.

## 2026-08-01 - Sync planner checkpoint

### Thay doi

- Them `CloudSnapshotSyncState` de gom mode `SUPABASE`/`GOOGLE_DRIVE`/`BOTH`, base revision, local revision va observed heads.
- Them `CloudSnapshotSyncDecision` gom:
  - `Automatic` khi state co the fast-forward/noop/restore target an toan;
  - `UserChoiceRequired` khi can hien prompt conflict cho UI.
- Them `CloudSnapshotSyncPlanner.decide(...)` de uu tien chan target invalid/diverged truoc khi so sanh revision local-cloud.
- Them `CloudSnapshotSyncPlanner.uploadHeadWritePlans(...)` de tao CAS plans cho upload/local-copy; restore plan bi reject neu goi head write.
- Them `CloudSnapshotSyncPlannerTest` khoa 5 tinh huong: Supabase/Drive diverged, local newer BOTH, target newer single target, invalid target, va restore plan khong duoc ghi head.

### Kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotSyncPlannerTest" --tests "io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --console=plain` PASS.
- `.\gradlew.bat :app:compileAppDebugKotlin --console=plain` PASS.

### Ket qua

- Da co domain bridge giua observed cloud heads, conflict UI prompt va CAS write plan.
- Runtime Supabase/Drive adapter sau nay chi can nap heads/last local revision vao planner de biet nen auto sync hay hien sheet cho user.

## Rui ro/cong viec con lai

- Chua co uploader/downloader runtime cho Supabase Storage va Google Drive appDataFolder.
- Da co domain CAS plan va sync planner; chua co optimistic compare-and-set runtime tren Supabase `sync_heads` va Drive `head.json`.
- Da co domain conflict resolver/use-case prompt, Compose conflict sheet va planner; chua co runtime adapter goi planner voi cloud heads that.
- Da co adapter contract/coverage gate; chua co adapter runtime day du cho Room/file subsystems.
- Chua chay duoc Supabase/Drive runtime scenarios: interrupted upload, concurrent device, corrupt object, RLS ownership va Drive namespace sau restore.
- Cac gate tren tiep tuc nam o P10.T07/P10.T08/P11 regression.
