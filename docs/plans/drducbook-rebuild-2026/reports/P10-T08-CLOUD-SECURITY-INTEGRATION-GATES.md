# P10.T08 - Cloud security va integration gates checkpoint

## Muc tieu

Dong cac gate co the chay local cho Functions, Auth/config, RLS/private Storage, asset delivery, Supabase sync, Drive appDataFolder va snapshot conflict/restore; ghi ro nhung runtime gate can Supabase CLI/Deno/OAuth/device that.

## Pham vi da thuc hien

- Them `scripts/test-cloud-security-gates.mjs` gom cac static/local gates:
  - scan source cloud/app/script khong chua HF token, Supabase service-role secret, ticket secret hoac Google Drive package URL cu;
  - validate HF manifest chi allow-list `Drduc/Legadofork`, hash/path/size/delivery class hop le va khong chua URL/token nhay cam;
  - verify `asset-ticket` khong doc `HF_READ_TOKEN`, chi persist `id_hash`, co JWT subject va rate limit;
  - verify `asset-download` consume ticket one-time truoc khi fetch HF, dung `HF_READ_TOKEN` server-side, normalize `Range` va chan `storage_mirror_required` qua proxy;
  - verify `artifact_tickets` RLS/revoke/RPC service-role one-time consume;
  - verify cloud sync migration bat RLS, private buckets, immutable snapshots va delete cascade.
  - verify Piper voice license review chi clear 25 audited `Apache-2.0` voices, giu 4 voice pending bi ticket guard chan, va Android public catalog/resolver khong expose 4 voice nay.
- Chay lai Node gates co san:
  - `scripts/test-asset-ticket.mjs`
  - `scripts/test-cloud-sync-migration.mjs`
- Chay lai app-side Kotlin cloud contracts:
  - asset delivery
  - HF manifest
  - Supabase public config/client contract
  - Supabase cloud sync storage contract
  - Google Drive appDataFolder scope/metadata contract
  - snapshot conflict/restore policy.

## Lenh kiem tra

```powershell
node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs
.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --no-daemon --console=plain
Get-Command supabase -ErrorAction SilentlyContinue
Get-Command deno -ErrorAction SilentlyContinue
```

## Ket qua

- Node cloud security suite: 11 focused tests PASS, failures = 0 after Piper quarantine checkpoint (`test-asset-ticket.mjs` + `test-cloud-security-gates.mjs`). Full cloud suite truoc do PASS 14/14 sau Piper license review.
- Kotlin cloud contract suite: BUILD SUCCESSFUL in 1m02s.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.AssetDeliveryClientContractTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.HfArtifactManifestTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.CloudSyncClientContractTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.GoogleDriveAppDataContractTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.CloudConsentScopesTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.SupabaseClientProviderTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotPolicyTest.xml`
- `supabase` CLI: khong co trong PATH.
- `deno`: khong co trong PATH.

## Checkpoint 2026-08-01 19:22

- Chay lai Node local/static cloud gates sau checkpoint P11.T05 docs metadata.
- Lenh: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`.
- Ket qua: 13 tests PASS, failures = 0, duration ~993 ms.
- Pham vi bao phu: artifact ticket signing/hash/expiry/range parser; secret leak scan; HF manifest allow-list; Supabase ticket/download function contract; ticket RLS/RPC; cloud sync RLS/private storage/immutable snapshot migration.
- Gioi han: chua thay the Supabase/Deno/Edge Function/Google Drive runtime smoke that.

## Checkpoint 2026-08-01 19:26

