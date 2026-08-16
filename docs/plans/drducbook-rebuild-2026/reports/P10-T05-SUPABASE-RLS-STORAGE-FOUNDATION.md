# P10.T05 - Supabase Postgres/RLS/Storage foundation checkpoint

## Muc tieu

Tao nen tang Supabase de moi user chi truy cap metadata, snapshot va user asset cua chinh minh; app co contract duong dan/bucket/hash de cac phase sync/restore sau khong tu do ghi path tuy y.

## Pham vi da thuc hien

- Them migration `supabase/migrations/20260731060000_cloud_sync_foundation.sql`.
- Tao bang:
  - `profiles`
  - `cloud_devices`
  - `sync_snapshots`
  - `sync_heads`
  - `sync_events`
- Bat RLS va revoke `anon` tren 100% bang exposed cua foundation.
- Them policy own-user bang `auth.uid()` cho select/insert/update/delete; snapshot va event la immutable bang update policy `using (false)`.
- Tao private Storage buckets:
  - `drducbook-snapshots`
  - `drducbook-user-assets`
- Them Storage object policies theo folder dau tien `{auth.uid()}`.
- Them app-side contract/gateway/usecase:
  - `CloudSyncModels.kt`
  - `CloudSyncGateway.kt`
  - `CloudSyncUseCase.kt`
  - `CloudSyncClientContract.kt`
  - `SupabaseCloudSyncRepository.kt`
  - Koin bindings trong `appModule.kt`
- Them tests:
  - `CloudSyncClientContractTest.kt`
  - `scripts/test-cloud-sync-migration.mjs`

## Invariants da khoa

- Snapshot object path bat buoc: `{user_id}/snapshots/{revision}/{snapshot_id}.drducsnapshot`.
- User assets bat buoc nam trong: `{user_id}/assets/...`.
- `content_sha256` bat buoc la 64 ky tu hex lowercase.
- `content_size_bytes >= 0`, `schema_version > 0`.
- Bucket snapshot metadata chi chap nhan `drducbook-snapshots`.
- App contract tu choi path traversal va UUID sai dinh dang.

## Lenh kiem tra

```powershell
node --test scripts/test-cloud-sync-migration.mjs
.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
rg -n "hf_[A-Za-z0-9]{20,}|supabase.*service.*key|service_role.*eyJ|ASSET_TICKET_SECRET\s*=|HF_READ_TOKEN\s*=|SUPABASE_SERVICE_ROLE_KEY\s*=" app/src/main/java app/src/main/res app/build.gradle.kts gradle/libs.versions.toml supabase/functions supabase/migrations scripts -g "*.kt" -g "*.xml" -g "*.kts" -g "*.toml" -g "*.ts" -g "*.mjs" -g "*.sql" -g "*.ps1"
```

## Ket qua

- Node migration policy tests: 4 PASS.
- Kotlin cloud sync contract tests: BUILD SUCCESSFUL in 2m19s.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 39s.
- Secret scan hep: khong co match.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.CloudSyncClientContractTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.SupabaseClientProviderTest.xml`

## Rui ro/cong viec con lai

- Chua co Supabase CLI/local stack trong PATH, nen chua apply migration de test bang auth users that.
- Cross-user RLS, signed URL expiry, bucket list isolation va delete-account cleanup can P10.T08 runtime gate.
- Sync settings UI day du va conflict/restore se duoc thuc hien o P10.T07.

## Checkpoint 2026-08-02 11:36

- Chay lai local RLS/storage/cloud-sync gates qua P10.T08:
  - `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`: PASS 15/15.
  - `CloudSyncClientContractTest`, `SupabaseClientProviderTest`, `CloudSnapshotPolicyTest` trong Kotlin cloud contract suite: PASS 22/22.
- P10.T05 van IN_PROGRESS vi chua co Supabase CLI/local stack hoac project runtime de apply migration va tao auth users that cho cross-user RLS/bucket isolation.
