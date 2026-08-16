# ADR-008 - Supabase, Hugging Face va Google Drive backup

- Status: Accepted
- Date: 2026-07-29
- Owners: P01, P10, P11

## Context

Package/model/voice cu nam tren Google Drive; user da chon Hugging Face dataset `Drduc/Legadofork`, Supabase backend proxy/Auth/sync va van muon giu sao luu/dong bo Google Drive. HF token da gui trong conversation duoc xem la compromised va khong duoc dua vao app/deploy.

## Decision

### Asset delivery

1. Hugging Face la canonical source cua immutable package/model/voice artifacts. Manifest server-side khoa path, version, size, SHA-256, license va delivery class.
2. `HF_READ_TOKEN` chi nam trong Supabase Edge Function secrets. Function verify Supabase JWT, allow-list manifest va cap opaque one-time ticket gan user/artifact/TTL; GET/HEAD/Range proxy streaming tu HF, khong buffer toan file.
3. Artifact vuot Edge Runtime size/time budget duoc release pipeline mirror byte-identical sang private bucket `artifact-mirror`; function cap signed URL ngan han chi cho path/hash allow-listed. App khong duoc chon arbitrary repo/bucket/path.
4. App verify size/hash va atomic install. Package Google Drive URLs bi xoa; Google Drive khong la delivery fallback.

### Account va authorization

5. Account dung Supabase Auth email/password va Google. Google sign-in exchange ID token/nonce qua Supabase; callback `drducbook://auth/callback`; session do Supabase SDK quan ly.
6. Google Drive la consent rieng, chi xin scope `https://www.googleapis.com/auth/drive.appdata` khi user bat Drive target. Supabase Google login khong dong nghia co Drive access; email account co the link Drive. UI hien ro khi Supabase va Drive identities khac nhau.
7. Supabase publishable key co the nam trong client; moi exposed table/bucket bat RLS. Secret/service-role/HF token chi server-side. Drive access/refresh token dung provider/Android secure session, khong Room plaintext/log/backup.

### Supabase data va Storage

8. Postgres schema v1:
   - `profiles(id uuid PK -> auth.users)`.
   - `devices(id uuid, user_id, name, last_seen_at)`.
   - `snapshots(id uuid, user_id, revision bigint, schema_version, object_path, sha256, size, device_id, created_at, state)`.
   - `sync_heads(user_id, scope, revision, snapshot_id, updated_at)` voi unique `(user_id, scope)`.
   - `artifact_tickets(id_hash, user_id, artifact_id, expires_at, consumed_at)` server-only.
9. RLS ownership dung `auth.uid() = user_id`; user A khong list/read/write user B. Private bucket `user-snapshots` path `{user_id}/{snapshot_id}.snapshot`; bucket `artifact-mirror` chi doc qua signed delivery function.
10. Supabase snapshot head update bang optimistic compare-and-set revision. Retention mac dinh 10 automatic snapshots/30 ngay; manual pinned snapshot theo quota. Account deletion xoa DB rows, Storage prefix va revoke sessions qua audited server flow.

### Google Drive va multi-target snapshot

11. Drive `appDataFolder` luu cung immutable snapshot bytes/manifest va versioned `head.json` trong namespace DrDucBook. Khong tim/doc file backup cua app cu va khong dung broad `drive.file`/`drive` scope.
12. `SnapshotTarget` co `SUPABASE`, `GOOGLE_DRIVE`, `BOTH`. Moi target co head revision doc lap; `BOTH` upload cung snapshot ID/hash. Neu head diverge, dung conflict UI, khong target nao am tham ghi de target kia.
13. Snapshot include source configs da redacted credential fields, reading progress, authoring projects/assets, Agent custom tool source/manifest, manual bookmarks, appearance, Web policy va health summary. Exclude cookies, auth/session/Drive tokens, API keys/Authorization headers, cache, logs, model/media downloads va temporary recovery data.
14. Restore verify manifest/schema/size/hash vao staging va commit transactional. Conflict options: keep local as new revision, restore selected cloud target, hoac save local as separate cloud copy. Khong auto-merge authoring/Agent/source configs.
15. V1 dua vao TLS, provider encryption at rest, private Storage/RLS va strict secret exclusion; khong claim end-to-end encryption. E2EE neu them sau phai la snapshot envelope schema moi va migration co recovery-key UX.

## Public contract

- Supabase client: project URL + publishable key only.
- Edge secrets: `HF_READ_TOKEN`; service-role neu can chi Edge-managed.
- Buckets: `artifact-mirror`, `user-snapshots` private.
- Drive scope duy nhat: `drive.appdata`.
- Snapshot target values: `SUPABASE`, `GOOGLE_DRIVE`, `BOTH`; schema version `1`.
- Token cu trong conversation phai revoke truoc deploy; token moi read-only, rotation khong rebuild app.

## Alternatives

- Firebase/Cloud Run: loai bo theo quyet dinh chuyen backend sang Supabase.
- Chi Supabase, bo Drive: loai bo theo yeu cau giu Google Drive backup.
- Dung HF token trong APK: loai bo vi token co the extract.
- Proxy moi artifact lon qua Edge Function: loai bo neu vuot runtime budget; Storage mirror la duong phat hanh duoc benchmark.
- Mot head chung cho Supabase va Drive: loai bo vi partial failure tao mat du lieu/ghi de.

## Consequences

- He thong co hai authorization lifecycle va hai snapshot transports; UI/tests phai hien account mismatch va target divergence.
- Artifact mirror tang storage/egress nhung tranh Edge timeout cho model lon; HF van la canonical provenance.
- `BOTH` can retry idempotent va co trang thai partial, khong bao thanh cong khi mot target chua commit.
- Login hoat dong khong can Drive; Drive backup hoat dong theo consent/revoke rieng.

## Rollback

- Tat delivery ticket/mirror bang feature flag va pin manifest cu; khong fallback sang Drive package URL.
- Tat Supabase sync hoac Drive target doc lap, giu local data va immutable snapshots.
- Rollback migration bang forward fix; khong ha Postgres/schema snapshot da publish.
- Khi Edge secret bi nghi lo, revoke/rotate va invalidate tickets; app khong can rebuild.

## Tai lieu chuan

- Supabase Kotlin: https://supabase.com/docs/reference/kotlin/installing
- Supabase Auth Google: https://supabase.com/docs/guides/auth/social-login/auth-google?platform=android
- Edge Function secrets/auth: https://supabase.com/docs/guides/functions/secrets va https://supabase.com/docs/guides/functions/auth
- Supabase private Storage/RLS: https://supabase.com/docs/guides/storage/buckets/fundamentals
- Google Drive appDataFolder: https://developers.google.com/workspace/drive/api/guides/appdata