- Chay lai Node local/static cloud gates sau khi P10.T01 manifest duoc cap nhat tu metadata-only sang local verified upload pending cho Valtec/Hy-MT2.
- Lenh: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`.
- Ket qua: 13 tests PASS, failures = 0, duration ~738 ms.
- Manifest moi van allow-list `Drduc/Legadofork`, khong co token/Drive URL cu va delivery class hop le.

## Checkpoint 2026-08-01 20:51

- Sau upload HF that va manifest update, chay lai Node local/static cloud gates.
- Lenh: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`.
- Ket qua: 13 tests PASS, failures = 0, duration ~2.24 s.
- Authenticated HF remote verify:
  - revision `main` sha `adc61e3a041893fc38233e02fee3a183bde5083c`;
  - dataset `private: true`;
  - artifactCount 35;
  - `hf_proxy` 35;
  - `storage_mirror_required` 0;
  - `metadata_only_pending_source` 0.
- 4 artifact lon Valtec/Hy-MT2 HEAD HTTP 200 va Content-Length khop manifest.

## Checkpoint 2026-08-02 01:20

- Them gate `Piper voice license review only clears audited Apache voices`.
- Manifest local sau regenerate:
  - Piper `Apache-2.0`: 25.
  - Piper `license-review-required`: 4 (`indo_goreng`, `john`, `mattheo`, `mattheo1`).
  - `local_verified_license_pending`: 4.
- Lenh: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`.
- Ket qua: 14 tests PASS, failures = 0, duration ~28.4s.

## Checkpoint 2026-08-02 07:57

- Them gate `asset ticket endpoint blocks license-pending Piper voices before issuing tickets`.
- Android catalog them `releaseEligibleTtsVoiceCatalog`; 4 Piper voice pending khong con nam trong public catalog/resolver/UI.
- Lenh:
  - `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-security-gates.mjs`.
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AssetDeliveryCatalogResolverTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --console=plain --no-daemon`.
  - `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`.
- Ket qua: Node focused cloud gates PASS 11/11; Android catalog/HF manifest/client contract tests PASS 13/13; rerun gop voi P11.T03 compatibility de giu XML evidence moi nhat PASS 13 test classes / 49 tests; Kotlin compile PASS.
- Rollout verifier static controls moi: `licensePendingTicketBlocked = true`, `publicPiperCatalogFiltered = true`.

## Checkpoint 2026-08-02 11:36

- Lam moi P10 cloud local gates sau cac hotfix Agent/release metadata.
- Lenh:
  - `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --console=plain --no-daemon`
- Ket qua:
  - Node local/static cloud gates PASS 15/15, failures=0, duration ~42.5s.
  - Kotlin cloud contract suite PASS 36/36, failures=0, errors=0; Gradle BUILD SUCCESSFUL in 8m25s.
- XML evidence moi:
  - `TEST-com.drducbook.app.cloud.AssetDeliveryClientContractTest.xml` tests=5.
  - `TEST-io.legado.app.domain.model.HfArtifactManifestTest.xml` tests=3.
  - `TEST-com.drducbook.app.cloud.CloudSyncClientContractTest.xml` tests=5.
  - `TEST-com.drducbook.app.cloud.GoogleDriveAppDataContractTest.xml` tests=5.
  - `TEST-com.drducbook.app.cloud.CloudConsentScopesTest.xml` tests=1.
  - `TEST-com.drducbook.app.cloud.SupabaseClientProviderTest.xml` tests=3.
  - `TEST-io.legado.app.domain.usecase.CloudSnapshotPolicyTest.xml` tests=14.
- Gioi han: van chua thay the Supabase CLI/Deno/Edge Function/Google Drive OAuth runtime smoke that.

## Gate chua the dong trong moi truong hien tai

- Supabase local stack apply migration, Auth JWT expiry/revoke, Edge Function serve/deploy va cross-user RLS/private Storage runtime.
- HF proxy runtime voi secret moi, replay/range/hash qua Edge Function thuc te va Storage mirror signed URL TTL.
- Google Drive AuthorizationClient, revoke/re-consent, account mismatch, upload/download/resumable trong `appDataFolder`.
- Multi-target restore runtime tren hai device/account va conflict UI end-to-end.
- APK/log runtime scan sau khi assemble/release va device smoke day du se chuyen sang P11.
